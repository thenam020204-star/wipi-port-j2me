package hl4.game;

import hl4.sys.Bytes;
import hl4.sys.Rng;

/**
 * The hero hitting a monster: HERO_attackMonsterProcess @0x79054, HERO_attackMonster @0x77f1c, Hero_canAttack @0x9efcc, HERO_getEffectIdx @0x77d08,
 * HERO_isBasicTargetEff @0x77e74, Hero_addTargetEffectToMonster @0x3c708, HERO_getSkillSndIdx @0x76cec, CHAR_heroAddSp @0x396a4 and the hero side of
 * BattleProc_getSkillDamage @0xcc478 / BattleProc_setSkillEff @0xcec9c.  Written from the disassembly (CONFIRMED paths: class 0, basic attack of a
 * physical kind); everything else stops with "later".
 */
public final class HeroBattle {
    private HeroBattle() {
    }

    private static void later(String what) {
        throw new IllegalStateException(what + ": later");
    }

    /** C.84.20547 @0xd874c: the basic effect indices of the four classes */
    private static final byte[] BASIC_EFF = {31, 32, -1, -1, 32, 33, -1, -1, 26, 27, 28, 29, 26, -1, -1, -1};

    /** HERO_getEffectIdx(h, skill) @0x77d08 */
    static int getEffectIdx(Char h, int skill) {
        int cls = h.m[0x16b];
        if (cls != 0) {
            later("HERO_getEffectIdx: class " + cls);
        }
        if ((skill & 0xff) > 0x10) {
            return -1;
        }
        int b = 1 << skill;
        if ((b & 0xca4) != 0) {
            return 0x20;
        }
        if ((b & 0x1005b) != 0) {
            return 0x1f;
        }
        return -1;
    }

    /** HERO_isBasicTargetEff(h, skill) @0x77e74: the basic effect of the class; its cycle counter (+0xd12, 0..3) advances */
    static boolean isBasicTargetEff(Char h, int skill) {
        byte[] m = h.m;
        for (int i = 0; i < 4; i++) {
            if (BASIC_EFF[m[0x16b] * 4 + i] == getEffectIdx(h, skill)) {
                int c = m[0xd12];
                m[0xd12] = (byte) (c <= 2 ? c + 1 : 0);
                return true;
            }
        }
        return false;
    }

    /** HERO_getSkillSndIdx(skill) @0x76cec (class 0 part) */
    static int getSkillSndIdx(int skill) {
        Char h = Pool.hero;
        int cls = h.m[0x16b];
        int mo = h.m[0x167];
        if (cls != 0) {
            later("HERO_getSkillSndIdx: class " + cls);
        }
        if (skill > 0x10) {
            return -1;
        }
        switch (skill) {
            case 0:
            case 6:
                return h.m[0xa2e] == 1 ? 5 : 0;
            case 1:
                return mo == 0xe ? 5 : 0;
            case 2:
            case 5:
                return h.m[0xa2e] == 1 ? 5 : 1;
            case 3:
            case 4:
                return h.m[0xa2e] == 1 ? 5 : 1;
            case 7:
                return h.m[0xa2e] == 1 ? 5 : 0;
            case 0x10:
                return 3;
            default:
                later("HERO_getSkillSndIdx: skill " + skill);
                return -1;
        }
    }

    /** HERO_playSkillSound(skill) @0x78df4 */
    static void playSkillSound(int skill) {
        int s = getSkillSndIdx(skill);
        if (s > -1) {
            Sound.playEff(s);
        }
    }

    /** CHAR_heroAddSp(n) @0x396a4 (n >= 0: the SP is raised to at most the maximum) */
    static void heroAddSp(int n) {
        byte[] m = Pool.hero.m;
        if (n < 0) {
            later("CHAR_heroAddSp: n < 0");
        }
        int sp = Bytes.s32(m, 0x218) + n;
        Bytes.w32(m, 0x218, sp);
        if (sp > Bytes.s32(m, 0x21c)) {
            Bytes.w32(m, 0x218, Bytes.s32(m, 0x21c));
        }
        if (Bytes.s32(m, 0x218) <= 0) {
            Bytes.w32(m, 0x218, 0);
        }
    }

