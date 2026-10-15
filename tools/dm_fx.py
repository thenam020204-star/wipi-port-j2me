#!/usr/bin/env python3
"""Non-geometric pixel operations of the DM module (AND2011), RGB565 path (`_lcdG+0x92 == 1`), verified
differentially against the ORIGINAL routines executed in the reference emulator.

Covered here (CONFIRMED by selftest):
  * BlendingTable32Bit (16 modes x 32 x 32 bytes) builder  DM_SetBlendingTable565 0x51360 (called from
    DM_Initialize with the 16 mode codes 0..15, 0x562d4).
  * DM_DrawImgEx 0x4ee60 -> DM_DrawScreen565Ex 0x3fdec : same placement/clipping as DM_DrawImg
    (dm_blit.plan) + per-pixel blend  f(src, dst, alpha, effect).
  * DM_RestorePalette 0x3d898, DM_SetChagePalBlendEx 0x4e66c (palette tint / blend / HSB shift), with
    DM_ConvertRGBtoHSB 0x4dc6c and DM_ConvertHSBtoRGB 0x4ddf4.

DM_DrawImgEx(frame, x, y, w, h, img, srcx, srcy, flip, rot, alpha, effect, anchor)
DM_DrawScreen565Ex(fb, X, Y, W, H, fbw, fbh, img, SX, SY, aux, imgh, mode, alpha, effect)

Per-pixel rule (5-bit channels; g5 = (g6 & 0x7e0) >> 6, i.e. the lowest green bit of src AND dst is dropped):
  src == SKIP_COL (0xF81F) -> pixel untouched;  alpha == 0 -> nothing drawn.
  effect == 0x10 : R = G = B = (6*r_s + 4*g5_s + 2*b_s) >> 4   (grey, ignores dst)
  otherwise      : R = T[effect][r_s][r_d], G5 = T[effect][g5_s][g5_d], B = T[effect][b_s][b_d]
  alpha < 255    : ra = (alpha>>3)+1 ; ga = ((alpha&0xf8)>>2)+1 ; ba = alpha>>3   (QUIRK: no +1 for blue)
                   R = (R*ra + r_d*(32-ra)) >> 5 ; G6 = (2*G5*ga + 2*g5_d*(64-ga)) >> 6 ; B = (B*ba + b_d*(32-ba)) >> 5
  out = R<<11 | (G6<<5)&0x7e0 | B&0x1f
effect values 17..: index past the 16-mode table (UNDEFINED, reads other .bss objects); real CIF data uses 0..17
(effect 17: 4 parts of 79,162 long parts).  The model refuses effect > 16.

Usage:  python3 tools/dm_fx.py selftest <binary.mod> <res dir>
"""
import os
import random
import struct
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import dm_blit
import gbm

SKIP = 0xF81F


# --------------------------------------------------------------------------------- blend table
def build_table(codes=tuple(range(16))):
    """DM_SetBlendingTable565 0x51360: T[k][a][b], a = source channel, b = destination channel (0..31)."""
    t = bytearray(len(codes) * 32 * 32)
    for a in range(32):
        for b in range(32):
            na = 31 - a                  # iVar7
            for k, code in enumerate(codes):
                v = None
                if code == 0:
                    v = a
                elif code == 1:
                    v = min(a, b)
                elif code == 2:
                    v = max(a, b)
                elif code == 3:
                    v = (a * b) // 31
                elif code == 4:
                    v = 31 - (na * (31 - b)) // 31
                elif code == 5:
                    v = b * 31 if a == 31 else (b * 31) // na
                    v = 31 if v >= 32 else v
                elif code == 6:
                    v = 31 - ((31 - b) * 31) // (a + 1)
                    v = 0 if v < 0 else v
                elif code == 7:
                    v = a + b
                    v = 31 if v >= 32 else v
                elif code == 8:
                    v = a - 31 + b
                    v = 0 if v < 0 else v
                elif code == 9:
                    v = a * 2 - 31 + b
                    v = 0 if v < 0 else (31 if v >= 32 else v)
                elif code == 10:
                    v = 31 - ((na * (31 - b)) >> 4) if b > 16 else (a * b) >> 4
                    v = 0 if v < 0 else v
                elif code == 11:
                    v = 31 - ((na * (31 - b)) >> 4) if a >= 17 else (a * b) >> 4
                    v = 0 if v < 0 else v
                elif code == 12:
                    lo, hi = 2 * a - 31, 2 * a
                    if b < lo:
                        v = lo
                    elif hi < b:
                        v = 31 if hi > 31 else hi
                    else:
                        v = b
                elif code == 13:
                    v = 31 if (31 - b) <= a else 0
                elif code == 14:
                    v = abs(a - b)
                elif code == 15:
                    v = (b + a) - ((a * b) >> 4)
                    v = 0 if v < 0 else (31 if v >= 32 else v)
                if v is not None:
                    t[(k * 32 + a) * 32 + b] = v & 0xff
    return bytes(t)


