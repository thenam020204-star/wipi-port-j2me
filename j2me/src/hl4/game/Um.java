package hl4.game;

/** Math/geometry helpers of the original (UM_* @0xd8c74..0xd9a6c): angle tables, 8-direction vectors, rectangle collision. */
public final class Um {
    private Um() {
    }

    public static int abs(int v) {
        return v < 0 ? -v : v;
    }

    /** UM_sin(deg): screen convention (positive Y is down): the sine table is negated for 0..180 */
    public static int sin(int a) {
        if (a > 0x5a) {
            if (a > 0xb4) {
                if (a < 0x10f) {
                    return Tab.UM_SIN[a - 180];
                }
                if (a < 0x169) {
                    return Tab.UM_SIN[0x168 - a];
                }
                return 0;
            }
            a = 0xb4 - a;
        }
        return -Tab.UM_SIN[a];
    }

    /** UM_cos(deg) */
    public static int cos(int a) {
        if (a < 0x5b) {
            return Tab.UM_COS[a];
        }
        if (a < 0xb5) {
            return -Tab.UM_COS[0xb4 - a];
        }
        if (a < 0x10f) {
            return -Tab.UM_COS[a - 180];
        }
        if (a < 0x169) {
            return Tab.UM_COS[0x168 - a];
        }
        return 0;
    }

    /** UM_getAngleY(angle, r) = r * sin / 10000 */
    public static int angleY(int a, int r) {
        return (r * sin(a)) / 10000;
    }

    public static int angleX(int a, int r) {
        return (r * cos(a)) / 10000;
    }

    public static int vecX(int d) {
        return d != -1 ? Tab.VEC_X[d] : 0;
    }

    public static int vecY(int d) {
        return d != -1 ? Tab.VEC_Y[d] : 0;
    }

    public static int turnOpposite(int d) {
        return d != -1 ? Tab.TURN_180[d] : 0;
    }

    /** UM_turnDirection(x1, y1, x2, y2) @0xd9380: 4-direction facing from point 1 to point 2 (0 up, 1 down, 2 left, 3 right), -1 when equal */
    public static int turnDirection(int x1, int y1, int x2, int y2) {
        int dx = x2 - x1;
        int dy = y2 - y1;
        if (dy != 0 || dx != 0) {
            int ay = abs(dy);
            int ax = abs(dx);
            if (dy == 0 || ay < ax) {
                return dx < 1 ? 2 : 3;
            }
            if (dx == 0 || ax < ay) {
                return dy > 0 ? 1 : 0;
            }
            if (ax == ay) {
                if (dx < 0) {
                    return 2;
                }
                if (dx != 0) {
                    return 3;
                }
                if (dy < 0) {
                    return 0;
                }
                if (dy != 0) {
                    return 1;
                }
            }
        }
        return -1;
    }

    /** UM_direction8(x1, y1, x2, y2) @0xd9430 */
    public static int direction8(int x1, int y1, int x2, int y2) {
        int dy = y2 - y1;
        int ay = abs(dy);
        int dx = x2 - x1;
        boolean horiz = false;
        if (ay != 0) {
            int ax = abs(dx);
            if (ax != 0) {
                if (ax == ay) {
                    if (dx < 1) {
                        return dy < 1 ? 5 : 4;
                    }
                    return dy < 1 ? 6 : 7;
                }
                int t1 = angleY(0x2d, ax);
                int t2 = angleY(0x2d, ay);
                if (t1 == 0) {
                    t1 = 2;
                }
                if (t2 == 0) {
                    t2 = 2;
                }
                if (ay < ax) {
                    if (t1 / 2 <= ay) {
                        if (dy >= 0) {
                            return dx < 0 ? 4 : 7;
                        }
                        return dx < 0 ? 5 : 6;
                    }
                    horiz = true;
                } else if (t2 / 2 <= ax) {
                    if (dx > 0) {
                        return dy < 0 ? 6 : 7;
                    }
                    return dy < 0 ? 5 : 4;
                }
            }
            if (!horiz) {
                return dy > 0 ? 1 : 0;
            }
        }
        return dx < 0 ? 2 : 3;
    }

    /** UM_getAngleFor8Dir @0xd9584 */
    public static int angleFor8Dir(int d) {
        switch (d) {
            case 0:
                return 0x5a;
            case 1:
                return 0x10e;
            case 2:
                return 0xb4;
            case 6:
                return 0x2d;
            case 7:
                return 0x13b;
            case 4:
                return 0xe1;
            case 5:
                return 0x87;
            default:
                return 0;
        }
    }

    /** UM_get8DirForAngle @0xd95f0 */
    public static int dirForAngle(int a) {
        if ((a - 0xf) >= 0 && (a - 0xf) < 0x5a) {
            return 6;
        }
        if (!(a - 0x69 >= 0 && a - 0x69 < 0x5a)) {
            if (!(a - 0xc3 >= 0 && a - 0xc3 < 0x5a)) {
                return (a - 0x11d >= 0 && a - 0x11d < 0x4b) ? 7 : 3;
            }
            return 4;
        }
        return 5;
    }

