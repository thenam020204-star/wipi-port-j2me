package hl4.game;

import hl4.sys.Bytes;
import hl4.sys.Rng;

/**
 * Character functions shared by hero / NPCs / monsters / summons (CHAR_* @0x38ce8..0x4cd..., 0xdb...).  Offsets: +0x20 SPR struct, +0x28..+0x34 move rect
 * (x, y, w, h relative to the position), +0x38..+0x44 hit rect, +0x94 level, +0xa4/+0xa8 hp/max hp, +0x130/+0x13c effect lists, +0x148..+0x154 absolute move rect,
 * +0x160/+0x162 data ids, +0x164/+0x165 sprite flags, +0x166 main state, +0x167 motion, +0x168 direction (0..4), +0x169 next motion, +0x16a next direction,
 * +0x16b class, +0x16c, +0x16d blend counter, +0x16f speed, +0x172 action direction (0..7).
 */
public final class Chars {
    private Chars() {
    }

    // ---- accessors --------------------------------------------------------------------------------------------------------------------
    public static int getX(Obj c) {
        return Bytes.s32(c.m, 8);
    }

    public static int getY(Obj c) {
        return Bytes.s32(c.m, 0xc);
    }

    public static int getDirection(Obj c) {
        return c.m[0x168];
    }

    public static int actionDir(Obj c) {
        return c.m[0x172];
    }

    public static int getMotion(Obj c) {
        return c.m[0x167];
    }

    public static void setMainState(Obj c, int s) {
        c.m[0x166] = (byte) s;
    }

    public static int getMainState(Obj c) {
        return c.m[0x166];
    }

    public static int getMoveRectX(Obj c) {
        return getX(c) + Bytes.s32(c.m, 0x28);
    }

    public static int getMoveRectY(Obj c) {
        return getY(c) + Bytes.s32(c.m, 0x2c);
    }

    public static int getMoveRectW(Obj c) {
        return Bytes.s32(c.m, 0x30);
    }

    public static int getMoveRectH(Obj c) {
        return Bytes.s32(c.m, 0x34);
    }

    public static int getHittedRectX(Obj c) {
        return getX(c) + Bytes.s32(c.m, 0x38);
    }

    public static int getHittedRectY(Obj c) {
        return getY(c) + Bytes.s32(c.m, 0x3c);
    }

    public static int getHittedRectW(Obj c) {
        return Bytes.s32(c.m, 0x40);
    }

    public static int getHittedRectH(Obj c) {
        return Bytes.s32(c.m, 0x44);
    }

    public static boolean isNpc(Obj c) {
        return Obj.structType(c) == 1;
    }

    public static boolean isHero(Obj c) {
        return c != null && Obj.structType(c) == 2;
    }

    public static boolean isMonster(Obj c) {
        return Obj.structType(c) == 3;
    }

    public static boolean isSummon(Obj c) {
        return c != null && Obj.structType(c) == 4;
    }

    public static void setVisible(Obj c, int v) {
        if (c != null) {
            c.m[Obj.VISIBLE] = (byte) v;
        }
    }

    public static boolean getVisible(Obj c) {
        return c != null && c.m[Obj.VISIBLE] != 0;
    }

    /** CHAR_setPosition(c, dx, dy) @0x48e40: moves by an offset and refreshes the absolute move rect */
    public static void setPosition(Obj c, int dx, int dy) {
        Bytes.w32(c.m, 8, getX(c) + dx);
        Bytes.w32(c.m, 0xc, getY(c) + dy);
        refreshMoveRect(c);
    }

    private static void refreshMoveRect(Obj c) {
        Bytes.w32(c.m, 0x148, getMoveRectX(c));
        Bytes.w32(c.m, 0x14c, getMoveRectY(c));
        Bytes.w32(c.m, 0x150, getMoveRectW(c));
        Bytes.w32(c.m, 0x154, getMoveRectH(c));
    }

    /** CHAR_resetPosition(c, x, y) @0x4994c */
    public static void resetPosition(Obj c, int x, int y) {
        Bytes.w32(c.m, 0xc, y);
        Bytes.w32(c.m, 8, x);
        refreshMoveRect(c);
    }

    /** CHAR_getMoveRectHalfW @0x48e90 */
    public static int moveRectHalfW(Obj c, int d) {
        d &= 0xff;
        if (d < 2) {
            return 0;
        }
        int x = Bytes.s32(c.m, 0x28);
        if (d == 2) {
            return x < 0 ? -x : x;
        }
        if (x < 0) {
            x = -x;
        }
        return (Bytes.s32(c.m, 0x30) + 1) - x;
    }

