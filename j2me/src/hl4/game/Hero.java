package hl4.game;

import hl4.sys.Bytes;
import hl4.sys.Res;

/**
 * The hero (the pooled 0xd50-byte struct _pool[0], MP_getHero) : HERO_* @0x86620..: init, stat derivation (HERO_set_HeroStatus: every derived stat is a
 * formula from FR/_FR_PL evaluated over the base stats, equipment and passive skills), basic stat loading.  Offsets used here: +0x94 level, +0xa4/+0xa8 hp/max hp,
 * +0x16b class, +0x178/+0x17c exp / next exp, +0x189 job attribute, +0x1ac..+0x1af base attribute bytes, +0x218/+0x21c sp/max sp, +0x220/+0x222 attribute/skill points,
 * +0x294.. 16 skill slots of 0x28 bytes, +0xa28 money, +0xd0d current effect slot, +0xd38.. misc.
 */
public final class Hero {
    private Hero() {
    }

    public static Char hero() {
        return Pool.hero;
    }

    /** HERO_jobIdx @0x8902c: class / 2 */
    public static int jobIdx() {
        return Pool.hero.m[0x16b] / 2;
    }

    /** HERO_set(hero) @0x8ba28 = CHAR_set + HERO_init(0, 0) (pool initialisation) */
    public static void set(Char h) {
        Chars.set(h);
        init(0, 0);
    }

    /** HERO_init(class, p2) @0x8b5b8 */
    public static void init(int cls, int p2) {
        Char h = Pool.hero;
        byte[] m = h.m;
        Chars.init(h, cls, p2, 0, 0, 0, 1, 0);
        Bytes.w16(m, 0x220, 0);
        Bytes.w16(m, 0x222, 0);
        Bytes.w16(m, 0x224, 0);
        m[0x94] = 1;
        Bytes.w32(m, 0x178, 0);
        Bytes.w32(m, 0x17c, 0);
        Bytes.w32(m, 0x218, 0);
        Bytes.w32(m, 0x21c, 0);
        Bytes.w32(m, 0xa28, 0);
        int dir = m[0x168];
        m[0x19] = 5;
        m[0xa41] = 2;
        m[0x22a] = (byte) dir;
        m[0x229] = 0;
        m[0x228] = 0;
        m[0x227] = 0;
        m[0x226] = 0;
        m[0xa31] = 0;
        m[0x16e] = 0;
        m[0xa45] = 0;
        m[0xa2c] = 0;
        m[0xa44] = 0;
        m[0xa42] = 0;
        m[0x20c] = 0;
        Bytes.w16(m, 0xa3a, 0);
        Bytes.w16(m, 0xa3c, 0);
        Bytes.w16(m, 0xa3e, 0);
        Bytes.w16(m, 0x2ba, 0);
        m[0x2b9] = 0;
        m[0x2b8] = 0;
        for (int k = 1; k <= 15; k++) {
            int b = k * 0x28;
            Bytes.w16(m, 0x2ba + b, 0);
            m[0x2b8 + b] = 0;
            m[0x2b9 + b] = 0;
        }
        for (int i = 0; i < 7; i++) {
            G.equip[i] = null;
        }
        for (int i = 0; i < 6; i++) {
            G.armsEquip[i] = null;
        }
        m[0x2b8] = 1;
        for (int k = 0; k < 2; k++) {
            int b = 0x1f0 + k;
            m[b + 4] = 0;
            m[b + 0x10] = (byte) 0xff;
            for (int j = 0; j < 5; j++) {
                m[b + 6 + 2 * j] = 0;
                m[b + 6 + 2 * j + 0xc] = (byte) 0xff;
            }
        }
        Bytes.w32(m, 0xd30, 0);
        Bytes.w32(m, 0xd1c, -1);
        Bytes.w16(m, 0x1b6, 0);
        m[0xd3a] = 0;
        m[0x204] = 0;
        m[0xa31] = 0;
        m[0xd13] = 0;
        Bytes.w32(m, 0xd18, -1);
        Bytes.w32(m, 0xd20, 0);
        m[0xd24] = 0;
        m[0x217] = 0;
        m[0x214] = 0;
        m[0xa30] = 0;
        Bytes.w16(m, 0xc6, 0);
        m[0x216] = 0;
        m[0x1ed] = 0;
        m[0x1ec] = 0;
        m[0x1ac] = 0;
        m[0x1ad] = 0;
        m[0x1ae] = 0;
        m[0x1af] = 0;
        Bytes.w32(m, 0xd2c, 0);
    }

