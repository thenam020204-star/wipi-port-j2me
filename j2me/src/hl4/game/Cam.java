package hl4.game;

import hl4.sys.Bytes;
import hl4.sys.Rng;

/** Camera / scrolling of the map (Map_Mapmove @0x94fb4, Event_*CameraMove @0x5958c..0x59f80, Map_Shake @0x94ce4, Map_Set_Shake @0xab10c). */
public final class Cam {
    private static final byte[] m = G.map;
    private static final byte[] e = G.event;

    private Cam() {
    }

    public static int cameraType() {
        return Bytes.s16(e, 0x93a);
    }

    /** Event_Set_CameraType */
    public static void setCameraType(int v) {
        Bytes.w16(e, 0x93a, v);
    }

    /** Event_Set_CameraMove(x, y, steps, type) @0x59f80: starts a scripted camera move to centre (x, y) (steps = -1: jump) */
    public static void setCameraMove(int p1, int p2, int p3, int p4) {
        int oldY = Bytes.s32(m, 0xa0);
        Bytes.w16(e, 0x93c, p4);
        if ((short) p4 < 0) {
            Bytes.w16(e, 0x93c, ((p4 & 0xffff) + 0x100));
        }
        Bytes.w16(e, 0x93a, 0xfe);
        if ((p2 | p1) >= 0) {
            p1 = p1 - Glob.gameW / 2;
            p1 = p1 & ~(p1 >> 31);
            if (Bytes.s32(m, 0x84) < p1 + Glob.gameW) {
                p1 = Bytes.s32(m, 0x84) - Glob.gameW;
            }
            p2 = p2 - Glob.gameH / 2;
            p2 = p2 & ~(p2 >> 31);
            if (Bytes.s32(m, 0x88) < p2 + Glob.gameH) {
                p2 = Bytes.s32(m, 0x88) - Glob.gameH;
            }
            Bytes.w32(e, 0x614, p1);
            Bytes.w32(e, 0x618, p2);
            if (p3 == -1) {
                Bytes.w32(e, 0x210, 1);
                Bytes.w32(e, 0x620, 0);
                Bytes.w32(e, 0x61c, 0);
            } else {
                if (p1 == Bytes.s32(m, 0x9c)) {
                    Bytes.w32(e, 0x61c, 0);
                } else {
                    Bytes.w32(e, 0x61c, (p1 - Bytes.s32(m, 0x9c)) / p3);
                }
                if (p2 == oldY) {
                    Bytes.w32(e, 0x620, 0);
                    Bytes.w32(e, 0x210, p3);
                } else {
                    Bytes.w32(e, 0x620, (p2 - oldY) / p3);
                    Bytes.w32(e, 0x210, p3);
                }
            }
            Maps.initDrawFlag();
            Bytes.w32(e, 0x20c, 0);
        }
    }

    /** Event_CameraMove(p1) @0x59edc: one step of the scripted move; false when it has finished */
    public static boolean cameraMove(boolean count) {
        Obj h = Pool.hero;
        Bytes.w32(m, 0x9c, Bytes.s32(e, 0x61c) + Bytes.s32(m, 0x9c));
        Bytes.w32(m, 0xa0, Bytes.s32(e, 0x620) + Bytes.s32(m, 0xa0));
        Bytes.w32(m, 0xc4, h.x());
        Bytes.w32(m, 0xcc, Bytes.s32(m, 0xc4));
        Bytes.w32(m, 0xc8, h.y());
        Bytes.w32(m, 0xd0, Bytes.s32(m, 0xc8));
        if (count) {
            Bytes.w32(e, 0x20c, Bytes.s32(e, 0x20c) + 1);
            if (Bytes.s32(e, 0x210) <= Bytes.s32(e, 0x20c)) {
                endCameraMove(true);
                Maps.initDrawFlag();
                return false;
            }
        }
        return true;
    }

    /** Event_endCameraMove(p1) @0x5958c */
    public static void endCameraMove(boolean follow) {
        Bytes.w32(m, 0xa0, Bytes.s32(e, 0x618));
        Bytes.w32(m, 0x9c, Bytes.s32(e, 0x614));
        setCameraType(Bytes.s16(e, 0x93c));
        if (follow && Bytes.s16(e, 0x93c) == 0xff) {
            mapMove();
        }
    }

    private static int third(int v) {
        int a = v < 0 ? -v : v;
        if (a < 3) {
            return v < 0 ? -1 : 1;
        }
        return v < 0 ? -(a / 3) : a / 3;
    }