    /** Hero_canAttack(mon) @0x9efcc */
    static boolean canAttack(Char o) {
        if (Monsters.isDie(o)) {
            return false;
        }
        if (!Monsters.isBoss(o) && Monsters.isConceal(o)) {
            return false;
        }
        if (o.m[0x30a] != 0) {
            return false;
        }
        return !Event.isEvent();
    }

    // ---- BattleProc (hero attacker) -------------------------------------------------------------------------------------------------------------

    private static final byte[] A = new byte[4];
    private static final int[] B = new int[4];
    private static final byte[] C = new byte[4];

    /** BattleProc_getSkillEffValue(self, A, B, C, skill, infoType) @0xcc110, hero attacker: the four effect kinds / values / levels of the skill slot */
    private static void getSkillEffValue(Char self, int skill, int infoType) {
        byte[] m = self.m;
        if (Obj.structType(self) != 2) {
            later("BattleProc_getSkillEffValue: attacker type " + Obj.structType(self));
        }
        if (infoType != 1) {
            later("BattleProc_getSkillEffValue: infoType " + infoType);
        }
        int o = skill * 0x28;
        for (int i = 0; i < 4; i++) {
            A[i] = m[o + 0x2a8 + i];
            C[i] = m[o + 0x2b4 + i];
        }
        B[0] = Bytes.u16(m, o + 0x2ac);
        B[1] = Bytes.u16(m, o + 0x2ae);
    }

    /** BattleProc_getSkillDamage(hero, mon, skill, infoType, hero, mon) @0xcc478 */
    static int getSkillDamage(Char self, Char tgt, int skill, int infoType, Obj hero, Obj mon) {
        getSkillEffValue(self, skill, infoType);
        int res = -1;
        for (int i = 0; i < 4; i++) {
            self.m[0xd0d] = (byte) i;
            int kind = A[i] & 0xff;
            if (kind == 0) {
                continue;
            }
            boolean magic = ((kind - 0x29) & 0xff) <= 9 && kind != 0x30;
            if (magic) {
                later("BattleProc_setSkillEff_Magical");
            } else if (kind == 0x23 || ((kind - 0x33) & 0xff) <= 9 || kind == 0x30) {
                res = MonBattle.physical(self, tgt, kind, skill, infoType, hero, mon);
            }
        }
        return res;
    }

    /** BattleProc_setSkillEff(hero, mon, skill, infoType, hero, mon) @0xcec9c: the extra effects of the skill (status, class, summon, item kinds) */
    static int setSkillEff(Char self, Char tgt, int skill, int infoType, Obj hero, Obj mon) {
        getSkillEffValue(self, skill, infoType);
        int res = -1;
        for (int i = 0; i < 4; i++) {
            int kind = A[i] & 0xff;
            if (kind == 0) {
                continue;
            }
            self.m[0xd0d] = (byte) i;
            if (((kind - 0x3d) & 0xff) <= 9 || kind == 0x59 || ((kind - 0x47) & 0xff) <= 8 || kind == 0x55 || kind == 0x5f || kind == 0x58 || kind == 0x56) {
                later("BattleProc_setSkillEff_Etc: kind " + kind);
            } else if (((kind - 0x51) & 0xff) <= 3) {
                later("BattleProc_setSkillEff_Class: kind " + kind);
            } else if (((kind - 0x5b) & 0xff) <= 3) {
                later("BattleProc_setSkillEff_Summon: kind " + kind);
            } else if (((kind - 0x65) & 0xff) <= 0x24) {
                later("BattleProc_setSkillEff_Item: kind " + kind);
            }
        }
        return res & 0xff;
    }

