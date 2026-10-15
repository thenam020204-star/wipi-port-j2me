package hl4.gfx;

/**
 * DM_DrawImg (@0x4f760) and DM_DrawScreen565 (@0x3de54): placement, anchor, flip/rotate, clipping and the 9 raster modes.
 * Port of tools/dm_blit.py, which was verified byte-for-byte against the ORIGINAL (1,776,085 full-frame cases and 907k plan
 * cases).  Every quirk of the original is reproduced (e.g. rot-90 images clipped at the top do not advance SX).
 *
 * Deviation for undefined behaviour of the original: zero-extent plans (W or H = 0 after clipping) draw nothing, and source
 * pixels outside the image are skipped (the original reads neighbouring memory).
 */
public final class Blit {
    private Blit() {
    }

    // plan result (X, Y, W, H, SX, SY, mode); single-threaded use only
    private static final int[] P = new int[7];
    // 6 coefficients of the dest->source mapping, see raster()
    private static final int[] M = new int[6];

    /** DM_CalcAnchorGbm @0x4d29c; returns x in out[0], y in out[1]. */
    private static void anchor(int x, int y, int w, int h, int rot, int anchor, int[] out) {
        boolean rotated = rot == 90 || rot == 270;
        if ((anchor & 0x10) != 0) {
            x -= w >> 1;
            if (rotated) {
                x += (w >> 1) - (h >> 1);
            }
        } else if ((anchor & 0x20) != 0) {
            x -= w;
        }
        if ((anchor & 0x02) != 0) {
            y -= h >> 1;
            if (rotated) {
                y -= (w >> 1) - (h >> 1);
            }
        } else if ((anchor & 0x04) != 0) {
            y -= h;
        }
        out[0] = x;
        out[1] = y;
    }

    private static final int[] XY = new int[2];

    // dispatch results of mode(): mode, r4, path ('A'=0, 'B'=1, 'C'=2)
    private static int dMode;
    private static int dR4;
    private static int dPath;

    private static void set(int mode, int r4, int path) {
        dMode = mode;
        dR4 = r4;
        dPath = path;
    }

    /** DM_DrawImg 0x4f7dc..0x4fc54 dispatch. */
    private static void mode(int flip, int rot) {
        if (flip == 0) {
            if (rot == 180) {
                set(5, 1, 0);
            } else if (rot == 270) {
                set(6, 0, 2);
            } else if (rot == 90) {
                set(4, 0, 1);
            } else {
                set(0, 0, 0);
            }
        } else if (flip == 1) {
            if (rot == 0) {
                set(1, 0, 0);
            } else if (rot == 180) {
                set(2, 0, 0);
            } else if (rot == 270) {
                set(8, 1, 2);
            } else if (rot == 90) {
                set(7, 1, 1);
            } else {
                set(0, 0, 0);
            }
        } else if (flip == 2) {
            if (rot == 0) {
                set(2, 0, 0);
            } else if (rot == 180) {
                set(1, 0, 0);
            } else if (rot == 270) {
                set(7, 1, 1);
            } else if (rot == 90) {
                set(8, 1, 2);
            } else {
                set(0, 0, 0);
            }
        } else if (flip == 3) {
            set(3, 0, 0);
        } else {
            set(0, 0, 0);
        }
    }

    /** DM_DrawImgEx dispatch (@0x4ee60): flip == 3 composes rot instead of ignoring it. */
    private static void modeEx(int flip, int rot) {
        if (flip == 3) {
            if (rot == 0) {
                set(3, 0, 0);
            } else if (rot == 90) {
                set(6, 0, 2);
            } else if (rot == 180) {
                set(0, 0, 0);
            } else if (rot == 270) {
                set(4, 0, 1);
            } else {
                set(0, 0, 0);
            }
        } else {
            mode(flip, rot);
        }
    }

