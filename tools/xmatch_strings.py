#!/usr/bin/env python3
"""Cross-match KTF client.bin <-> Android libbinary.so by shared C strings.
For each string present in both, find GOT-relative literals referencing it
(KTF: lit = str - 0x88bb4 image-relative; SO: lit = str - GOT 0xe01a8) and
report SO function owning the reference -> candidate name for KTF code site.
Usage: xmatch_strings.py <client.bin> <libbinary.so> [out.tsv]"""
import sys, struct, re, collections, bisect
from elftools.elf.elffile import ELFFile
KIMG, KGOT, SGOT = 0x1104, 0x88bb4, 0xe01a8
img = open(sys.argv[1], 'rb').read()[KIMG:]
so = open(sys.argv[2], 'rb').read()
e = ELFFile(open(sys.argv[2], 'rb'))
funcs = sorted((s['st_value'] & ~1, s['st_size'], s.name) for s in e.get_section_by_name('.symtab').iter_symbols()
               if s['st_info']['type'] == 'STT_FUNC' and s['st_size'])
starts = [f[0] for f in funcs]
def owner(a):
    i = bisect.bisect_right(starts, a) - 1
    return funcs[i][2] if i >= 0 and a < funcs[i][0] + funcs[i][1] else None
def words(buf):
    d = collections.defaultdict(list)
    for o in range(0, len(buf) - 3, 4):
        d[struct.unpack_from('<I', buf, o)[0]].append(o)
    return d
KW, SW = words(img), words(so)
ro = e.get_section_by_name('.rodata')
lo, hi = ro['sh_offset'], ro['sh_offset'] + ro['sh_size']
sostr = {}
o = lo
while o < hi:
    z = so.find(b'\0', o)
    if z < 0 or z >= hi: break
    if z - o >= 3: sostr.setdefault(so[o:z], o)  # rodata vaddr == offset in this .so
    o = z + 1
rows = []; ambiguous = 0
for s, so_o in sostr.items():
    ko = img.find(b'\0' + s + b'\0')
    if ko < 0: continue
    if img.find(b'\0' + s + b'\0', ko + 1) >= 0: ambiguous += 1
    ko += 1
    kr = KW.get((ko - KGOT) & 0xffffffff, [])
    sr = SW.get((so_o - SGOT) & 0xffffffff, [])
    names = sorted({owner(x) for x in sr} - {None})
    for r in kr:
        rows.append((r, ko, ','.join(names), s.decode('euc-kr', 'replace')))
rows.sort()
print('SO rodata strings', len(sostr), 'found in KTF', sum(1 for s in sostr if img.find(b'\0' + s + b'\0') >= 0),
      'ambiguous', ambiguous)
print('KTF literal sites', len(rows), 'with SO owner', sum(1 for r in rows if r[2]),
      'distinct SO names', len({n for r in rows for n in r[2].split(',') if n}))
if len(sys.argv) > 3:
    with open(sys.argv[3], 'w', encoding='utf-8') as f:
        f.write('ktf_lit_site\tktf_str\tso_functions\tstring\n')
        for r in rows:
            f.write('%#07x\t%#07x\t%s\t%s\n' % (r[0], r[1], r[2], r[3].replace('\t', ' ').replace('\n', '\\n')))
