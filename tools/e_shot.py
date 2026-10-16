#!/usr/bin/env python3
"""Screenshots of the ORIGINAL game at chosen ticks.  Usage: e_shot.py <binary.mod> <res> <outdir> <t1,t2,..> [tick:key ...]  (PNG, RGB565 -> RGB888)"""
import os, struct, sys, zlib
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from ref_game import Game


def png(path, w, h, px):
    raw = bytearray()
    for y in range(h):
        raw.append(0)
        for x in range(w):
            v = px[y * w + x]
            r = (v >> 11) & 31; g = (v >> 5) & 63; b = v & 31
            raw += bytes(((r << 3) | (r >> 2), (g << 2) | (g >> 4), (b << 3) | (b >> 2)))
    def ch(t, d):
        c = struct.pack('>I', len(d)) + t + d
        return c + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
    open(path, 'wb').write(b'\x89PNG\r\n\x1a\n' + ch(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 2, 0, 0, 0)) + ch(b'IDAT', zlib.compress(bytes(raw))) + ch(b'IEND', b''))


def main():
    binp, res, out, ticks = sys.argv[1], sys.argv[2], sys.argv[3], [int(x) for x in sys.argv[4].split(',')]
    script = {}
    for kv in sys.argv[5:]:
        t, k = kv.split(':'); script[int(t)] = int(k, 0)
    os.makedirs(out, exist_ok=True)
    g = Game(binp, res).start()
    for i in range(max(ticks) + 1):
        k = script.get(i)
        g.tick(press=k, release=k) if k is not None else g.tick()
        if i in ticks:
            w, h, px = g.frame()
            png('%s/t%04d.png' % (out, i), w, h, px)

main()