    /** HERO_checkEquip @0x86898: drops equipment pointers whose item type does not fit the slot */
    public static void checkEquip() {
        Obj[] e = G.equip;
        if (e[0] != null && e[0].m[0x20] != 0) {
            e[0] = null;
        }
        if (e[1] != null && e[1].m[0x20] != 1) {
            e[1] = null;
        }
        if (e[2] != null && e[2].m[0x20] != 2) {
            e[2] = null;
        }
        if (e[4] != null && ((e[4].m[0x20] & 0xff) - 4) > 2) {
            e[4] = null;
        }
        if (e[5] != null && e[5].m[0x20] != 7) {
            e[5] = null;
        }
        if (e[6] != null && e[6].m[0x20] != 7) {
            e[6] = null;
        }
    }

    /** HERO_load_basic_stat(hero) @0x8f050: base stats of the class from HDAT/_H_BH (record per class) */
    public static void loadBasicStat(Obj h) {
        byte[] m = h.m;
        byte[] d = Res.loadDes("/HDAT/_H_BH");
        int cls = m[0x16b];
        int a;
        int pa;
        int pb;
        int pc;
        if (cls + 1 < 1) {
            pc = 3;
            a = 0;
            pa = 1;
            pb = 2;
        } else {
            a = 0;
            int p = 0;
            int len = 0;
            int n = 0;
            int q;
            do {
                q = a + len;
                len = Bytes.s16(d, q);
                n++;
                a = q + 2;
            } while (n < cls + 1);
            pc = q + 5;
            pb = q + 4;
            pa = q + 3;
        }
        m[0x188] = d[a];
        m[0x189] = d[pa];
        int nameLen = (short) (d[pb] & 0xff);
        Bytes.fill(m, 0x95, 0, 0xf);
        int s = nameLen + pc;
        Bytes.copy(d, pc, m, 0x95, nameLen);
        m[0x18e] = d[s];
        m[0x18a] = d[s + 1];
        m[0x18b] = d[s + 2];
        m[0x18c] = d[s + 3];
        m[0x18d] = d[s + 4];
        for (int k = 0x18f; k <= 0x1a8; k++) {
            m[k] = d[s + 5 + (k - 0x18f)];
        }
    }

    // ---- derived stats ---------------------------------------------------------------------------------------------------------------

    private static void w16(byte[] m, int o, int v) {
        Bytes.w16(m, o, v);
    }

    /** HERO_set_HeroStatus(refill, keepPassive) @0x8c150 */
    public static void setHeroStatus(int refill, int p2) {
        Char h = Pool.hero;
        byte[] m = h.m;
        checkEquip();
        setHeroStatusPassive(h, p2);
        recalcFormulas(h);
        if (refill != 0) {
            Bytes.w32(m, 0xa4, Bytes.s32(m, 0xa8));
            if (m[0x189] == 1) {
                Bytes.w32(m, 0x218, Bytes.s32(m, 0x21c));
            }
        }
        if (G.equip[4] == null) {
            m[0xa41] = 2;
        }
        if (Bytes.s16(m, 0x1cc) > 100) {
            Bytes.w16(m, 0x1cc, 100);
        }
        for (int k = 0; k < 16; k++) {
            int b = k * 0x28;
            if (m[0x2b8 + b] > 0) {
                int v = Bytes.s16(m, 0x29c + b) * (100 - Bytes.s16(m, 0x1cc));
                v = v / 100;
                Bytes.w16(m, 0x29e + b, v);
                if ((short) v < Bytes.s16(m, 0x2ba + b)) {
                    Bytes.w16(m, 0x2ba + b, v);
                }
            }
        }
        if (p2 == 0) {
            int v = m[0xd38] & 0xff;
            m[0xd39] = m[0xd38];
            if (v > 0xf) {
                m[0xd39] = 0xf;
            }
        }
    }

