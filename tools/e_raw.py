#!/usr/bin/env python3
"""Raw RGB565 dumps of the ORIGINAL game at chosen ticks: <outdir>/s<tick>.raw (game area 240x320) and b<tick>.raw (back buffer).
  e_raw.py <binary.mod> <res> <outdir> <t1,t2,..> [tick:key ...]
  e_raw.py cmp <a.raw> <b.raw> [w=240]   -> bounding boxes of differing pixels (coarse 16x16 cell map)"""
import os, struct, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))


def cmp(a, b, w):
    A = open(a, 'rb').read(); B = open(b, 'rb').read()
    n = min(len(A), len(B)) // 2
    h = n // w
    cells = {}
    first = None
    cnt = 0
    for i in range(n):
        if A[2 * i:2 * i + 2] != B[2 * i:2 * i + 2]:
            x, y = i % w, i // w
            cnt += 1
            if first is None:
                first = (x, y, A[2 * i] | A[2 * i + 1] << 8, B[2 * i] | B[2 * i + 1] << 8)
            cells[(x // 16, y // 16)] = cells.get((x // 16, y // 16), 0) + 1
    print('%d pixels differ of %d; first (x,y,a,b)=%s' % (cnt, n, first))
    if cells:
        for cy in range((h + 15) // 16):
            print(''.join('#' if (cx, cy) in cells else '.' for cx in range((w + 15) // 16)))


def main():
    if sys.argv[1] == 'cmp':
        cmp(sys.argv[2], sys.argv[3], int(sys.argv[4]) if len(sys.argv) > 4 else 240)
        return
    from ref_game import Game
    binp, res, out, ticks = sys.argv[1], sys.argv[2], sys.argv[3], [int(x) for x in sys.argv[4].split(',')]
    import ref_game
    script = ref_game.parse_script(sys.argv[5:])
    os.makedirs(out, exist_ok=True)
    import e_snap
    g, first = e_snap.boot(binp, res)        # env SNAPLOAD: tools/e_snap.py (T0.3)
    for i in range(first, max(ticks) + 1):
        ref_game.tick_script(g, script, i)
        if i in ticks:
            w, h, px = g.frame()
            open('%s/s%04d.raw' % (out, i), 'wb').write(b''.join(struct.pack('<H', v) for v in px[:240 * 320]))
            bw, bh, bp = g.back_area()
            open('%s/b%04d.raw' % (out, i), 'wb').write(b''.join(struct.pack('<H', v) for v in bp[:240 * 320]))
            print('tick', i, 'frame', w, h, 'back', bw, bh)

main()
