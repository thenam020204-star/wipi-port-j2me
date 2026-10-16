package hl4.game;

import hl4.sys.Bytes;

/**
 * Tile collision of the hero's move (Map_Check_CollisionZone_Move_hero @0xa2c6c, ..._n_Rect_MoveFlag_hero @0xa1e0c, Map_Check_Collision_RectForDirection @0xa1134,
 * Map_Check_Collision_RectVsRect @0xa109c).  Tile properties 1 and 6 block a whole tile, 2 / 3 the upper / lower half, 4 / 5 the left / right half (slopes slide the hero along).
 * The original passes pointers to the move and to locals; here a pointer is a (byte[], offset) pair over s32 cells.
 */
public final class HeroColl {
    private HeroColl() {
    }

    private static final byte[] M = G.map;
    private static final int[] recC = new int[8];
    private static final int[] recR = new int[8];
    /** locals of Map_Check_CollisionZone_Move_hero: +0 local_2c (y slide), +4 local_30 (x slide), +8 local_3c, +12 local_38, +16 local_34 */
    private static final byte[] loc = new byte[24];

    private static int tp(int x, int y) {
        return Coll.tileProp((byte) x, (byte) y);
    }

    private static int abs(int v) {
        return v < 0 ? -v : v;
    }

    private static void absP(byte[] b, int o) {
        Bytes.w32(b, o, abs(Bytes.s32(b, o)));
    }

    private static void negP(byte[] b, int o) {
        int v = Bytes.s32(b, o);
        Bytes.w32(b, o, v > 0 ? -v : v);
    }

    /** Map_Check_Collision_RectVsRect */
    static boolean rectVsRect(int p1, int p2, int p3, int p4, int p5, int p6, int p7, int p8) {
        boolean xo;
        if (p1 <= p5) {
            if (p5 < p3 + p1) {
                xo = true;
            } else if (p1 < p5) {
                return false;
            } else {
                xo = p7 + p5 > p1;
            }
        } else {
            xo = p7 + p5 > p1;
        }
        if (!xo) {
            return false;
        }
        if (p2 <= p6) {
            if (p6 < p4 + p2) {
                return true;
            }
            if (p2 < p6) {
                return false;
            }
        }
        return p2 < p8 + p6;
    }

    /** Map_Check_Collision_RectForDirection(prop, tx, ty, rect): the hero rectangle meets the solid part of a half tile (props 2..5); false for other props */
    static boolean rectForDir(int prop, int tx, int ty, int p4, int p5, int p6, int p7) {
        int tw = M[0x7e];
        int th = M[0x7f];
        switch (prop) {
            case 2:
                return rectVsRect(p4, p5, p6, p7, tw * tx, th * ty, tw, th >> 1);
            case 3: {
                int h2 = th >> 1;
                return rectVsRect(p4, p5, p6, p7, tw * tx, th * ty + h2, tw, h2);
            }
            case 4:
                return rectVsRect(p4, p5, p6, p7, tw * tx, th * ty, tw >> 1, th);
            case 5: {
                int w2 = tw >> 1;
                return rectVsRect(p4, p5, p6, p7, tw * tx + w2, th * ty, w2, th);
            }
            default:
                return false;
        }
    }

    /** the vertical measure of the slope tail (cases 0 / 1): distance to the tile edge limited by the pending slide, signed by the side of the tile centre */
    private static void tailY(int p2, int p4, int bottom, int row, int th, int k1, byte[] b8, int o8) {
        int v8;
        if (k1 == 0) {
            Bytes.w32(b8, o8, 0);
            v8 = 0;
        } else {
            v8 = Bytes.s32(b8, o8);
        }
        int base = row * th;
        int d = p2 < base ? bottom - base : (row * th + th) - p2;
        if (v8 <= d) {
            d = v8;
        }
        int mid = base + (th >> 1);
        Bytes.w32(b8, o8, p2 + (p4 >> 1) < mid ? -d : d);
    }