    /** Map_Mapmove @0x94fb4: the camera follows the hero (or the scripted target) */
    public static void mapMove() {
        Obj h = Pool.hero;
        boolean cm = false;                      // MenuOptions_camera() == 0
        if (!Event.isEvent()) {
            cm = false;
        }
        int c2e6 = Bytes.s16(m, 0x2e6);
        if (c2e6 > 0) {
            Bytes.w16(m, 0x2e6, c2e6 - 1);
        }
        if (m[0x2e4] != 0) {
            int u = Bytes.s16(m, 0x2e6) & 0xffff;
            if (u == 0 || (short) u < 0) {
                if (u == 0) {
                    m[0x2e4] = (byte) Bytes.s16(m, 0x2e6);
                }
            } else {
                cm = true;
            }
        }
        int cx = h.x();
        int cy = h.y();
        int dir = h.m[0x22a] & 0xff;
        int type = cameraType();
        if (type == 0xfe) {
            cx -= 8;
            cy -= 8;
        } else if (type == 0xff || type == -1) {
            cy = h.y() - 8;
            cx = h.x() - 8;
        } else {
            cx = Bytes.s16(e, 10 + type * 10) + 8;
            cy = Bytes.s16(e, 10 + type * 10 + 2) + 8;
        }
        int ux = 0;
        int uy = 0;
        if (cm) {
            int d = (byte) dir;
            switch (d) {
                case 0:
                    ux = cx - Bytes.s32(m, 0xd4);
                    uy = ((cy - Glob.gameH / 2) - Tab.CAMERA_RANGE[d]) - Bytes.s32(m, 0xa0);
                    break;
                case 1:
                    ux = cx - Bytes.s32(m, 0xd4);
                    uy = (-(Glob.gameH / 2) - Bytes.s32(m, 0xa0)) + Tab.CAMERA_RANGE[d] + cy;
                    break;
                case 2:
                    uy = cy - Bytes.s32(m, 0xd8);
                    ux = ((cx - Glob.gameW / 2) - Tab.CAMERA_RANGE[d]) - Bytes.s32(m, 0x9c);
                    break;
                case 3:
                    ux = (-(Glob.gameW / 2) - Bytes.s32(m, 0x9c)) + Tab.CAMERA_RANGE[d] + cx;
                    uy = cy - Bytes.s32(m, 0xd8);
                    break;
                default:
                    break;
            }
            if (ux != 0 && dir > 1) {
                ux = third(ux);
            }
            if (uy != 0 && ((dir - 2) & 0xffffffffL) > 1) {
                uy = third(uy);
            }
        }
        if (m[0x96] != 0) {
            Bytes.w32(m, 0xc4, cx);
            boolean join;
            if (cx < Bytes.s32(m, 0xcc)) {
                join = cx <= Bytes.s32(m, 0x84) - Glob.gameW / 2;
            } else {
                join = cx <= Bytes.s32(m, 0xcc) || Glob.gameW / 2 <= cx;
            }
            boolean done = false;
            if (join) {
                int t = cameraType();
                if (t != 0xfe) {
                    if (t == 0xff || t == -1) {
                        int d = !cm ? Bytes.s32(m, 0xcc) - Bytes.s32(m, 0xc4) : -ux;
                        Bytes.w32(m, 0x9c, Bytes.s32(m, 0x9c) - d);
                        Bytes.w32(m, 0xcc, Bytes.s32(m, 0xc4));
                        done = true;
                    } else {
                        Bytes.w32(m, 0x9c, cx - Glob.gameW / 2);
                    }
                }
            }
            if (!done) {
                Bytes.w32(m, 0xcc, Bytes.s32(m, 0xc4));
            }
        }
        if (m[0x97] != 0) {
            Bytes.w32(m, 0xc8, cy);
            boolean join;
            if (cy < Bytes.s32(m, 0xd0)) {
                join = cy <= Bytes.s32(m, 0x88) - Glob.gameH / 2;
            } else {
                join = cy <= Bytes.s32(m, 0xd0) || Glob.gameH / 2 <= cy;
            }
            boolean done = false;
            if (join) {
                int t = cameraType();
                if (t != 0xfe) {
                    if (t == 0xff || t == -1) {
                        int d = !cm ? Bytes.s32(m, 0xd0) - Bytes.s32(m, 0xc8) : -uy;
                        Bytes.w32(m, 0xa0, Bytes.s32(m, 0xa0) - d);
                        Bytes.w32(m, 0xd0, Bytes.s32(m, 0xc8));
                        done = true;
                    } else {
                        Bytes.w32(m, 0xa0, cy - Glob.gameH / 2);
                    }
                }
            }
            if (!done) {
                Bytes.w32(m, 0xd0, Bytes.s32(m, 0xc8));
            }
        }
        if (Bytes.s32(m, 0x9c) < 0) {
            Bytes.w32(m, 0x9c, 0);
        }
        if (m[0x96] != 0 && Bytes.s32(m, 0x84) < Glob.gameW + Bytes.s32(m, 0x9c)) {
            Bytes.w32(m, 0x9c, Bytes.s32(m, 0x84) - Glob.gameW);
        }
        if (Bytes.s32(m, 0xa0) < 0) {
            Bytes.w32(m, 0xa0, 0);
        }
        if (m[0x97] != 0 && Bytes.s32(m, 0x88) < Glob.gameH + Bytes.s32(m, 0xa0)) {
            Bytes.w32(m, 0xa0, Bytes.s32(m, 0x88) - Glob.gameH);
        }
        Bytes.w32(m, 0xd8, cy);
        Bytes.w32(m, 0xd4, cx);
    }

