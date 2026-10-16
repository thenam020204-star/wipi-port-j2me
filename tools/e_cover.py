#!/usr/bin/env python3
"""Per-tick function coverage of the reference game: tools/e_cover.py <binary.mod> <resdir> <out.tsv> <from_tick> <to_tick> [tick:key ...]
Coverage counting is switched on at <from_tick> (before that the run is unhooked = fast); output: function, first tick seen, entry count."""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from ref_game import Game

binp, resdir, out, t0, t1 = sys.argv[1], sys.argv[2], sys.argv[3], int(sys.argv[4]), int(sys.argv[5])
script = {}
for kv in sys.argv[6:]:
    t, k = kv.split(':')
    script[int(t)] = int(k, 0)
g = Game(binp, resdir).start()
first = {}
for i in range(t1):
    if i == t0:
        g.enable_cover()
    k = script.get(i)
    g.tick(press=k, release=k) if k is not None else g.tick()
    if i >= t0:
        for n in g.cover:
            first.setdefault(n, i)
        print(i, len(first), flush=True)
with open(out, 'w') as f:
    for n, t in sorted(first.items(), key=lambda x: (x[1], x[0])):
        f.write('%s\t%d\t%d\n' % (n, t, g.cover[n]))
