#!/usr/bin/env python3
"""Print named globals of the ORIGINAL after each tick.  Usage: e_probe.py <binary.mod> <res> <from> <to> name:size[:off][,..] [tick:key ...]  (sizes 1/2/4, hex printed)"""
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from ref_game import Game
binp, res, t0, t1, names = sys.argv[1], sys.argv[2], int(sys.argv[3]), int(sys.argv[4]), [(x.split(':') + ['0'])[:3] for x in sys.argv[5].split(',')]
script = {}
for kv in sys.argv[6:]:
    t, k = kv.split(':'); script[int(t)] = int(k, 0)
g = Game(binp, res).start()
for i in range(t1 + 1):
    k = script.get(i)
    g.tick(press=k, release=k) if k is not None else g.tick()
    if i >= t0:
        print(i, ' '.join('%s+%s=%s' % (n, off, g.read(g.objaddr(n) + int(off, 0), int(sz)).hex()) for n, sz, off in names))
