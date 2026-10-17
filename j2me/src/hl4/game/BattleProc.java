package hl4.game;

import hl4.sys.Bytes;

/** Skill/item stat effects (BattleProc_* @0xdc110..0xdcf54): which stat a skill/option kind adds to. */
public final class BattleProc {
    private BattleProc() {
    }

    private static void addS16(byte[] m, int off, int v) {
        Bytes.w16(m, off, Bytes.s16(m, off) + v);
    }

    private static void addS8(byte[] m, int off, int v) {
        m[off] = (byte) (m[off] + v);
    }

    /** BattleProc_getSkillEffValue @0xdc110: the 4 effect kinds / values / secondary values of a skill into out arrays (4 bytes each) */
    public static void getSkillEffValue(Obj self, byte[] kinds, byte[] v1, byte[] v2, int skill, int infoType) {
        Obj hero = Pool.hero;
        Obj sm = Pool.summon;
        int t = Obj.structType(self);
        if (t == 2) {
            if (infoType == 1) {
                int k = skill * 0x28;
                Bytes.copy(hero.m, k + 0x2a8, kinds, 0, 4);
                Bytes.copy(hero.m, k + 0x2ac, v1, 0, 4);
                Bytes.copy(hero.m, k + 0x2b4, v2, 0, 4);
            } else if (infoType == 2) {
                int k = skill * 0x1a + 0x618;
                Bytes.copy(hero.m, k + 5, kinds, 0, 4);
                Bytes.copy(hero.m, k + 10, v1, 0, 4);
                Bytes.copy(hero.m, skill * 0x1a + 0x62a, v2, 0, 4);
            } else if (infoType == 3) {
                int k = skill * 0x1c + 0x2d0;
                Bytes.copy(sm.m, k + 6, kinds, 0, 4);
                Bytes.copy(sm.m, k + 10, v1, 0, 4);
                Bytes.copy(sm.m, skill * 0x1c + 0x2e2, v2, 0, 4);
            }
        } else if (t != 3 && t == 4) {
            int k = skill * 0x1c + 0x2d0;
            Bytes.copy(sm.m, k + 6, kinds, 0, 4);
            Bytes.copy(sm.m, k + 10, v1, 0, 4);
            Bytes.copy(sm.m, skill * 0x1c + 0x2e2, v2, 0, 4);
        }
    }

    private static boolean validKind(int k) {
        int u = (k - 1) & 0xff;
        boolean b = 0x4f < k;
        if (k != 0x50) {
            b = 0x1d < u;
        }
        return !b || k == 0x50 || u == 0x1e;
    }

    private static final byte[] KINDS = new byte[4];
    private static final byte[] V1 = new byte[4];
    private static final byte[] V2 = new byte[4];

    /** BattleProc_setSkillPassive(self, target, skill, infoType, hero, p6) @0xdcf54 */
    public static int setSkillPassive(Obj self, Obj tgt, int skill, int infoType, Obj hero, int p6) {
        getSkillEffValue(self, KINDS, V1, V2, skill, infoType);
        int n = Obj.structType(self) == 3 ? 1 : 4;
        int last = -1;
        for (int i = 0; i < n; i++) {
            if (KINDS[i] == 0) {
                continue;
            }
            int k;
            int a = 0;
            int b = 0;
            if (Obj.structType(self) == 2) {
                a = V1[i];
                b = V2[i];
                k = KINDS[i] & 0xff;
                hero.m[0xd0d] = (byte) i;
            } else if (Obj.structType(self) == 3) {
                throw new IllegalStateException("BattleProc_setSkillPassive(monster): later");
            } else {
                k = 0;
            }
            if (k != 0 && (validKind(k) || k == 0x5a || k == 0x57 || k == 0x31)) {
                last = statBN(self, tgt, k, a, b, skill, infoType, hero, p6, 0);
            }
        }
        return last;
    }