    /**
     * The formula block shared by HERO_set_HeroStatus and CHAR_removeTargetEffGood: derived stats from FR formulas (the second keeps the first's
     * stores; only the sp/exp bookkeeping differs, see the callers).
     */
    private static void recalcFormulas(Char h) {
        byte[] m = h.m;
        Bytes.w16(m, 0xb2, Fr.calc(3000, h, null, -1, 0, h, null));
        Bytes.w16(m, 0xba, Fr.calc(0xbba, h, null, -1, 0, h, null));
        Bytes.w16(m, 0xc2, Fr.calc(0xbbb, h, h, -1, 0, h, null));
        Bytes.w32(m, 0x17c, Fr.calc(0xbbd, h, h, -1, 0, h, null));
        Bytes.w16(m, 0xd4, Fr.calc(0xbc5, h, null, -1, 0, h, null));
        Bytes.w16(m, 0xd8, Fr.calc(0xbc6, h, null, -1, 0, h, null));
        Bytes.w16(m, 0xdc, Fr.calc(0xbcc, h, null, -1, 0, h, null));
        Bytes.w16(m, 0xe4, Fr.calc(0xbc7, h, null, -1, 0, h, null));
        Bytes.w16(m, 0xe6, Fr.calc(0xbc8, h, null, -1, 0, h, null));
        Bytes.w16(m, 0xe0, Fr.calc(0xbc9, h, null, -1, 0, h, null));
        Bytes.w16(m, 0xea, Fr.calc(0xbca, h, null, -1, 0, h, null));
        Bytes.w16(m, 0xec, Fr.calc(0xbcb, h, null, -1, 0, h, null));
        Bytes.w32(m, 0xa8, Fr.calc(0xbbf, h, null, -1, 0, h, null));
        Bytes.w16(m, 0xca, Fr.calc(0xbbe, h, null, -1, 0, h, null));
        Bytes.w16(m, 0xd0, Fr.calc(0xbc0, h, null, -1, 0, h, null));
        if (Bytes.s32(m, 0xa8) < Bytes.s32(m, 0xa4)) {
            Bytes.w32(m, 0xa4, Bytes.s32(m, 0xa8));
        }
        int id = m[0x189] == 0 ? 0xbd0 : 0xbce;
        int sp = (short) Fr.calc(id, h, null, -1, 0, h, null);
        Bytes.w16(m, 0x1b2, sp);
        if (sp < Bytes.s32(m, 0x218)) {
            Bytes.w32(m, 0x218, sp);
        }
        Bytes.w32(m, 0x21c, sp);
        Bytes.w16(m, 0x1be, Fr.calc(0xbcd, h, null, -1, 0, h, null));
        Bytes.w16(m, 0x1ba, Fr.calc(0xbcf, h, null, -1, 0, h, null));
        Bytes.w16(m, 0x1ce, Fr.calc(0xbb9, h, null, -1, 0, h, null));
        Bytes.w16(m, 0x1c8, Fr.calc(0x3ea, h, null, -1, 0, h, null));
    }

    /** the tail of CHAR_removeTargetEffGood for a hero @0xdba0c: clears the buff bonus, re-derives the stats (no refill) */
    public static void recalcAfterBuffRemoval() {
        Char h = Pool.hero;
        byte[] m = h.m;
        Bytes.w16(m, 0x1e0, 0);
        checkEquip();
        setHeroStatusPassive(h, 0);
        recalcFormulas(h);
        if (G.equip[4] == null) {
            m[0xa41] = 2;
        }
        if (Bytes.s16(m, 0x1cc) > 100) {
            Bytes.w16(m, 0x1cc, 100);
        }
        for (int k = 0; k < 16; k++) {
            int b = k * 0x28;
            if (m[0x2b8 + b] > 0) {
                int v = Bytes.s16(m, 0x29c + b) * (100 - Bytes.s16(m, 0x1cc));
                v = v / 100;
                Bytes.w16(m, 0x29e + b, v);
                if ((short) v < Bytes.s16(m, 0x2ba + b)) {
                    Bytes.w16(m, 0x2ba + b, v);
                }
            }
        }
        int v = m[0xd38] & 0xff;
        m[0xd39] = m[0xd38];
        if (v > 0xf) {
            m[0xd39] = 0xf;
        }
    }