    /**
     * BattleProc_setItemEff(hero, mon, hero, mon, item) @0xcea2c: the three effect slots of an equipped item (ids +0x215.., chances +0x79.., values +0x7c..)
     * each draw a percent roll; the passive stat kinds (1..0x3c, 0x50, 0x57, 0x5a) do nothing on a hit, the status / class / summon / item kinds act.
     */
    static void setItemEff(Char h, Obj item) {
        byte[] im = item.m;
        for (int k = 0; k < 3; k++) {
            if (im[0x215 + k] == 0) {
                continue;
            }
            h.m[0xd0d] = (byte) k;
            int e = im[0x215 + k] & 0xff;
            if (e == 0) {
                continue;
            }
            int r = Rng.rand(0, 0x63);
            if (r > im[0x79 + k]) {
                continue;
            }
            if (e == 0x50 || ((e - 1) & 0xff) <= 0x1e || e == 0x5a || e == 0x57 || ((e - 0x1f) & 0xff) <= 9 || ((e - 0x29) & 0xff) <= 9
                    || ((e - 0x33) & 0xff) <= 9) {
                continue;
            }
            if (((e - 0x3d) & 0xff) <= 9 || e == 0x59 || ((e - 0x47) & 0xff) <= 8 || e == 0x55 || e == 0x5f || e == 0x58 || e == 0x56) {
                later("BattleProc_setSkillEff_Etc (item effect " + e + ")");
            } else if (((e - 0x51) & 0xff) <= 3 || ((e - 0x5b) & 0xff) <= 3 || ((e - 0x65) & 0xff) <= 0x24) {
                later("BattleProc_setSkillEff_Class/Summon/Item (item effect " + e + ")");
            }
        }
    }

    // ---- the hit -------------------------------------------------------------------------------------------------------------------------------

    /** one of the 'common effect' notices over the monster (miss 0x20, block 0x1d, shield 0x19) */
    private static void notice(Char h, Char o, int eff) {
        TEffects.charTargetEffect(h, o, 0, 9, eff, 0, 0, -1, 0, 0, h.m[0x168], Bytes.s32(o.m, 0x20), Bytes.s32(o.m, 0x24), 0);
    }