    /** the horizontal measure of the slope tail (cases 2 / 3) */
    private static void tailX(int p1, int p3, int right, int col, int tw, int k1, byte[] b8, int o8) {
        int v9;
        if (k1 == 0) {
            Bytes.w32(b8, o8, 0);
            v9 = 0;
        } else {
            v9 = Bytes.s32(b8, o8);
        }
        int base = col * tw;
        int d = p1 < base ? right - base : (col * tw + tw) - p1;
        if (v9 <= d) {
            d = v9;
        }
        int mid = base + (tw >> 1);
        Bytes.w32(b8, o8, p1 + (p3 >> 1) < mid ? -d : d);
    }

    /**
     * Map_Check_CollisionZone_n_Rect_MoveFlag_hero(x, y, w, h, flag, vx*, vy*, slide*): scans the strip the hero is about to enter (flag 0 left, 1 right, 2 up, 3 down) for blocked
     * tiles; with a hit it writes the shortened move (vx / vy) and the slide along the edge (slide)
     */
    static void moveFlag(int p1, int p2, int p3, int p4, int flag, byte[] b6, int o6, byte[] b7, int o7, byte[] b8, int o8) {
        if (p3 < 1 || p4 < 1) {
            return;
        }
        int tw = M[0x7e];
        int th = M[0x7f];
        int bottom = p2 + p4;
        int right = p1 + p3;
        int c0 = p1 / tw;
        int r0 = p2 / th;
        int c1 = right / tw;
        int r1 = bottom / th;
        Bytes.w32(b8, o8, 0);
        int idx = 0;
        int n = 0;
        int last = -1;
        int full = 0;
        switch (flag) {
            case 0:
                if (right % tw == 0) {
                    c1--;
                }
                if (bottom % th != 0) {
                    r1++;
                }
                if (!(c0 - 1 < c1)) {
                    return;
                }
                for (int c = c1;;) {
                    int row = r0;
                    while (row < r1) {
                        int prop = tp(c, row);
                        if (prop != 0) {
                            if (rectForDir(prop, c, row, p1, p2, p3, p4)) {
                                recC[n] = c;
                                recR[n] = row;
                                row++;
                                n++;
                                last = last == 1 ? 1 : prop;
                                continue;
                            } else if (prop == 6 || prop == 1) {
                                recC[n] = c;
                                recR[n] = row;
                                idx = n;
                                n++;
                                full++;
                                last = prop;
                            }
                        }
                        row++;
                    }
                    if (n >= 1) {
                        break;
                    }
                    c--;
                    if (c <= c0 - 1) {
                        tail(full, b8, o8);
                        return;
                    }
                }
                {
                    int col = recC[idx];
                    int u4 = last == 4 ? (col * tw + (tw >> 1)) - right : (col * tw + tw) - right;
                    int row = recR[idx];
                    Bytes.w32(b8, o8, p3 - abs(u4));
                    Bytes.w32(b6, o6, u4);
                    Bytes.w32(b7, o7, 0);
                    slopeY(col, row, 4, p2, p4, bottom, th, b8, o8);
                }
                tail(full, b8, o8);
                return;
            case 1:
                if (right % tw != 0) {
                    c1++;
                }
                if (bottom % th != 0) {
                    r1++;
                }
                if (c1 <= c0) {
                    return;
                }
                for (int c = c0; c != c1; c++) {
                    int row = r0;
                    while (row < r1) {
                        int prop = tp(c, row);
                        if (prop != 0) {
                            if (rectForDir(prop, c, row, p1, p2, p3, p4)) {
                                recC[n] = c;
                                recR[n] = row;
                                row++;
                                n++;
                                last = last == 1 ? 1 : prop;
                                continue;
                            } else if (prop == 6 || prop == 1) {
                                recC[n] = c;
                                recR[n] = row;
                                idx = n;
                                n++;
                                full++;
                                last = prop;
                            }
                        }
                        row++;
                    }
                    if (n > 0) {
                        int col = recC[idx];
                        int u4 = last == 5 ? (col * tw - (tw >> 1)) - p1 : col * tw - p1;
                        int rr = recR[idx];
                        Bytes.w32(b8, o8, p3 - u4);
                        Bytes.w32(b6, o6, u4);
                        Bytes.w32(b7, o7, 0);
                        if (p2 % th < (th >> 1)) {
                            Bytes.w32(b8, o8, -Bytes.s32(b8, o8));
                        }
                        slopeY(col, rr, 5, p2, p4, bottom, th, b8, o8);
                        break;
                    }
                }
                tail(full, b8, o8);
                return;
            case 2:
                if (right % tw != 0) {
                    c1++;
                }
                if (bottom % th == 0) {
                    r1--;
                }
                if (!(r0 - 1 < r1)) {
                    return;
                }
                for (int r = r1;;) {
                    int col = c0;
                    while (col < c1) {
                        int prop = tp(col, r);
                        if (prop != 0) {
                            if (rectForDir(prop, col, r, p1, p2, p3, p4)) {
                                recC[n] = col;
                                recR[n] = r;
                                col++;
                                n++;
                                last = last == 1 ? 1 : prop;
                                continue;
                            } else if (prop == 6 || prop == 1) {
                                recC[n] = col;
                                recR[n] = r;
                                idx = n;
                                n++;
                                full++;
                                last = prop;
                            }
                        }
                        col++;
                    }
                    if (n >= 1) {
                        break;
                    }
                    r--;
                    if (r <= r0 - 1) {
                        tail(full, b8, o8);
                        return;
                    }
                }
                {
                    int row = recR[idx];
                    int u5 = last == 2 ? ((row * th + th) - (th >> 1)) - bottom : (row * th + th) - bottom;
                    int col = recC[idx];
                    Bytes.w32(b8, o8, p4 - abs(u5));
                    Bytes.w32(b6, o6, 0);
                    Bytes.w32(b7, o7, u5);
                    slopeX(col, row, 2, p1, p3, right, tw, b8, o8);
                }
                tail(full, b8, o8);
                return;
            default:
                if (right % tw != 0) {
                    c1++;
                }
                if (bottom % th != 0) {
                    r1++;
                }
                if (r1 <= r0) {
                    return;
                }
                for (int r = r0; r != r1; r++) {
                    int col = c0;
                    while (col < c1) {
                        int prop = tp(col, r);
                        if (prop != 0) {
                            if (rectForDir(prop, col, r, p1, p2, p3, p4)) {
                                recC[n] = col;
                                recR[n] = r;
                                col++;
                                n++;
                                last = last == 1 ? 1 : prop;
                                continue;
                            } else if (prop == 6 || prop == 1) {
                                recC[n] = col;
                                recR[n] = r;
                                idx = n;
                                n++;
                                full++;
                                last = prop;
                            }
                        }
                        col++;
                    }
                    if (n > 0) {
                        int row = recR[idx];
                        int u5 = last == 3 ? (row * th + (th >> 1)) - p2 : row * th - p2;
                        int col2 = recC[idx];
                        Bytes.w32(b8, o8, p4 - u5);
                        Bytes.w32(b6, o6, 0);
                        Bytes.w32(b7, o7, u5);
                        slopeX(col2, row, 3, p1, p3, right, tw, b8, o8);
                        break;
                    }
                }
                tail(full, b8, o8);
                return;
        }
    }

