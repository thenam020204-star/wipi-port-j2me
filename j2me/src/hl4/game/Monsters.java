package hl4.game;

import hl4.sys.Bytes;
import hl4.sys.Rng;

/** Monsters (pooled type 3, 0x430-byte structs): MONSTER_* @0xae7c8..0xb1xxx. Only the state used by the load path so far. */
public final class Monsters {
    /** _questBoss (4 bytes) */
    public static final byte[] questBoss = new byte[4];

    private Monsters() {
    }

    /** MONSTER_questBossDel @0xae794 */
    public static void questBossDel() {
        questBoss[3] = 0;
        questBoss[2] = (byte) 0xff;
        questBoss[0] = 0;
        questBoss[1] = 0;
    }

    /** MONSTERS_setAirStatusFall @0x9e950: airborne monsters (state 1) start to fall (state 2) */
    public static void setAirStatusFall() {
        if (G.monsterList.isEmpty()) {
            return;
        }
        for (int i = 0; i <= 0x7e; i++) {
            Obj o = G.monsterList.a[i];
            if (o != null && o.m[0x327] == 1) {
                o.m[0x329] = 0;
                o.m[0x327] = 2;
            }
        }
    }

    // ---- predicates ----------------------------------------------------------------------------------------------------------------------
    public static boolean isBlank(Obj o) {
        return Bytes.s16(o.m, 0x160) == 0xff;
    }

    /** MONSTER_getType */
    public static int getType(Obj o) {
        return o == null ? -1 : o.m[0x16b];
    }

    /** MONSTER_isBoss @0xb69e8 */
    public static boolean isBoss(Obj o) {
        if (isBlank(o)) {
            return false;
        }
        return 199 < Bytes.s16(o.m, 0x160);
    }

    /** Boss_isRemoveMon @0xdf340 */
    public static boolean bossIsRemoveMon(Obj o) {
        if (isBoss(o)) {
            return false;
        }
        return 0xa1 < Bytes.s16(o.m, 0x160);
    }

    public static boolean meetHero(Obj o) {
        return true;
    }

    /** MONSTER_isDie @0xb5e44 */
    public static boolean isDie(Obj o) {
        int st = o.m[0x166];
        if (st != 4 && st != 9) {
            return 0xfe < (Bytes.s16(o.m, 0x160) & 0xffff);
        }
        return true;
    }

    /** MONSTER_isHidding */
    public static boolean isHidding(Obj o) {
        return o.m[0x35d] != 0;
    }

    // ---- creation ------------------------------------------------------------------------------------------------------------------------

    /** CHAR_copy(dst, src) @0x495dc */
    public static void charCopy(Char d, Char s) {
        d.spr.copyFrom(s.spr);
        d.effects.init();
        d.targetEffects.init();
        d.m[0x16b] = s.m[0x16b];
        Bytes.w16(d.m, 0x162, Bytes.s16(s.m, 0x162));
        Bytes.w16(d.m, 0x160, Bytes.s16(s.m, 0x160));
    }

    /** MONSTER_setRegenDelay @0xaee2c */
    public static void setRegenDelay(Obj o) {
        int v = Bytes.s16(o.m, 0x1ea) & 0xffff;
        int r = Rng.rand(0, ((short) v) >> 2);
        Bytes.w16(o.m, 0x1e8, (v - (Bytes.s16(o.m, 0x1ea) >> 2)) + (short) r);
    }

