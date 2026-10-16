#!/usr/bin/env python3
"""Scalar-function goldens straight from the ORIGINAL routines (reference emulator): work/golden/fn.txt.
Line: NAME arg... = ret   (all signed 32-bit);  'NAME arg... = ret | o0 o1 o2 o3' for functions with a 4-int output block (last arg is that pointer).
Usage: python3 tools/e_fn.py <binary.mod> <res dir> [out dir]"""
import os
import random
import struct
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from and_hle import Harness


def s32(v):
    v &= 0xffffffff
    return v - (1 << 32) if v & 0x80000000 else v


def main():
    binp, res = sys.argv[1], sys.argv[2]
    out = sys.argv[3] if len(sys.argv) > 3 else os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'work', 'golden')
    os.makedirs(out, exist_ok=True)
    h = Harness(binp, res)
    rng = random.Random(7)
    buf = h.alloc(64)
    lines = []

    def scalar(name, args):
        r = h.call(name, *[a & 0xffffffff for a in args])
        lines.append('%s %s = %d' % (name, ' '.join(str(a) for a in args), s32(r)))

    for a in range(0, 361):
        scalar('UM_sin', (a,))
        scalar('UM_cos', (a,))
    for a in (361, 400, 1000):
        scalar('UM_sin', (a,))
        scalar('UM_cos', (a,))
    for a in range(0, 361, 7):
        for r in (0, 1, 7, 50, 160, 333):
            scalar('UM_getAngleY', (a, r))
            scalar('UM_getAngleX', (a, r))
    for d in range(-1, 8):
        scalar('UM_vecX', (d,))
        scalar('UM_vecY', (d,))
        scalar('UM_turnOppositeDirection', (d,))
        scalar('UM_getAngleFor8Dir', (d,))
    for a in range(0, 361):
        scalar('UM_get8DirForAngle', (a,))
    for a in range(0, 26):
        scalar('UM_getDir24Anglendex', (a,))
    for _ in range(3000):
        x1, y1, x2, y2 = [rng.randint(-40, 40) if rng.random() < .7 else rng.randint(-400, 400) for _ in range(4)]
        if rng.random() < .15:
            x2 = x1
        if rng.random() < .15:
            y2 = y1
        if rng.random() < .1:
            x2, y2 = x1 + 9, y1 - 9
        scalar('UM_turnDirection', (x1, y1, x2, y2))
        scalar('UM_direction8', (x1, y1, x2, y2))
    for _ in range(6000):
        v = [rng.randint(-8, 30) for _ in range(8)]
        if rng.random() < .3:
            v[0] = v[4]
        if rng.random() < .3:
            v[1] = v[5]
        if rng.random() < .3:
            v[0] = v[4] + v[6]
        if rng.random() < .3:
            v[1] = v[5] + v[7]
        if rng.random() < .1:
            v[rng.randint(2, 3)] = 0
        for i in (2, 3, 6, 7):
            if rng.random() < .85:
                v[i] = abs(v[i])
        h.write(buf, b'\xaa' * 16)
        r = h.call('UM_Collision_Two_Rect', *[a & 0xffffffff for a in v], buf)
        o = struct.unpack('<4i', h.read(buf, 16))
        lines.append('UM_Collision_Two_Rect %s = %d | %s' % (' '.join(map(str, v)), s32(r), ' '.join(map(str, o))))
    open(os.path.join(out, 'fn.txt'), 'w').write('\n'.join(lines) + '\n')
    print('fn goldens', len(lines))


main()
