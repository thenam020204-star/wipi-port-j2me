#!/usr/bin/env python3
"""Compare the reference frame at tick N with a Java PPM (TraceTest --ppm): prints the differing bounding box and writes a side-by-side PNG.
Usage: python3 tools/e_diff.py <binary.mod> <res> <ticks N> <java.ppm> <out.png> [tick:key ...]"""
import os
import sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from ref_game import Game
from PIL import Image

binp, res, n, ppm, out = sys.argv[1], sys.argv[2], int(sys.argv[3]), sys.argv[4], sys.argv[5]
script = {}
for kv in sys.argv[6:]:
    t, k = kv.split(':')
    script[int(t)] = int(k, 0)
g = Game(binp, res).start()
for i in range(n + 1):
    k = script.get(i)
    g.tick(press=k, release=k) if k is not None else g.tick()
w, h, px = g.frame()
ref = Image.new('RGB', (w, h))
ref.putdata([(((v >> 11) & 31) * 255 // 31, ((v >> 5) & 63) * 255 // 63, (v & 31) * 255 // 31) for v in px])
jv = Image.open(ppm).convert('RGB')
d = Image.new('RGB', (w, h))
dp = []
box = [w, h, 0, 0]
for y in range(h):
    for x in range(w):
        a, b = ref.getpixel((x, y)), jv.getpixel((x, y))
        if a != b:
            dp.append((255, 0, 0))
            box = [min(box[0], x), min(box[1], y), max(box[2], x), max(box[3], y)]
        else:
            dp.append((a[0] // 3, a[1] // 3, a[2] // 3))
d.putdata(dp)
n_diff = sum(1 for p in dp if p == (255, 0, 0))
print('differing pixels', n_diff, 'bbox', box)
sheet = Image.new('RGB', (w * 3, h))
sheet.paste(ref, (0, 0)); sheet.paste(jv, (w, 0)); sheet.paste(d, (2 * w, 0))
sheet.save(out)