    /** MONSTER_setMonsterStatus @0xaea88: the combat stats of the monster from its data record and the formulas 2000.. */
    public static void setMonsterStatus(Char o) {
        byte[] m = o.m;
        Bytes.w16(m, 0xd4, Bytes.s16(m, 0x180));
        Bytes.w16(m, 0xdc, Bytes.s16(m, 0x194));
        Bytes.w16(m, 0xae, Bytes.s16(m, 0x18e));
        Bytes.w16(m, 0xb2, Bytes.s16(m, 0x192));
        Bytes.w16(m, 0xba, Bytes.s16(m, 400));
        Bytes.w16(m, 0xc2, Bytes.s16(m, 0x18a));
        Bytes.w16(m, 0xd8, Bytes.s16(m, 0x184));
        Bytes.w16(m, 0xce, 0);
        m[0xcc] = 0;
        Bytes.w16(m, 200, 0);
        Bytes.w16(m, 0xe8, 0);
        Bytes.w16(m, 0xee, 0);
        Bytes.w16(m, 0xd6, 0);
        Bytes.w16(m, 0xe2, 0);
        Bytes.w16(m, 0xda, 0);
        Bytes.w16(m, 0xac, 0);
        Bytes.w16(m, 0xc4, 0);
        Bytes.w16(m, 0xb8, 0);
        Bytes.w16(m, 0xb0, 0);
        Bytes.w16(m, 0xbc, 0);
        Bytes.w16(m, 0xbe, 0);
        Bytes.w16(m, 0xb4, 0);
        Bytes.w16(m, 0xde, 0);
        Bytes.w16(m, 0xc0, 0);
        m[0xd2] = 0;
        Bytes.w16(m, 0xb6, Bytes.s16(m, 0x18c));
        Bytes.w16(m, 0xec, Bytes.s16(m, 0x17e));
        Bytes.w16(m, 0xe0, Bytes.s16(m, 0x182));
        Bytes.w16(m, 0xca, Bytes.s16(m, 0x196));
        Bytes.w32(m, 0xa8, Bytes.s32(m, 0x178));
        Bytes.w16(m, 0xd0, Bytes.s16(m, 0x198));
        Bytes.w16(m, 0xe6, Bytes.s16(m, 0x17c));
        Bytes.w16(m, 0xe4, Bytes.s16(m, 0x186));
        Bytes.w16(m, 0xea, Bytes.s16(m, 0x188));
        // MONSTER_setMonsterStatusBuff @0xae9f0: passives of the monster's own buff list (+0x12c count, 8-byte entries from +0xf4)
        for (int k = 0; k < m[0x12c]; k++) {
            if (m[0xf4 + 8 * k] != 0) {
                BattleProc.setSkillPassive(Pool.hero, o, m[0xf6 + 8 * k], m[0xf5 + 8 * k], Pool.hero, 0);
            }
        }
        Bytes.w16(m, 0xb2, Fr.calc(2000, o, null, -1, 0, null, o));
        Bytes.w16(m, 0xae, Bytes.s16(m, 0x18e));
        Bytes.w16(m, 0xb6, Bytes.s16(m, 0x18c));
        Bytes.w16(m, 0xba, Fr.calc(0x7d1, o, null, -1, 0, null, o));
        Bytes.w16(m, 0xc2, Fr.calc(0x7d2, o, null, -1, 0, null, o));
        Bytes.w16(m, 0xd4, Fr.calc(0x7d7, o, null, -1, 0, null, o));
        Bytes.w16(m, 0xd8, Fr.calc(0x7d8, o, null, -1, 0, null, o));
        Bytes.w16(m, 0xdc, Fr.calc(0x7de, o, null, -1, 0, null, o));
        Bytes.w16(m, 0xe4, Fr.calc(0x7d9, o, null, -1, 0, null, o));
        Bytes.w16(m, 0xe6, Fr.calc(0x7da, o, null, -1, 0, null, o));
        Bytes.w16(m, 0xe0, Fr.calc(0x7db, o, null, -1, 0, null, o));
        Bytes.w16(m, 0xea, Fr.calc(0x7dc, o, null, -1, 0, null, o));
        Bytes.w16(m, 0xec, Fr.calc(0x7dd, o, null, -1, 0, null, o));
        Bytes.w32(m, 0xa8, Fr.calc(0x7d5, o, null, -1, 0, null, o));
        Bytes.w16(m, 0xca, Fr.calc(0x7d4, o, null, -1, 0, null, o));
        Bytes.w16(m, 0xd0, Fr.calc(0x7d6, o, null, -1, 0, null, o));
    }

    /** MONSTER_attackCancel @0xae6ac */
    public static void attackCancel(Obj o) {
        Bytes.w16(o.m, 0x350, 0);
        o.m[0x36d] = 0;
        Bytes.w16(o.m, 0x34e, 0);
        o.m[0x34d] = 0;
        o.m[0x36c] = 0;
    }

    /** MONSTER_setStandMotion @0xaf058 */
    public static void setStandMotion(Obj o) {
        Chars.setNextMotionDirection(o, 0, o.m[0x168]);
        o.m[0x340] = 0;
        o.m[0x8f] = (byte) 0xff;
        o.m[0x8e] = 0;
        attackCancel(o);
        o.m[0x35c] = 1;
        o.m[0x36e] = 0;
    }