    /** CHAR_getMoveRectHalfH @0x48ed4 */
    public static int moveRectHalfH(Obj c, int d) {
        if (((d - 2) & 0xff) < 2) {
            return 0;
        }
        int y = Bytes.s32(c.m, 0x2c);
        if (d != 0) {
            if (y < 0) {
                y = -y;
            }
            return (Bytes.s32(c.m, 0x34) + 1) - y;
        }
        return y < 0 ? -y : y;
    }

    public static int getAbsX(Obj a, Obj b) {
        return Um.abs(getX(a) - getX(b));
    }

    public static int getAbsY(Obj a, Obj b) {
        return Um.abs(getY(a) - getY(b));
    }

    /** CHAR_getInverseDirection @0x48f88 */
    public static int inverseDirection(Obj c) {
        int d = c.m[0x168];
        switch (d) {
            case 0:
                return 1;
            case 1:
                return 0;
            case 2:
                return 3;
            case 3:
                return 2;
            default:
                return d;
        }
    }

    /** CHAR_getMoveX/Y(c, dir) @0x49558: speed * unit vector */
    public static int getMoveX(Obj c, int dir) {
        return c.m[0x16f] * Um.vecX(dir);
    }

    public static int getMoveY(Obj c, int dir) {
        return c.m[0x16f] * Um.vecY(dir);
    }

    /** CHAR_convertDir(c, d) @0x48d38: records the 8-way action direction in +0x172 and returns the 4-way facing (0..4) */
    public static int convertDir(Obj c, int d) {
        c.m[0x172] = (byte) d;
        return d > 4 ? (byte) (d - 5) : (byte) d;
    }

    /** CHAR_setDirection @0x48d5c */
    public static void setDirection(Obj c, int d) {
        int r = convertDir(c, d);
        c.m[0x16a] = (byte) 0xff;
        c.m[0x168] = (byte) r;
    }

    /** CHAR_setNextMotionDirection(c, motion, dir) @0x48ce8 */
    public static void setNextMotionDirection(Obj c, int motion, int dir) {
        c.m[0x169] = (byte) motion;
        if (c.m[0x172] > 4 && dir > 4) {
            c.m[0x16a] = (byte) (dir - 5);
        } else {
            c.m[0x16a] = (byte) dir;
        }
    }

    /** CHAR_turnDirection(a, b) @0x498dc: facing of a toward b (keeps the direction when on top of it) */
    public static int turnDirection(Obj a, Obj b) {
        int d = Um.turnDirection(getX(a), getY(a), getX(b), getY(b));
        if (d != -1) {
            return d;
        }
        return getDirection(a);
    }

    /** CHAR_AddHp(c, n) @0x49504 (during events only heals are applied) */
    public static void addHp(Obj c, int n) {
        if (!Event.isEvent() || n >= 0) {
            int max = Bytes.s32(c.m, 0xa8);
            int hp = n + Bytes.s32(c.m, 0xa4);
            Bytes.w32(c.m, 0xa4, hp);
            if (max < hp) {
                Bytes.w32(c.m, 0xa4, max);
                hp = max;
            }
            if (hp < 1) {
                Bytes.w32(c.m, 0xa4, 0);
            }
        }
    }

    // ---- init / destroy ------------------------------------------------------------------------------------------------------------------

    /** CHAR_set(c) @0x4ccf4: pool-slot reset */
    public static void set(Char c) {
        c.objSet(1);
        c.spr.set();
        Bytes.fill(c.m, 0x20, 0, 0x74);          // the SPR struct lives at +0x20: SPR_set clears its frame counter (+0x8c) too
        init(c, 0, 0, 0, 0, 0, 0, 0);
    }

    /**
     * CHAR_destroy(c) @0x4cc3c: both effect lists emptied, data ids / class / motion reset, the sprite released (SPR_destroy, or SPR_copyNPCIDFree for a
     * sprite shared with an NPC id: the shared data is garbage collected here, so both end in the zero SPR state), header back to the free state.
     */
    public static void destroy(Char c) {
        c.effects.release();
        c.targetEffects.release();
        Bytes.w16(c.m, 0x162, 0xffff);
        Bytes.w16(c.m, 0x160, 0xffff);
        c.m[0x16b] = (byte) 0xff;
        c.m[0x16c] = (byte) 0xff;
        c.m[0x167] = (byte) 0xff;
        c.spr.set();
        Bytes.fill(c.m, 0x20, 0, 0x74);          // SPR_destroy: memset of the SPR struct (frame counter +0x8c included)
        c.m[0x165] = 0;
        c.m[0x164] = 0;
        c.objSet(1);
    }

