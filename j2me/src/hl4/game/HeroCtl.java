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

    /** HERO_setWalkMotion(h, dir) @0x88f0c */
    public static void setWalkMotion(Obj h, int dir) {
        byte[] m = h.m;
        setStatusAir(h, 0);
        m[0x20e] = 0;
        m[0x168] = (byte) dir;
        m[0x166] = 1;
        m[0x174] = (byte) 0xff;
        m[0x167] = 1;
    }

    /** HERO_Match_Number_key @0x86a74: the digit keys of the movement pad become the soft arrow codes */
    public static int matchNumberKey(int k) {
        switch (k) {
            case 0x32:
                return -1;
            case 0x34:
                return -3;
            case 0x35:
                return -5;
            case 0x36:
                return -4;
            case 0x38:
                return -2;
            default:
                return k;
        }
    }

    /** HERO_Key_press(h, key) @0x8adb0 */
    public static void keyPress(Char h, int key) {
        byte[] m = h.m;
        int d = (Um.abs(key) - 1) & 0xff;
        if (d >= 4) {
            d = m[0x168];
        }
        int k = matchNumberKey(key);
        if (k == 0x23) {
            m[0x20c] = (byte) ((m[0x20c] + 1) & 1);
            return;
        }
        if (k == -16 || k == -6 || k == -7) {
            return;
        }
        if (m[0xa41] == 0 || m[0xa41] == 2) {
            if (k != -1 && k != -3 && k != -2 && k != -4) {
                return;
            }
        }
        if (Event.isEvent()) {
            return;
        }
        if (Bytes.s16(G.map, 0x356) > 0 && m[0x170] == 0) {
            return;
        }
        if (m[0x217] > 0) {
            return;
        }
        if (m[0x124] != 0 && m[0x126] == 1) {
            return;
        }
        if (m[0x167] == 6) {
            return;
        }
        if (m[0x16b] == 2 || m[0x16b] == 3) {
            throw new IllegalStateException("HERO_Sunju_key_press: later");
        }
        humanKeyPress(h, k);
        int s = m[0x20c] & 0xff;
        int p2;
        int p3;
        switch (k) {
            case 0x31:
                p2 = m[0x1f4 + s];
                p3 = m[0x200 + s];
                break;
            case -5:
                p2 = m[0x1f8 + s];
                p3 = m[0x204 + s];
                break;
            case 0x30:
                p2 = m[0x1fe + s];
                p3 = m[0x20a + s];
                break;
            case 0x37:
                p2 = m[0x1fa + s];
                p3 = m[0x206 + s];
                break;
            case 0x39:
                p2 = m[0x1fc + s];
                p3 = m[0x208 + s];
                break;
            case 0x33:
                p2 = m[0x1f6 + s];
                p3 = m[0x202 + s];
                break;
            default:
                return;
        }
        HeroSkill.checkSkillUse(h, p2, p3, d, 0);
    }

    /** HERO_Human_key_press(h, key) @0x91150: a direction key starts / continues walking (double tap in a battle scene = dash) */
    private static void humanKeyPress(Char h, int key) {
        byte[] m = h.m;
        if (m[0x167] == 8) {
            return;
        }
        int d = (Um.abs(key) - 1) & 0xff;
        if (d >= 4) {
            d = m[0x168];
        }
        if (key + 4 < 0 || key + 4 >= 4) {
            m[0x229] = 0;
            m[0x226] = 0;
            m[0x227] = 0;
            m[0x228] = 0;
            return;
        }
        m[0xd25 + d] = 1;
        if (m[0x10c] != 0 && m[0x10e] == 4) {
            throw new IllegalStateException("CHAR_removeEtcStatus (siege buff): later");
        }
        int mo = m[0x167];
        if (mo == 6 || !(mo == 0 || mo == 2 || mo == 1 || mo == 3)) {
            return;
        }
        if (m[0x226 + d] < 1 || m[0x226 + m[0x168]] < 1 || m[0xd15] < 1 || d != m[0x168]) {
            setWalkMotion(h, d);
            m[0x22b] = 0;
            setDirection(h, d);
            m[0x226 + m[0x22a]] = 0;
            m[0x226 + d]++;
        } else {
            if (m[0xa41] == 1) {
                setMotion(h, 8, d);
                Sound.playEff(0xb);
                m[0x166] = 3;
                return;
            }
            setWalkMotion(h, d);
            m[0x22b] = 0;
            setDirection(h, d);
            m[0x226 + m[0x22a]] = 0;
            m[0x226 + d] = 0;
        }
        int o = Opt.v[2] & 0xff;
        m[0xd15] = (byte) (o <= 4 ? 10 : o + 6);
    }

    /** HERO_Key_Release(h, key) @0x8ab6c (also inlined in MainGameFrame_KeyReleased @0x9a3b0) */
    public static void keyRelease(Char h, int key) {
        byte[] m = h.m;
        int d = (Um.abs(key) - 1) & 0xff;
        if (d >= 4) {
            d = m[0x168];
        }
        if (key == -16 || key == -6 || key == -7) {
            return;
        }
        int k = matchNumberKey(key);
        if (Event.isEvent()) {
            return;
        }
        if (m[0xa41] == 0 || m[0xa41] == 2) {
            if (k != -1 && k != -3 && k != -2 && k != -4) {
                return;
            }
        }
        if (Bytes.s16(G.map, 0x356) > 0 && m[0x170] == 0) {
            return;
        }
        if (m[0x124] != 0 && m[0x126] == 1) {
            return;                                     // CHAR_isStunBuff
        }
        if (m[0x11c] != 0 && m[0x11e] == 2) {
            return;                                     // CHAR_isIcedBuff
        }
        if (m[0x16b] == 2 || m[0x16b] == 3) {
            throw new IllegalStateException("HERO_Sunju_key_release: later");
        }
        humanKeyRelease(h, k);
        int s = m[0x20c] & 0xff;
        int p2;
        int p3;
        switch (k) {
            case 0x31:
                p2 = m[0x1f4 + s];
                p3 = m[0x200 + s];
                break;
            case -5:
                p2 = m[0x1f8 + s];
                p3 = m[0x204 + s];
                break;
            case 0x30:
                p2 = m[0x1fe + s];
                p3 = m[0x20a + s];
                break;
            case 0x37:
                p2 = m[0x1fa + s];
                p3 = m[0x206 + s];
                break;
            case 0x39:
                p2 = m[0x1fc + s];
                p3 = m[0x208 + s];
                break;
            case 0x33:
                p2 = m[0x1f6 + s];
                p3 = m[0x202 + s];
                break;
            default:
                return;
        }
        HeroSkill.checkSkillUse(h, p2, p3, d, 2);
    }

    /** HERO_Human_key_release(h, key) @0x91010 */
    private static void humanKeyRelease(Char h, int key) {
        byte[] m = h.m;
        int d = (Um.abs(key) - 1) & 0xff;
        if (d >= 4) {
            d = m[0x168];
        }
        if (key + 4 < 0 || key + 4 >= 4) {
            return;
        }
        int mo = m[0x167];
        m[0xd25 + d] = 0;
        if (mo == 1 || mo == 3) {
            if (m[0xa41] == 1) {
                int o = Opt.v[2] & 0xff;
                m[0xd15] = (byte) (o <= 4 ? 10 : o + 6);
            }
            if (m[0x168] != d) {
                if (m[0xd25] != 0 || m[0xd26] != 0 || m[0xd27] != 0) {
                    return;
                }
                if (m[0xd28] != 0) {
                    return;
                }
            }
            setStandMotion(h, 0, 0, d);
            return;
        }
        if (mo == 8) {
            m[0x226 + m[0x168]] = 0;
            m[0x226 + m[0x22a]] = 0;
        }
    }
}