    /** MONSTER_init(m, idx, x, y) @0xaf6c0 */
    /** MONSTER_initBlendType @0xaedd4 (MONSTER_initBuff @0xae758 is an empty function) */
    static void initBlendType(Char o) {
        byte[] m = o.m;
        int b = (m[0x373] + 1) & 0xff;
        m[0x373] = (byte) b;
        if (m[0x372] != -1 && b > 2 && b < 0x80) {
            m[0x373] = 2;              // MONSTER_setBlendType(p1, -1)
            m[0x372] = (byte) 0xff;
            o.spr.f68 = 0;             // SPR_set_restore_pal(p1 + 0x20)
            m[0x373] = 0;
        }
    }

    /** EnemyAIParsing_Initialize @0xd18ec = branch to EnemyAIParsing_TestInit @0xd1854 */
    static void aiInitialize(byte[] m) {
        Bytes.w32(m, 0x290, 0x78);
        Bytes.w32(m, 0x294, 0x78);
        Bytes.w32(m, 0x298, 0xf);
        Bytes.w16(m, 0x28e, 0);
        m[0x29e] = (byte) 0xff;
        Bytes.w32(m, 0x270, -1);
        m[0x26d] = (byte) 0xff;
        m[0x26b] = (byte) 0xff;
        m[0x26c] = (byte) 0xff;
        m[0x26a] = (byte) 0xff;
        Bytes.w32(m, 0x274, 0);
        m[0x28c] = 0;
        m[0x29c] = 0;
        m[0x29d] = 0;
        Bytes.fill(m, 0x29f, 0, 9);
        m[0x268] = 0;
        m[0x2ad] = 1;
        m[0x2a8] = 0;
        m[0x2a9] = 0;
        m[0x2ac] = 0;
        m[0x2aa] = 0;
    }

    public static void init(Char o, int p2, int p3, int p4) {
        byte[] m = o.m;
        int speed = m[0x1ad];
        int type = getType(o);
        if (!isBoss(o)) {
            Chars.init(o, type, p2, p3, p4, 0, 1, speed);
        } else {
            Chars.init(o, type, p2, p3, p4, 0, m[0x30b] > 8 ? 1 : 0, speed);
        }
        Bytes.w16(m, 0x360, 0);
        Bytes.w16(m, 0x350, 0);
        m[0x164] = 0;
        Bytes.w32(m, 0x2d4, 0);
        m[0x33f] = 0;
        m[0x340] = 0;
        m[0x346] = 0;
        m[0x35d] = 0;
        m[0x35e] = 0;
        m[0x35f] = 0;
        m[0x36e] = 0;
        m[0x36f] = 0;
        Bytes.w32(m, 0x2c8, 0);
        Bytes.w32(m, 0x2cc, 0);
        Bytes.w32(m, 0x2d0, 0);
        Bytes.w16(m, 0x2bc, 0xffff);
        m[0x2c5] = (byte) 0xff;
        m[0x326] = (byte) 0xff;
        m[0x348] = 1;
        for (int k = 1; k <= 3; k++) {
            int b = 0x10 * k;
            Bytes.w32(m, b + 0x2c8, 0);
            Bytes.w32(m, b + 0x2cc, 0);
            Bytes.w32(m, b + 0x2d0, 0);
            Bytes.w32(m, b + 0x2d4, 0);
        }
        m[0x35c] = 0;
        if (isBoss(o)) {
            throw new IllegalStateException("Boss_Initialize: later");
        }
        setStandMotion(o);
        Bytes.w16(m, 0x1ba, p3);
        Bytes.w16(m, 0x1bc, p4);
        Bytes.w16(m, Obj.MAPIDX, Bytes.s32(G.map, 0x2d4));
        setMonsterStatus(o);
        Bytes.w32(m, 0xa4, Bytes.s32(m, 0xa8));
        setRegenDelay(o);
        m[0x34c] = 4;
        Bytes.w32(m, 0x334, 0);
        Bytes.w32(m, 0x338, 0);
        initBlendType(o);
        cryingFallFrameMob = 0;
        m[0x324] = 0;
        m[0x323] = 0;
        m[0x35c] = 0;
        aiInitialize(m);
        Bytes.w16(m, 0x32e, 0);
        m[0x2aa] = 0;
        Bytes.w32(m, 0x2b8, 0);
        Bytes.w16(m, 0x330, 0);
        m[0x32d] = 0;
        m[0x40f] = 0;
        Bytes.w32(m, 0x2b4, 0);
    }

