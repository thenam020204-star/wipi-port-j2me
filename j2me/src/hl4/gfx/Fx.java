package hl4.gfx;

/**
 * Pixel operations of the DM module (RGB565 build): blend table, DM_DrawImgEx, the scaled draws (DM_DrawImgRotate/Ex) and the palette
 * operations (restore, tint/blend/HSB, time-of-day tints).  Port of tools/dm_fx.py, verified against the original (see docs/phaseC_fx.md).
 * Integer arithmetic only (the original's single "float" test is S == 0).
 *
 * Deviations for undefined behaviour of the original: blend effect values outside 0..16 behave as effect 0 (the original indexes past
 * the 16-mode table); source pixels outside the image are skipped; images whose time-of-day slot is undefined are left untouched.
 */
public final class Fx {
    private Fx() {
    }

    static final int SKIP = Gbm.SKIP_COL;

    /** BlendingTable32Bit: T[mode][a][b] (mode 0..15), built like DM_SetBlendingTable565 @0x51360. */
    public static final byte[] TABLE = buildTable();

    private static byte[] buildTable() {
        byte[] t = new byte[16 * 32 * 32];
        for (int a = 0; a < 32; a++) {
            for (int b = 0; b < 32; b++) {
                int na = 31 - a;
                for (int k = 0; k < 16; k++) {
                    int v;
                    switch (k) {
                        case 0:
                            v = a;
                            break;
                        case 1:
                            v = a < b ? a : b;
                            break;
                        case 2:
                            v = a > b ? a : b;
                            break;
                        case 3:
                            v = (a * b) / 31;
                            break;
                        case 4:
                            v = 31 - (na * (31 - b)) / 31;
                            break;
                        case 5:
                            v = a == 31 ? b * 31 : (b * 31) / na;
                            v = v >= 32 ? 31 : v;
                            break;
                        case 6:
                            v = 31 - ((31 - b) * 31) / (a + 1);
                            v = v < 0 ? 0 : v;
                            break;
                        case 7:
                            v = a + b;
                            v = v >= 32 ? 31 : v;
                            break;
                        case 8:
                            v = a - 31 + b;
                            v = v < 0 ? 0 : v;
                            break;
                        case 9:
                            v = a * 2 - 31 + b;
                            v = v < 0 ? 0 : (v >= 32 ? 31 : v);
                            break;
                        case 10:
                            v = b > 16 ? 31 - ((na * (31 - b)) >> 4) : (a * b) >> 4;
                            v = v < 0 ? 0 : v;
                            break;
                        case 11:
                            v = a >= 17 ? 31 - ((na * (31 - b)) >> 4) : (a * b) >> 4;
                            v = v < 0 ? 0 : v;
                            break;
                        case 12: {
                            int lo = 2 * a - 31;
                            int hi = 2 * a;
                            if (b < lo) {
                                v = lo;
                            } else if (hi < b) {
                                v = hi > 31 ? 31 : hi;
                            } else {
                                v = b;
                            }
                            break;
                        }
                        case 13:
                            v = (31 - b) <= a ? 31 : 0;
                            break;
                        case 14:
                            v = a > b ? a - b : b - a;
                            break;
                        default:
                            v = (b + a) - ((a * b) >> 4);
                            v = v < 0 ? 0 : (v >= 32 ? 31 : v);
                            break;
                    }
                    t[(k * 32 + a) * 32 + b] = (byte) v;
                }
            }
        }
        return t;
    }