def blend_pixel(s, d, alpha, effect, table):
    """DM_DrawScreen565Ex per-pixel rule; s, d RGB565 ints. Returns the new destination pixel."""
    if s == SKIP:
        return d
    rs, gs, bs = s >> 11, (s & 0x7e0) >> 6, s & 31
    rd, gd, bd = d >> 11, (d & 0x7e0) >> 6, d & 31
    if effect == 0x10:
        R = G = B = (rs * 6 + bs * 2 + gs * 4) >> 4
    else:
        base = effect * 1024
        R = table[base + rs * 32 + rd]
        G = table[base + gs * 32 + gd]
        B = table[base + bs * 32 + bd]
    G6 = G * 2
    if alpha < 0xff:
        ra = ((alpha & 0xf8) >> 3) + 1
        ga = ((alpha & 0xf8) >> 2) + 1
        ba = (alpha & 0xf8) >> 3
        R = (R * ra + rd * (32 - ra)) >> 5
        G6 = (G6 * ga + gd * 2 * (64 - ga)) >> 6
        B = (B * ba + bd * (32 - ba)) >> 5
    return ((R << 11) | ((G6 << 5) & 0x7e0) | (B & 0x1f)) & 0xffff


def raster_ex(fb, fw, rows, X, Y, W, H, SX, SY, mode, alpha, effect, table):
    """DM_DrawScreen565Ex 0x3fdec: like dm_blit.raster but blending into the destination."""
    if alpha == 0:
        return
    flip, rot = dm_blit.MODE_TRANSFORM[mode]
    dw, dh = dm_blit.dest_size(W, H, flip, rot)
    for dy in range(dh):
        for dx in range(dw):
            u, v = dm_blit.src_of(dx, dy, W, H, flip, rot)
            s = rows[SY + v][SX + u]
            if s is not None:
                o = ((Y + dy) * fw + X + dx) * 2
                d = fb[o] | (fb[o + 1] << 8)
                n = blend_pixel(s, d, alpha, effect, table)
                fb[o] = n & 0xff
                fb[o + 1] = n >> 8


def draw_ex(fb, fw, fh, clip, rows, x, y, w, h, sx, sy, flip, rot, alpha, effect, anchor, table):
    """Pure model of DM_DrawImgEx (placement identical to DM_DrawImg, verified)."""
    p = dm_blit.plan(x, y, w, h, sx, sy, flip, rot, anchor, clip, ex=True)
    if p is not None:
        raster_ex(fb, fw, rows, p[0], p[1], p[2], p[3], p[4], p[5], p[6], alpha, effect, table)


# --------------------------------------------------------------------------------- palette operations
def cdiv(a, b):
    """C integer division (truncates toward zero)."""
    q = abs(a) // abs(b)
    return q if (a < 0) == (b < 0) else -q


def rgb_to_hsb(r, g, b):
    """DM_ConvertRGBtoHSB 0x4dc6c; r,g,b 0..255 -> (H 0..360, S 0..100, B 0..100). Integer only."""
    bb, gg, rr = cdiv(b * 100, 255), cdiv(g * 100, 255), cdiv(r * 100, 255)
    mx = max(gg, bb, rr)
    if mx < 1:
        return 0, 0, 0
    mn = min(gg, bb, rr)
    d = mx - mn
    if rr == mx:
        if gg < bb:
            H = cdiv((gg - bb) * 60, d) + 360 if d != 0 else d
        else:
            H = cdiv((gg - bb) * 60, d) if d != 0 else d
    elif gg == mx:
        H = 120 if d == 0 else cdiv((bb - rr) * 60, d) + 120
    elif bb == mx:
        H = 240 if d == 0 else cdiv((rr - gg) * 60, d) + 240
    else:
        H = 0
    return H, 100 - cdiv(mn * 100, mx), mx


def hsb_to_rgb(H, S, B):
    """DM_ConvertHSBtoRGB 0x4ddf4.  The only 'float' in it is the test `(double)S == 0.0`, i.e. S == 0
    (CONFIRMED: __aeabi_i2d + __aeabi_dcmpeq against 0)."""
    if B == 0:
        return 0, 0, 0
    if S == 0:
        v = cdiv(B * 255, 100)
        return v, v, v
    hh = cdiv(H * 100, 360)
    r = B
    if hh < 16:
        b = cdiv(B * (100 - S), 100)
        g = cdiv((B - b) * hh * 6, 100) + b
    else:
        g = B
        if hh < 33:
            b = cdiv(B * (100 - S), 100)
            r = B - cdiv((B - b) * (hh - 16) * 6, 100)
        elif hh < 50:
            r = cdiv(B * (100 - S), 100)
            b = cdiv((B - r) * (hh - 33) * 6, 100) + r
        else:
            b = B
            if hh < 66:
                r = cdiv(B * (100 - S), 100)
                g = B - cdiv((B - r) * (hh - 50) * 6, 100)
            elif hh < 83:
                g = cdiv(B * (100 - S), 100)
                r = cdiv((B - g) * (hh - 66) * 6, 100) + g
            else:
                g = cdiv(B * (100 - S), 100)
                b = B - cdiv((B - g) * (hh - 83) * 6, 100)
    return cdiv(r * 255, 100), cdiv(g * 255, 100), cdiv(b * 255, 100)