    public static int cryingFallFrameMob;

    /** MONSTER_canRegen @0xaee6c */
    public static boolean canRegen(Obj o) {
        return Coll.isRectMoveable(o, Bytes.s32(o.m, 0x148), Bytes.s32(o.m, 0x14c), Bytes.s32(o.m, 0x150), Bytes.s32(o.m, 0x154), true, true);
    }

    /** MONSTER_addBody(type, mode) @0xb0408: a template monster of a map (mode 1: only registered in the map's body list) */
    public static Char addBody(int type, int mode) {
        if (type == -1) {
            return null;
        }
        Char o = (Char) Pool.getNew(Pool.MONSTER);
        if (o == null) {
            return null;
        }
        if ((type & 0xff) == 0xff) {
            Bytes.w16(o.m, 0x160, 0xff);
        } else {
            throw new IllegalStateException("MONSTER_loadRes: later");
        }
        if (mode == 0) {
            init(o, 0, 0, 0);
            Maps.addMobBodyList(o);
            return o;
        } else if (mode == 1) {
            Maps.addMobBodyList(o);
            return null;
        }
        init(o, 0, 0, 0);
        return o;
    }

    /** MONSTER_Clone(body, idx, x, y, releaseType) @0xafadc */
    public static Char clone(Char body, int idx, int x, int y, int rel) {
        if (body == null) {
            return null;
        }
        Char o = (Char) Pool.getNew(Pool.MONSTER);
        if (o == null) {
            return null;
        }
        charCopy(o, body);
        Bytes.copy(body.m, 0x94, o.m, 0x94, 0x18);
        Bytes.copy(body.m, 0x178, o.m, 0x178, 0x24);
        Bytes.copy(body.m, 0xac, o.m, 0xac, 0x48);
        Bytes.copy(body.m, 0x19c, o.m, 0x19c, 0x54);
        Bytes.copy(body.m, 0x1f0, o.m, 0x1f0, 0x1c);
        o.m[0x3e9] = 0;
        if (!isBoss(o)) {
            if (bossIsRemoveMon(o)) {
                o.m[0x3e9] = (byte) 0xfa;
            }
        } else {
            o.m[0x30b] = body.m[0x30b];
            o.m[0x30c] = body.m[0x30c];
            o.m[0x94] = body.m[0x94];
            if (o.m[0x30c] == '5') {
                throw new IllegalStateException("Boss_spriteEtcLoad: later");
            }
        }
        o.m[0x40d] = body.m[0x40d];
        o.m[0x323] = 0;
        init(o, idx, x, y);
        if (!canRegen(o)) {
            Pool.ret(o);
            return null;
        }
        if ((G.map[o.m[0x40d] * 2 + 0x48]) != -1) {
            throw new IllegalStateException("MONSTER_dataLoad_ai: later");
        }
        G.monsterList.add(o);
        G.objList.addObject(o);
        o.m[Obj.RELEASE] = (byte) rel;
        return o;
    }

    /** Boss_isNotCheckCollision @0xdf48c: a boss whose +0x3ee flag is 0 is not collided with */
    public static boolean bossNotCheckCollision(Obj o) {
        return isBoss(o) && o.m[0x3ee] == 0;
    }

    /** MONSTER_isConceal @0xb68d4 / MONSTER_isTeleport @0xb6870: both need MONSTER_getMainStateByCategory (live monster categories, Phase E6) */
    public static boolean isConceal(Char o) {
        throw new IllegalStateException("MONSTER_isConceal: later (E6)");
    }

    public static boolean isTeleport(Char o) {
        throw new IllegalStateException("MONSTER_isTeleport: later (E6)");
    }

    /** MONSTER_preProcess @0xb2fa0: a blank placeholder (type 0xff) or a pending scene change does nothing; live monsters are Phase E6 */
    public static void preProcess(Char o) {
        if (Event.nextScene() != 0 || isBlank(o)) {
            return;
        }
        throw new IllegalStateException("MONSTER_preProcess (live monster): later (E6)");
    }

    /** MONSTER_endProcess @0xb3b8c */
    public static void endProcess(Char o) {
        if (Event.nextScene() != 0 || isBlank(o)) {
            return;
        }
        throw new IllegalStateException("MONSTER_endProcess (live monster): later (E6)");
    }
}