    /** DM_DrawScreen565Ex per-pixel rule; s, d RGB565 (0..65535); returns the new destination pixel. */
    public static int blendPixel(int s, int d, int alpha, int effect) {
        if (s == SKIP) {
            return d;
        }
        if (effect < 0 || effect > 16) {
            effect = 0;
        }
        int rs = s >> 11;
        int gs = (s & 0x7e0) >> 6;
        int bs = s & 31;
        int rd = d >> 11;
        int gd = (d & 0x7e0) >> 6;
        int bd = d & 31;
        int r;
        int g;
        int b;
        if (effect == 0x10) {
            r = (rs * 6 + bs * 2 + gs * 4) >> 4;
            g = r;
            b = r;
        } else {
            int base = effect * 1024;
            r = TABLE[base + rs * 32 + rd] & 0xff;
            g = TABLE[base + gs * 32 + gd] & 0xff;
            b = TABLE[base + bs * 32 + bd] & 0xff;
        }
        int g6 = g * 2;
        if (alpha < 0xff) {
            int ra = ((alpha & 0xf8) >> 3) + 1;
            int ga = ((alpha & 0xf8) >> 2) + 1;
            int ba = (alpha & 0xf8) >> 3;
            r = (r * ra + rd * (32 - ra)) >> 5;
            g6 = (g6 * ga + gd * 2 * (64 - ga)) >> 6;
            b = (b * ba + bd * (32 - ba)) >> 5;
        }
        return ((r << 11) | ((g6 << 5) & 0x7e0) | (b & 0x1f)) & 0xffff;
    }

    /**
     * DM_FillRectBlendingRGB(frame, x, y, w, h, mode, alpha, rgb) @0x5222c (565 frames; the 24-bit colour is cut to 5-bit R/G/B): the rectangle is
     * clipped to the frame buffer (not the clip rectangle); per pixel the table of 'mode' is applied to (colour, destination) and then mixed with the
     * destination by alpha exactly as blendPixel does (the blue weight uses alpha >> 3, not +1: CONFIRMED in the disassembly).
     */
    public static void fillRectBlend(Frame f, int x, int y, int w, int h, int mode, int alpha, int rgb) {
        if (x < 0) {
            w += x;
            x = 0;
        }
        if (y < 0) {
            h += y;
            y = 0;
        }
        if (x + w > f.w) {
            w = f.w - x;
        }
        if (y + h > f.h) {
            h = f.h - y;
        }
        int sr = (rgb & 0xf80000) >> 19;
        int sg = (rgb & 0xf800) >> 11;
        int sb = (rgb & 0xf8) >> 3;
        int base = (mode & 0xff) * 1024;
        int ra = ((alpha & 0xf8) >> 3) + 1;
        int ga = ((alpha & 0xf8) >> 2) + 1;
        int ba = (alpha & 0xf8) >> 3;
        for (int j = 0; j < h; j++) {
            int o = (y + j) * f.w + x;
            for (int i = 0; i < w; i++, o++) {
                int d = f.px[o] & 0xffff;
                int rd = d >> 11;
                int gd = (d & 0x7e0) >> 6;
                int bd = d & 31;
                int r = TABLE[base + sr * 32 + rd] & 0xff;
                int g6 = (TABLE[base + sg * 32 + gd] & 0xff) * 2;
                int b = TABLE[base + sb * 32 + bd] & 0xff;
                if (alpha != 0xff) {
                    r = (r * ra + rd * (32 - ra)) >> 5;
                    g6 = (g6 * ga + gd * 2 * (64 - ga)) >> 6;
                    b = (b * ba + bd * (32 - ba)) >> 5;
                }
                f.px[o] = (short) ((r << 11) | ((g6 << 5) & 0x7e0) | (b & 0x1f));
            }
        }
    }

    /** DM_DrawScreen565Ex @0x3fdec: like Blit.raster but blending into the destination. */
    private static void rasterEx(Frame f, Gbm img, int X, int Y, int W, int H, int SX, int SY, int mode, int alpha, int effect) {
        if (alpha == 0 || W <= 0 || H <= 0) {
            return;
        }
        int flip = Blit.MODE_FLIP[mode];
        int rot = Blit.MODE_ROT[mode];
        Blit.mapping(flip, rot, W, H);
        boolean swapped = flip != 3 && (rot == 90 || rot == 270);
        int dw = swapped ? H : W;
        int dh = swapped ? W : H;
        int u0 = Blit.M[0], dudx = Blit.M[1], dudy = Blit.M[2], v0 = Blit.M[3], dvdx = Blit.M[4], dvdy = Blit.M[5];
        int x0 = X < 0 ? -X : 0;
        int x1 = X + dw > f.w ? f.w - X : dw;
        int y0 = Y < 0 ? -Y : 0;
        int y1 = Y + dh > f.h ? f.h - Y : dh;
        short[] dst = f.px;
        for (int dy = y0; dy < y1; dy++) {
            int di = (Y + dy) * f.w + X + x0;
            int u = SX + u0 + dudx * x0 + dudy * dy;
            int v = SY + v0 + dvdx * x0 + dvdy * dy;
            for (int dx = x0; dx < x1; dx++, di++, u += dudx, v += dvdx) {
                int s = img.colorAt(u, v);
                if (s >= 0 && s != SKIP) {
                    dst[di] = (short) blendPixel(s, dst[di] & 0xffff, alpha, effect);
                }
            }
        }
    }

