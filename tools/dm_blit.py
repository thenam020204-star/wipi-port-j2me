#!/usr/bin/env python3
"""DM_DrawImg geometry (placement, anchor, flip/rotate, clipping) of AND2011, verified differentially
against the ORIGINAL DM_DrawImg + DM_DrawScreen565 executed in the reference harness.

Signature (CONFIRMED, DM_DrawImg 0x4f760 prologue + gbm.py):
  DM_DrawImg(frame, x, y, w, h, img, srcx, srcy, flip, rot, anchor)
  (w, h) = size of the SOURCE sub-rectangle taken at (srcx, srcy) of the loaded image.
  Returns immediately when w <= 0 or h <= 0 (0x4f774..0x4f7a0).
Clip rectangle `OutR` (global @0xfa29c, set by DM_SetCliptoGBM 0x4d378 with stm {r0-r3}):
  OutR = (x0, y0, x1, y1) as four s32; x1/y1 EXCLUSIVE (DM_DrawImg tests x >= OutR+8 -> skip, 0x4f824).
Anchor (DM_CalcAnchorGbm 0x4d29c, applied BEFORE flip/rotation, on the unrotated w/h):
  0x10: x -= w>>1     0x20: x -= w      (0x10 wins when both set)
  0x02: y -= h>>1     0x04: y -= h      (0x02 wins when both set)
  for rot 90/270 the centring terms use the rotated extent (x -= h>>1, y -= w>>1) but the 0x20/0x04
  terms still use the unrotated w/h (QUIRK, transcribed).
Transform (CONFIRMED by exhaustive differential test): source sub-rect is first mirrored (flip 1 = X,
  flip 2 = Y), then rotated clockwise by `rot` (0/90/180/270); flip 3 = rotate 180 and IGNORES rot
  (but the anchor still sees the raw rot).  Any other rot value behaves as 0 in the transform.
Side effect: DM_DrawImg copies img+8 -> img+0x18 (0x4f7d0..0x4f7d8): the "current body" pointer is
  reset to the base body on every draw (DM_DrawScreen565 likewise resets +0x14 <- +0xc, the palette).
Transparency: colour after palette lookup == SKIP_COL (0xF81F) is not drawn (gbm.py).
Out of scope here (see dm_fx): Ex/blending/mosaic variants; images drawn larger than the frame buffer.

Usage:  python3 tools/dm_blit.py selftest <binary.mod> <res dir>
"""
import os
import struct
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gbm

SKIP = 0xF81F


def calc_anchor(x, y, w, h, rot, anchor):
    """DM_CalcAnchorGbm 0x4d29c; all arithmetic is s32 with arithmetic shift (asr)."""
    rotated = rot in (90, 270)
    if anchor & 0x10:
        x -= w >> 1
        if rotated:
            x += (w >> 1) - (h >> 1)
    elif anchor & 0x20:
        x -= w
    if anchor & 0x02:
        y -= h >> 1
        if rotated:
            y -= (w >> 1) - (h >> 1)
    elif anchor & 0x04:
        y -= h
    return x, y


def effective(flip, rot):
    """-> (flip_x, flip_y, rot) actually applied to the source sub-rect."""
    if flip == 3:
        return False, False, 180
    return flip == 1, flip == 2, rot if rot in (90, 180, 270) else 0


def dest_size(w, h, flip, rot):
    r = effective(flip, rot)[2]
    return (h, w) if r in (90, 270) else (w, h)


def src_of(px, py, w, h, flip, rot):
    """dest-local pixel (px, py) -> source sub-rect pixel (u, v)."""
    fx, fy, r = effective(flip, rot)
    if r == 0:
        u, v = px, py
    elif r == 90:
        u, v = py, h - 1 - px
    elif r == 180:
        u, v = w - 1 - px, h - 1 - py
    else:
        u, v = w - 1 - py, px
    if fx:
        u = w - 1 - u
    if fy:
        v = h - 1 - v
    return u, v


MODE = {  # (flip, rot) -> (mode, r4 flag, path)  (0x4f7dc..0x4fc54; path A = 0x4f81c, B = 0x4f938, C = 0x4faec)
}


