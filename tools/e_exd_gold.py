#!/usr/bin/env python3
"""Golden for hl4.gfx.SprProps: the verified Python model (tools/exd.py) over every H4/EXD file -> work/golden/sprprops.txt (one line per file: hdr, n, idx nonzero, cells)."""
import os, struct, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import exd
res = sys.argv[1]
out = []
for root in ('H4/EXD',):
    d = os.path.join(res, root)
    for fn in sorted(os.listdir(d)):
        m = exd.parse(open(os.path.join(d, fn), 'rb').read())
        parts = [fn, ' '.join(map(str, m['hdr'])), str(m['n']), ','.join('%d:%d' % (i, v) for i, v in enumerate(m['idx']) if v)]
        cells = []
        for k, sl in enumerate(m['slots']):
            if sl is None:
                cells.append('H')
                continue
            for (eid, nB, cs) in sl:
                for c in cs:
                    cells.append('%d.%d.%d.t%d:%s' % (k, eid, nB, c[1], ','.join(map(str, struct.unpack_from('<16i', c, 4)))))
        parts.append(' '.join(cells))
        out.append('|'.join(parts))
open(os.path.join('work', 'golden', 'sprprops.txt'), 'w').write('\n'.join(out) + '\n')
print(len(out))