    /** DM_DrawImgEx(frame, x, y, w, h, img, srcx, srcy, flip, rot, alpha, effect, anchor) (@0x4ee60). */
    public static void drawImgEx(Frame f, int x, int y, int w, int h, Gbm img, int sx, int sy, int flip, int rot, int alpha, int effect,
                                 int anchor) {
        if (Blit.plan(x, y, w, h, sx, sy, flip, rot, anchor, f.clipL, f.clipT, f.clipR, f.clipB, true)) {
            rasterEx(f, img, Blit.planX(), Blit.planY(), Blit.planW(), Blit.planH(), Blit.planSX(), Blit.planSY(), Blit.planMode(), alpha, effect);
        }
    }

    // ----------------------------------------------------------------------------- scaled draws
    /** DM_DrawRotate565 dispatch: flip == 3 -> mode 3 whatever rot is; unknown rot -> mode 0. */
    private static int rotMode(int flip, int rot) {
        if (flip == 3) {
            return 3;
        }
        if (flip == 0) {
            return rot == 0 ? 0 : rot == 180 ? 3 : rot == 270 ? 6 : rot == 90 ? 4 : 0;
        }
        if (flip == 1) {
            return rot == 0 ? 1 : rot == 180 ? 2 : rot == 270 ? 8 : rot == 90 ? 7 : 0;
        }
        if (flip == 2) {
            return rot == 0 ? 2 : rot == 180 ? 1 : rot == 270 ? 7 : rot == 90 ? 8 : 0;
        }
        return 0;
    }

    /** colour at source (sx + col, sy + row) addressed like DM_DrawRotate565 (4 bpp: nibble parity from col only); -1 outside the image. */
    private static int colorRaw(Gbm img, int sx, int sy, int row, int col) {
        int yy = sy + row;
        if (yy < 0 || yy >= img.h) {
            return -1;
        }
        if (img.type == 0x0b) {
            int stride = (img.w + 1) >> 1;
            int xb = (sx >> 1) + (col >> 1);
            if (xb < 0 || xb >= stride) {
                return -1;
            }
            int b = img.data[img.pixOff + stride * yy + xb] & 0xff;
            return img.pal[(col & 1) == 0 ? b >> 4 : b & 15] & 0xffff;
        }
        int xx = sx + col;
        if (xx < 0 || xx >= img.w) {
            return -1;
        }
        return img.pal[img.data[img.pixOff + img.w * yy + xx] & 0xff] & 0xffff;
    }

