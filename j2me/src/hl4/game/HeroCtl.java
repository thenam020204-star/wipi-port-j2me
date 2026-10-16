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

    /** HERO_set_move(h, speed, dir) @0x86690: stops the hero, then the axis speed for the direction (0 up / 1 down: +0x15c, 2 left / 3 right: +0x158) */
    public static void setMove(Obj h, int speed, int dir) {
        moveStop(h);
        switch (dir) {
            case 0:
                Bytes.w32(h.m, 0x15c, -speed);
                break;
            case 1:
                Bytes.w32(h.m, 0x15c, speed);
                break;
            case 2:
                Bytes.w32(h.m, 0x158, -speed);
                break;
            case 3:
                Bytes.w32(h.m, 0x158, speed);
                break;
            default:
                break;
        }
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

    /**
     * HERO_set_frame_control(h, motion, dir, frames) @0x8ec70 (script command 0x10): dir 4 keeps the facing; frames 0xff = no hold counter (0), 0 = hold
     * forever (0xff), else that many; the motion starts and the frame counter restarts; a skip (+0x206) clears the hold.
     */
    public static void setFrameControl(Char h, int motion, int dir, int frames) {
        byte[] m = h.m;
        byte d = dir == 4 ? m[0x168] : (byte) dir;
        if ((frames & 0xff) == 0xff) {
            m[0xa31] = 0;
        } else if (frames == 0) {
            m[0xa31] = (byte) 0xff;
        } else {
            m[0xa31] = (byte) frames;
        }
        setMotion(h, motion, d);
        if (d < 4) {
            m[0x168] = d;
        } else {
            int b = m[0x172] & 0xff;
            if (b != 4 && (b << 24) >= 0x4000000) {
                m[0x168] = (byte) (b - 5);
            }
        }
        h.spr.f6c = 0;
        if (G.event[0x206] != 0) {
            m[0xa31] = 0;
        }
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
