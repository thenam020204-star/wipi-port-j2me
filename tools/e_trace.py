#!/usr/bin/env python3
"""Per-tick golden trace of the ORIGINAL game (tools/ref_game.py): FNV-1a of the game-area RGB565 bytes + number of UM_Random calls.
Usage: python3 tools/e_trace.py <binary.mod> <res dir> <out.txt> <ticks> [tick:key ...]
Scenario keys are WIPI key codes delivered as press+release in that tick (-5 = OK, -1/-2 up/down, 0x31 = '1' ...).
Header lines start with '#': the reference configuration (ref_game.IMPL) so a trace states what it depended on."""
import os
import struct
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ref_game
from ref_game import Game


def fnv(b):
    h = 0x811c9dc5
    for c in b:
        h = ((h ^ c) * 0x01000193) & 0xffffffff
    return h


def main():
    binp, res, out, n = sys.argv[1], sys.argv[2], sys.argv[3], int(sys.argv[4])
    script = {}
    for kv in sys.argv[5:]:
        t, k = kv.split(':')
        script[int(t)] = int(k, 0)
    g = Game(binp, res).start()
    lines = ['# ref_game.IMPL: ' + k + ' = ' + v for k, v in ref_game.IMPL.items()]
    lines.append('# clock base %d ms, tick %d ms; scenario %s' % (ref_game.CLOCK_BASE_MS, g.tick_ms, ' '.join(sys.argv[5:])))
    snap = set(int(x) for x in os.environ.get('SNAP', '').split(',') if x)
    for i in range(n):
        if i in snap:
            # RNG state before this tick: BSD randtbl (32 words, [0] = type marker), fptr/rptr as indices into the 31-word state
            tb = g.objaddr('_randtbl')
            words = struct.unpack('<32I', g.read(tb, 128))
            fp, rp = struct.unpack('<II', g.read(g.objaddr('_fptr'), 4) + g.read(g.objaddr('_rptr'), 4))
            fp = struct.unpack('<I', g.read(g.objaddr('_fptr'), 4))[0]
            rp = struct.unpack('<I', g.read(g.objaddr('_rptr'), 4))[0]
            lines.append('# snap %d calls %d f %d r %d %s' % (i, g.rng_calls, (fp - tb) // 4 - 1, (rp - tb) // 4 - 1, ' '.join('%08x' % w for w in words[1:])))
        k = script.get(i)
        g.tick(press=k, release=k) if k is not None else g.tick()
        w, h, px = g.frame()
        lines.append('%d %08x %d' % (i, fnv(struct.pack('<%dH' % len(px), *px)), g.rng_calls))
    lines.append('# missing imports: %r' % g.missing)
    open(out, 'w').write('\n'.join(lines) + '\n')


main()