    private static void scaled(Frame f, Gbm img, int x, int y, int W, int H, int sx, int sy, int mode, int scx, int scy, int anchor,
                               boolean ex, int alpha, int effect) {
        if (ex && alpha == 0) {
            return;
        }
        int dw = W * scx / 100;
        if (dw < 1) {
            dw = 1;
        }
        int dh = H * scy / 100;
        if (dh < 1) {
            dh = 1;
        }
        int stx = (W << 10) / dw + 1;
        int sty = (H << 10) / dh + 1;
        int bw;
        int bh;
        int ax;
        int ay;
        int aw;
        int ah;
        if (mode < 4) {
            aw = dw;
            ah = dh;
            bw = dw;
            bh = dh;
        } else {
            aw = dh;
            ah = dw;
            bw = dh;
            bh = dw;
        }
        ax = x;
        ay = y;
        if ((anchor & 0x10) != 0) {
            ax -= aw >> 1;
        } else if ((anchor & 0x20) != 0) {
            ax -= aw;
        }
        if ((anchor & 2) != 0) {
            ay -= ah >> 1;
        } else if ((anchor & 4) != 0) {
            ay -= ah;
        }
        int L = f.clipL;
        int T = f.clipT;
        int R = f.clipR;
        int B = f.clipB;
        for (int py = 0; py < bh; py++) {
            int dy = ay + py;
            if (dy < T || dy >= B || dy < 0 || dy >= f.h) {
                continue;
            }
            for (int px = 0; px < bw; px++) {
                int dx = ax + px;
                if (dx < L || dx >= R || dx < 0) {
                    continue;
                }
                int col;
                int row;
                if (mode < 4) {
                    col = (px * stx) >> 10;
                    row = (py * sty) >> 10;
                    if (mode == 1 || mode == 3) {
                        col = W - 1 - col;
                    }
                    if (mode == 2 || mode == 3) {
                        row = H - 1 - row;
                    }
                } else {
                    row = (px * sty) >> 10;
                    col = (py * stx) >> 10;
                    if (mode == 4 || mode == 7) {
                        row = H - 1 - row;
                    }
                    if (mode == 6 || mode == 7) {
                        col = W - 1 - col;
                    }
                }
                int s = colorRaw(img, sx, sy, row, col);
                if (s >= 0 && s != SKIP) {
                    int o = dy * f.w + dx;             // DM_DrawRotate565(Ex) clips only against the clip rectangle: a clip wider than the frame wraps into the next row
                    if (o >= f.px.length) {
                        continue;
                    }
                    f.px[o] = (short) (ex ? blendPixel(s, f.px[o] & 0xffff, alpha, effect) : s);
                }
            }
        }
    }

    /** DM_DrawImgRotate(frame, x, y, w, h, img, srcx, srcy, flip, rot, scaleX%, scaleY%, anchor): nearest-neighbour scaling. */
    public static void drawImgRotate(Frame f, int x, int y, int w, int h, Gbm img, int sx, int sy, int flip, int rot, int scx, int scy,
                                     int anchor) {
        scaled(f, img, x, y, w, h, sx, sy, rotMode(flip, rot), scx, scy, anchor, false, 0, 0);
    }

    /** DM_DrawImgRotateEx: the same with the blend rule of DM_DrawImgEx. */
    public static void drawImgRotateEx(Frame f, int x, int y, int w, int h, Gbm img, int sx, int sy, int flip, int rot, int scx, int scy,
                                       int effect, int alpha, int anchor) {
        scaled(f, img, x, y, w, h, sx, sy, rotMode(flip, rot), scx, scy, anchor, true, alpha, effect);
    }

    /**
     * DM_DrawEffectClip(frame, x, y, w, h, img, srcx, srcy, mirror, rot, scaleA, scaleB, effect, alpha, anchor) @0x4ff3c (CONFIRMED in the disassembly):
     * mirror code 8 -> flip 1, 0xc -> flip 3, 4 -> flip 2, anything else -> 0; effect 0x11 -> 7.  Scales 100/100: rot 0 with effect 0 and alpha 0xff is the
     * plain blit (DM_DrawImageClip for mirror 0x10, DM_DrawImageClipMirror otherwise), rot 0 with effect 0 and another alpha, or effect 1..16: DM_DrawImgEx;
     * rot 90/180/270: DM_DrawImgEx; other rot / effect > 16: nothing.  Other scales: rot 0/90/180/270 -> DM_DrawImgRotateEx(scaleA, scaleB), else nothing.
     */
    public static void drawEffectClip(Frame f, int x, int y, int w, int h, Gbm img, int sx, int sy, int mirror, int rot, int scA, int scB, int effect,
                                      int alpha, int anchor) {
        int flip = mirror == 8 ? 1 : (mirror == 0xc ? 3 : (mirror == 4 ? 2 : 0));
        if (effect == 0x11) {
            effect = 7;
        }
        alpha &= 0xff;
        effect &= 0xff;
        boolean rotOk = rot == 0xb4 || rot == 0x5a || rot == 0x10e;
        if (scA != 100 || scB != 100) {
            if (rot == 0 || rotOk) {
                drawImgRotateEx(f, x, y, w, h, img, sx, sy, flip, rot, scA, scB, effect, alpha, anchor);
            }
            return;
        }
        if (rot != 0) {
            if (rotOk) {
                drawImgEx(f, x, y, w, h, img, sx, sy, flip, rot, alpha, effect, anchor);
            }
            return;
        }
        if (effect == 0) {
            if (alpha == 0xff) {
                Blit.drawImg(f, x, y, w, h, img, sx, sy, mirror == 0x10 ? 0 : flip, 0, anchor);
            } else {
                drawImgEx(f, x, y, w, h, img, sx, sy, flip, 0, alpha, effect, anchor);
            }
        } else if (effect <= 0x10) {
            drawImgEx(f, x, y, w, h, img, sx, sy, flip, 0, alpha, effect, anchor);
        }
    }