    /**
     * Transliteration of DM_DrawImg 0x4f760..0x4fd10 up to the call of DM_DrawScreen565.  Fills P with
     * (X, Y, W, H, SX, SY, mode) and returns true, or returns false when nothing is drawn.
     */
    static boolean plan(int x, int y, int w, int h, int sx, int sy, int flip, int rot, int anc,
                        int L, int T, int R, int B, boolean ex) {
        if (w <= 0 || h <= 0) {
            return false;
        }
        anchor(x, y, w, h, rot, anc, XY);
        x = XY[0];
        y = XY[1];
        if (ex) {
            modeEx(flip, rot);
        } else {
            mode(flip, rot);
        }
        int m = dMode;
        int r4 = dR4;
        int path = dPath;
        int X;
        int Y;
        int W;
        int H;
        int SX;
        int SY;
        if (path == 0) {
            if (x >= R || x + w < L || y >= B || y + h < T) {
                return false;
            }
            if (x < L) {
                W = R >= x + w ? w - (L - x) : R - L;
                SX = (m == 0 || m == 2) ? sx + (L - x) : sx;
                X = L;
            } else {
                X = x;
                if (R >= x + w) {
                    W = w;
                    SX = sx;
                } else {
                    W = R - x;
                    SX = (m == 1 || m == 3 || r4 != 0) ? sx + (x + w - R) : sx;
                }
            }
            if (y < T) {
                H = B >= y + h ? y + h - T : B - T;
                SY = m <= 1 ? sy + (T - y) : sy;
                Y = T;
            } else {
                Y = y;
                if (B >= y + h) {
                    H = h;
                    SY = sy;
                } else {
                    H = B - y;
                    SY = (m == 2 || m == 3 || r4 != 0) ? sy + (y + h - B) : sy;
                }
            }
        } else if (path == 1) {
            if (x >= R || x + h < L || y >= B || y + w < T) {
                return false;
            }
            if (x < L) {
                if (R >= x + h) {
                    H = h - (L - x);
                    SY = sy;
                } else {
                    H = R - L;
                    SY = sy + (x + h - R);
                }
                X = L;
            } else {
                X = x;
                if (R >= x + h) {
                    H = h;
                    SY = sy;
                } else {
                    H = R - x;
                    SY = sy + (x + h - R);
                }
            }
            if (y < T) {
                if (B >= y + w) {
                    W = w - (T - y);
                    SX = sx;           // QUIRK: no advance (0x4f9c0)
                } else {
                    W = B - T;
                    SX = sx + (T - y);
                }
                Y = T;
            } else {
                Y = y;
                if (B >= y + w) {
                    W = w;
                    SX = sx;
                } else {
                    W = B - y;
                    SX = r4 != 0 ? sx + (y + w - B) : sx;
                }
            }
        } else {
            if (x >= R || x + h < L || y >= B || y + w < T) {
                return false;
            }
            if (x < L) {
                H = R >= x + h ? h - (L - x) : R - L;
                SY = sy + (L - x);
                X = L;
            } else {
                X = x;
                H = R >= x + h ? h : R - x;
                SY = sy;
            }
            if (y < T) {
                W = B >= y + w ? w - (T - y) : B - T;
                SX = r4 != 0 ? sx + (T - y) : sx;
                Y = T;
            } else {
                Y = y;
                if (B >= y + w) {
                    W = w;
                    SX = sx;
                } else {
                    W = B - y;
                    SX = m == 8 ? sx : sx + (y + w - B);
                }
            }
        }
        P[0] = X;
        P[1] = Y;
        P[2] = W;
        P[3] = H;
        P[4] = SX;
        P[5] = SY;
        P[6] = m;
        return true;
    }

    // DM_DrawScreen565 mode -> (flip, rot)
    private static final int[] MODE_FLIP = {0, 1, 2, 3, 0, 0, 0, 1, 1};
    private static final int[] MODE_ROT = {0, 0, 0, 0, 90, 180, 270, 90, 270};