def set_pal_blend(pal, flag, p2, p3, p4, mode, alpha, table):
    """DM_SetChagePalBlendEx 0x4e66c (RGB565 branch).  pal: list of working-palette u16; returns (pal, flag).
    p2..p4 are signed 32-bit ints, mode / alpha are bytes.  No-op when flag == 1 (already changed)."""
    if flag == 1:
        return pal, flag
    pal = list(pal)
    if mode == 0x15:
        if p2 == 0 and p3 == 0 and p4 == 0:
            return pal, flag
        for i, c in enumerate(pal):
            if c == SKIP:
                continue
            r, g, b = (c >> 11) << 3, (c >> 3) & 0xf8, (c & 0x1f) << 3
            H, S, V = rgb_to_hsb(r, g, b)
            H += p2
            if H < 0:
                H += 360
            if H > 360:
                H -= 360
            if p3 >= 1:
                S = cdiv((100 - S) * p3, 100) + S
            elif p3 != 0:
                S = cdiv(S * p3, 100) + S
            r, g, b = hsb_to_rgb(H, S, V)
            if p4 >= 1:
                r = cdiv((255 - r) * p4, 100) + r
                g = cdiv((255 - g) * p4, 100) + g
                b = cdiv((255 - b) * p4, 100) + b
            elif p4 != 0:
                r = cdiv(r * p4, 100) + r
                g = cdiv(g * p4, 100) + g
                b = cdiv(b * p4, 100) + b
            out = (((g >> 2) & 0x3f) << 5) | (((r >> 3) & 0x1f) << 11) | ((b >> 3) & 0x1f)
            pal[i] = out if out != SKIP else 0x821
        return pal, 1
    ra = ((alpha & 0xf8) >> 3) + 1
    ga = ((alpha & 0xf8) >> 2) + 1
    have_tint = ((p2 | p3 | p4) & 0xffff) != 0
    tint = ((p4 >> 3) | ((p2 >> 3) << 11) | ((p3 >> 2) << 5)) & 0xffff if have_tint else None
    for i, c in enumerate(pal):
        if c == SKIP:
            continue
        t = tint if have_tint else c
        t_r, t_g, t_b = t >> 11, (t & 0x7e0) >> 6, t & 0x1f
        c_r, c_g, c_b = c >> 11, (c & 0x7e0) >> 6, c & 0x1f
        if mode == 0x14:
            inv = 100 - alpha
            g = cdiv(t_g * alpha + inv * c_g, 100) & 0xff        # C division: alpha > 100 gives negative numerators
            r = cdiv(t_r * alpha + inv * c_r, 100) & 0xff
            b = cdiv(t_b * alpha + inv * c_b, 100) & 0xff
            sh = 6
        else:
            if mode == 0x10:
                r = g = b = (t_r * 6 + t_b * 2 + t_g * 4) >> 4
            else:
                base = mode * 1024
                b = table[base + t_b * 32 + c_b]
                r = table[base + t_r * 32 + c_r]
                g = table[base + t_g * 32 + c_g]
            if alpha == 0xff:
                g = g * 2
                if mode == 0:
                    b, r, g = t_b, t_r, t_g
                    sh = 6
                else:
                    sh = 5
            else:
                g = (ga * g * 2 + (0x40 - ga) * c_g * 2) >> 6
                r = (r * ra + (0x20 - ra) * c_r) >> 5
                b = (b * ra + (0x20 - ra) * c_b) >> 5
                sh = 5
        pal[i] = ((r << 11) | ((g << sh) & 0x7e0) | (b & 0x1f)) & 0xffff
    return pal, 1


def restore_palette(body_pal, pal, flag):
    """DM_RestorePalette 0x3d898: copy the pristine body palette back, clear the flag (only if flag != 0)."""
    if flag == 0:
        return pal, flag
    return list(body_pal), 0


# --------------------------------------------------------------------------------- time-of-day tint at image load
TINT = {0: 0xabd8, 1: 0xc591, 2: 0xbd2c, 3: 0x45ff, 4: 0xffff, 5: 0xfc2d}      # DM_BlendPixelAtTime 0x4db88 / loaders (565 build)


def gbm_load_blend(pal, slot, table):
    """DM_LoadResGbmBlending 0x53e68 (tiles, map objects; `slot` = _map[0x78], the time-of-day class).
    Returns the new palette: BODY and working palette end up IDENTICAL (the tint is applied in the working buffer, then copied to the body);
    slot -1 leaves both untouched.
    slot 0..5: tint colour TINT[slot], blend mode 10;  other slots (6, 7, ...): tint 0, mode 0.
    channel (5 bit; green uses the g5 convention) : out = ((T[mode][tint_ch][c] + c) * k) with the QUIRKY weights
        R = ((T+r) << 10) & 0xf800 ,  B = ((T+b) >> 1) & 0x1f  (low bit dropped) ,  G6 = ((T_g*0x3e + g5*0x42) >> 1) & 0x7e0"""
    if slot == -1:
        return list(pal)
    tint, mode = (TINT[slot], 10) if slot in TINT else (0, 0)
    out = []
    base = mode * 1024
    for c in pal:
        if c == SKIP:
            out.append(c)
            continue
        r, g, b = c >> 11, (c & 0x7e0) >> 6, c & 0x1f
        tr, tg, tb = tint >> 11, (tint & 0x7e0) >> 6, tint & 0x1f
        R = ((table[base + tr * 32 + r] + r) * 0x400) & 0xf800
        B = (((table[base + tb * 32 + b] + b) * 0x4000000) & 0xffffffff) >> 27
        G = ((table[base + tg * 32 + g] * 0x3e + g * 0x42) >> 1) & 0x7e0
        out.append((R | G | B) & 0xffff)
    return out