    /** CHAR_init(c, class, p3, x, y, motion, dir, speed) @0x4cb94 */
    public static void init(Char c, int cls, int p3, int x, int y, int motion, int dir, int speed) {
        c.effects.init();
        c.targetEffects.init();
        c.m[0x16b] = (byte) cls;
        c.m[0x16c] = (byte) p3;
        resetPosition(c, x, y);
        c.m[0x166] = 0;
        c.m[0x167] = (byte) motion;
        c.m[0x168] = (byte) convertDir(c, (byte) dir);
        c.m[0x16f] = (byte) speed;
        c.m[0x19] = (byte) (Bytes.s32(c.m, 0x2c) + Bytes.s32(c.m, 0x34));
        initBuff(c);
    }

    /** CHAR_initBuff @0xdbb60 */
    public static void initBuff(Char c) {
        byte[] m = c.m;
        for (int k = 0; k < 3; k++) {
            int b = k * 8;
            Bytes.w16(m, 0xf8 + b, 0);
            m[0xf5 + b] = 0;
            m[0xf4 + b] = 0;
            Bytes.w16(m, 0xfa + b, 0);
            m[0xf6 + b] = 0;
        }
        m[0x12c] = 0;
        for (int k = 0; k < 4; k++) {
            int b = k * 8;
            m[0x10d + b] = 0;
            Bytes.w16(m, 0x110 + b, 0);
            Bytes.w16(m, 0x112 + b, 0);
            m[0x10c + b] = 0;
            m[0x10e + b] = 0;
        }
        removeCommonEffect(c, 9, 2);
        removeTargetEffGood(c, 7);
        removeTargetEffBad(c, 9);
        removeTargetEffMagic(c, 9);
        removeCommonEffect(c, 9, 10);
    }

    // ---- effect lists (the effect objects themselves: Effects) ---------------------------------------------------------------------------

    /** CHAR_searchAndRemoveEffect(list, fromTargetList, type, a, b) @0x4ae10 */
    public static int searchAndRemoveEffect(LList list, int fromTarget, int type, int a, int b) {
        if (list.head == null) {
            return 0;
        }
        int r = 0;
        Obj p = list.head;
        do {
            Obj nx = p.next;
            if (p.m[Obj.TYPE] == type) {
                if (p.m[0x20] == a && (b == -1 || p.m[0x21] == b)) {
                    list.remove(p);
                    r = 1;
                    if (fromTarget != 0) {
                        G.targetEffectList.remove(p);
                    }
                }
            }
            p = nx;
        } while (p != null);
        return r;
    }

    public static int removeCommonEffect(Char c, int a, int b) {
        return searchAndRemoveEffect(c.targetEffects, 1, 6, a, b);
    }

    public static int removeEffect(Char c, int a, int b) {
        return searchAndRemoveEffect(c.effects, 0, 5, a, b);
    }

    /** CHAR_removeTargetEffGood(c, n) @0xdba0c (the byte table C_5_16487 lists the 4 effect ids); a hero re-derives its stats afterwards */
    public static void removeTargetEffGood(Char c, int n) {
        for (int i = 0; i < 4; i++) {
            removeCommonEffect(c, n, Tab.EFF_GOOD[i]);
        }
        if (Obj.structType(c) == 2) {
            Hero.recalcAfterBuffRemoval();
        }
    }

    public static void removeTargetEffBad(Char c, int n) {
        for (int i = 0; i < 4; i++) {
            removeCommonEffect(c, n, Tab.EFF_BAD[i]);
        }
    }

    public static void removeTargetEffMagic(Char c, int n) {
        int cls = Pool.hero.m[0x16b];
        for (int i = 0; i < 6; i++) {
            removeCommonEffect(c, n, Tab.EFF_MAGIC[i]);
        }
        if (cls != 0 && cls != 1) {
            return;
        }
        removeCommonEffect(c, 7, 0x28);
    }

    /** Eff_deleteEffectInList(c) @0x4a17c: drops the character's own effect objects */
    public static void deleteEffectInList(Char c) {
        for (Obj p = c.effects.head; p != null; ) {
            Obj nx = p.next;
            c.effects.remove(p);
            p = nx;
        }
        if (c.targetEffects.head != null) {
            throw new IllegalStateException("Eff_deleteEffectInList: target effects: later");
        }
    }

    /** Eff_deleteEffectInObjList(c) @0x4a040: removes target-effect objects (type 6) that belong to c from the object list */
    public static void deleteEffectInObjList(Char c) {
        for (Obj p = G.objList.head; p != null; p = p.next) {
            if (Obj.structType(p) == 6) {
                throw new IllegalStateException("Eff_deleteEffectInObjList: later");
            }
        }
    }

    // ---- per-tick processing -----------------------------------------------------------------------------------------------------------

    /** CHAR_isStunBuff @0xcb304 */
    public static boolean isStunBuff(Obj c) {
        return c.m[0x124] != 0 && c.m[0x126] == 1;
    }

    /** CHAR_isIcedBuff @0xcb2e0 */
    public static boolean isIcedBuff(Obj c) {
        return c.m[0x11c] != 0 && c.m[0x11e] == 2;
    }