    /**
     * HERO_attackMonster(mon, dmg0, skill, kind, a7, knock) @0x77f1c: roll the hit, critical and block chances, compute the damage, show it, take the HP and
     * return the damage (-1 when the monster cannot be attacked, 0 on a miss).
     */
    static int attackMonster(Char o, int sb, int skill, int kind, int a7, int knock) {
        Char h = Pool.hero;
        byte[] m = h.m;
        byte[] om = o.m;
        int fp;
        int type;
        int sp34;
        if (kind == 1) {
            int cur = m[0xd0c];
            int slot = skill * 0x28;
            fp = m[0x2a5 + slot] & 0xff;
            type = m[0x2a8 + slot] & 0xff;
            sp34 = (short) m[cur * 0x28 + 0x29a];
        } else {
            later("HERO_attackMonster: arms / class skill kind " + kind);
            return 0;
        }
        if (type == 0x22) {
            later("HERO_attackMonster: type 0x22");
        }
        int s38 = skill & 0xff;
        int s3c = kind & 0xff;
        if (!canAttack(o)) {
            return -1;
        }
        // the hit chance
        boolean hit;
        int idx = type - 0x22;
        int frId;
        if (idx >= 0 && idx <= 0x2b && (idx == 0 || idx == 0x12 || idx == 0x16)) {
            frId = 0x3f7;
        } else if (idx >= 0 && idx <= 0x2b && (idx == 1 || idx == 0x11 || idx == 0x14 || idx == 0x15 || (idx >= 0x17 && idx <= 0x1a))) {
            frId = 0x3f6;
        } else if (idx >= 0 && idx <= 0x2b && (idx == 10 || idx == 14 || idx == 0x13 || idx == 0x1e || idx == 0x2b)) {
            frId = 0x3f5;
        } else {
            frId = -1;
        }
        if (frId < 0) {
            hit = Rng.ratio(0);
        } else {
            hit = Rng.ratio((byte) Fr.calc(frId, h, o, skill, kind, h, o));
        }
        if (!hit) {
            notice(h, o, 0x20);
            return sb;
        }
        // the second table: the block rate source
        int fr2;
        if (idx == 0 || idx == 1 || idx == 0xe || idx == 0x13 || idx == 0x16 || idx == 0x1e || idx == 0x2b) {
            fr2 = fp == 0 ? 0x3ef : 0x3f0;
            fp = Fr.calc(fr2, h, o, skill, kind, h, o) & 0xff;
        } else if (idx == 0x11 || idx == 0x12 || idx == 0x14 || idx == 0x15 || (idx >= 0x17 && idx <= 0x1a)) {
            fr2 = fp == 0 ? 0x3f1 : 0x3f2;
            fp = Fr.calc(fr2, h, o, skill, kind, h, o) & 0xff;
        } else {
            fp = 0;
        }
        // critical
        int crit;
        if (Rng.ratio((byte) Fr.calc(0x3f3, h, o, skill, kind, h, o))) {
            m[0xa2d] = 1;
            m[0xa2e] = 1;
            crit = 1;
        } else {
            crit = 0;
        }
        if (Chars.isFireEnchantBuff(h)) {
            later("HERO_attackMonster: fire enchant");
        }
        if (Chars.isIceEnchantBuff(h)) {
            later("HERO_attackMonster: ice enchant");
        }
        if (om[0x32a] == 0) {
            sb = getSkillDamage(h, o, (byte) s38, (byte) s3c, h, o);
            if (a7 != 0 && sb != 0) {
                later("HERO_attackMonster: a7 branch");
            }
        }
        if (om[0x32a] != 0) {
            later("HERO_attackMonster: monster +0x32a set");
        }
        int sk = (byte) s38;
        int kd = (byte) s3c;
        if (sb < 1) {
            sb = 1;
        }
        Bytes.w32(m, 0xf0, sb);
        if (m[0xa2d] != 0) {
            sb = Fr.calc(0x3ec, h, o, sk, kd, h, o);
        }
        Bytes.w32(m, 0xf0, sb);
        int blk = (Bytes.s32(om, 0x3f0) & 0x00ffff00) != 0 ? 0x64 : (byte) fp;
        if (Rng.ratio(blk)) {
            notice(h, o, 0x1d);
            sb = Fr.calc(0x3e9, h, o, sk, kd, h, o);
        }
        int refl = 0;
        if (Chars.isReflectBuff(h)) {
            refl = Fr.calc(0x3f9, h, o, sk, kd, h, o) & 0xffff;
        }
        if (m[0x16b] != 0) {
            later("HERO_attackMonster: class " + m[0x16b]);
        }
        if (m[0xd0c] == 1) {
            later("HERO_attackMonster: skill 1");
        }
        MonBattle.initMonsterInfo(o, 0x5a, 0);
        if (sb > 0) {
            TEffects.addEffect(o, crit != 0 ? 1 : 0, 0, sb);
            heroAddSp(sp34);
        }
        if (knock > 0) {
            if (MonDamage.isImmune(o, 1) == 1) {
                om[0x176] = 1;
                knock = 0;
            }
        }
        MonDamage.addHp(o, -sb, knock, 1, 0);
        if ((short) refl > 0) {
            TEffects.addEffect(h, 4, 0, (short) refl);
            MonBattle.heroAddHp(-(short) refl);
        }
        Bytes.w16(m, 0xc6, Fr.calc(0x3ee, h, o, sk, kd, h, o));
        return sb;
    }

    private static final int[] RECT = new int[4];