    /** LAB_000a20c0: two or more blocking tiles cancel the slide */
    private static void tail(int full, byte[] b8, int o8) {
        if (full > 1) {
            Bytes.w32(b8, o8, 0);
        }
    }

    /** the slide direction decision of the horizontal moves (flags 0 / 1): by the tile under the hit, else by its neighbours above / below (free = 4 or 5) */
    private static void slopeY(int col, int row, int freeK, int p2, int p4, int bottom, int th, byte[] b8, int o8) {
        int prop = tp(col, row);
        if (prop == 2) {
            absP(b8, o8);
            return;
        }
        if (prop == 3) {
            negP(b8, o8);
            return;
        }
        int a = tp(col, row - 1);
        int b = tp(col, row + 1);
        int k1 = a == 0 ? freeK : (a == 3 ? 1 : 0);
        int k2 = b == 0 ? freeK : (b == 2 ? 2 : 0);
        if (k1 < k2) {
            absP(b8, o8);
        } else if (k2 < k1) {
            negP(b8, o8);
        } else {
            tailY(p2, p4, bottom, row, th, k1, b8, o8);
        }
    }

    /** the slide direction decision of the vertical moves (flags 2 / 3) */
    private static void slopeX(int col, int row, int flag, int p1, int p3, int right, int tw, byte[] b8, int o8) {
        int prop = tp(col, row);
        if (prop == 4) {
            absP(b8, o8);
            return;
        }
        if (prop == 5) {
            negP(b8, o8);
            return;
        }
        int a = tp(col - 1, row);
        int b = tp(col + 1, row);
        int extra = flag == 2 ? 2 : 3;
        int k1 = (a == 0 || a == extra) ? 2 : (a == 5 ? 1 : 0);
        int k2 = (b == 0 || b == extra) ? 2 : (b == 4 ? 2 : 0);
        if (k1 < k2) {
            absP(b8, o8);
        } else if (k2 < k1) {
            negP(b8, o8);
        } else {
            tailX(p1, p3, right, col, tw, k1, b8, o8);
        }
    }