def gbm_merge_blend(pal, slot, table):
    """DM_LoadResGbmMergeBlending 0x54750, tint stage (the _PAL swap `changePalette(img, n)` for n in 0..254 runs BEFORE it,
    see pal.py; n >= 255 / -1 skips it).  slot 0..5: tint TINT[slot], mode 10, alpha 0x7f ; slot 6: tint 0xf800, mode 4, alpha 200;
    slot -1: no tint (return); other slots: UNDEFINED in the original (alpha variable uninitialised) -> refused here.
    out = blend(T[mode][tint][c]) with alpha weights ra = ((a&0xf8)>>3)+1 (R and B), ga = ((a&0xf8)>>2)+1 (G)."""
    if slot == -1:
        return list(pal)
    if slot in TINT:
        tint, mode, alpha = TINT[slot], 10, 0x7f
    elif slot == 6:
        tint, mode, alpha = 0xf800, 4, 200
    else:
        raise ValueError('slot %d: alpha uninitialised in the original' % slot)
    ra = ((alpha & 0xf8) >> 3) + 1
    ga = ((alpha & 0xf8) >> 2) + 1
    base = mode * 1024
    out = []
    for c in pal:
        if c == SKIP:
            out.append(c)
            continue
        r, g, b = c >> 11, (c & 0x7e0) >> 6, c & 0x1f
        tr, tg, tb = tint >> 11, (tint & 0x7e0) >> 6, tint & 0x1f
        R = table[base + tr * 32 + r]
        B = table[base + tb * 32 + b]
        G = table[base + tg * 32 + g] * 2
        if alpha != 0xff:
            R = (ra * R + r * (32 - ra)) >> 5
            G = (ga * G + g * 2 * (64 - ga)) >> 6
            B = (ra * B + (32 - ra) * b) >> 5
        out.append(((R << 11) | ((G << 5) & 0x7e0) | (B & 0x1f)) & 0xffff)
    return out


# --------------------------------------------------------------------------------- scaled draw (Rotate family)
ROT_MODE = {  # DM_DrawRotate565 0x49d20 / DM_DrawRotate565Ex 0x4a308 dispatch: (flip, rot) -> mode (0..8, see dm_blit.MODE_TRANSFORM)
    (0, 0): 0, (0, 180): 3, (0, 270): 6, (0, 90): 4,
    (1, 0): 1, (1, 180): 2, (1, 270): 8, (1, 90): 7,
    (2, 0): 2, (2, 180): 1, (2, 270): 7, (2, 90): 8,
}


def rot_mode(flip, rot):
    """flip == 3 -> mode 3 whatever rot is (as DM_DrawImg, unlike DM_DrawImgEx); unknown rot -> mode 0."""
    if flip == 3:
        return 3
    return ROT_MODE.get((flip, rot), 0)


def calc_anchor_simple(x, y, w, h, anchor):
    """DM_CalcAnchor 0x4ce4c (used by the Rotate family; no rotation special-case)."""
    if anchor & 0x10:
        x -= w >> 1
    elif anchor & 0x20:
        x -= w
    if anchor & 2:
        y -= h >> 1
    elif anchor & 4:
        y -= h
    return x, y


class Img:
    """Raw GBM body view used by the scaled draw (reads like DM_DrawRotate565: raw bytes, no decoding)."""

    def __init__(self, body):
        self.t, self.w, self.h, self.aux, self.nc, self.pal, self.px = gbm.header(body)
        self.stride = ((self.w + 1) >> 1) if self.t == 0x0b else self.w

    def color(self, sx, sy, row, col):
        """colour at source (sx + ..) exactly as the original addresses it (4 bpp: nibble parity from `col` only)."""
        if self.t == 0x0b:
            b = self.px[self.stride * (sy + row) + (sx >> 1) + (col >> 1)]
            return self.pal[(b >> 4) if (col & 1) == 0 else (b & 15)]
        return self.pal[self.px[self.stride * (sy + row) + sx + col]]


def raster_scaled(fb, fw, clip, img, x, y, W, H, sx, sy, mode, scx, scy, anchor, alpha=None, effect=0, table=None):
    """DM_DrawRotate565 (alpha is None) / DM_DrawRotate565Ex: nearest-neighbour scaled copy.
    dest size = max(1, W*scx/100) x max(1, H*scy/100), 10-bit fixed-point steps (step + 1 added per pixel);
    modes >= 4 swap the destination box (extent along x = scaled H).  Clipping is per pixel against `clip`."""
    if alpha == 0:
        return
    dw = max(1, cdiv(W * scx, 100))
    dh = max(1, cdiv(H * scy, 100))
    stx = cdiv(W << 10, dw) + 1
    sty = cdiv(H << 10, dh) + 1
    if mode < 4:
        x, y = calc_anchor_simple(x, y, dw, dh, anchor)
        bw, bh = dw, dh
    else:
        x, y = calc_anchor_simple(x, y, dh, dw, anchor)
        bw, bh = dh, dw
    L, T, R, B = clip
    for py in range(bh):
        for px in range(bw):
            if mode < 4:
                col, row = (px * stx) >> 10, (py * sty) >> 10
                if mode in (1, 3):
                    col = W - 1 - col
                if mode in (2, 3):
                    row = H - 1 - row
            else:
                row, col = (px * sty) >> 10, (py * stx) >> 10
                if mode in (4, 7):
                    row = H - 1 - row
                if mode in (6, 7):
                    col = W - 1 - col
            s = img.color(sx, sy, row, col)
            dx, dy = x + px, y + py
            if s != SKIP and L <= dx < R and T <= dy < B:
                o = (dy * fw + dx) * 2
                if alpha is None:
                    n = s
                else:
                    n = blend_pixel(s, fb[o] | (fb[o + 1] << 8), alpha, effect, table)
                fb[o] = n & 0xff
                fb[o + 1] = n >> 8