    // ---- screen shake --------------------------------------------------------------------------------------------------------------------

    /** Map_Shake @0x94ce4 */
    public static void shake() {
        if (m[0x2db] != 0) {
            if (m[0x2dc] == 0) {
                m[0x2db] = 0;
                return;
            }
            m[0x2dd]++;
            if (m[0x2dd] != 1 && m[0x2dd] >= 1) {
                if (m[0x2dc] != -1) {
                    m[0x2dc]--;
                }
                if (m[0x2da] > 0) {
                    m[0x2da]--;                      // SM_Vibrator(1, 100): no effect on the picture
                }
                m[0x2dd] = 0;
                if (m[0x2dc] == 0) {
                    m[0x2db] = 0;
                }
            }
            int c = m[0x2df];
            if (m[0x2df] == 4) {
                c = Rng.rand(0, 3);
            }
            int a = m[0x2de];
            if (m[0x2db] == 0) {
                Bytes.w16(m, 0x2e2, 0);
                m[0x2dd] = (byte) 0xff;
                Bytes.w16(m, 0x2e0, 0);
                m[0x2de] = 0;
                m[0x2dc] = 0;
                m[0x2df] = 0;
            } else if (m[0x2dd] == 0) {
                switch (c) {
                    case 0:
                        Bytes.w16(m, 0x2e2, 0);
                        Bytes.w16(m, 0x2e0, -a);
                        break;
                    case 1:
                        Bytes.w16(m, 0x2e0, 0);
                        Bytes.w16(m, 0x2e2, -a);
                        break;
                    case 2:
                        Bytes.w16(m, 0x2e2, -a);
                        Bytes.w16(m, 0x2e0, -a);
                        break;
                    case 3:
                        Bytes.w16(m, 0x2e2, a);
                        Bytes.w16(m, 0x2e0, -a);
                        break;
                    default:
                        break;
                }
            } else if (m[0x2dd] == 1) {
                switch (m[0x2df]) {
                    case 0:
                        Bytes.w16(m, 0x2e2, 0);
                        Bytes.w16(m, 0x2e0, a);
                        break;
                    case 1:
                        Bytes.w16(m, 0x2e0, 0);
                        Bytes.w16(m, 0x2e2, a);
                        break;
                    case 2:
                        Bytes.w16(m, 0x2e0, a);
                        Bytes.w16(m, 0x2e2, a);
                        break;
                    case 3:
                        Bytes.w16(m, 0x2e2, -a);
                        Bytes.w16(m, 0x2e0, a);
                        break;
                    default:
                        break;
                }
            }
        }
    }

    /** Map_Set_Shake(frames, amplitude, kind) @0xab10c */
    public static void setShake(int p1, int p2, int p3) {
        int u1 = p1;
        int u2;
        if (e[0x1fe] == 0) {
            u2 = m[0x2da] & 0xff;
            if (m[0x2db] != 0) {
                if (p1 < (m[0x2dc] & 0xff)) {
                    p1 = m[0x2dc] & 0xff;
                }
                m[0x2dc] = (byte) p1;
                return;
            }
        } else {
            u2 = u1;
            if (p1 != 1) {
                if ((p1 & 0xff) == 0xff) {
                    m[0x2dc] = 0;
                    u2 = m[0x2da] & 0xff;
                    u1 = m[0x2dc] & 0xff;
                } else {
                    u2 = m[0x2da] & 0xff;
                    if (p1 == 0) {
                        m[0x2dc] = (byte) 0xff;
                        u1 = m[0x2dc] & 0xff;
                    }
                }
            }
        }
        m[0x2dc] = (byte) u1;
        m[0x2da] = (byte) u2;
        m[0x2dd] = (byte) 0xff;
        m[0x2db] = 1;
        Bytes.w16(m, 0x2e2, 0);
        Bytes.w16(m, 0x2e0, 0);
        m[0x2df] = (byte) p3;
        m[0x2de] = (byte) p2;
    }
}