    /** BattleProc_setItemPassive(self, target, hero, item, flag) @0xdcc50 */
    public static int setItemPassive(Obj self, Obj tgt, Obj hero, Obj item, int flag) {
        byte[] im = item.m;
        int last = -1;
        for (int i = 0; i < 5; i++) {
            boolean skip = i == 2;
            if (i < 3) {
                skip = im[0x215 + i] == 0;
            }
            if (skip || (im[0x16e] == 0 && i == 3)) {
                continue;
            }
            if (Obj.structType(self) != 2) {
                throw new IllegalStateException("BattleProc_setItemPassive(non-hero): later");
            }
            hero.m[0xd0d] = (byte) i;
            int k;
            int a;
            int b;
            if (i < 3) {
                a = Bytes.s16(im, 0x7c + 2 * i) & 0xffff;
                k = im[0x215 + i] & 0xff;
                b = im[0x79 + i];
            } else if (i != 3) {
                k = im[0x213] & 0xff;
                a = 3;
                b = 0;
            } else {
                switch (im[0x16e]) {
                    case 3:
                        k = 0x12;
                        im[0x15b] = 5;
                        im[0x15a] = 0x12;
                        break;
                    case 4:
                        k = 0x13;
                        im[0x15b] = 5;
                        im[0x15a] = 0x13;
                        break;
                    case 5:
                        k = 0x16;
                        im[0x15b] = 5;
                        im[0x15a] = 0x16;
                        break;
                    case 6:
                        k = 0x18;
                        im[0x15b] = 5;
                        im[0x15a] = 0x18;
                        break;
                    case 7:
                        k = 2;
                        im[0x15b] = 3;
                        im[0x15a] = 2;
                        break;
                    case 8:
                        k = 3;
                        im[0x15a] = 3;
                        im[0x15b] = 3;
                        break;
                    case 9:
                    case 10:
                        k = 5;
                        im[0x15b] = 3;
                        im[0x15a] = 5;
                        break;
                    case 0xb:
                        k = 0x1e;
                        im[0x15b] = 5;
                        im[0x15a] = 0x1e;
                        break;
                    case 0xc:
                        k = 0x1b;
                        im[0x15b] = 2;
                        im[0x15a] = 0x1b;
                        break;
                    default:
                        k = 0;
                        im[0x15b] = 0;
                        break;
                }
                b = 0;
                int t = (im[0x15b] & 0xff) * im[0x16f];
                t = im[0x16f] * t + t;
                a = ((t - (t >> 31)) * 0x8000) >>> 16;
            }
            if (k != 0 && (validKind(k) || k == 0x57 || k == 0x5a) && (flag == 0 || k != 0x57)) {
                last = statBN(self, tgt, k, (short) a, b, 0, 0, hero, 0, 1);
            }
        }
        return last;
    }

