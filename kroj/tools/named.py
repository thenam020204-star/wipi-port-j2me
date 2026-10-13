# named.py <dir> <out.png> name1 name2 ... : grid of named screenshots (240x320), 5 per row, labelled
import sys, os
from PIL import Image, ImageDraw
d, out, names = sys.argv[1], sys.argv[2], sys.argv[3:]
if not names: names = sorted(f[:-4] for f in os.listdir(d) if f.endswith('.png') and not f.startswith('f0'))
cols = 5; rows = (len(names) + cols - 1) // cols
im = Image.new('RGB', (cols * 242, rows * 334), (40, 40, 40)); dr = ImageDraw.Draw(im)
for i, n in enumerate(names):
    p = os.path.join(d, n + '.png')
    if not os.path.exists(p): continue
    x, y = (i % cols) * 242, (i // cols) * 334
    im.paste(Image.open(p).convert('RGB'), (x, y + 14)); dr.text((x + 2, y + 1), n, fill=(255, 255, 0))
im.save(out)
