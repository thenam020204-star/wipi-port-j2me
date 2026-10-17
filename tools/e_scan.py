#!/usr/bin/env python3
"""Map scan of the ORIGINAL (T0.4: which map exercises which module): for each map id, start from a snapshot, warp there
(Event_moveNextSceneWithWeatherTime, the 'w' scenario token), run N ticks pressing OK every K ticks with the hero healed (fixture 'H'), and
count the entries of every function whose name starts with one of the given prefixes.

Usage: e_scan.py <binary.mod> <res> <snapshot> <out.tsv> <map ids: 0-96 or 3,5,9> <prefix,prefix..> [ticks=80] [tileX=7] [tileY=15]
Output: map<TAB>prefix=count ...<TAB>top functions<TAB>error.  Read-only reference tool (snapshots: tools/e_snap.py)."""
import os
import sys
from collections import Counter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from unicorn import UC_HOOK_CODE
import e_snap


def ids(spec):
    out = []
    for part in spec.split(','):
        if '-' in part:
            a, b = part.split('-')
            out += list(range(int(a), int(b) + 1))
        else:
            out.append(int(part))
    return out


def main():
    binp, res, snap, outp, maps, prefixes = sys.argv[1:7]
    prefixes = prefixes.split(',')
    n = int(sys.argv[7]) if len(sys.argv) > 7 else 80
    tx = int(sys.argv[8]) if len(sys.argv) > 8 else 7
    ty = int(sys.argv[9]) if len(sys.argv) > 9 else 15
    out = open(outp, 'w')
    g, t0 = e_snap.load(binp, res, snap)
    d = e_snap.read(snap)
    cnt = Counter()
    targets = {a: name for name, (a, s, th) in g.elf.fbyname.items() if any(name.startswith(p) for p in prefixes)}

    def hook(uc, addr, size, ud):
        cnt[targets[addr]] += 1
    for a in targets:
        g.uc.hook_add(UC_HOOK_CODE, hook, begin=a, end=a)
    for mid in ids(maps):
        e_snap.restore(g, d)
        cnt.clear()
        err = ''
        try:
            g.call('Event_moveNextSceneWithWeatherTime', mid, tx, ty, 0, 0xffffffff)
            hp = g.objaddr('_pool')
            for i in range(n):
                g.write(hp + 0xa4, g.read(hp + 0xa8, 4))
                k = -5 if i % 7 == 6 else None
                g.tick(press=k, release=k)
        except Exception as ex:
            err = str(ex)[:120]
        fam = Counter()
        for f, c in cnt.items():
            for p in prefixes:
                if f.startswith(p):
                    fam[p] += c
        top = ' '.join('%s:%d' % x for x in sorted(cnt.items(), key=lambda x: -x[1])[:12])
        out.write('%d\t%s\t%s\t%s\n' % (mid, ' '.join('%s=%d' % (p, fam[p]) for p in prefixes if fam[p]), top, err))
        out.flush()
        print(mid, dict(fam), err, flush=True)


if __name__ == '__main__':
    main()
