package hl4.game;

import hl4.sys.Bytes;

/**
 * The attack side of the first hero class (HK): HERO_check_skilluseHK @0x808a8, HERO_end_skilluseHK @0x80538, HERO_setAttackMotion @0x76c1c,
 * HERO_check_AutoTarget @0x7b098 + SortHorizon @0x76aec, HERO_check_attack_frame @0x7ba48 / ..HK @0x803c8, HERO_set_move_attack_frame @0x7b980 and
 * HERO_serch_monster_area @0x79bb8.  Written from the disassembly; the branches that belong to skills / systems not ported yet stop with "later".
 */
public final class HeroAttack {
    private HeroAttack() {
    }

    private static void later(String what) {
        throw new IllegalStateException(what + ": later");
    }

    /** _HK_Skill_Data @0xe18b9: 5 bytes per skill, the motion of combo step 0..4 (-1 = none) */
    private static final byte[] HK_SKILL = {9, 10, 11, -1, -1, 14, 15, 16, 17, -1, 13, -1, -1, -1, -1, 10, -1, -1, -1, -1, 21, 22, -1, -1, -1, 19, 20, -1,
            -1, -1, 23, 24, 25, 27, -1, 28, 29, 29, -1, -1, 30, -1, -1, -1, -1, 30, -1, -1, -1, -1, 13, -1, -1, -1, -1, 13, -1, -1, -1, -1};

    /** HERO_isStatusMoveDodge @0x76bb8 */
    static boolean isStatusMoveDodge(Char h) {
        return h.m[0x189] == 0 && h.m[0x167] == 8;
    }

    /** HERO_setAttackMotion(h, motion, dir) @0x76c1c: false when motion < 0 */
    static boolean setAttackMotion(Char h, int motion, int dir) {
        if (motion < 0) {
            return false;
        }
        byte[] m = h.m;
        m[0x20e]++;
        HeroCtl.setMotion(h, motion, dir);
        m[0x174] = (byte) 0xff;
        m[0x8c] = 0;
        m[0x20d] = 3;
        m[0x166] = 3;
        return true;
    }

    // ---- auto target ------------------------------------------------------------------------------------------------------------------------

    /** the original's global 'autotarget' (0x400 bytes at 0xfd888): 128 x {s32 distance, u8 monster index} */
    private static final int[] atDist = new int[128];
    private static final byte[] atIdx = new byte[128];

    /** SortHorizon(n) @0x76aec: ascending by distance, the empty (0) entries last; the pair swaps are the original's (index bytes follow) */
    private static void sortHorizon(int n) {
        for (int r4 = n - 1; r4 >= 0; r4--) {
            for (int i = 0; i < r4; i++) {
                int a = atDist[i];
                int b = atDist[i + 1];
                boolean swap;
                if (a >= b) {
                    swap = b != 0;
                } else {
                    swap = a == 0;
                }
                if (swap) {
                    atDist[i] = b;
                    atDist[i + 1] = a;
                    byte t = atIdx[i];
                    atIdx[i] = atIdx[i + 1];
                    atIdx[i + 1] = t;
                }
            }
        }
    }

    /** HERO_check_AutoTarget() @0x7b098: the direction towards the nearest attackable monster within 49 px (squared 2499), else the hero's own */
    static int checkAutoTarget() {
        Char h = Pool.hero;
        byte[] m = h.m;
        PList l = G.monsterList;
        if (l.isEmpty()) {
            return m[0x168];
        }
        for (int i = 0; i < 128; i++) {
            atDist[i] = 0;
            atIdx[i] = 0;
        }
        int cnt = 0;
        for (int i = 0; i < 0x7f; i++) {
            Obj o = l.a[i];
            if (o == null || Monsters.isDie(o) || Monsters.bodyRemain(o) || Monsters.isHidding(o) || Monsters.isObject(o)
                    || Bytes.s16(o.m, 0x160) == 0xff) {
                continue;
            }
            int dx = Bytes.s32(m, 8) - Bytes.s32(o.m, 8);
            int dy = Bytes.s32(m, 0xc) - Bytes.s32(o.m, 0xc);
            atDist[cnt] = dx * dx + dy * dy;
            atIdx[cnt] = (byte) i;
            cnt++;
        }
        sortHorizon(0x80);
        if (atDist[0] == 0) {
            return m[0x168];
        }
        Obj t = l.a[atIdx[0]];
        int dx = Bytes.s32(m, 8) - Bytes.s32(t.m, 8);
        int dy = Bytes.s32(m, 0xc) - Bytes.s32(t.m, 0xc);
        int ax = Um.abs(dx);
        int ay = Um.abs(dy);
        if (ax * ax + ay * ay > 0x9c3) {
            return m[0x168];
        }
        if (ax > ay) {
            return dx <= 0 ? 3 : 2;
        }
        if (ax < ay) {
            return dy > 0 ? 0 : 1;
        }
        return m[0x168];
    }

    // ---- skill start --------------------------------------------------------------------------------------------------------------------------