# --------------------------------------------------------------------------------- self-test
FW, FH = 240, 400


def _rig(binp, resdir):
    """Reference harness + the two ARM-EABI soft-float helpers DM_ConvertHSBtoRGB calls (standard EABI
    semantics: __aeabi_i2d int -> double in r1:r0 ; __aeabi_dcmpeq a(r1:r0) == b(r3:r2) -> 1/0)."""
    from and_hle import Harness
    h = Harness(binp, resdir).boot_dm()

    def i2d(e, a):
        v = a[0] - (1 << 32) if a[0] & 0x80000000 else a[0]
        lo, hi = struct.unpack('<II', struct.pack('<d', float(v)))
        return (lo, hi)

    def dcmpeq(e, a):
        x = struct.unpack('<d', struct.pack('<II', a[0], a[1]))[0]
        y = struct.unpack('<d', struct.pack('<II', a[2], a[3]))[0]
        return 1 if x == y else 0
    h.imports['__aeabi_i2d'] = i2d
    h.imports['__aeabi_dcmpeq'] = dcmpeq
    return h


def _load(h, body):
    src = h.alloc(len(body)); h.write(src, body)
    cell = h.alloc(4); h.write(cell, struct.pack('<I', src))
    img = h.alloc(0x20)
    h.call('DM_LoadMergeGbm565', cell, img)
    return img


def _rand_body(rng, w, hh, t):
    """Random GBM body with a random palette (a few SKIP entries) and random pixels."""
    n = 256 if t == 0x0c else 16
    n = min(n, rng.randint(2, n))
    pal = []
    for _ in range(n):
        pal.append(SKIP if rng.random() < 0.15 else rng.randrange(65536))
    body = bytes([t]) + struct.pack('<4H', w, hh, w, n) + b''.join(struct.pack('<H', c) for c in pal)
    if t == 0x0c:
        body += bytes(rng.randrange(n) for _ in range(w * hh))
    else:
        stride = (w + 1) >> 1
        raw = bytearray(stride * hh)
        for yy in range(hh):
            for xx in range(w):
                i = rng.randrange(n)
                raw[yy * stride + (xx >> 1)] |= (i << 4) if xx % 2 == 0 else i
        body += bytes(raw)
    return body