    /**
     * HERO_attackMonsterProcess(h, mon, motion, skill, rect, a7) @0x79054: the hit on one monster: damage, knock back, target effects, shake, item and skill
     * effects.  Returns the damage (> 0) when the hit counts.
     */
    static int attackMonsterProcess(Char h, Char o, int motion, int skill, int[] r, int a7) {
        byte[] m = h.m;
        byte[] om = o.m;
        int effIdx = getEffectIdx(h, skill);
        int dirv = HeroCtl.isSkipDir(h, effIdx) ? 0 : m[0x168];
        if (om[0x3f1] != 0) {
            Bytes.w32(om, 0x3f4, Bytes.s32(om, 0x3f4) + 0x64);
            if (Bytes.s32(om, 0x3f4) >= Bytes.s32(om, 0xa8) / 5) {
                om[0x3f0] = 1;
                Bytes.w32(om, 0x3f4, 0);
                om[0x3f1] = 0;
            }
        }
        if (om[0x3f2] == 1) {
            Bytes.w32(om, 0x3f4, Bytes.s32(om, 0x3f4) + 0x64);
            if (Bytes.s32(om, 0x3f4) >= Bytes.s32(om, 0xa8) / 5) {
                om[0x3f8] = (byte) 0x96;
                Bytes.w32(om, 0x3f4, 0);
                om[0x3f2] = 0;
                om[0x8d] = 0;
            }
        }
        if (m[0x283] == 0) {
            m[0x284] = 0;
        }
        boolean alt = a7 != 0 || skill == 0x10;
        int kind = alt ? 2 : 1;
        int sk = alt ? 0 : skill;
        int res = attackMonster(o, 0, sk, kind, a7, m[0x284]);
        if (res <= 0) {
            return res;
        }
        int fx = r[0] + (r[2] >> 1);
        int fy = r[1] + (r[3] >> 1);
        if (m[0x283] != 0) {
            if (MonDamage.isImmune(o, 1) == 1) {
                om[0x176] = 1;
            } else {
                MonDamage.knockBack(o, m[0x284], m[0x168]);
            }
        } else if (om[0x175] != 0) {
            if (MonDamage.isImmune(o, 1) == 1) {
                om[0x176] = 1;
            } else {
                MonDamage.knockBack(o, om[0x175], m[0x168]);
            }
        } else {
            MonDamage.knockBack(o, 0, m[0x168]);
        }
        int cls = m[0x16b];
        if (sk == 0 && cls == 3) {
            later("HERO_addTargetEffectIceBoltAttacked");
        } else if (cls == 3 && (sk == 6 || sk == 4)) {
            later("HERO_addTargetEffectFireAttacked");
        }
        if (m[0xa2d] != 0 && Opt.v[5] == 1) {
            // a critical with the 'critical' option shows the common critical effect INSTEAD of the skill's hit effect (0x791bc -> 0x7957c -> 0x79224)
            TEffects.addCommonEffect(h, o, 0, 0, 0, 0, 0, -1);
        } else {
            if (isBasicTargetEff(h, sk)) {
                dirv = m[0xd12];
            }
            TEffects.charTargetEffect(h, o, 0, 0xa, getEffectIdx(h, sk), dirv, 1, -1, sk, -1, 0, fx, fy, 1);
        }
        int c = m[0xd12] & 0xff;
        int rem = (byte) c % 4;
        m[0xd12] = (byte) (c + 1);
        int ef = (om[0x16b] & 1) != 0 ? 0x12 : 0x11;
        TEffects.charTargetEffect(h, o, 0, 9, ef, rem, 1, -1, sk, -1, m[0x168], fx, fy, 1);
        if (m[0xa2d] != 0) {
            Cam.setShake(3, 3, Rng.random() & 1);
        } else {
            Cam.setShake(1, 1, Rng.random() & 1);
        }
        m[0xa2d] = 0;
        if (!Monsters.isDie(o)) {
            for (int i = 0; i < 7; i++) {
                if (G.equip[i] != null) {
                    setItemEff(h, G.equip[i]);
                }
            }
            setSkillEff(h, o, sk, kind, h, o);
        }
        if (om[0x176] != 0) {
            notice(h, o, 0x19);
            om[0x176] = 0;
            if (m[0x16b] == 0 && m[0xd0c] == 1) {
                res = 0;
            }
        }
        return res;
    }
}