    /** Map_Check_CollisionZone_Move_hero(x, y, w, h, vx*, vy*): the pending move (s32 at m[ox], m[oy]) is shortened at blocked tiles; the slide continues along the edge */
    public static void zoneMoveHero(int p1, int p2, int p3, int p4, byte[] m, int ox, int oy) {
        int vx = Bytes.s32(m, ox);
        int vy = Bytes.s32(m, oy);
        if (vx == 0 && vy == 0) {
            return;
        }
        if (M[0x270] < 1) {
            return;
        }
        byte[] l = loc;
        Bytes.w32(l, 4, 0);                      // local_30: x slide
        Bytes.w32(l, 0, 0);                      // local_2c: y slide
        int x;
        int y;
        int w = p3;
        int h = p4;
        if (vx != 0 && vy != 0) {
            return;                              // diagonal: the original's switch has no case (the keypad never moves diagonally)
        }
        if (vx != 0) {
            if (vx < 0) {
                int a = abs(vx);
                moveFlag(p1 - a, p2, a, p4, 0, m, ox, m, oy, l, 0);
                x = p1 - abs(Bytes.s32(m, ox));
            } else {
                moveFlag(p3 + p1, p2, vx, p4, 1, m, ox, m, oy, l, 0);
                x = abs(Bytes.s32(m, ox)) + p1;
            }
            y = p2;
        } else if (vy < 0) {
            int a = abs(vy);
            moveFlag(p1, p2 - a, p3, a, 2, m, ox, m, oy, l, 4);
            y = p2 - abs(Bytes.s32(m, oy));
            x = p1;
        } else {
            moveFlag(p1, p4 + p2, p3, vy, 3, m, ox, m, oy, l, 4);
            y = abs(Bytes.s32(m, oy)) + p2;
            x = p1;
        }
        int sy = Bytes.s32(l, 0);
        if (sy != 0) {
            int hh = sy < 1 ? -sy : sy;
            y += sy < 1 ? sy : p4;
            Bytes.w32(l, 8, 0);
            Bytes.w32(l, 12, sy);
            Bytes.w32(l, 16, 0);
            moveFlag(x, y, p3, hh, sy < 1 ? 2 : 3, l, 8, l, 12, l, 16);
            Bytes.w32(m, oy, Bytes.s32(l, 12));
        }
        int sx = Bytes.s32(l, 4);
        if (sx != 0) {
            if (sx < 1) {
                w = sx;
            }
            Bytes.w32(l, 12, 0);
            Bytes.w32(l, 16, sx);
            Bytes.w32(l, 8, 0);
            moveFlag(w + x, y, abs(sx), p4, sx < 0 ? 0 : 1, l, 16, l, 12, l, 8);
            Bytes.w32(m, ox, Bytes.s32(l, 16));
        }
    }
}