    /** the shared tail of check_skilluseHK @0x80ad8: the skill's cool-down counter (+0x2ba of its 0x28-byte slot) restarts; the basic attack takes the full value */
    private static int startCooldown(Char h, int skill) {
        byte[] m = h.m;
        int cur = m[0xd0c];
        Bytes.w16(m, cur * 0x28 + 0x2ba, 0);
        int mx = Bytes.u16(m, skill * 0x28 + 0x29e);
        int add = (short) Bytes.s16(m, 0x1cc) * (short) mx / 100;
        Bytes.w16(m, skill * 0x28 + 0x2ba, Bytes.u16(m, skill * 0x28 + 0x2ba) + add);
        if (skill == 0) {
            Bytes.w16(m, 0x2ba, mx);
        }
        return 1;
    }

    /** the queued next hit of a combo (@0x80bbc): the motion is not changed, the key is remembered in +0x174 and +0x20d */
    private static int queueNext(Char h, int skill) {
        byte[] m = h.m;
        m[0xd0c] = (byte) skill;
        m[0x166] = 3;
        m[0x20d] = 0x11;
        m[0x174] = (byte) skill;
        return startCooldown(h, skill);
    }

    /** HERO_check_skilluseHK(h, skill, dir, flag) @0x808a8: 1 when an attack / skill started or was queued */
    static int checkSkillUse(Char h, int skill, int dir, int flag) {
        byte[] m = h.m;
        int cur = m[0xd0c];
        if (cur >= 0 && cur != skill) {
            return 0;
        }
        if (skill == 4) {
            later("HERO_check_skilluseHK: skill 4");
        } else if (flag == 2) {
            return 0;
        }
        if (skill == 9) {
            later("HERO_check_skilluseHK: skill 9");
        }
        m[0x174] = (byte) 0xff;
        if (skill == 1) {
            m[0x169] = 0x12;
        } else if (skill == 6) {
            later("HERO_check_skilluseHK: skill 6");
        }
        boolean start = true;
        if ((m[0x167] & 0xff) > 3) {
            if (!isStatusMoveDodge(h) || m[0x8c] <= 0 || m[0x8c] > 5) {
                start = false;                                  // 0x80b60: an attack key during a motion
            }
        }
        if (!start) {
            if (flag == 0) {
                int c2 = m[0xd0c];
                if (skill != c2) {
                    m[0x174] = (byte) c2;
                    return 0;
                }
            } else {
                int c2 = m[0xd0c];
                if (skill == c2) {
                    m[0x174] = (byte) 0xff;
                    return 0;
                }
                start = true;
            }
        }
        if (!start) {
            int step = m[0x20e];
            int t = HK_SKILL[skill * 5 + step];
            if (skill != 0) {
                later("HERO_check_skilluseHK: combo of skill " + skill);
            }
            if (step == 2 && m[skill * 0x28 + 0x2b8] <= 4) {
                m[0x174] = (byte) 0xff;
                return 0;
            }
            if (t < 0) {
                m[0x174] = (byte) 0xff;
                return 0;
            }
            return queueNext(h, skill);
        }
        if (isStatusMoveDodge(h)) {
            later("HERO_check_skilluseHK: attack out of a dodge move");
        }
        if (Opt.v[8] == 1) {
            dir = checkAutoTarget();
        }
        if (skill == 0x10) {
            later("HERO_check_skilluseHK: arms (bow) shot");
        }
        int t = HK_SKILL[skill * 5 + m[0x20e]];
        if (t < 0) {
            m[0x174] = (byte) 0xff;
            return 0;
        }
        Bytes.w16(m, 0x212, 0);
        m[0xd0c] = (byte) skill;
        setAttackMotion(h, t, dir);
        int c = m[0xd0c];
        if (c == 6 || c == 1 || c == 2 || c == 10) {
            later("HERO_check_skilluseHK: airborne / special skill start");
        }
        if (c == 4 || c == 8 || c == 9) {
            later("HERO_check_skilluseHK: sound / effect skill start");
        }
        return startCooldown(h, skill);
    }

    // ---- skill end (combo) --------------------------------------------------------------------------------------------------------------------

    /** Hero_end_skilluse(h, r1, r2) @0x7b020 for class 0 -> HERO_end_skilluseHK(h, r1, r2) @0x80538 */
    static boolean endSkillUse(Char h, int p1, int p2) {
        if (p1 < 0) {
            return false;
        }
        byte[] m = h.m;
        if (m[0x164 + 3] == 4) {
            return false;
        }
        if (m[0x16b] != 0) {
            later("Hero_end_skilluse: class " + m[0x16b]);
        }
        return endSkillUseHK(h, p1, p2);
    }

    private static boolean endSkillUseHK(Char h, int r6, int r4) {
        byte[] m = h.m;
        boolean dodge = isStatusMoveDodge(h);
        if (dodge) {
            if (r6 == 3 && r4 != 1) {
                return false;
            }
        } else if (r6 == 3) {
            return false;
        }
        int cur = m[0xd0c];
        if (cur < 0 || cur > 10) {
            return false;
        }
        switch (cur) {
            case 0:
            case 7:
                break;
            case 1:
            case 6:
            case 2:
            case 10:
            case 4:
            case 5:
                later("HERO_end_skilluseHK: skill " + cur);
                return false;
            default:
                return false;
        }
        if (r4 != 1) {
            if (m[0x174] != cur) {
                return false;
            }
        }
        int t = HK_SKILL[cur * 5 + m[0x20e]];
        return setAttackMotion(h, t, m[0x168]);
    }