    // ----------------------------------------------------------------------------- HSB
    /** DM_ConvertRGBtoHSB @0x4dc6c: r,g,b 0..255 -> {H 0..360, S 0..100, B 0..100}. */
    public static void rgbToHsb(int r, int g, int b, int[] out) {
        int bb = b * 100 / 255;
        int gg = g * 100 / 255;
        int rr = r * 100 / 255;
        int mx = gg > bb ? gg : bb;
        if (rr > mx) {
            mx = rr;
        }
        if (mx < 1) {
            out[0] = 0;
            out[1] = 0;
            out[2] = 0;
            return;
        }
        int mn = gg < bb ? gg : bb;
        if (rr < mn) {
            mn = rr;
        }
        int d = mx - mn;
        int h;
        if (rr == mx) {
            if (gg < bb) {
                h = d != 0 ? (gg - bb) * 60 / d + 360 : d;
            } else {
                h = d != 0 ? (gg - bb) * 60 / d : d;
            }
        } else if (gg == mx) {
            h = d == 0 ? 120 : (bb - rr) * 60 / d + 120;
        } else if (bb == mx) {
            h = d == 0 ? 240 : (rr - gg) * 60 / d + 240;
        } else {
            h = 0;
        }
        out[0] = h;
        out[1] = 100 - mn * 100 / mx;
        out[2] = mx;
    }

    /** DM_ConvertHSBtoRGB @0x4ddf4 -> {r, g, b} 0..255. */
    public static void hsbToRgb(int H, int S, int B, int[] out) {
        if (B == 0) {
            out[0] = 0;
            out[1] = 0;
            out[2] = 0;
            return;
        }
        if (S == 0) {
            int v = B * 255 / 100;
            out[0] = v;
            out[1] = v;
            out[2] = v;
            return;
        }
        int hh = H * 100 / 360;
        int r = B;
        int g;
        int b;
        if (hh < 16) {
            b = B * (100 - S) / 100;
            g = (B - b) * hh * 6 / 100 + b;
        } else {
            g = B;
            if (hh < 33) {
                b = B * (100 - S) / 100;
                r = B - (B - b) * (hh - 16) * 6 / 100;
            } else if (hh < 50) {
                r = B * (100 - S) / 100;
                b = (B - r) * (hh - 33) * 6 / 100 + r;
            } else {
                b = B;
                if (hh < 66) {
                    r = B * (100 - S) / 100;
                    g = B - (B - r) * (hh - 50) * 6 / 100;
                } else if (hh < 83) {
                    g = B * (100 - S) / 100;
                    r = (B - g) * (hh - 66) * 6 / 100 + g;
                } else {
                    g = B * (100 - S) / 100;
                    b = B - (B - g) * (hh - 83) * 6 / 100;
                }
            }
        }
        out[0] = r * 255 / 100;
        out[1] = g * 255 / 100;
        out[2] = b * 255 / 100;
    }

    // ----------------------------------------------------------------------------- palette operations
    private static final int[] HSB = new int[3];
    private static final int[] RGB = new int[3];

    /** DM_RestorePalette @0x3d898: copy the pristine body palette back, clear the flag (only if the flag is set). */
    public static void restorePalette(Gbm img) {
        if (img.palFlag == 0) {
            return;
        }
        img.pal = img.palBase;
        img.palFlag = 0;
    }