    /**
     * BattleProc_setSkillEff_StatBN(self, target, kind, a, b, skillIdx, infoType, hero, p9, p10) @0xdc648: adds a stat bonus.  The value is
     * (short) a when p10 != 0, otherwise the result of the stat formula for this skill; kind selects the stat of 'tgt' or 'hero' it adds to.
     */
    public static int statBN(Obj self, Obj tgt, int kind, int a, int b, int skillIdx, int infoType, Obj hero, int p9, int p10) {
        int v;
        if (p10 != 0) {
            v = (short) a;
        } else {
            int id;
            int t = Obj.structType(self);
            if (t == 2) {
                id = 0xbbc;
            } else if (t == 4) {
                id = 0x138b;
            } else {
                if (t == 3) {
                    throw new IllegalStateException("StatBN(monster): later");
                }
                id = 0;
            }
            v = id == 0 ? -1 : (short) Fr.calc(id, self, null, (byte) skillIdx, (byte) infoType, hero, null);
        }
        int c = (byte) v;
        byte[] t = tgt == null ? null : tgt.m;
        byte[] h = hero == null ? null : hero.m;
        switch (kind) {
            case 1:
                addS16(h, 0x1b0, v);
                break;
            case 2:
                addS16(t, 0xce, v);
                break;
            case 3:
                addS8(t, 0xd2, c);
                break;
            case 4:
                addS8(t, 0xcc, c);
                break;
            case 5:
                addS16(t, 200, v);
                break;
            case 6:
                addS16(h, 0x1b4, v);
                break;
            case 7:
                addS16(h, 0x1b8, v);
                break;
            case 8:
                addS16(h, 0x1bc, v);
                break;
            case 9:
                addS16(h, 0x1c0, v);
                break;
            case 10:
                addS16(h, 0x1c2, v);
                break;
            case 0xb:
                addS16(h, 0x1c6, v);
                break;
            case 0xc:
                addS16(t, 0xe8, v);
                break;
            case 0xd:
                addS16(h, 0x1de, v);
                break;
            case 0xe:
                addS16(t, 0xee, v);
                break;
            case 0xf:
                addS16(t, 0xd6, v);
                break;
            case 0x10:
                addS16(t, 0xe2, v);
                break;
            case 0x11:
                addS16(t, 0xda, v);
                break;
            case 0x12:
                addS16(t, 0xac, v);
                break;
            case 0x13:
                addS16(t, 0xc4, v);
                break;
            case 0x14:
                addS16(t, 0xb8, v);
                break;
            case 0x15:
                addS16(t, 0xb0, v);
                break;
            case 0x16:
                addS16(t, 0xbc, v);
                break;
            case 0x17:
                addS16(t, 0xbe, v);
                break;
            case 0x18:
                addS16(t, 0xb4, v);
                break;
            case 0x19:
                addS16(t, 0xc0, v);
                break;
            case 0x1a:
                addS16(h, 0x1ca, v);
                break;
            case 0x1b:
                addS16(h, 0x1cc, v);
                break;
            case 0x1c:
                addS8(h, 0x1e8, c);
                break;
            case 0x1d:
                addS16(h, 0x1dc, v);
                break;
            case 0x1e:
                addS16(t, 0xde, v);
                break;
            case 0x1f:
                addS16(h, 0x1d0, v);
                break;
            case 0x31:
                addS16(h, 0xd4c, v);
                break;
            case 0x50:
                addS16(h, 0x1d2, v);
                break;
            case 0x57:
                addS8(h, 0xd38, c);
                break;
            case 0x5a:
                addS16(t, 0xd6, v);
                addS16(t, 0xee, v);
                break;
            default:
                break;
        }
        return 1;
    }

    // ---- BattleProc_setSetItemPassive / BattleProc_setSkillEff_* (real addresses 0xccb40, 0xcb16c..0xcb184, 0xcd0e4) -----------------------------

    /**
     * BattleProc_setSetItemPassive(self, tgt, hero, item) @0xccb40: the five set-bonus options of the first equipped item (kinds +0x233.., values
     * +0x23e.. (16 bit), second values +0x238..) as passive stat bonuses; returns the result of the last one applied (-1 for none)
     */
    public static int setSetItemPassive(Obj self, Obj tgt, Obj hero, Obj item) {
        byte[] im = item.m;
        int last = -1;
        for (int i = 0; i < 5; i++) {
            if (im[0x233 + i] == 0) {
                continue;
            }
            if (Obj.structType(self) != 2) {
                throw new IllegalStateException("BattleProc_setSetItemPassive: attacker is not the hero: later");
            }
            hero.m[0xd0d] = (byte) i;
            int a = (short) Bytes.u16(im, 0x23e + 2 * i);
            int k = im[0x233 + i] & 0xff;
            int b = im[0x238 + i];
            if (k == 0x50 || ((k - 1) & 0xff) <= 0x1e || k == 0x5a || k == 0x57) {
                last = statBN(self, tgt, k, a, b, 0, 0, hero, 0, 1);
            }
        }
        return last;
    }

    /** BattleProc_setSkillEff_Magical(self, tgt, kind, val, b, skill, infoType, hero, mon) @0xcb16c: an empty function in the original (the caller drops the result) */
    static int setSkillEffMagical(Obj self, Obj tgt, int kind, int val, int b, int skill, int infoType, Obj hero, Obj mon) {
        return 0;
    }

    /** BattleProc_setSkillEff_Class(self, tgt, kind, val, b, skill, infoType, hero, mon) @0xcb174: an empty function in the original (returns 0) */
    static int setSkillEffClass(Obj self, Obj tgt, int kind, int val, int b, int skill, int infoType, Obj hero, Obj mon) {
        return 0;
    }