def _mode(flip, rot):
    """Dispatch of DM_DrawImg 0x4f7dc..0x4fc54 -> (mode, r4, path). path A: unswapped, B/C: w/h swapped."""
    if flip == 0:
        if rot == 180: return 5, 1, 'A'
        if rot == 270: return 6, 0, 'C'
        if rot == 90:  return 4, 0, 'B'
        return 0, 0, 'A'
    if flip == 1:
        if rot == 0:   return 1, 0, 'A'
        if rot == 180: return 2, 0, 'A'
        if rot == 270: return 8, 1, 'C'
        if rot == 90:  return 7, 1, 'B'
        return 0, 0, 'A'
    if flip == 2:
        if rot == 0:   return 2, 0, 'A'
        if rot == 180: return 1, 0, 'A'
        if rot == 270: return 7, 1, 'B'
        if rot == 90:  return 8, 1, 'C'
        return 0, 0, 'A'
    if flip == 3:
        return 3, 0, 'A'
    return 0, 0, 'A'


def _mode_ex(flip, rot):
    """DM_DrawImgEx 0x4ee60 dispatch: identical to _mode() except flip == 3, where rot is NOT ignored
    (flip 3 = rotate 180, composed with rot): rot 0 -> mode 3, 90 -> 6, 180 -> 0, 270 -> 4, other -> 0."""
    if flip == 3:
        return {0: (3, 0, 'A'), 90: (6, 0, 'C'), 180: (0, 0, 'A'), 270: (4, 0, 'B')}.get(rot, (0, 0, 'A'))
    return _mode(flip, rot)


def plan(x, y, w, h, sx, sy, flip, rot, anchor, clip, ex=False):
    """Transliteration of DM_DrawImg 0x4f760..0x4fd10 up to the call of DM_DrawScreen.
    Returns None (nothing drawn) or (X, Y, W, H, SX, SY, mode): the arguments handed to DM_DrawScreen565
    (W/H/SX/SY are in SOURCE orientation, mode selects the transform). Every quirk of the original
    (e.g. SX not advanced when a rot-90 image is clipped at the top, 0x4f9b8) is reproduced."""
    if w <= 0 or h <= 0:
        return None
    x, y = calc_anchor(x, y, w, h, rot, anchor)
    m, r4, path = (_mode_ex if ex else _mode)(flip, rot)
    L, T, R, B = clip
    sx0, sy0 = sx, sy
    if path == 'A':
        if x >= R or x + w < L or y >= B or y + h < T:
            return None
        if x < L:                                    # 0x4fa68
            if R >= x + w:
                W = w - (L - x)
            else:
                W = R - L
            if m in (0, 2):
                SX = sx0 + (L - x)
            else:
                SX = sx0
            X = L
        else:
            X = x
            if R >= x + w:
                W, SX = w, sx0
            else:
                W = R - x
                SX = sx0 + (x + w - R) if (m in (1, 3) or r4) else sx0
        if y < T:                                    # 0x4f8a4
            H = (y + h - T) if B >= y + h else B - T
            if m <= 1:
                SY = sy0 + (T - y)
            else:
                SY = sy0
            Y = T
        else:
            Y = y
            if B >= y + h:                           # 0x4fb94
                H, SY = h, sy0
            else:                                    # 0x4fc08
                H = B - y
                SY = sy0 + (y + h - B) if (m in (2, 3) or r4) else sy0
        return X, Y, W, H, SX, SY, m
    # swapped paths: x extent = h, y extent = w
    if x >= R or x + h < L or y >= B or y + w < T:
        return None
    if path == 'B':
        if x < L:                                    # 0x4f97c..0x4f9a8
            if R >= x + h:
                H = h - (L - x); SY = sy0
            else:
                H = R - L; SY = sy0 + (x + h - R)
            X = L
        else:                                        # 0x4fc98
            X = x
            if R >= x + h:
                H, SY = h, sy0
            else:
                H = R - x; SY = sy0 + (x + h - R)
        if y < T:                                    # 0x4f9ac
            if B >= y + w:
                W = w - (T - y); SX = sx0            # QUIRK: no advance (0x4f9c0)
            else:
                W = B - T; SX = sx0 + (T - y)
            Y = T
        else:                                        # 0x4fc5c
            Y = y
            if B >= y + w:
                W, SX = w, sx0
            else:
                W = B - y
                SX = sx0 + (y + w - B) if r4 else sx0
        return X, Y, W, H, SX, SY, m
    # path C
    if x < L:                                        # 0x4fb34..0x4fb58
        if R >= x + h:
            H = h - (L - x)
        else:
            H = R - L
        SY = sy0 + (L - x)
        X = L
    else:                                            # 0x4fcf8
        X = x
        if R >= x + h:
            H = h
        else:
            H = R - x
        SY = sy0
    if y < T:                                        # 0x4fb5c
        if B >= y + w:
            W = w - (T - y)
        else:
            W = B - T
        SX = sx0 + (T - y) if r4 else sx0
        Y = T
    else:                                            # 0x4fcc8
        Y = y
        if B >= y + w:
            W, SX = w, sx0
        else:
            W = B - y
            SX = sx0 if m == 8 else sx0 + (y + w - B)
    return X, Y, W, H, SX, SY, m


