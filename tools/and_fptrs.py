#!/usr/bin/env python3
"""Address-taken functions in AND2011: function pointers stored in data sections
(tables) and functions whose address is loaded via GOT/GOTOFF in code.
Usage: python3 tools/and_fptrs.py <binary.mod> <out.tsv>"""
import os, sys, struct, collections
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from andelf import AndElf
e = AndElf(sys.argv[1])
starts = {a: n for a, sz, n, t in e.funcs}
rows = []
for sec in ('.data', '.data.rel.ro', '.got'):
    s = e.e.get_section_by_name(sec)
    d = s.data()
    for o in range(0, len(d) - 3, 4):
        v = struct.unpack_from('<I', d, o)[0]
        if v in starts:
            va = s['sh_addr'] + o
            rows.append((sec, va, e.obj_at(va) or '', starts[v]))
# group consecutive data entries into tables
with open(sys.argv[2], 'w') as f:
    f.write('section\tslot_vaddr\tobject\tfunction\n')
    for r in rows: f.write('%s\t%#07x\t%s\t%s\n' % r)
tables = collections.OrderedDict()
for sec, va, obj, fn in rows:
    if sec == '.got': continue
    key = obj.split('+')[0] or '%s@%#x' % (sec, va & ~0xff)
    tables.setdefault(key, []).append(fn)
print('data fptr slots', sum(1 for r in rows if r[0] != '.got'), 'GOT fptr slots', sum(1 for r in rows if r[0] == '.got'))
for k, v in tables.items():
    print('%-28s %3d  %s' % (k, len(v), ' '.join(v)[:400]))
