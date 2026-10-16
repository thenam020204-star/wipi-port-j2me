#!/usr/bin/env python3
"""raw2png.py <in.raw> <out.png> [w=240] : RGB565 LE dump -> PNG;  raw2png.py side <a.raw> <b.raw> <out.png> : two dumps side by side"""
import struct, sys, zlib


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


def load(p):
    d = open(p, 'rb').read()
    return list(struct.unpack('<%dH' % (len(d) // 2), d))


if sys.argv[1] == 'side':
    a, b = load(sys.argv[2]), load(sys.argv[3])
    w = 240
    h = len(a) // w
    px = []
    for y in range(h):
        px += a[y * w:(y + 1) * w] + [0xffff] * 4 + b[y * w:(y + 1) * w]
    png(sys.argv[4], 2 * w + 4, h, px)
else:
    w = int(sys.argv[3]) if len(sys.argv) > 3 else 240
    a = load(sys.argv[1])
    png(sys.argv[2], w, len(a) // w, a)