MODE_TRANSFORM = {0: (0, 0), 1: (1, 0), 2: (2, 0), 3: (3, 0), 4: (0, 90), 5: (0, 180), 6: (0, 270),
                  7: (1, 90), 8: (1, 270)}      # DM_DrawScreen565 `mode` -> (flip, rot)  (layer 2, CONFIRMED by test)


def raster(fb, fw, rows, X, Y, W, H, SX, SY, mode):
    """DM_DrawScreen565 0x3de54 (layer 2): copy the source rectangle (SX, SY, W, H), transformed by `mode`,
    to (X, Y); colour None (== SKIP_COL after palette lookup) is skipped. No clipping happens here."""
    flip, rot = MODE_TRANSFORM[mode]
    dw, dh = dest_size(W, H, flip, rot)
    for dy in range(dh):
        for dx in range(dw):
            u, v = src_of(dx, dy, W, H, flip, rot)
            c = rows[SY + v][SX + u]
            if c is not None:
                o = ((Y + dy) * fw + X + dx) * 2
                fb[o] = c & 0xff
                fb[o + 1] = c >> 8


def draw(fb, fw, fh, clip, rows, x, y, w, h, sx, sy, flip, rot, anchor):
    """Pure model of DM_DrawImg = plan() (layer 1) + raster() (layer 2). fb: bytearray RGB565 LE."""
    p = plan(x, y, w, h, sx, sy, flip, rot, anchor, clip)
    if p is not None:
        raster(fb, fw, rows, *p)


# ------------------------------------------------------------------------------------ self-test
FW, FH = 240, 400
BG = 0x1234


def _synthetic(w, h, t):
    """Synthetic GBM body with unique colour per pixel (8 bpp) or per i%16 (4 bpp)."""
    n = w * h if t == 0x0c else 16
    pal = [((i + 1) * 0x101) & 0xffff for i in range(n)]
    body = bytes([t]) + struct.pack('<4H', w, h, w, n) + b''.join(struct.pack('<H', c) for c in pal)
    if t == 0x0c:
        body += bytes(range(w * h))
    else:
        stride = (w + 1) >> 1
        raw = bytearray(stride * h)
        for yy in range(h):
            for xx in range(w):
                i = (yy * w + xx) % 16
                raw[yy * stride + (xx >> 1)] |= (i << 4) if xx % 2 == 0 else i
        body += bytes(raw)
    return body