    /** HERO_set_HeroStatusPassive(hero, p2) @0x8be98: base attribute bytes -> stat fields, then passive skills and item options */
    public static void setHeroStatusPassive(Char h, int p2) {
        byte[] m = h.m;
        if ((m[0x1ac] & 0xff) < (m[0x18a] & 0xff)) {
            m[0x1ac] = m[0x18a];
        }
        if ((m[0x1ad] & 0xff) < (m[0x18b] & 0xff)) {
            m[0x1ad] = m[0x18b];
        }
        if ((m[0x1ae] & 0xff) < (m[0x18c] & 0xff)) {
            m[0x1ae] = m[0x18c];
        }
        if ((m[0x1af] & 0xff) < (m[0x18d] & 0xff)) {
            m[0x1af] = m[0x18d];
        }
        w16(m, 200, m[0x192]);
        w16(m, 0xe8, m[0x199]);
        w16(m, 0xee, m[0x19a]);
        w16(m, 0x1c2, m[0x197]);
        w16(m, 0xd6, m[0x19b]);
        w16(m, 0xe2, m[0x19c]);
        w16(m, 0xda, m[0x19d]);
        w16(m, 0xac, m[0x19e]);
        w16(m, 0x1b0, m[0x18e]);
        w16(m, 0x1b4, m[0x193]);
        w16(m, 0x1b8, m[0x194]);
        w16(m, 0x1bc, m[0x195]);
        w16(m, 0x1c0, m[0x196]);
        m[0xd2] = m[0x190];
        w16(m, 0x1c6, m[0x198]);
        w16(m, 0xce, Bytes.s16(m, 0x1e0) + m[0x18f]);
        m[0xcc] = m[0x191];
        w16(m, 0xbe, m[0x1a3]);
        w16(m, 0xb4, m[0x1a4]);
        w16(m, 0xde, m[0x1a5]);
        w16(m, 0xc0, m[0x1a6]);
        w16(m, 0x1ca, m[0x1a7]);
        w16(m, 0x1cc, m[0x1a8]);
        w16(m, 0x1d0, 0);
        w16(m, 0xc4, m[0x19f]);
        w16(m, 0xb8, m[0x1a0]);
        w16(m, 0xb0, Bytes.s16(m, 0x1e0) + m[0x1a1]);
        w16(m, 0xbc, m[0x1a2]);
        w16(m, 0x1d2, 0);
        w16(m, 0x1dc, 0);
        w16(m, 0x1de, 0);
        w16(m, 0xd4c, 0);
        m[0xd38] = 0;
        setHeroStatusSkillPassive(h);
        if (m[0x189] == 1) {
            setHeroStatusSummonPassive(h);
        } else if (m[0x189] == 0) {
            setHeroStatusArmsPassive(h);
        }
        setHeroStatusItemPassive(h, p2);
        int n = m[0x12c];
        int i = 0;
        if (n > 0) {
            do {
                if (m[0xf4 + i * 8] != 0) {
                    BattleProc.setSkillPassive(h, h, m[0xf6 + i * 8], m[0xf5 + i * 8], h, 0);
                    n = m[0x12c];
                }
                i = (i + 1) & 0xff;
            } while ((byte) i < n);
        }
        if (m[0x10c] != 0) {
            BattleProc.setSkillPassive(h, h, m[0xf6 + (((i << 24) >> 24) << 3)], m[0xf5 + (((i << 24) >> 24) << 3)], h, 0);
        }
    }

    /** HERO_set_HeroStatusSkillPassive @0x8bd3c: skills 12..15 */
    private static void setHeroStatusSkillPassive(Char h) {
        BattleProc.setSkillPassive(h, h, 0xc, 1, h, 0);
        for (int k = 0xd; k != 0x10; k++) {
            BattleProc.setSkillPassive(h, h, k, 1, h, 0);
        }
    }

    /** HERO_set_HeroStatusSummonPassive @0x8bc28 */
    private static void setHeroStatusSummonPassive(Char h) {
        if (h.m[0x1ed] != 0) {
            BattleProc.setSkillPassive(h, h, (h.m[0x1ec] * 0x4000000 + 0x9000000) >> 24, 3, h, 0);
        }
    }

    /** HERO_set_HeroStatusArmsPassive @0x8bc70 */
    private static void setHeroStatusArmsPassive(Char h) {
        int cnt = G.armsSlotCnt;
        for (int i = 0; i < cnt; i++) {
            Obj a = G.armsEquip[i];
            if (a != null && a.m[0x173] == 5) {
                BattleProc.setSkillPassive(h, h, a.m[0x174], 2, h, 0);
            }
        }
    }

    /** HERO_set_HeroStatusItemPassive(hero, p2) @0x8bda0 */
    private static void setHeroStatusItemPassive(Char h, int p2) {
        byte[] m = h.m;
        for (int i = 0; i < 7; i++) {
            if (G.equip[i] != null) {
                BattleProc.setItemPassive(h, h, h, G.equip[i], p2);
            }
        }
        int cnt = 0;
        for (int i = 0; i < 4; i++) {
            if (G.equip[i] != null && G.equip[i].m[0x64] > 4) {
                cnt = (cnt + 1) & 0xff;
            }
        }
        if (cnt == 4) {
            m[0xd3a] = 1;
            throw new IllegalStateException("set item bonus (Item_loadSetOptData): later");
        }
        m[0xd3a] = 0;
    }

    /** HERO_add_curMoney(h, n) @0x76974: +0xa28 += n, capped at 2,000,000,000 */
    public static void addCurMoney(Char h, int n) {
        int v = Bytes.s32(h.m, 0xa28) + n;
        Bytes.w32(h.m, 0xa28, v > 2000000000 ? 2000000000 : v);
    }
}