    /** dest-local (px, py) -> source (u, v), as affine functions of (px, py); w/h are the source sub-rectangle size. */
    private static void mapping(int flip, int rot, int w, int h) {
        boolean fx;
        boolean fy;
        int r;
        if (flip == 3) {
            fx = false;
            fy = false;
            r = 180;
        } else {
            fx = flip == 1;
            fy = flip == 2;
            r = (rot == 90 || rot == 180 || rot == 270) ? rot : 0;
        }
        int u0;
        int dudx;
        int dudy;
        int v0;
        int dvdx;
        int dvdy;
        if (r == 0) {
            u0 = 0; dudx = 1; dudy = 0; v0 = 0; dvdx = 0; dvdy = 1;
        } else if (r == 90) {
            u0 = 0; dudx = 0; dudy = 1; v0 = h - 1; dvdx = -1; dvdy = 0;
        } else if (r == 180) {
            u0 = w - 1; dudx = -1; dudy = 0; v0 = h - 1; dvdx = 0; dvdy = -1;
        } else {
            u0 = w - 1; dudx = 0; dudy = -1; v0 = 0; dvdx = 1; dvdy = 0;
        }
        if (fx) {
            u0 = w - 1 - u0;
            dudx = -dudx;
            dudy = -dudy;
        }
        if (fy) {
            v0 = h - 1 - v0;
            dvdx = -dvdx;
            dvdy = -dvdy;
        }
        M[0] = u0;
        M[1] = dudx;
        M[2] = dudy;
        M[3] = v0;
        M[4] = dvdx;
        M[5] = dvdy;
    }

    /** DM_DrawScreen565 (layer 2): copy the source rectangle (SX, SY, W, H), transformed by `mode`, to (X, Y). */
    private static void raster(Frame f, Gbm img, int X, int Y, int W, int H, int SX, int SY, int mode) {
        if (W <= 0 || H <= 0) {
            return;                                   // original: undefined (uninitialised stack slot); draws nothing here
        }
        int flip = MODE_FLIP[mode];
        int rot = MODE_ROT[mode];
        mapping(flip, rot, W, H);
        boolean swapped = (flip != 3) && (rot == 90 || rot == 270);
        int dw = swapped ? H : W;
        int dh = swapped ? W : H;
        int u0 = M[0], dudx = M[1], dudy = M[2], v0 = M[3], dvdx = M[4], dvdy = M[5];
        int x0 = 0;
        int x1 = dw;
        if (X < 0) {
            x0 = -X;
        }
        if (X + x1 > f.w) {
            x1 = f.w - X;
        }
        int y0 = 0;
        int y1 = dh;
        if (Y < 0) {
            y0 = -Y;
        }
        if (Y + y1 > f.h) {
            y1 = f.h - Y;
        }
        short[] pal = img.pal;
        byte[] d = img.data;
        int po = img.pixOff;
        int iw = img.w;
        int ih = img.h;
        boolean b8 = img.type == 0x0c;
        int stride = b8 ? iw : (iw + 1) >> 1;
        short[] dst = f.px;
        for (int dy = y0; dy < y1; dy++) {
            int di = (Y + dy) * f.w + X + x0;
            int u = SX + u0 + dudx * x0 + dudy * dy;
            int v = SY + v0 + dvdx * x0 + dvdy * dy;
            for (int dx = x0; dx < x1; dx++, di++, u += dudx, v += dvdx) {
                if (u < 0 || v < 0 || u >= iw || v >= ih) {
                    continue;
                }
                int c;
                if (b8) {
                    c = pal[d[po + v * stride + u] & 0xff] & 0xffff;
                } else {
                    int bb = d[po + v * stride + (u >> 1)] & 0xff;
                    c = pal[(u & 1) == 0 ? bb >> 4 : bb & 0xf] & 0xffff;
                }
                if (c != Gbm.SKIP_COL) {
                    dst[di] = (short) c;
                }
            }
        }
    }

    /** DM_DrawImg(frame, x, y, w, h, img, srcx, srcy, flip, rot, anchor) using the frame's clip rectangle. */
    public static void drawImg(Frame f, int x, int y, int w, int h, Gbm img, int sx, int sy, int flip, int rot, int anc) {
        if (plan(x, y, w, h, sx, sy, flip, rot, anc, f.clipL, f.clipT, f.clipR, f.clipB, false)) {
            raster(f, img, P[0], P[1], P[2], P[3], P[4], P[5], P[6]);
        }
    }
}
