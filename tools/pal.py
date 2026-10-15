#!/usr/bin/env python3
"""H4/PAL/_H_###_PAL palette-swap files of AND2011 + DM_ChangePalette, verified against the ORIGINAL
DM_ChangePalette 0xbc1f4 / changePalette 0xbcaf8 in the reference emulator.

File layout (CONFIRMED):  u8 header (pair count; NOT used by the loader), then pairs of
    u32le old, u32le new          each 0x00RRGGBB  (bytes B, G, R, 0)
The loader iterates  pos = 1, 9, 17 ... while pos < file size  (so a trailing partial pair is read past the end).
Per pair (DM_ChangePalette, RGB565 build; `_lcdG[0x92] == 1`):
    old565 = (old>>8 & 0xf800) | (old & 0xff)>>3 | (old>>5 & 0x7c0)     # green keeps its top 5 bits only (g5 << 6)
    new565 = (new>>16 & 0xf8)<<8 | (new & 0xff)>>3 | (new>>8 & 0xf8)<<3
    find the FIRST palette index i (0 .. ncolors-1) of the image BODY palette equal to old565;
    if found: body_palette[i] = new565 and working_palette[i] = new565.   ncolors == 0 -> pair skipped.
The first statements set img+0x18 <- img+8 (body pointer) and img+0x14 <- img+0xc (palette pointer).
The BODY palette is modified in place, so the change survives DM_RestorePalette (which copies body -> working).
changePalette(img, n) loads "/H4/PAL/_H_%03d_PAL" (n >= 0) and applies it; setPalImages applies one file to `count`
consecutive 32-byte image structs.

Usage:  python3 tools/pal.py list <res dir>
        python3 tools/pal.py selftest <binary.mod> <res dir>
"""
import os
import random
import struct
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gbm


def parse(b):
    """-> list of (old888, new888) as the loader reads them (pos = 1, 9, ... while pos < len)."""
    out = []
    pos = 1
    while pos < len(b):
        chunk = b[pos:pos + 8]
        chunk = chunk + b'\0' * (8 - len(chunk))                       # the original reads past the end (UNKNOWN content)
        out.append(struct.unpack('<II', chunk))
        pos += 8
    return out


def to565_old(v):
    return ((v >> 8) & 0xf800) | ((v & 0xff) >> 3) | ((v >> 5) & 0x7c0)


def to565_new(v):
    return (((v >> 16) & 0xf8) << 8) | ((v & 0xff) >> 3) | (((v >> 8) & 0xf8) << 3)


def apply(body_pal, work_pal, pairs):
    """DM_ChangePalette on (body palette, working palette) lists; returns the modified copies."""
    body, work = list(body_pal), list(work_pal)
    if not body:
        return body, work
    for old, new in pairs:
        o = to565_old(old)
        for i, c in enumerate(body):
            if c == o:
                n = to565_new(new)
                body[i] = n
                work[i] = n
                break
    return body, work


# --------------------------------------------------------------------------------- self-test
def selftest(binp, resdir):
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    from and_hle import Harness
    h = Harness(binp, resdir).boot_dm()
    palfiles = sorted(os.path.join(resdir, 'H4', 'PAL', f) for f in os.listdir(os.path.join(resdir, 'H4', 'PAL')))
    imgs = []                                                       # real images: first image of up to 40 hero containers
    for root, _, fs in sorted(os.walk(os.path.join(resdir, 'H4'))):
        for fn in sorted(fs):
            if not fn.startswith('_HIMG_'):
                continue
            b = open(os.path.join(root, fn), 'rb').read()
            if gbm.is_container(b):
                imgs.append(gbm.parse_container(b)[0])
        if len(imgs) >= 40:
            break
    rng = random.Random(5)
    bad = n = swaps = 0

    def load(body):
        src = h.alloc(len(body)); h.write(src, body)
        cell = h.alloc(4); h.write(cell, struct.pack('<I', src))
        img = h.alloc(0x20)
        h.call('DM_LoadMergeGbm565', cell, img)
        return img

    def run(body, data):
        nonlocal bad, n, swaps
        _, _, _, _, nc, bpal, _ = gbm.header(body)
        img = load(body)
        pairs = parse(data)
        exp_b, exp_w = apply(bpal, bpal, pairs)
        swaps += sum(1 for a, b in zip(bpal, exp_b) if a != b)
        buf = h.alloc(len(data) + 16); h.write(buf, data)
        h.call('DM_ChangePalette', img, buf, 0, len(data))
        body_ptr = h.u32(img + 8)
        got_b = [struct.unpack('<H', h.read(body_ptr + 9 + 2 * i, 2))[0] for i in range(nc)]
        wp = h.u32(img + 0xc)
        got_w = [struct.unpack('<H', h.read(wp + 2 * i, 2))[0] for i in range(nc)]
        n += 1
        if got_b != exp_b or got_w != exp_w or h.u32(img + 0x18) != h.u32(img + 8) or h.u32(img + 0x14) != h.u32(img + 0xc):
            bad += 1
            if bad <= 5:
                print('PAL MISMATCH', len(data), nc, got_b[:6], exp_b[:6])

    # 1) every real PAL file x several real images
    for p in palfiles:
        data = open(p, 'rb').read()
        for body in rng.sample(imgs, min(6, len(imgs))):
            run(body, data)
    # 2) synthetic images whose palettes contain the PAL files' own 'old' colours (so swaps really happen)
    for p in palfiles:
        data = open(p, 'rb').read()
        pairs = parse(data)
        olds = [to565_old(o) for o, _ in pairs[:15]]
        for t in (0x0b, 0x0c):
            cols = olds[:] + [rng.randrange(65536) for _ in range(3)]
            rng.shuffle(cols)
            cols = cols[:16] if t == 0x0b else cols
            nc = len(cols)
            w, hh = 4, 3
            px = bytes(rng.randrange(nc) for _ in range(w * hh)) if t == 0x0c else bytes(rng.randrange(256) & 0xff for _ in range(2 * hh))
            if t == 0x0b:
                px = bytes(((rng.randrange(nc) << 4) | rng.randrange(nc)) for _ in range(2 * hh))
            body = bytes([t]) + struct.pack('<4H', w, hh, w, nc) + b''.join(struct.pack('<H', c) for c in cols) + px
            run(body, data)
    print('PAL files', len(palfiles), 'cases', n, 'palette entries actually swapped', swaps, 'mismatches', bad)
    return bad


if __name__ == '__main__':
    if sys.argv[1] == 'list':
        d = os.path.join(sys.argv[2], 'H4', 'PAL')
        for f in sorted(os.listdir(d)):
            b = open(os.path.join(d, f), 'rb').read()
            print(f, len(b), 'header', b[0], 'pairs', len(parse(b)), 'tail', (len(b) - 1) % 8)
    elif sys.argv[1] == 'selftest':
        sys.exit(1 if selftest(sys.argv[2], sys.argv[3]) else 0)