    // ---- hit frames ---------------------------------------------------------------------------------------------------------------------------

    /** HERO_check_attack_frame(h) @0x7ba48 (hero process, attack state) */
    static void checkAttackFrame(Char h) {
        byte[] m = h.m;
        int motion = Chars.getMotion(h);
        if ((motion & 0x80) != 0) {
            return;
        }
        Attk.getProperty(h, 0x22c, (byte) motion);
        if (h.hitTable == null) {
            return;
        }
        int frame = m[0x8c];
        if (!Attk.getHitProperty(h, 0x22c, frame)) {
            return;
        }
        if (Bytes.s16(m, 0x234) != frame) {
            HeroCtl.moveStop(h);
            return;
        }
        setMoveAttackFrame(h);
        switch (m[0x16b]) {
            case 0:
                checkAttackFrameHK(h);
                break;
            default:
                later("HERO_check_attack_frame: class " + m[0x16b]);
        }
    }

    /** HERO_set_move_attack_frame(h) @0x7b980: the pending move of the frame is cut by walls / monsters, applied, then cleared */
    private static void setMoveAttackFrame(Char h) {
        byte[] m = h.m;
        HeroColl.zoneMoveHero(Bytes.s32(m, 0x28) + h.x(), Bytes.s32(m, 0x2c) + h.y(), Bytes.s32(m, 0x30), Bytes.s32(m, 0x34), m, 0x158, 0x15c);
        if (!HeroProc.checkPierce(h)) {
            HeroProc.searchMoveArea(h);
        }
        Bytes.w32(m, 8, h.x() + Bytes.s32(m, 0x158));
        Bytes.w32(m, 0xc, h.y() + Bytes.s32(m, 0x15c));
        HeroCtl.moveStop(h);
    }

    private static final int[] RECT = new int[4];

    /** HERO_check_attack_frameHK(h) @0x803c8: the attack cell of the frame (type 3: a rectangle per facing at +0x240) against the monsters */
    private static void checkAttackFrameHK(Char h) {
        byte[] m = h.m;
        int type = m[0x23d];
        int d = m[0x168];
        if (type == 4) {
            int sk = m[0xd0c];
            if (sk == 0x10) {
                later("HERO_armsAttack");
            }
            if (sk == 1) {
                later("HERO_check_attack_frameHK: type 4, skill 1");
            }
            return;
        }
        if (type != 3) {
            return;
        }
        int sk = m[0xd0c];
        int o = 0x240 + d * 16;
        RECT[0] = h.x() + Bytes.s32(m, o);
        RECT[1] = h.y() + Bytes.s32(m, o + 4);
        RECT[2] = Bytes.s32(m, o + 8);
        RECT[3] = Bytes.s32(m, o + 12);
        if (sk == 1) {
            later("HERO_check_attack_frameHK: skill 1");
        }
        searchMonsterArea(h, RECT, m[0x167], sk, 0, 0, 0);
        if (sk == 0xa || sk == 0xb || sk == 2) {
            m[0xa2f]++;
        }
    }

    private static final int[] OVL = new int[4];

    /**
     * HERO_serch_monster_area(h, rect, motion, skill, a5, a6, a7) @0x79bb8: every live visible monster whose hit rectangle meets the attack rectangle is
     * hit (HERO_attackMonsterProcess); a swing that hits nothing plays the miss sound.  Returns 1 when something was hit.
     */
    static int searchMonsterArea(Char h, int[] r, int motion, int skill, int a5, int a6, int a7) {
        PList l = G.monsterList;
        if (l.isEmpty()) {
            return 0;
        }
        int hits = 0;
        for (int i = 0; i < 0x7f; i++) {
            Obj o = l.a[i];
            if (o == null || o.m[0x30a] != 0 || Monsters.isDie(o) || Monsters.bodyRemain(o) || Monsters.isHidding(o) || o.m[0x32a] != 0) {
                continue;
            }
            int c = Um.collision(r[0], r[1], r[2], r[3], Chars.getHittedRectX(o), Chars.getHittedRectY(o), Chars.getHittedRectW(o),
                    Chars.getHittedRectH(o), OVL);
            if (c != 0) {
                if (a5 != 0) {
                    later("HERO_serch_monster_area: Hero_canAttack");
                }
                later("HERO_attackMonsterProcess");
            } else if (o.m[0x327] == 1) {
                later("HERO_serch_monster_area: monster with +0x327 set");
            }
        }
        if (hits > 0) {
            later("HERO_serch_monster_area: hit tail");
        }
        int cl = h.m[0x16b];
        if (cl != 0 && cl != 2) {
            return 0;
        }
        if (motion != 0x1e && motion != 0x20 || cl != 2) {
            Sound.playEff(4);
            return 0;
        }
        return 0;
    }
}
