#!/usr/bin/env python3
"""Particle array of the ORIGINAL after the given ticks: e_part.py <binary.mod> <res> <t1,t2,..> [tick:key ...]  -> 'T n' then one hex line per particle (0x28 bytes)"""
import os, struct, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from ref_game import Game
import ref_game
binp, res, ticks = sys.argv[1], sys.argv[2], [int(x) for x in sys.argv[3].split(',')]
script = ref_game.parse_script(sys.argv[4:])
g = Game(binp, res).start()
for i in range(max(ticks) + 1):
    ref_game.tick_script(g, script, i)
    if i in ticks:
        n = struct.unpack('<h', g.read(g.objaddr('m_curParticlesCnt'), 2))[0]
        p = struct.unpack('<I', g.read(g.objaddr('Paticles'), 4))[0]
        print('T', i, n)
        for k in range(n):
            print(g.read(p + k * 0x28, 0x28).hex())
