#!/usr/bin/env python3
"""contact sheet: jsheet.py <dir> <out.png> [start_index] [count] [scale]"""
import sys, os, glob
from PIL import Image, ImageDraw
d = sys.argv[1]; out = sys.argv[2]
start = int(sys.argv[3]) if len(sys.argv) > 3 else 0
cnt = int(sys.argv[4]) if len(sys.argv) > 4 else 40
sc = float(sys.argv[5]) if len(sys.argv) > 5 else 0.5
fs = sorted(glob.glob(os.path.join(d, 'f*.png')))[start:start + cnt]
if not fs:
    print('no frames'); sys.exit()
w, h = int(240 * sc), int(320 * sc)
cols = min(8, len(fs)); rows = (len(fs) + cols - 1) // cols
sheet = Image.new('RGB', (cols * w, rows * (h + 12)), (40, 40, 40))
dr = ImageDraw.Draw(sheet)
for i, f in enumerate(fs):
    im = Image.open(f).convert('RGB').resize((w, h))
    x, y = (i % cols) * w, (i // cols) * (h + 12)
    sheet.paste(im, (x, y + 12))
    dr.text((x + 2, y), os.path.basename(f)[1:-4].lstrip('0') or '0', fill=(255, 255, 0))
sheet.save(out)
print(out, len(fs))
