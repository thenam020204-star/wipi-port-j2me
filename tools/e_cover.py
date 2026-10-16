#!/usr/bin/env python3
"""Per-tick function coverage of the reference game: tools/e_cover.py <binary.mod> <resdir> <out.tsv> <from_tick> <to_tick> [tick:key ...]
Coverage counting is switched on at <from_tick> (before that the run is unhooked = fast); output: function, first tick seen, entry count."""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from ref_game import Game

binp, resdir, out, t0, t1 = sys.argv[1], sys.argv[2], sys.argv[3], int(sys.argv[4]), int(sys.argv[5])
import ref_game
script = ref_game.parse_script(sys.argv[6:])
g = Game(binp, resdir).start()
first = {}
for i in range(t1):
    if i == t0:
        g.enable_cover()
    ref_game.tick_script(g, script, i)
    if i >= t0:
        for n in g.cover:
            first.setdefault(n, i)
        print(i, len(first), flush=True)
with open(out, 'w') as f:
    for n, t in sorted(first.items(), key=lambda x: (x[1], x[0])):
        f.write('%s\t%d\t%d\n' % (n, t, g.cover[n]))
