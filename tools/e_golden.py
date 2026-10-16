#!/usr/bin/env python3
"""Phase E golden vectors taken DIRECTLY from the ORIGINAL AND2011 routines run in the reference emulator (tools/ref_game.py).
(Phase D goldens came from Python models; here the original is the oracle itself.)
Writes work/golden/<kind>.txt.   Usage: python3 tools/e_golden.py <kind> <binary.mod> <res dir> [out dir]     kinds: hf
Hash = FNV-1a 32 over the RGB565 little-endian bytes of the 240x400 frame (hl4.test.Fnv.shorts)."""
import os
import random
import struct
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from ref_game import Game


def fnv(b):
    h = 0x811c9dc5
    for c in b:
        h = ((h ^ c) * 0x01000193) & 0xffffffff
    return h


def bg_bytes(w=240, h=400):
    """Deterministic non-trivial background (formula mirrored in the Java tests)."""
    out = bytearray()
    for y in range(h):
        for x in range(w):
            v = ((x * 31 + y * 17) ^ ((x >> 3) * 257)) & 0xffff
            out += struct.pack('<H', v)
    return bytes(out)


def boot(binp, res):
    g = Game(binp, res)
    g.boot_dm()
    g.call('HF_Initialize')
    return g


PGC = None


def gen_hf(binp, res, out, n=6000):
    g = boot(binp, res)
    rng = random.Random(20260615)
    fb_ptr = g.frames[g.screen][0]
    bg = bg_bytes()
    pgc = g.objaddr('_lcdG') + 0x18
    sem_addr = g.objaddr('bSemicolon')
    fc_addr = g.objaddr('_fontColor')
    acc_addr = g.objaddr('_applyColorChange')
    sbuf = g.alloc(4096)
    cc = list(b"&|${}'\"`^<")
    lines = []

    bounds = []

    def rstr():
        parts = []
        for _ in range(rng.randint(0, 24)):
            k = rng.random()
            if k < 0.45:
                parts.append(bytes([rng.randint(0x20, 0x7e)]))
            elif k < 0.55:
                parts.append(bytes([rng.choice(cc)]))
            elif k < 0.62:
                parts.append(b';')
            elif k < 0.67:
                parts.append(b'=')
            elif k < 0.90:
                parts.append(bytes([rng.randint(0xb0, 0xc8), rng.randint(0xa1, 0xfe)]))
            elif k < 0.95:
                parts.append(bytes([0xa4, rng.randint(0xa1, 0xd3)]))
            elif k < 0.97:
                parts.append(rng.choice([b'\xa3\xbf', b'\xa1\xa4', b'\xa1\xa5']))
            else:
                parts.append(bytes([0xa4, rng.randint(0xd4, 0xfe)]))
        out, pos = b'', [0]
        for q in parts:
            out += q
            pos.append(len(out))
        bounds[:] = pos        # character boundaries: 'start' splitting a 2-byte pair makes the original read outside its tables (UB)
        return out

    for case in range(n):
        s = rstr()
        L = len(s)
        x = rng.randint(-30, 260)
        y = rng.randint(-20, 410)
        maxw = rng.choice([1000, 1000, rng.randint(10, 80), rng.randint(20, 250)])   # maxW < 10 never advances in the original (endless loop)
        ft = rng.randint(0, 1)
        start = rng.choice(bounds) if rng.random() < 0.35 else 0
        skip = rng.randint(0, 12) if rng.random() < 0.3 else 0
        end = rng.choice([L, L, L + 5, rng.randint(0, L + 2), 1000])
        anchor = rng.choice([0, 0, 1, 2, 1, 2, 3])
        color = -1 if rng.random() < 0.7 else rng.randint(0, 0xffff)
        sem = rng.randint(0, 1) if rng.random() < 0.4 else 0
        fc0 = rng.randint(0, 0xffff)
        fg0 = rng.randint(0, 0xffff)
        g.write(fb_ptr, bg)
        g.write(sem_addr, struct.pack('<i', sem))
        g.call('HF_SetRgbColor', fc0)       # sets _fontColor AND the pixel buffer HF_DrawPixel really copies
        g.ctx.setdefault(pgc, {})[1] = fg0
        g.write(sbuf, s + b'\0' * 256)       # zero padding: a skip count past the end walks over NULs
        g.call('HF_DrawTextAnchorH2Color', g.screen, x, y, maxw, ft, sbuf, start, skip, end, anchor, color & 0xffffffff)
        px = g.read(fb_ptr, 240 * 400 * 2)
        fc1 = struct.unpack('<I', g.read(fc_addr, 4))[0]
        fg1 = g.ctx[pgc][1]
        lines.append('%d %d %d %d %d %d %d %d %d %d %d %d %04x %04x %s %08x %08x %04x' % (
            x, y, maxw, ft, start, skip, end, anchor, color, sem, L, 0, fc0, fg0, s.hex() or '-', fnv(px), fc1, fg1))
    # HF_GetWidth2(str, end, start, maxW, font) - any maxW (the game itself calls it with 0 via HF_GetWidth)
    for case in range(3000):
        s = rstr()
        L = len(s)
        start = rng.choice(bounds) if rng.random() < 0.4 else 0
        end = rng.choice([L, L, L + 3, rng.randint(0, L + 1), 1000])
        maxw = rng.choice([0, 0, -3, 1, 5, 11, 30, 80, 250, 1000])
        ft = rng.randint(0, 1)
        sem = rng.randint(0, 1)
        g.write(sem_addr, struct.pack('<i', sem))
        g.write(sbuf, s + b'\0' * 256)
        r = g.call('HF_GetWidth2', sbuf, end, start, maxw & 0xffffffff, ft)
        r = r - (1 << 32) if r & 0x80000000 else r
        lines.append('W %d %d %d %d %d %s %d' % (end, start, maxw, ft, sem, s.hex() or '-', r))
    open(os.path.join(out, 'hf.txt'), 'w').write('\n'.join(lines) + '\n')
    print('hf.txt', n, 'cases + 3000 width cases')


if __name__ == '__main__':
    kind, binp, res = sys.argv[1], sys.argv[2], sys.argv[3]
    out = sys.argv[4] if len(sys.argv) > 4 else os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'work', 'golden')
    os.makedirs(out, exist_ok=True)
    {'hf': gen_hf}[kind](binp, res, out)
