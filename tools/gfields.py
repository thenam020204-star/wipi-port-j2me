#!/usr/bin/env python3
"""Global-variable field census from the annotated disassembly (andelf.annotate resolves GOT-relative global addresses).
Usage: gfields.py <binary.mod> <global name regex> [min count]   (cache: work/cache/gmem.pkl)
Prints for every byte offset inside the matching globals: access kinds (R/W + width/sign) with counts and the first functions."""
import os, re, sys, pickle, collections
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from andelf import AndElf

binp = sys.argv[1]
rx = re.compile(sys.argv[2])
minc = int(sys.argv[3]) if len(sys.argv) > 3 else 1
cache = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'work', 'cache', 'gmem.pkl')
W = {'ldr': 'w32', 'str': 'w32', 'ldrb': 'u8', 'strb': 'w8', 'ldrsb': 's8', 'ldrh': 'u16', 'strh': 'w16', 'ldrsh': 's16'}
e = AndElf(binp)
if os.path.exists(cache):
    rec = pickle.load(open(cache, 'rb'))
else:
    rec = []
    for a, sz, n, t in e.funcs:
        if t or sz == 0:
            continue
        for i, note in e.annotate(n):
            if note.startswith('mem ') and i.mnemonic in W:
                rec.append((n, i.mnemonic, int(note.split()[1], 16), i.address))
    os.makedirs(os.path.dirname(cache), exist_ok=True)
    pickle.dump(rec, open(cache, 'wb'))
census = collections.defaultdict(lambda: collections.defaultdict(list))
for fn, m, addr, pc in rec:
    nm = e.obj_at(addr)
    if not nm:
        continue
    base = nm.split('+')[0]
    if not rx.search(base):
        continue
    kind = ('R' if m.startswith('ldr') else 'W') + W[m]
    census[(base, addr)][kind].append(fn)
for (base, addr) in sorted(census, key=lambda k: k[1]):
    cnt = sum(len(v) for v in census[(base, addr)].values())
    if cnt < minc:
        continue
    o = [x for x in e.objs if x[2] == base][0]
    kinds = ' '.join('%s:%d' % (k, len(v)) for k, v in sorted(census[(base, addr)].items()))
    fn = sorted(set(f for v in census[(base, addr)].values() for f in v))
    print('%s+%#x  %-34s %3d fn  %s' % (base, addr - o[0], kinds, len(fn), ' '.join(fn[:4])))