    /** BattleProc_setSkillEff_Summon(self, tgt, kind, val, b, skill, infoType, hero, mon) @0xcb17c: an empty function in the original (returns 0) */
    static int setSkillEffSummon(Obj self, Obj tgt, int kind, int val, int b, int skill, int infoType, Obj hero, Obj mon) {
        return 0;
    }

    /** BattleProc_setSkillEff_Item(self, tgt, kind, val, b, skill, infoType, hero, mon) @0xcb184: an empty function in the original (returns 0) */
    static int setSkillEffItem(Obj self, Obj tgt, int kind, int val, int b, int skill, int infoType, Obj hero, Obj mon) {
        return 0;
    }

    /** v / 2 of a 16-bit value, rounded towards 0 (the immune halving of a status value) */
    private static int half(int v) {
        v = (short) v;
        return (short) ((v + (v < 0 ? 1 : 0)) >> 1);
    }

    /** the effect id derived from the class of the target: class / 2 (towards 0) + add, one byte */
    private static int clsEff(Obj tgt, int add) {
        int c = tgt.m[0x16b];
        return (((c + (c < 0 ? 1 : 0)) >> 1) + add) & 0xff;
    }

    /**
     * BattleProc_setSkillEff_Etc(self, tgt, kind, val, b, skill, infoType, hero, mon, flag) @0xcd0e4: the status / heal / buff kinds 0x3d..0x5f of a
     * skill (flag 0) or of an item option (flag 1: the values come from 'val' instead of the formulas).  Every case picks the effect id (eff, 0xff = none),
     * whether it is a common effect (+0x13c list, kind 9) or a target effect of kind 7, and the value; the common tail at the end shows it.
     * Returns 1 for the kinds 0x3f, 0x40, 0x42, 0x47, 0x49, 0x4a, 0x4d, 0x56, else 0.
     * Original quirk: for an attacker that is neither hero, monster nor summon the effect owner type is an uninitialised stack variable (here 0).
     */
    static int setSkillEffEtc(Char self, Char tgt, int kind, int val, int b, int skill, int infoType, Obj hero, Obj mon, int flag) {
        skill = (byte) skill;
        infoType = (byte) infoType;
        flag &= 0xff;
        int x = Chars.getX(tgt) & 0xffff;
        int y = Chars.getY(tgt) & 0xffff;
        int st = Obj.structType(self);
        int tt = Obj.structType(tgt);
        int body = st == 3 ? 1 : (st == 4 ? 2 : 0);
        int ret = 0;                // r4: the return value
        int eff = 0xff;             // fp: the effect id (0xff: none)
        int f1 = 0;                 // r1: shows at the top pivot of the target
        int f2 = 0;                 // r2: ditto
        int v = 0;                  // [sp+0x40]: the value (16 bit)
        boolean common = true;      // r7: common effect (kind 9) instead of a target effect (kind 7)
        boolean decide = true;      // false: the life flag is 1 without looking at f1 / f2
        boolean derive = false;     // f1 / f2 follow from eff
        int life = 0;               // [sp+0x44]
        switch (kind) {
            case 0x3d: {
                if (st == 2) {
                    Chars.addHp(tgt, Fr.calc(0xbbc, self, tgt, skill, infoType, hero, mon));
                } else if (st == 3) {
                    Chars.addHp(tgt, Fr.calc(0x7d3, self, tgt, skill, infoType, hero, mon));
                } else if (st == 4) {
                    Chars.addHp(tgt, Fr.calc(0x138b, self, tgt, skill, infoType, hero, mon));
                }
                TEffects.addCommonEffect(self, tgt, (byte) body, 0x1e, 0, 0, 0, -1);
                break;
            }
            case 0x3e: {
                int id = st == 2 ? 0xbbc : (st == 3 ? 0x7d3 : (st == 4 ? 0x138b : 0));
                if (id != 0) {
                    int mp = Bytes.s32(hero.m, 0x21c);
                    HeroBattle.heroAddSp(mp * Fr.calc(id, self, tgt, skill, infoType, hero, mon) / 100);
                }
                TEffects.addCommonEffect(self, tgt, (byte) body, 0x1f, 0, 0, 0, -1);
                break;
            }
            case 0x3f:
            case 0x40: {
                if (body != 0) {
                    return 0;
                }
                v = (short) Fr.calc(0xfaf, self, tgt, skill, infoType, hero, mon);
                Chars.removeCommonEffect(tgt, 9, kind - 0x3e);
                Chars.addBuff(self, tgt, v, skill, infoType);
                ret = 1;
                eff = kind - 0x3e;
                break;
            }
            case 0x42: {
                int cnt = tgt.m[0x12c];
                if (cnt > 0) {
                    Chars.removeBuff(tgt, (byte) (cnt - 1), 0);
                    ret = 1;
                }
                break;
            }
            case 0x44: {
                if (flag == 0) {
                    tgt.m[0x175] = (byte) (Fr.calc(0xbbc, self, tgt, skill, infoType, hero, mon) << 4);
                } else {
                    tgt.m[0x175] = (byte) (val << 4);
                }
                break;
            }
            case 0x47: {
                if (st != 2) {
                    return 0;
                }
                v = (short) Fr.calc(0xfb4, self, tgt, skill, infoType, hero, mon);
                Chars.removeTargetEffGood(tgt, 7);
                Chars.addEtcStatus(self, tgt, 0, 1, val, v);
                int s = Fr.calc(0xfb0, self, tgt, skill, infoType, hero, mon) & 0xffff;
                byte[] hm = hero.m;
                Bytes.w16(hm, 0x1e0, s);
                int a = Bytes.u16(hm, 0xb0) + Bytes.u16(hm, 0x1e0);
                int c = Bytes.u16(hm, 0xce) + s;
                Bytes.w16(hm, 0xb0, a);
                Bytes.w16(hm, 0xce, c);
                eff = 0x21;
                ret = 1;
                common = false;
                decide = false;
                break;
            }
            case 0x48:
            case 0x49:
            case 0x4a: {
                if (st != 2) {
                    return 0;
                }
                if (kind == 0x4a) {
                    Chars.removeTargetEffGood(tgt, 7);
                    Chars.addEtcStatus(self, tgt, 0, 4, val, -0x8000);
                    v = -0x8000;
                    eff = 0x1e;
                    ret = 1;
                } else {
                    v = (short) Fr.calc(kind == 0x48 ? 0xfb4 : 0xfb2, self, tgt, skill, infoType, hero, mon);
                    Chars.removeTargetEffGood(tgt, 7);
                    Chars.addEtcStatus(self, tgt, 0, kind - 0x46, val, v);
                    eff = kind == 0x48 ? 0x1a : 0x1b;
                    ret = kind == 0x48 ? 0 : 1;
                }
                common = false;
                decide = false;
                break;
            }
            case 0x4b: {
                v = flag == 0 ? (short) Fr.calc(0xfb4, self, tgt, skill, infoType, hero, mon) : (short) ((short) val * 0x14 + 0x1e);
                if (tt == 3 && MonDamage.isImmune(mon, 2) == 1) {
                    mon.m[0x176] = 1;
                    break;
                }
                Chars.removeTargetEffBad(tgt, 9);
                Chars.addEtcStatus(self, tgt, 1, 1, val, v);
                eff = 0xc;
                break;
            }
            case 0x4c: {
                v = flag == 0 ? (short) Fr.calc(0xfb5, self, tgt, skill, infoType, hero, mon) : (short) (val * 2 + 10);
                if (tt == 3) {
                    if (MonDamage.isImmune(mon, 3) == 2) {
                        v = half(v);
                        Chars.removeCommonEffect(tgt, 9, 10);
                        Chars.addEtcStatus(self, tgt, 3, 1, val, v);
                        mon.m[0x176] = 1;
                        eff = 10;
                        f1 = 1;
                        break;
                    }
                    if (MonDamage.isImmune(mon, 3) == 1) {                // the original asks again
                        mon.m[0x176] = 1;
                        break;
                    }
                }
                Chars.removeCommonEffect(tgt, 9, 10);
                Chars.addEtcStatus(self, tgt, 3, 1, val, v);
                eff = 10;
                f1 = 1;
                break;
            }
            case 0x4d: {
                v = (short) Fr.calc(0xfb4, self, tgt, skill, infoType, hero, mon);
                int p = (short) Fr.calc(0xbbc, self, tgt, skill, infoType, hero, mon);
                Chars.removeTargetEffBad(tgt, 9);
                Chars.addEtcStatus(self, tgt, 1, 3, p, v);
                eff = 0xf;
                ret = 1;
                break;
            }
            case 0x56: {
                int p = val;
                if (flag == 0) {
                    v = (short) Fr.calc(0xfb4, self, tgt, skill, infoType, hero, mon);
                    p = (short) Fr.calc(0x138b, self, tgt, skill, infoType, hero, mon);
                } else {
                    v = (short) ((short) val * 0x14 + 0x1e);
                }
                Chars.removeTargetEffBad(tgt, 9);
                Chars.addEtcStatus(self, tgt, 1, 3, p, v);
                eff = 0xf;
                ret = 1;
                break;
            }
            case 0x4e: {
                int r5;
                if (flag == 0) {
                    v = (short) Fr.calc(0xfb4, self, tgt, skill, infoType, hero, mon);
                    r5 = (short) Fr.calc(0xfa4, self, tgt, skill, infoType, hero, mon);
                } else {
                    v = (short) (val * 0x14 + 0x1e);
                    r5 = (short) (val * 6);
                }
                derive = true;
                if (tt == 3) {
                    boolean im2 = MonDamage.isImmune(mon, 4) == 2;      // (unlike the others this kind has no 'immune' = 1 branch)
                    if (im2) {
                        v = half(v);
                    }
                    Chars.removeTargetEffMagic(tgt, 9);
                    if (Chars.addEtcStatus(self, tgt, 2, 1, r5, v) != 0) {
                        eff = clsEff(tgt, 4);
                    }
                    if (im2) {
                        mon.m[0x176] = 1;
                    }
                    break;
                }
                Chars.removeTargetEffMagic(tgt, 9);
                if (Chars.addEtcStatus(self, tgt, 2, 1, r5, v) != 0 && !(tt == 2 && st == 2)) {
                    eff = clsEff(tgt, 4);
                }
                break;
            }
            case 0x4f: {
                v = flag == 0 ? (short) Fr.calc(0xfb3, self, tgt, skill, infoType, hero, mon) : (short) ((short) val * 5 + 0x14);
                derive = true;
                int mode;                       // 0: plain (hero vs hero refused), 1: infoType 2, 2: plain on a monster
                boolean mark = false;
                if (tt != 3) {
                    mode = infoType == 2 ? 1 : 0;
                } else {
                    // the original asks MONSTER_isImmune again for each comparison: 2, 1, 3, 4
                    boolean im2 = MonDamage.isImmune(mon, 5) == 2;
                    boolean im1 = !im2 && MonDamage.isImmune(mon, 5) == 1;
                    boolean im3 = !im2 && !im1 && MonDamage.isImmune(mon, 5) == 3;
                    boolean im4 = !im2 && !im1 && !im3 && MonDamage.isImmune(mon, 5) == 4;
                    if (im1) {
                        mon.m[0x176] = 1;
                        break;
                    }
                    if (im3 || im4) {
                        if (im4) {
                            v = half(v);
                        }
                        if (infoType == 2) {
                            int sc = self.m[0x16b] & 0xff;
                            eff = sc == 0 || sc == 1 ? 0x28 : 0xff;
                            common = false;
                            decide = false;
                        } else {
                            Chars.removeTargetEffMagic(tgt, 9);
                            eff = clsEff(tgt, 7);
                        }
                        mon.m[0x176] = 1;
                        break;
                    }
                    if (im2) {
                        v = half(v);
                        mark = true;
                    }
                    mode = infoType == 2 ? 1 : 2;
                }
                if (mode == 1) {
                    int rr = Chars.addEtcStatus(self, tgt, infoType, infoType, val, v);
                    int sc = self.m[0x16b] & 0xff;
                    eff = rr != 0 && (sc == 0 || sc == 1) ? 0x28 : 0xff;
                    common = false;
                    decide = false;
                } else {
                    Chars.removeTargetEffMagic(tgt, 9);
                    int rr = Chars.addEtcStatus(self, tgt, 2, 2, val, v);
                    if (rr != 0 && !(mode == 0 && tt == 2 && st == 2)) {
                        eff = clsEff(tgt, 7);
                    }
                }
                if (mark) {
                    mon.m[0x176] = 1;
                }
                break;
            }
            case 0x55:
            case 0x58: {
                int r5;
                if (kind == 0x58 || flag == 0) {
                    v = (short) Fr.calc(0xfb4, self, tgt, skill, infoType, hero, mon);
                    r5 = (short) Fr.calc(0xfa8, self, tgt, skill, infoType, hero, mon);
                } else {
                    v = (short) (val * 0x14 + 0x1e);
                    r5 = (short) (val * 6);
                }
                int type = kind == 0x55 ? 2 : 4;
                if (tt == 3) {
                    boolean im2 = MonDamage.isImmune(mon, 6) == 2;
                    if (!im2 && MonDamage.isImmune(mon, 6) == 1) {         // the original asks again
                        mon.m[0x176] = 1;
                        break;
                    }
                    if (im2) {
                        v = half(v);
                        Chars.removeTargetEffBad(tgt, 9);
                        Chars.addEtcStatus(self, tgt, 1, type, r5, v);
                        mon.m[0x176] = 1;
                        eff = kind == 0x55 ? 0xd : 0xe;
                        f2 = kind == 0x55 ? 1 : 0;
                        break;
                    }
                }
                Chars.removeTargetEffBad(tgt, 9);
                Chars.addEtcStatus(self, tgt, 1, type, r5, v);
                eff = kind == 0x55 ? 0xd : 0xe;
                f2 = kind == 0x55 ? 1 : 0;
                break;
            }
            case 0x5f: {
                if (st == 2) {
                    Chars.addHp(self, Fr.calc(0xbbc, self, tgt, skill, infoType, hero, mon));
                } else if (st == 3) {
                    Chars.addHp(self, Fr.calc(0x7d3, self, tgt, skill, infoType, hero, mon));
                } else if (st == 4) {
                    Chars.addHp(self, Fr.calc(0x138b, self, tgt, skill, infoType, hero, mon));
                }
                TEffects.addCommonEffect(self, self, (byte) body, 0x1e, 0, 0, 0, -1);
                break;
            }
            default:
                break;
        }
        if (derive) {
            f1 = eff == 10 || eff == 11 ? 1 : 0;
            f2 = eff == 0xd ? 1 : 0;
        }
        if (decide) {
            if (((f1 | f2) & 0xff) == 0 && eff != 0xe) {
                life = 1;
            } else {
                x = Bytes.s32(tgt.m, 0x20) & 0xffff;        // CHAR_getTopPivotX / Y
                y = Bytes.s32(tgt.m, 0x24) & 0xffff;
                life = 0;
            }
        } else {
            life = 1;
        }
        if (eff == 0xff) {
            return ret;
        }
        int e = (byte) eff;
        if (common) {
            if (tt == 2) {
                if (Chars.isFireEnchantBuff(tgt) && skill == 7) {
                    Pool.hero.m[0xd13] = 1;
                } else if (Chars.isIceEnchantBuff(tgt) && skill == 8) {
                    Pool.hero.m[0xd13] = 2;
                }
            }
            Chars.removeCommonEffect(tgt, 9, e);
            TEffects.addCommonEffect2(self, tgt, (byte) body, e, (short) x, (short) y, v, life);
        } else {
            Chars.removeTargetEffect(tgt, 7, e);
            TEffects.charTargetEffect(self, tgt, (byte) body, 7, e, 0, -1, v, 0, 0, 0, (short) x, (short) y, life);
            if (eff == 0x1b) {
                Chars.removeCommonEffect(tgt, 9, 1);
                Chars.addBuff(self, tgt, v, skill, infoType);
            }
        }
        return ret;
    }
}