    /**
     * CHAR_buffProcess @0xcbe3c: buff slots (+0xf4, 3 x 8 bytes, counter +0xfa) and status slots (+0x10c, 4 x 8 bytes, counter +0x112) count down;
     * an expired status slot 0 of kind 2 first switches the character to main state 0x15 / motion 0x13.
     */
    public static void buffProcess(Char c) {
        byte[] m = c.m;
        for (int i = 0; i < 3; i++) {
            int o = i * 8;
            if (m[0xf4 + o] == 1) {
                int v = (Bytes.s16(m, 0xfa + o) - 1) & 0xffff;
                Bytes.w16(m, 0xfa + o, v);
                if ((v & 0x8000) != 0) {
                    throw new IllegalStateException("CHAR_removeBuff (buff expiry): later");
                }
            }
        }
        for (int i = 0; i < 4; i++) {
            int o = i * 8;
            if (m[0x10c + o] == 1) {
                int v = (Bytes.s16(m, 0x112 + o) - 1) & 0xffff;
                Bytes.w16(m, 0x112 + o, v);
                if ((v & 0x8000) != 0) {
                    throw new IllegalStateException("CHAR_removeEtcStatus (status expiry): later");
                }
            }
        }
    }

    public static boolean isPoisonBuff(Obj c) {
        return c.m[0x114] != 0 && c.m[0x116] == 2;
    }

    public static boolean isBleedBuff(Obj c) {
        return c.m[0x114] != 0 && c.m[0x116] == 4;
    }

    public static boolean isFiredBuff(Obj c) {
        return c.m[0x11c] != 0 && c.m[0x11e] == 1;
    }

    public static boolean isHideBuff(Obj c) {
        return c.m[0x10c] != 0 && c.m[0x10e] == 2;
    }

    /** CHAR_preProcessTargetEffectList @0x4ac38 (the target-effect objects are not ported yet: an occupied list stops here) */
    public static void preProcessTargetEffects(Char c) {
        if (!c.targetEffects.isEmpty()) {
            throw new IllegalStateException("EFF_preProcessOneTargetEffect: later");
        }
    }

    /** CHAR_endProcessTargetEffectList @0x4a4cc */
    public static void endProcessTargetEffects(Char c) {
        if (!c.targetEffects.isEmpty()) {
            throw new IllegalStateException("EFF_endProcessOneTargetEffect: later");
        }
    }

    /** damage-over-time tick of a buff (poison/bleed use +0x118, burning +0x120) */
    private static void buffDamage(Char c, int dmgOff) {
        int d = Bytes.s16(c.m, dmgOff);
        if (c.m[Obj.TYPE] == 2 || c.m[Obj.TYPE] == 3) {
            if (d > 2) {
                d = d - 2 + Rng.rand(0, 4);
            }
            throw new IllegalStateException("CHAR_process buff damage (CHAR_heroAddHp / MONSTER_addHp / CHAR_addEffect): later");
        }
    }

    /** CHAR_process(c, force) @0x4c890: buff damage ticks; for non-hero characters applies a pending direction/motion change (true = the motion changed and the frame restarted) */
    public static boolean process(Char c, boolean force) {
        byte[] m = c.m;
        if ((isPoisonBuff(c) || isBleedBuff(c)) && Bytes.s16(m, 0x11a) % 0x14 == 1) {
            buffDamage(c, 0x118);
        }
        if (isFiredBuff(c) && Bytes.s16(m, 0x122) % 0x14 == 1) {
            buffDamage(c, 0x120);
        }
        if (m[Obj.TYPE] != 2) {
            int nd = m[0x16a];
            if (m[0x168] != nd && nd > -1) {
                m[0x168] = (byte) nd;
                m[0x16a] = (byte) 0xff;
            }
            if (force || m[0x167] != m[0x169]) {
                m[0x8c] = 0;                       // SPR_initFrame
                m[0x167] = m[0x169];
                return true;
            }
        }
        return false;
    }

    /** CHAR_turnCorner(c, dir) @0x49f58: the nearest free direction when the way ahead is blocked (right 90/45 first, then left), -1 when none */
    public static int turnCorner(Obj c, int dir) {
        boolean a = Coll.charIsMoveable(c, Tab.TURN_R90[dir]);
        boolean b = Coll.charIsMoveable(c, Tab.TURN_R45[dir]);
        if (a && b) {
            return Tab.TURN_R90[dir];
        }
        a = Coll.charIsMoveable(c, Tab.TURN_L90[dir]);
        b = Coll.charIsMoveable(c, Tab.TURN_L45[dir]);
        if (a && b) {
            return Tab.TURN_L90[dir];
        }
        return -1;
    }
}
