#!/usr/bin/env python3
"""Chroisen image containers (image/*.dat): u32 n, n x (u32 size, u32 offset from the data start), data
(data start = 4 + 8n; each entry a PNG, size 0 = empty slot).

  datpack.py unpack <file.dat> <out_dir>               -> out_dir/NN.png
  datpack.py pack <orig.dat> <out.dat> [NN=new.png ...] -> replace entries (others byte-identical)
  datpack.py sheet <file.dat> <out.png>                 -> contact sheet with entry numbers
"""
import sys, os, struct, io


def read(path):
    d = open(path, 'rb').read()
    n = struct.unpack_from('<I', d, 0)[0]
    base = 4 + 8 * n
    ents = []
    for i in range(n):
        size, off = struct.unpack_from('<II', d, 4 + 8 * i)
        ents.append(d[base + off:base + off + size])
    return ents


def write(path, ents):
    hdr = struct.pack('<I', len(ents))
    off, body = 0, b''
    for e in ents:
        hdr += struct.pack("<II", len(e), off)  # empty slot: running offset (as in the originals)
        body += e
        off += len(e)
    open(path, 'wb').write(hdr + body)


def main():
    cmd = sys.argv[1]
    if cmd == 'unpack':
        os.makedirs(sys.argv[3], exist_ok=True)
        for i, e in enumerate(read(sys.argv[2])):
            if e: open(os.path.join(sys.argv[3], '%02d.png' % i), 'wb').write(e)
    elif cmd == 'pack':
        ents = read(sys.argv[2])
        for a in sys.argv[4:]:
            k, p = a.split('=', 1)
            ents[int(k)] = open(p, 'rb').read()
        write(sys.argv[3], ents)
        # round trip check of untouched entries
        new = read(sys.argv[3])
        assert len(new) == len(ents) and all(x == y for x, y in zip(new, ents))
    elif cmd == 'sheet':
        from PIL import Image, ImageDraw
        ims = []
        for i, e in enumerate(read(sys.argv[2])):
            if e: ims.append((i, Image.open(io.BytesIO(e)).convert('RGBA')))
        W = 480; x = y = rowh = 0; pos = []
        for i, im in ims:
            if x + im.width > W: x = 0; y += rowh + 14; rowh = 0
            pos.append((i, im, x, y)); x += im.width + 6; rowh = max(rowh, im.height)
        sheet = Image.new('RGB', (W, y + rowh + 14), (60, 60, 90))
        dr = ImageDraw.Draw(sheet)
        for i, im, x, y in pos:
            sheet.paste(im, (x, y + 12), im)
            dr.text((x, y), str(i), fill=(255, 255, 0))
        sheet.save(sys.argv[3])
    else:
        raise SystemExit(__doc__)


if __name__ == '__main__':
    main()