    /**
     * DM_SetChagePalBlendEx @0x4e66c (RGB565 branch): tint / blend / HSB shift of the working palette; no-op when the palette is
     * already changed.  p2..p4 are s32, mode and alpha bytes.
     */
    public static void setPalBlendEx(Gbm img, int p2, int p3, int p4, int mode, int alpha) {
        if (img.palFlag == 1) {
            return;
        }
        short[] pal = new short[img.palBase.length];
        System.arraycopy(img.pal, 0, pal, 0, pal.length);
        if (mode == 0x15) {
            if (p2 == 0 && p3 == 0 && p4 == 0) {
                return;
            }
            for (int i = 0; i < pal.length; i++) {
                int c = pal[i] & 0xffff;
                if (c == SKIP) {
                    continue;
                }
                int r = (c >> 11) << 3;
                int g = (c >> 3) & 0xf8;
                int b = (c & 0x1f) << 3;
                rgbToHsb(r, g, b, HSB);
                int H = HSB[0] + p2;
                int S = HSB[1];
                int V = HSB[2];
                if (H < 0) {
                    H += 360;
                }
                if (H > 360) {
                    H -= 360;
                }
                if (p3 >= 1) {
                    S = (100 - S) * p3 / 100 + S;
                } else if (p3 != 0) {
                    S = S * p3 / 100 + S;
                }
                hsbToRgb(H, S, V, RGB);
                r = RGB[0];
                g = RGB[1];
                b = RGB[2];
                if (p4 >= 1) {
                    r = (255 - r) * p4 / 100 + r;
                    g = (255 - g) * p4 / 100 + g;
                    b = (255 - b) * p4 / 100 + b;
                } else if (p4 != 0) {
                    r = r * p4 / 100 + r;
                    g = g * p4 / 100 + g;
                    b = b * p4 / 100 + b;
                }
                int out = (((g >> 2) & 0x3f) << 5) | (((r >> 3) & 0x1f) << 11) | ((b >> 3) & 0x1f);
                pal[i] = (short) (out != SKIP ? out : 0x821);
            }
            img.pal = pal;
            img.palFlag = 1;
            return;
        }
        int ra = ((alpha & 0xf8) >> 3) + 1;
        int ga = ((alpha & 0xf8) >> 2) + 1;
        boolean haveTint = ((p2 | p3 | p4) & 0xffff) != 0;
        int tint = haveTint ? ((p4 >> 3) | ((p2 >> 3) << 11) | ((p3 >> 2) << 5)) & 0xffff : 0;
        for (int i = 0; i < pal.length; i++) {
            int c = pal[i] & 0xffff;
            if (c == SKIP) {
                continue;
            }
            int t = haveTint ? tint : c;
            int tr = t >> 11;
            int tg = (t & 0x7e0) >> 6;
            int tb = t & 0x1f;
            int cr = c >> 11;
            int cg = (c & 0x7e0) >> 6;
            int cb = c & 0x1f;
            int r;
            int g;
            int b;
            int sh;
            if (mode == 0x14) {
                int inv = 100 - alpha;
                g = ((tg * alpha + inv * cg) / 100) & 0xff;
                r = ((tr * alpha + inv * cr) / 100) & 0xff;
                b = ((tb * alpha + inv * cb) / 100) & 0xff;
                sh = 6;
            } else {
                if (mode == 0x10) {
                    r = (tr * 6 + tb * 2 + tg * 4) >> 4;
                    g = r;
                    b = r;
                } else {
                    int base = (mode & 15) * 1024;
                    b = TABLE[base + tb * 32 + cb] & 0xff;
                    r = TABLE[base + tr * 32 + cr] & 0xff;
                    g = TABLE[base + tg * 32 + cg] & 0xff;
                }
                if (alpha == 0xff) {
                    g = g * 2;
                    if (mode == 0) {
                        b = tb;
                        r = tr;
                        g = tg;
                        sh = 6;
                    } else {
                        sh = 5;
                    }
                } else {
                    g = (ga * g * 2 + (0x40 - ga) * cg * 2) >> 6;
                    r = (r * ra + (0x20 - ra) * cr) >> 5;
                    b = (b * ra + (0x20 - ra) * cb) >> 5;
                    sh = 5;
                }
            }
            pal[i] = (short) ((r << 11) | ((g << sh) & 0x7e0) | (b & 0x1f));
        }
        img.pal = pal;
        img.palFlag = 1;
    }