def selftest_blend(h, rng, n_cases=3000):
    """Table equality + full-frame DM_DrawImgEx comparison."""
    tab_orig = h.read(h.objaddr('BlendingTable32Bit'), 16384)
    table = build_table()
    ok_table = tab_orig == table
    print('BlendingTable32Bit (16x32x32) equal to model:', ok_table)
    outr = h.objaddr('OutR')
    fb = h.frames[h.screen][0]
    bad = 0
    n = 0
    for _ in range(n_cases):
        w, hh = rng.randint(1, 20), rng.randint(1, 20)
        t = rng.choice([0x0b, 0x0c])
        body = _rand_body(rng, w, hh, t)
        iw, ih, rows = gbm.decode(body)
        img = _load(h, body)
        clip = rng.choice([(0, 0, FW, FH), (20, 30, 100, 90), (0, 0, 8, 8)])
        h.write(outr, struct.pack('<4i', *clip))
        flip, rot = rng.choice([(0, 0), (0, 90), (0, 180), (0, 270), (1, 0), (2, 0), (3, 0), (1, 90), (1, 270), (2, 90), (3, 90), (3, 180), (3, 270)])
        alpha = rng.choice([0, 1, 7, 8, 9, 100, 127, 128, 200, 247, 248, 254, 255, rng.randrange(256)])
        effect = rng.choice(list(range(17)) + [rng.randrange(17)])
        anchor = rng.choice([0, 0x12, 0x10, 0x04, 0x22])
        x = rng.randint(clip[0] - 10, clip[2] + 3)
        y = rng.randint(clip[1] - 10, clip[3] + 3)
        # random non-uniform background
        bg = bytes(rng.randrange(256) for _ in range(FW * FH * 2))
        h.write(fb, bg)
        pl = dm_blit.plan(x, y, iw, ih, 0, 0, flip, rot, anchor, clip, ex=True)
        if pl is not None and (pl[2] <= 0 or pl[3] <= 0):
            continue                                   # zero-extent UB, see phaseC_blit.md
        h.call('DM_DrawImgEx', h.screen, x, y, iw, ih, img, 0, 0, flip, rot, alpha, effect, anchor)
        got = h.read(fb, FW * FH * 2)
        exp = bytearray(bg)
        draw_ex(exp, FW, FH, clip, rows, x, y, iw, ih, 0, 0, flip, rot, alpha, effect, anchor, table)
        n += 1
        if got != bytes(exp):
            bad += 1
            if bad <= 5:
                d = [i // 2 for i in range(0, len(got), 2) if got[i:i + 2] != bytes(exp[i:i + 2])]
                print('MISMATCH DrawImgEx', hex(t), (iw, ih), 'flip,rot', (flip, rot), 'alpha', alpha, 'effect', effect,
                      'anchor', hex(anchor), 'xy', (x, y), 'ndiff', len(d), (d[0] % FW, d[0] // FW))
    print('DM_DrawImgEx full-frame cases', n, 'mismatches', bad)
    return bad + (0 if ok_table else 1)


def selftest_palette(h, rng, n_cases=4000):
    """Original DM_SetChagePalBlendEx / DM_RestorePalette vs the model, on random images and parameters."""
    table = build_table()
    bad = n = 0
    for case in range(n_cases):
        w, hh = rng.randint(1, 12), rng.randint(1, 12)
        t = rng.choice([0x0b, 0x0c])
        body = _rand_body(rng, w, hh, t)
        _, _, _, _, nc, bpal, _ = gbm.header(body)
        img = _load(h, body)
        pal_ptr = h.u32(img + 0xc)
        flag0 = rng.choice([0, 0, 0, 2])
        h.write(img + 0x1c, bytes([flag0]))
        mode = rng.choice([0x15, 0x15, 0x14, 0x10] + list(range(16)))
        if mode == 0x15:
            p2, p3, p4 = rng.choice([(0, 0, 0), (rng.randint(-360, 360), rng.randint(-100, 100), rng.randint(-100, 100)),
                                     (rng.randint(-50, 50), 0, 0), (0, rng.randint(-100, 100), 0), (0, 0, rng.randint(-100, 100))])
        else:
            p2, p3, p4 = rng.choice([(0, 0, 0), (rng.randint(-300, 300), rng.randint(-300, 300), rng.randint(-300, 300)),
                                     (rng.randrange(256), rng.randrange(256), rng.randrange(256))])
        alpha = rng.choice([0, 1, 50, 100, 101, 128, 254, 255, rng.randrange(256)])
        cur = [struct.unpack('<H', h.read(pal_ptr + 2 * i, 2))[0] for i in range(nc)]
        exp_pal, exp_flag = set_pal_blend(cur, flag0, p2, p3, p4, mode, alpha, table)
        h.call('DM_SetChagePalBlendEx', img, p2 & 0xffffffff, p3 & 0xffffffff, p4 & 0xffffffff, mode, alpha)
        got = [struct.unpack('<H', h.read(pal_ptr + 2 * i, 2))[0] for i in range(nc)]
        got_flag = h.read(img + 0x1c, 1)[0]
        n += 1
        ok = got == exp_pal and got_flag == exp_flag
        if ok and mode != 0x15 and exp_flag == 1 and flag0 != 1:
            ok = h.u32(img + 0x14) == pal_ptr                   # current palette pointer
        # restore
        exp_r, exp_rf = restore_palette(bpal, exp_pal, exp_flag)
        h.call('DM_RestorePalette', img)
        got_r = [struct.unpack('<H', h.read(pal_ptr + 2 * i, 2))[0] for i in range(nc)]
        ok = ok and got_r == exp_r and h.read(img + 0x1c, 1)[0] == exp_rf
        if exp_rf == 0 and exp_flag != flag0 or flag0 != 0:
            ok = ok and h.u32(img + 0x18) == h.u32(img + 8) if flag0 != 0 or exp_flag != 0 else ok
        if not ok:
            bad += 1
            if bad <= 5:
                print('PAL MISMATCH mode', hex(mode), (p2, p3, p4), 'alpha', alpha, 'flag0', flag0, 'got', got[:4], 'exp', exp_pal[:4], got_flag, exp_flag)
    print('palette cases', n, 'mismatches', bad)
    return bad


def selftest_hsb(h, rng, n=20000):
    """DM_ConvertRGBtoHSB / DM_ConvertHSBtoRGB, original vs model."""
    bad = 0
    buf_in, buf_out = h.alloc(16), h.alloc(16)
    for i in range(n):
        r, g, b = rng.randrange(256), rng.randrange(256), rng.randrange(256)
        if i % 5 == 0:
            r = g = rng.randrange(256)
        h.write(buf_in, struct.pack('<3i', r, g, b))
        h.call('DM_ConvertRGBtoHSB', buf_out, buf_in)
        got = struct.unpack('<3i', h.read(buf_out, 12))
        if got != rgb_to_hsb(r, g, b):
            bad += 1
            if bad <= 5: print('RGB2HSB DIFF', (r, g, b), got, rgb_to_hsb(r, g, b))
        H, S, V = rng.randint(0, 360), rng.randint(0, 100), rng.randint(0, 100)
        if i % 7 == 0: S = 0
        h.write(buf_in, struct.pack('<3i', H, S, V))
        h.call('DM_ConvertHSBtoRGB', buf_out, buf_in)
        got = struct.unpack('<3i', h.read(buf_out, 12))
        if got != hsb_to_rgb(H, S, V):
            bad += 1
            if bad <= 5: print('HSB2RGB DIFF', (H, S, V), got, hsb_to_rgb(H, S, V))
    print('HSB conversion cases', 2 * n, 'mismatches', bad)
    return bad


def selftest_scaled(h, rng, n_cases=2500):
    """DM_DrawImgRotate / DM_DrawImgRotateEx (-> DM_DrawRotate565 / Ex) vs raster_scaled, full-frame compare."""
    table = build_table()
    outr = h.objaddr('OutR')
    fb = h.frames[h.screen][0]
    bad = n = 0
    for _ in range(n_cases):
        w, hh = rng.randint(1, 16), rng.randint(1, 16)
        t = rng.choice([0x0b, 0x0c])
        body = _rand_body(rng, w, hh, t)
        im = Img(body)
        img = _load(h, body)
        clip = rng.choice([(0, 0, FW, FH), (20, 30, 100, 90), (0, 0, 8, 8)])
        h.write(outr, struct.pack('<4i', *clip))
        flip, rot = rng.choice([(0, 0), (0, 90), (0, 180), (0, 270), (1, 0), (1, 90), (1, 180), (1, 270), (2, 0), (2, 90),
                                (2, 180), (2, 270), (3, 0), (3, 90), (0, 45)])
        scx, scy = rng.choice([(100, 100), (200, 200), (50, 50), (30, 60), rng.randint(1, 300), (rng.randint(1, 300), rng.randint(1, 300))]) \
            if False else (rng.choice([100, 200, 50, 30, 80, 120, rng.randint(1, 300)]), rng.choice([100, 200, 60, 120, 80, rng.randint(1, 300)]))
        sx = rng.choice([0, 0, 0, 1]) if w > 2 else 0
        sy = rng.choice([0, 0, 1]) if hh > 2 else 0
        ww, hs = w - sx, hh - sy
        anchor = rng.choice([0, 0x12, 0x10, 0x04, 0x22, 0x06])
        ex = rng.random() < 0.5
        alpha = rng.choice([1, 100, 128, 255, rng.randrange(1, 256)])
        effect = rng.randrange(17)
        x = rng.randint(clip[0] - 5, clip[2] + 3)
        y = rng.randint(clip[1] - 5, clip[3] + 3)
        bg = bytes(rng.randrange(256) for _ in range(FW * FH * 2))
        h.write(fb, bg)
        if ex:
            h.call('DM_DrawImgRotateEx', h.screen, x, y, ww, hs, img, sx, sy, flip, rot, scx, scy, effect, alpha, anchor)
        else:
            h.call('DM_DrawImgRotate', h.screen, x, y, ww, hs, img, sx, sy, flip, rot, scx, scy, anchor)
        got = h.read(fb, FW * FH * 2)
        exp = bytearray(bg)
        raster_scaled(exp, FW, clip, im, x, y, ww, hs, sx, sy, rot_mode(flip, rot), scx, scy, anchor,
                      alpha if ex else None, effect, table)
        n += 1
        if got != bytes(exp):
            bad += 1
            if bad <= 5:
                d = [i // 2 for i in range(0, len(got), 2) if got[i:i + 2] != bytes(exp[i:i + 2])]
                print('SCALED MISMATCH', 'Ex' if ex else '', hex(t), (w, hh), (sx, sy), 'flip,rot', (flip, rot), 'scale', (scx, scy),
                      'anchor', hex(anchor), 'xy', (x, y), 'ndiff', len(d), (d[0] % FW, d[0] // FW))
    print('scaled-draw full-frame cases', n, 'mismatches', bad)
    return bad


def selftest_load_blend(h, resdir, rng):
    """DM_LoadResGbmBlending on every real TILE / OBJ file, DM_LoadResGbmMergeBlending on real hero images (+ PAL swap),
    all time-of-day slots; compares the resulting BODY palette, working palette and image header fields."""
    import pal as palmod
    table = build_table()
    files = []
    for sub in ('TILE', 'OBJ'):
        for root, _, fs in os.walk(os.path.join(resdir, sub)):
            for f in sorted(fs):
                b = open(os.path.join(root, f), 'rb').read()
                try:
                    gbm.header(b)
                except Exception:
                    continue
                files.append((os.path.relpath(os.path.join(root, f), resdir).replace(os.sep, '/'), b))
    bad = n = 0
    path = h.alloc(120)
    for rel, b in files:
        t, w, hh, aux, nc, bpal, px = gbm.header(b)
        if t not in (0x0b, 0x0c):
            continue
        for slot in (-1, 0, 1, 2, 3, 4, 5, 6, 7):
            h.write(path, b'/' + rel.encode() + b'\0')
            img = h.alloc(0x20)
            h.call('DM_LoadResGbmBlending', path, img, slot & 0xffffffff)
            body = h.u32(img + 8)
            got_b = [struct.unpack('<H', h.read(body + 9 + 2 * i, 2))[0] for i in range(nc)]
            wp = h.u32(img + 0xc)
            got_w = [struct.unpack('<H', h.read(wp + 2 * i, 2))[0] for i in range(nc)]
            hdr = struct.unpack('<4h', h.read(img, 8))
            exp_b = gbm_load_blend(bpal, slot, table)
            n += 1
            if got_b != exp_b or hdr != (w, hh, aux, nc) or got_w != exp_b or h.read(img + 0x1c, 1)[0] != 0:
                bad += 1
                if bad <= 5:
                    print('LOADBLEND MISMATCH', rel, slot, got_b[:4], exp_b[:4], hdr, (w, hh, aux, nc))
    print('DM_LoadResGbmBlending cases', n, '(files', len(files), ') mismatches', bad)
    # merge variant on hero containers with PAL swaps
    n2 = bad2 = 0
    heroes = []
    for root, _, fs in sorted(os.walk(os.path.join(resdir, 'H4'))):
        for f in sorted(fs):
            if f.startswith('_HIMG_'):
                bb = open(os.path.join(root, f), 'rb').read()
                if gbm.is_container(bb):
                    heroes.append(gbm.parse_container(bb)[0])
    heroes = rng.sample(heroes, min(30, len(heroes)))
    palfiles = {}
    for body in heroes:
        _, _, _, _, nc, bpal, _ = gbm.header(body)
        for pn in (-1, 255, 0, 1, 3, 10, 100, 195):
            for slot in (-1, 0, 1, 2, 3, 4, 5, 6):
                src = h.alloc(len(body)); h.write(src, body)
                cell = h.alloc(4); h.write(cell, struct.pack('<I', src))
                img = h.alloc(0x20)
                h.call('DM_LoadResGbmMergeBlending', cell, img, pn & 0xffffffff, slot & 0xffffffff)
                # model: PAL swap (if 0 <= pn <= 254) then tint
                pairs = []
                if 0 <= pn <= 254:
                    pf = os.path.join(resdir, 'H4', 'PAL', '_H_%03d_PAL' % pn)
                    if os.path.exists(pf):
                        pairs = palmod.parse(open(pf, 'rb').read())
                sw_b, sw_w = palmod.apply(bpal, bpal, pairs)
                tinted = gbm_merge_blend(sw_w, slot, table)
                exp_b = sw_b if slot == -1 else tinted           # slot -1 returns before the body palette is rewritten
                exp_w = tinted
                body_ptr = h.u32(img + 8)
                got_b = [struct.unpack('<H', h.read(body_ptr + 9 + 2 * i, 2))[0] for i in range(nc)]
                wp = h.u32(img + 0xc)
                got_w = [struct.unpack('<H', h.read(wp + 2 * i, 2))[0] for i in range(nc)]
                n2 += 1
                if got_b != exp_b or got_w != exp_w:
                    bad2 += 1
                    if bad2 <= 5:
                        print('MERGEBLEND MISMATCH pal', pn, 'slot', slot, 'body', got_b[:4], exp_b[:4], 'work', got_w[:4], exp_w[:4])
    print('DM_LoadResGbmMergeBlending cases', n2, 'mismatches', bad2)
    return bad + bad2


def selftest_plan(h):
    """DM_DrawImgEx placement == dm_blit.plan(): hook DM_DrawScreen565Ex's entry and compare the arguments
    handed to it on a dense grid (alpha / effect must be passed through unchanged)."""
    from unicorn import UC_HOOK_CODE
    from unicorn.arm_const import UC_ARM_REG_SP, UC_ARM_REG_LR, UC_ARM_REG_PC, UC_ARM_REG_R1, UC_ARM_REG_R2, UC_ARM_REG_R3
    rec = []

    def s32(v):
        return v - (1 << 32) if v & 0x80000000 else v

    def cb(uc, addr, size, _):
        sp = uc.reg_read(UC_ARM_REG_SP)
        st = struct.unpack('<11I', bytes(uc.mem_read(sp, 44)))
        rec.append(tuple(s32(v) for v in (uc.reg_read(UC_ARM_REG_R1), uc.reg_read(UC_ARM_REG_R2), uc.reg_read(UC_ARM_REG_R3),
                                          st[0], st[4], st[5], st[8], st[9], st[10])))
        uc.reg_write(UC_ARM_REG_PC, uc.reg_read(UC_ARM_REG_LR))
    a = h.elf.fbyname['DM_DrawScreen565Ex'][0]
    hk = h.uc.hook_add(UC_HOOK_CODE, cb, begin=a, end=a)
    body = _rand_body(random.Random(1), 5, 3, 0x0c)
    img = _load(h, body)
    outr = h.objaddr('OutR')
    clip = (4, 6, 14, 17)
    h.write(outr, struct.pack('<4i', *clip))
    combos = [(f, r) for f in (0, 1, 2, 3) for r in (0, 90, 180, 270)] + [(0, 45), (3, 45)]
    n = bad = 0
    for flip, rot in combos:
        for w in range(1, 8):
            for hh in range(1, 8):
                for x in range(-9, 24):
                    for y in range(-9, 24):
                        rec.clear()
                        h.call('DM_DrawImgEx', h.screen, x, y, w, hh, img, 3, 5, flip, rot, 77, 5, 0)
                        pl = dm_blit.plan(x, y, w, hh, 3, 5, flip, rot, 0, clip, ex=True)
                        exp = None if pl is None else tuple(pl[:6]) + (pl[6], 77, 5)
                        got = tuple(rec[0]) if rec else None
                        n += 1
                        if got != exp:
                            bad += 1
                            if bad <= 5:
                                print('PLAN DIFF', (flip, rot), (x, y, w, hh), got, exp)
    h.uc.hook_del(hk)
    print('DM_DrawImgEx clip-plan cases', n, 'mismatches', bad)
    return bad


if __name__ == '__main__':
    if sys.argv[1] == 'selftest':
        hh = _rig(sys.argv[2], sys.argv[3])
        r = selftest_load_blend(hh, sys.argv[3], random.Random(3))
        r += selftest_hsb(hh, random.Random(7))
        r += selftest_palette(hh, random.Random(11))
        r += selftest_scaled(hh, random.Random(31))
        r += selftest_plan(hh)
        r += selftest_blend(hh, random.Random(2024))
        sys.exit(1 if r else 0)
