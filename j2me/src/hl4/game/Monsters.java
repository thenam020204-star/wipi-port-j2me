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

    /** MONSTER_getCurHp @0x9e684: the HP, cut to the maximum (and stored back) */
    public static int getCurHp(Obj o) {
        int mx = Bytes.s32(o.m, 0xa8);
        int cur = Bytes.s32(o.m, 0xa4);
        if (cur > mx) {
            cur = mx;
            Bytes.w32(o.m, 0xa4, mx);
        }
        return cur;
    }

    /** MONSTER_bodyRemain @0x9efa8: the corpse stays (main state 0x12) */
    public static boolean bodyRemain(Obj o) {
        return o.m[0x166] == 0x12;
    }

    /** MONSTER_isObject @0xa4750 */
    public static boolean isObject(Obj o) {
        return o.m[0x1ae] == 8;
    }

    /** MONSTER_isHidding */
    public static boolean isHidding(Obj o) {
        return o.m[0x35d] != 0;
    }

    // ---- creation ------------------------------------------------------------------------------------------------------------------------

    /** CHAR_copy(dst, src) @0x495dc */
    public static void charCopy(Char d, Char s) {
        d.spr.copyFrom(s.spr);
        Bytes.copy(s.m, 0x20, d.m, 0x20, 0x28);            // SPR_copyRes: SPR+0..+0x27 (anchor, move rect, hit rect)
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

    private static String n3(int v) {
        String t = String.valueOf(v);
        return v < 10 ? "00" + t : (v < 100 ? "0" + t : t);
    }

    /** offset of record k in a table of (s16 length, data) records (the skip loop of MONSTER_dataLoad_g/_d) */
    private static int skipRecords(byte[] d, int k) {
        int off = 0;
        for (int i = 0; i < k; i++) {
            off += 2 + Bytes.s16(d, off);
        }
        return off;
    }

    /** MONSTER_loadRes(o, type) @0xa03ac */
    public static void loadRes(Char o, int type) {
        if (type < 200) {
            dataLoadG(o, type);
            dataLoadD(o, type);
        } else {
            throw new IllegalStateException("Boss_dataLoad_d: later");
        }
        spriteLoad(o);
        imageLoad(o);
    }

    /** MONSTER_dataLoad_g(o, type) @0xa01f4: graphics record of /E/_EGDAT */
    static void dataLoadG(Char o, int type) {
        byte[] d = hl4.sys.Res.load("/E/_EGDAT");
        byte[] m = o.m;
        int b = type < 1 ? 0 : skipRecords(d, type);
        Bytes.w16(m, 0x160, type);
        Bytes.w16(m, 0x162, d[b + 2]);
        m[0x16b] = d[b + 4];
        for (int i = 0; i < 6; i++) {
            m[0x19c + i] = d[b + 5 + i];
            m[0x1a2 + i] = d[b + 11 + i];
        }
        m[0x1a8] = d[b + 17];
        m[0x1a9] = d[b + 18];
        m[0x1aa] = d[b + 19];
        m[0x1b7] = d[b + 20];
        m[0x1ac] = d[b + 21];
        m[0x1ad] = d[b + 22];
        m[0x1ae] = d[b + 23];
        m[0x1af] = d[b + 24];
        m[0x1b0] = d[b + 25];
        m[0x1b1] = d[b + 26];
        m[0x1b3] = d[b + 27];
        m[0x1b4] = d[b + 28];
        m[0x1b5] = d[b + 29];
    }

    /** MONSTER_dataLoad_d(o, type) @0x9fda0: stats record of /E/_ESDAT_<_game[1]> (DES) */
    static void dataLoadD(Char o, int type) {
        byte[] d = hl4.sys.Res.loadDes("/E/_ESDAT_" + G.game[1]);
        byte[] m = o.m;
        int r = type < 1 ? 0 : skipRecords(d, type);
        int len = d[r + 3];
        for (int i = 0; i < 0xf; i++) {
            m[0x95 + i] = 0;
        }
        System.arraycopy(d, r + 4, m, 0x95, len);
        int b = r + 4 + len;
        m[0x1b8] = d[b];
        m[0x94] = d[b + 1];
        int hp = Bytes.s32(d, b + 2);
        Bytes.w32(m, 0x178, hp);
        Bytes.w32(m, 0xa8, hp);
        w16x2(m, 0x196, 0xca, d[b + 6]);
        w16x2(m, 0x198, 0xd0, d[b + 7]);
        for (int i = 0; i < 3; i++) {
            m[0x1be + i] = d[b + 8 + 5 * i];
            Bytes.w16(m, 0x1c2 + 2 * i, Bytes.s16(d, b + 9 + 5 * i));
            m[0x1ca + i] = d[b + 0xb + 5 * i];
            m[0x1ce + i] = d[b + 0xc + 5 * i];
        }
        w16x2(m, 0x17c, 0xe6, Bytes.s16(d, b + 0x17));
        w16x2(m, 0x17e, 0xec, Bytes.s16(d, b + 0x19));
        w16x2(m, 0x180, 0xd4, Bytes.s16(d, b + 0x1b));
        w16x2(m, 0x182, 0xe0, Bytes.s16(d, b + 0x1d));
        w16x2(m, 0x184, 0xd8, Bytes.s16(d, b + 0x1f));
        w16x2(m, 0x186, 0xe4, d[b + 0x21]);
        w16x2(m, 0x188, 0xea, d[b + 0x22]);
        w16x2(m, 0x18a, 0xc2, d[b + 0x23]);
        w16x2(m, 0x18c, 0xb6, d[b + 0x24]);
        w16x2(m, 0x18e, 0xae, d[b + 0x25]);
        w16x2(m, 0x190, 0xba, d[b + 0x26]);
        w16x2(m, 0x192, 0xb2, d[b + 0x27]);
        w16x2(m, 0x194, 0xdc, d[b + 0x28]);
        m[0x1d2] = d[b + 0x29];
        for (int i = 0; i < 6; i++) {
            Bytes.w16(m, 0x1d4 + 2 * i, Bytes.s16(d, b + 0x2a + 2 * i));
        }
        m[0x1e0] = d[b + 0x36];
        m[0x1e1] = d[b + 0x37];
        m[0x1e2] = d[b + 0x38];
        m[0x1e3] = d[b + 0x39];
        m[0x1e5] = d[b + 0x3a];
        m[0x1e4] = d[b + 0x3b];
        m[0x1e6] = d[b + 0x3c];
        m[0x1e7] = d[b + 0x3e];
        Bytes.w16(m, 0x1ea, Bytes.s16(d, b + 0x3f));
        m[0x1ec] = d[b + 0x41];
        m[0x1ed] = d[b + 0x42];
    }

    private static void w16x2(byte[] m, int a, int b, int v) {
        Bytes.w16(m, a, v);
        Bytes.w16(m, b, v);
    }

    /** MONSTER_spriteLoad @0x9f954: /H4/CIF/_H_<+0x162>_CIF (mode 3; 2 for a boss with +0x30b > 8) and its EXD */
    static void spriteLoad(Char o) {
        int sid = Bytes.s16(o.m, 0x162);
        int mode = isBoss(o) && o.m[0x30b] > 8 ? 2 : 3;
        o.spr = new hl4.gfx.Sprite(hl4.sys.Res.load("/H4/CIF/_H_" + n3(sid) + "_CIF"), mode);
        o.spr.props = hl4.gfx.SprProps.parse(hl4.sys.Res.load("/H4/EXD/_H_" + n3(sid) + "_EXD"));
        o.spr.props.store(o.m, 0x20);          // SPR_loadAttackProperty: anchor and the two rectangles into SPR+0..+0x24
    }

    /** MONSTER_imageLoad @0x9f8fc: image sets 0..5 (+0x19c ids, +0x1a2 palettes) */
    static void imageLoad(Char o) {
        for (int i = 0; i < 6; i++) {
            int id = o.m[0x19c + i];
            if (id > -1) {
                Npcs.loadMonsterImage(o.spr, i, id, o.m[0x1a2 + i] & 0xff);
            }
        }
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
            loadRes(o, type);
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

    /** MONSTER_release_ai @0x9fa18 = EnemyAIParsing_Finalize: frees the script state (the struct bytes are left as they are) */
    public static void releaseAi(Char o) {
        o.ai = null;
    }

    /** SPR_destroy(o+0x20) @0xc1734 */
    static void sprDestroy(Char o) {
        o.spr.set();
        Bytes.fill(o.m, 0x20, 0, 0x74);
    }

    /** MONSTER_CloneEtc(o, body, idx) @0x9fcc0: re-clones a live monster from a (re)loaded body at its spawn point (+0x1ba/+0x1bc) */
    static void cloneEtc(Char o, Char body, int idx) {
        int x = Bytes.s16(o.m, 0x1ba);
        int y = Bytes.s16(o.m, 0x1bc);
        charCopy(o, body);
        Bytes.copy(body.m, 0x94, o.m, 0x94, 0x18);
        Bytes.copy(body.m, 0x178, o.m, 0x178, 0x24);
        Bytes.copy(body.m, 0xac, o.m, 0xac, 0x48);
        Bytes.copy(body.m, 0x19c, o.m, 0x19c, 0x54);
        Bytes.copy(body.m, 0x1f0, o.m, 0x1f0, 0x1c);
        if (isBoss(o)) {
            o.m[0x30b] = body.m[0x30b];
            o.m[0x30c] = body.m[0x30c];
            o.m[0x94] = body.m[0x94];
        }
        o.m[0x323] = 0;
        o.m[0x40d] = body.m[0x40d];
        init(o, (byte) idx, x, y);
        EnemyAi.dataLoad(o, o.m[0x1b8]);
    }

    /** MONSTER_changeEnemy(group, slot, type) @0xa04d0: the group's body is reloaded as 'type' (0xff: removed) and its live monsters re-cloned */
    public static void changeEnemy(int group, int slot, int type) {
        Char body = Maps.monBodies[slot];
        if (body == null) {
            return;
        }
        for (Obj p = G.objList.head; p != null; ) {
            Obj nx = p.next;
            int st = Obj.structType(p);
            if (st == 3) {
                Chars.deleteEffectInList((Char) p);
                Chars.deleteEffectInObjList((Char) p);
            } else if (st == 2) {
                Chars.deleteEffectInList(Pool.hero);
                Chars.deleteEffectInObjList(Pool.hero);
            }
            p = nx;
        }
        body = Maps.monBodies[slot];
        if (type == 0xff) {
            sprDestroy(body);
            Maps.monBodies[slot].m[0x40c] = 1;
        } else if (Bytes.s16(body.m, 0x160) != type || body.m[0x40c] == 1) {
            sprDestroy(body);
            loadRes(body, type);
            Maps.monBodies[slot].m[0x40c] = 0;
        }
        for (Obj p = G.objList.head; p != null; p = p.next) {
            if (Obj.structType(p) != 3) {
                continue;
            }
            Char o = (Char) p;
            if (o.m[0x40d] != group) {
                continue;
            }
            if (type == 0xff) {
                if (Bytes.s16(o.m, 0x160) != 0xff) {
                    Bytes.w16(o.m, 0x160, type);
                    o.m[0x164] = 1;
                    releaseAi(o);
                    Chars.resetPosition(o, -Bytes.s32(o.m, 0x30), -Bytes.s32(o.m, 0x34));
                }
            } else {
                if (o.m[0x30c] == 0x38) {
                    Bytes.w32(G.map, 0x378, Bytes.s32(o.m, 0xa4));
                }
                cloneEtc(o, Maps.monBodies[slot], o.m[0x16c]);
            }
        }
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
            EnemyAi.dataLoad(o, o.m[0x1b8]);
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

    /** MONSTER_isConceal @0xa68d4: in the conceal state with the concealed motion */
    public static boolean isConceal(Char o) {
        if ((byte) MonProc.mainStateByCategory(o) != 5) {
            return false;
        }
        return o.m[0x167] == (byte) MonProc.concealedMotion(o);
    }

    /** MONSTER_isTeleport @0xa6870: in the teleport state with the teleport in / out motion */
    public static boolean isTeleport(Char o) {
        if ((byte) MonProc.mainStateByCategory(o) != 7) {
            return false;
        }
        return o.m[0x167] == (byte) MonProc.teleportInMotion(o) || o.m[0x167] == (byte) MonProc.teleportOutMotion(o);
    }

    /** MONSTER_isTeleporting @0xa6758: isTeleport and +0x324 set */
    public static boolean isTeleporting(Char o) {
        return isTeleport(o) && o.m[0x324] != 0;
    }

    /** MONSTER_preProcess: see MonProc */
    public static void preProcess(Char o) {
        MonProc.preProcess(o);
    }

    /** MONSTER_endProcess: see MonProc */
    public static void endProcess(Char o) {
        MonProc.endProcess(o);
    }

    /** MONSTER_noMoreRegen @0x9e874 (always false) */
    public static boolean noMoreRegen(Char o) {
        return false;
    }

    /** MONSTER_getIndex @0x9e87c */
    public static int getIndex(Char o) {
        return o == null ? -1 : o.m[0x16c];
    }

    /** MONSTER_Regen @0xa0708 */
    public static void regen(Char o) {
        throw new IllegalStateException("MONSTER_Regen: later");
    }

    /** MONSTER_addHp(o, n, p3, p4, p5) @0xa3568 */
    public static void addHp(Char o, int n, int p3, int p4, int p5) {
        throw new IllegalStateException("MONSTER_addHp: later");
    }
}