    /** UM_getDir24Anglendex @0xd9638 */
    public static int dir24(int v) {
        v &= 0xff;
        if (v - 1 >= 0 && v - 1 < 6) {
            return 6;
        }
        if (!(v - 7 >= 0 && v - 7 < 6)) {
            if (!(v - 0xd >= 0 && v - 0xd < 6)) {
                return (v - 0x13 >= 0 && v - 0x13 < 5) ? 7 : 3;
            }
            return 4;
        }
        return 5;
    }

    /**
     * UM_Collision_Two_Rect(ax, ay, aw, ah, bx, by, bw, bh, out[4]) @0xd901c: overlap area as a NEGATIVE number (callers test < 0),
     * 0 when the rectangles do not overlap or one is empty; out = overlap rectangle.
     */
    public static int collision(int p1, int p2, int p3, int p4, int p5, int p6, int p7, int p8, int[] o) {
        if (p3 == 0 || p4 == 0 || p7 == 0 || p8 == 0) {
            return 0;
        }
        o[3] = 0;
        o[0] = 0;
        o[1] = 0;
        o[2] = 0;
        int a4 = p3 + p1;
        int a1 = p4 + p2;
        int a3 = p5 + p7;
        int a2 = p6 + p8;
        boolean l98 = false;
        boolean l110 = false;
        if (p5 < p1) {
            l98 = true;
        } else {
            if (p5 < a4 && a3 <= a4 && p2 <= p6 && p6 < a1 && a2 <= a1) {
                o[3] = p8;
                o[2] = p7;
                o[0] = p5;
                o[1] = p6;
                return (a3 - p5) * (p6 - a2);
            }
            if (p5 <= p1) {
                l98 = true;
            }
        }
        if (l98 && p1 < a3) {
            if (a4 <= a3 && p6 <= p2 && p2 < a2 && a1 <= a2) {
                o[3] = p4;
                o[2] = p3;
                o[0] = p1;
                o[1] = p2;
                return (a4 - p1) * (p2 - a1);
            }
            l110 = true;
        }
        if (!l110 && a3 < p1) {
            return 0;
        }
        if (a4 < p5 || a2 < p2 || a1 < p6 || (a3 == p1 && (a2 == p2 || a1 == p6)) || (a4 == p5 && (a2 == p2 || a1 == p6))) {
            return 0;
        }
        if (a3 == p1) {
            if (a1 == p6) {
                return 0;
            }
            if (a2 == p2) {
                return 0;
            }
        } else {
            if (a2 == p2 || a1 == p6) {
                int d = p1 < p5 ? a4 - p5 : a3 - p1;
                o[0] = p5 < p1 ? p1 : p5;
                o[1] = p6 < p2 ? p2 : p6;
                o[3] = 1;
                o[2] = d;
                return d;
            }
            if (a4 != p5) {
                if (p6 < p2) {
                    p6 = p2;
                }
                if (p5 < p1) {
                    p5 = p1;
                }
                if (a1 <= a2) {
                    a2 = a1;
                }
                if (a4 < a3) {
                    a3 = a4 - p5;
                } else {
                    a3 = a3 - p5;
                }
                o[3] = a2 - p6;
                o[0] = p5;
                o[1] = p6;
                o[2] = a3;
                return a3 * (p6 - a2);
            }
        }
        int d = p2 < p6 ? a1 - p6 : a2 - p2;
        o[0] = p5 < p1 ? p1 : p5;
        o[1] = p6 < p2 ? p2 : p6;
        o[2] = 1;
        o[3] = d;
        return d;
    }

    /**
     * UM_getDiagonal(x0, y0, x1, y1, &x, &y, n) @0xc9a6c: the point n + 1 steps along the line from (x0, y0) toward (x1, y1); out is written only when the
     * end point is not reached within those steps (callers pre-set it).  The x-major branch subtracts 2*|dy| (not 2*|dx|) from the error term.
     */
    public static void getDiagonal(int x0, int y0, int x1, int y1, int n, int[] out) {
        int dy = y1 - y0;
        int dx = x1 - x0;
        int sx = dx >= 0 ? 1 : -1;
        int adx = dx < 0 ? -dx : dx;
        int sy = dy >= 0 ? 1 : -1;
        int ady = dy < 0 ? -dy : dy;
        int x;
        int y = y0;
        if (adx > ady) {
            if (x1 == x0) {
                return;
            }
            int lr = 2 * ady;
            int err = lr - adx;
            x = x0 + sx;
            while (true) {
                if (err >= 0) {
                    err -= lr;
                    y += sy;
                }
                n--;
                err += lr;
                if (n < 0) {
                    break;
                }
                x += sx;
                if (x1 == x - sx) {
                    return;
                }
            }
        } else {
            if (y1 == y0) {
                return;
            }
            int r5 = 2 * adx;
            y = y0 + sy;
            x = x0;
            int err = r5 - ady;
            while (true) {
                if (err >= 0) {
                    err -= 2 * ady;
                    x += sx;
                }
                n--;
                err += r5;
                if (n < 0) {
                    break;
                }
                y += sy;
                if (y1 == y - sy) {
                    return;
                }
            }
        }
        out[0] = x;
        out[1] = y;
    }
}