def selftest(binp, resdir):
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    from and_hle import Harness
    hh = Harness(binp, resdir).boot_dm()
    outr = hh.objaddr('OutR')
    fb = hh.frames[hh.screen][0]

    def load(body):
        src = hh.alloc(len(body)); hh.write(src, body)
        cell = hh.alloc(4); hh.write(cell, struct.pack('<I', src))
        img = hh.alloc(0x20)
        hh.call('DM_LoadMergeGbm565', cell, img)
        return img

    # ---- image set (deterministic, printed) ----
    imgs = []
    for (w, h, t) in ((5, 3, 0x0c), (4, 6, 0x0c), (5, 3, 0x0b), (7, 5, 0x0b), (1, 1, 0x0c), (16, 16, 0x0c)):
        imgs.append(('syn%dx%d_%#x' % (w, h, t), _synthetic(w, h, t)))
    # a few real images (first image of selected containers) of both bit depths
    real = []
    for root, _, fs in sorted(os.walk(resdir)):
        for fn in sorted(fs):
            p = os.path.join(root, fn)
            b = open(p, 'rb').read()
            if gbm.is_container(b):
                bodies = gbm.parse_container(b)
                for k, body in enumerate(bodies):
                    if len(body) >= 9 and gbm.header(body)[1] <= 120 and gbm.header(body)[2] <= 120:
                        real.append((os.path.relpath(p, resdir) + '#%d' % k, body))
                        break
    step = max(1, len(real) // 12)
    imgs += real[::step][:12]
    print('images:', [(n, gbm.header(b)[:3]) for n, b in imgs])

    clips = [(0, 0, FW, FH), (20, 30, 100, 90), (50, 50, 52, 52), (0, 0, 8, 8), (232, 392, 240, 400)]
    anchors = [0, 0x10, 0x20, 0x02, 0x04, 0x12, 0x24, 0x14, 0x22, 0x30, 0x06]
    combos = [(f, r) for f in (0, 1, 2) for r in (0, 90, 180, 270)] + [(3, 0), (3, 90), (3, 270)] + [(0, 45), (1, 360)]
    n = bad = 0
    crashes = []
    zero_ext = 0
    bgbytes = struct.pack('<H', BG) * (FW * FH)
    for name, body in imgs:
        iw, ih, rows = gbm.decode(body)
        img = load(body)
        subs = [(0, 0, iw, ih)]
        if iw > 2 and ih > 2:
            subs.append((1, 1, iw - 2, ih - 1))
        for (sx, sy, w, h) in subs:
            for clip in clips:
                hh.write(outr, struct.pack('<4i', *clip))
                for (flip, rot) in combos:
                    for anc in anchors:
                        # positions around the clip edges, negative and in-frame; both parities
                        xs = {clip[0] - w - 1, clip[0] - w, clip[0] - 1, clip[0], clip[0] + 1,
                              (clip[0] + clip[2]) // 2, clip[2] - 1, clip[2], 3, -7}
                        ys = {clip[1] - h - 1, clip[1] - h, clip[1] - 1, clip[1], clip[1] + 1,
                              (clip[1] + clip[3]) // 2, clip[3] - 1, clip[3], 5, -9}
                        for x in sorted(xs)[::2] + sorted(xs)[1::4]:
                            for y in sorted(ys)[::2] + sorted(ys)[1::4]:
                                pl = plan(x, y, w, h, sx, sy, flip, rot, anc, clip)
                                if pl is not None and (pl[2] <= 0 or pl[3] <= 0):
                                    zero_ext += 1       # original reads an uninitialised stack slot here (UB)
                                    continue
                                hh.write(fb, bgbytes)
                                try:
                                    hh.call('DM_DrawImg', hh.screen, x, y, w, h, img, sx, sy, flip, rot, anc)
                                except RuntimeError as ex:
                                    crashes.append((name, (sx, sy, w, h), clip, flip, rot, hex(anc), (x, y),
                                                    plan(x, y, w, h, sx, sy, flip, rot, anc, clip)))
                                    if len(crashes) <= 12:
                                        print('CRASH', crashes[-1], str(ex)[-45:])
                                    hh = Harness(binp, resdir).boot_dm(); outr = hh.objaddr('OutR'); fb = hh.frames[hh.screen][0]
                                    img = load(body); hh.write(outr, struct.pack('<4i', *clip))
                                    continue
                                got = hh.read(fb, FW * FH * 2)
                                exp = bytearray(bgbytes)
                                draw(exp, FW, FH, clip, rows, x, y, w, h, sx, sy, flip, rot, anc)
                                n += 1
                                if got != bytes(exp):
                                    bad += 1
                                    if bad <= 12:
                                        d = [i // 2 for i in range(0, len(got), 2) if got[i:i + 2] != bytes(exp[i:i + 2])]
                                        print('MISMATCH', name, 'sub', (sx, sy, w, h), 'clip', clip, 'flip', flip, 'rot', rot,
                                              'anchor', hex(anc), 'xy', (x, y), 'ndiff', len(d),
                                              'first', [(i % FW, i // FW) for i in d[:3]])
    # side effect: image-body pointer reset (+0x18 <- +8)
    img = load(imgs[0][1])
    hh.write(img + 0x18, struct.pack('<I', 0xdeadbee0))
    hh.write(outr, struct.pack('<4i', 0, 0, FW, FH))
    hh.call('DM_DrawImg', hh.screen, 10, 10, 5, 3, img, 0, 0, 0, 0, 0)
    side = hh.u32(img + 0x18) == hh.u32(img + 8)
    print('image-body pointer reset (+0x18 <- +8):', side)
    print('draw cases compared', n, 'mismatches', bad, 'original crashes', len(crashes), 'zero-extent (UB, not compared)', zero_ext)
    return bad + (0 if side else 1)


if __name__ == '__main__':
    if sys.argv[1] == 'selftest':
        sys.exit(1 if selftest(sys.argv[2], sys.argv[3]) else 0)