    // ----------------------------------------------------------------------------- time-of-day tints
    private static final int[] TINT = {0xabd8, 0xc591, 0xbd2c, 0x45ff, 0xffff, 0xfc2d};

    /**
     * DM_LoadResGbmBlending @0x53e68 (tiles, map objects; slot = _map[0x78], the time-of-day class): tints the palette; BODY and
     * working palette end up identical.  slot -1 = untouched; 0..5 tint TINT[slot] mode 10; other slots tint 0 mode 0.
     */
    public static void loadBlend(Gbm img, int slot) {
        if (slot == -1) {
            return;
        }
        int tint = slot >= 0 && slot < 6 ? TINT[slot] : 0;
        int mode = slot >= 0 && slot < 6 ? 10 : 0;
        int base = mode * 1024;
        short[] src = img.palBase;
        short[] out = new short[src.length];
        for (int i = 0; i < src.length; i++) {
            int c = src[i] & 0xffff;
            if (c == SKIP) {
                out[i] = (short) c;
                continue;
            }
            int r = c >> 11;
            int g = (c & 0x7e0) >> 6;
            int b = c & 0x1f;
            int tr = tint >> 11;
            int tg = (tint & 0x7e0) >> 6;
            int tb = tint & 0x1f;
            int R = ((TABLE[base + tr * 32 + r] + r) * 0x400) & 0xf800;
            int B = (((TABLE[base + tb * 32 + b] + b) * 0x4000000) >>> 27);
            int G = (((TABLE[base + tg * 32 + g] & 0xff) * 0x3e + g * 0x42) >> 1) & 0x7e0;
            out[i] = (short) (R | G | B);
        }
        img.palBase = out;
        img.pal = out;
    }

    /**
     * DM_LoadResGbmMergeBlending @0x54750, tint stage (the _PAL swap runs before it): slot 0..5 tint TINT[slot] mode 10 alpha 0x7f;
     * slot 6 tint 0xf800 mode 4 alpha 200; slot -1 or any other slot: untouched (undefined in the original for other slots).
     */
    public static void mergeBlend(Gbm img, int slot) {
        int tint;
        int mode;
        int alpha;
        if (slot >= 0 && slot < 6) {
            tint = TINT[slot];
            mode = 10;
            alpha = 0x7f;
        } else if (slot == 6) {
            tint = 0xf800;
            mode = 4;
            alpha = 200;
        } else {
            return;
        }
        int ra = ((alpha & 0xf8) >> 3) + 1;
        int ga = ((alpha & 0xf8) >> 2) + 1;
        int base = mode * 1024;
        short[] src = img.palBase;
        short[] out = new short[src.length];
        for (int i = 0; i < src.length; i++) {
            int c = src[i] & 0xffff;
            if (c == SKIP) {
                out[i] = (short) c;
                continue;
            }
            int r = c >> 11;
            int g = (c & 0x7e0) >> 6;
            int b = c & 0x1f;
            int tr = tint >> 11;
            int tg = (tint & 0x7e0) >> 6;
            int tb = tint & 0x1f;
            int R = TABLE[base + tr * 32 + r] & 0xff;
            int B = TABLE[base + tb * 32 + b] & 0xff;
            int G = (TABLE[base + tg * 32 + g] & 0xff) * 2;
            if (alpha != 0xff) {
                R = (ra * R + r * (32 - ra)) >> 5;
                G = (ga * G + g * 2 * (64 - ga)) >> 6;
                B = (ra * B + (32 - ra) * b) >> 5;
            }
            out[i] = (short) ((R << 11) | ((G << 5) & 0x7e0) | (B & 0x1f));
        }
        img.palBase = out;
        img.pal = out;
    }
}
