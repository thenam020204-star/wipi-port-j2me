package hl4.game;

import hl4.sys.Bytes;

/** Hero motion state helpers (HERO_move_stop @0x76680, HERO_set_direction @0x76808, HERO_set_motion @0x7687c, HERO_isSkipDir @0x766e8, HERO_setStatusAir @0x78ed4, HERO_setStandMotion @0x78f4c). */
public final class HeroCtl {
    private HeroCtl() {
    }

    /** HERO_move_stop */
    public static void moveStop(Obj h) {
        Bytes.w32(h.m, 0x15c, 0);
        Bytes.w32(h.m, 0x158, 0);
    }

    /** HERO_isSkipDir(h, motion, dir) @0x766e8: dirs the class skips for this motion list */
    public static boolean isSkipDir(Obj h, int p2) {
        byte[] t;
        switch (h.m[0x16b]) {
            case 0:
                t = Tab.HERO_SKIP_0;
                break;
            case 1:
                t = Tab.HERO_SKIP_1;
                break;
            case 2:
                t = Tab.HERO_SKIP_2;
                break;
            case 3:
                t = Tab.HERO_SKIP_3;
                break;
            default:
                return false;
        }
        for (int i = 0; i < t.length; i++) {
            if ((t[i] & 0xff) == (p2 & 0xff)) {
                return true;
            }
        }
        return false;
    }

    /** HERO_set_direction(h, dir) @0x76808 */
    public static void setDirection(Obj h, int dir) {
        byte[] m = h.m;
        m[0xa40] = (byte) dir;
        if (m[0x22b] == 0 && m[0x168] != m[0x22a]) {
            m[0x22a] = m[0x168];
        }
        if (!isSkipDir(h, m[0x167])) {
            m[0x168] = (byte) dir;
        } else {
            if (m[0x22b] == 0) {
                m[0x22b] = 1;
            }
            m[0x168] = 0;
        }
    }

    /** HERO_set_motion(h, motion, dir) @0x7687c */
    public static void setMotion(Obj h, int motion, int dir) {
        h.m[0x167] = (byte) motion;
        h.m[0x8c] = 0;
        setDirection(h, dir);
    }

    /** HERO_setStatusAir(h, on) @0x78ed4 */
    public static void setStatusAir(Obj h, int on) {
        h.m[0x20f] = (byte) on;
        if (on != 0) {
            h.m[0x210] = 0x2d;
        } else {
            Monsters.setAirStatusFall();
            h.m[0x210] = 0;
        }
    }

    /** HERO_setStandMotion(h, p2, p3, dir) @0x78f4c */
    public static void setStandMotion(Obj h, int p2, int p3, int dir) {
        byte[] m = h.m;
        Bytes.w16(m, 0x1b6, 0);
        setStatusAir(h, 0);
        moveStop(h);
        if (m[0xa41] == 1) {
            setMotion(h, 2, dir);
        } else {
            setMotion(h, 0, dir);
        }
        m[0x174] = (byte) 0xff;
        m[0x166] = 0;
        m[0x20e] = 0;
        if (p2 != 0) {
            int a = m[0x228 + 2];
            m[a + 0x226] = 0;
            m[m[0x168] + 0x226] = 0;
        }
        if (p3 != 0) {
            m[0xa44] = 1;
            m[0xa42] = 0;
        }
        Bytes.fill(m, 0xd25, 0, 4);
        m[0xd16] = 0;
    }
}
