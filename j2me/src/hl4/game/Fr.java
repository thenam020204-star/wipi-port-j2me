package hl4.game;

import hl4.sys.Bytes;
import hl4.sys.Rng;
import hl4.sys.Res;

/**
 * The battle/stat formula engine (FR_* @0x6ff6c..0x714d8).  A formula is a postfix program of 5-byte tokens {kind, s32 value}:
 * kind 0 = constant, 0xc = value-function id (FR_GetValFunc, evaluated lazily when an operator reads it), 0x11..0x16 = + - * / % ^.
 * A formula is selected by its id: file = (id - 1000) / 1000 (0 battle, 1 enemy, 2 player, 3 skill, 4 summon), record = (id - 1000) % 1000;
 * the record is {?, ?, min s32, max s32, nTokens, tokens...}; the result is clamped to [min, max].
 * Evaluation order (top operand first, then the second) matters: value functions may consume random numbers.
 */
public final class Fr {
    private static byte[] battle;
    private static byte[] player;
    private static byte[] enemy;
    private static byte[] summon;
    private static byte[] skill;
    /** _frSkillInfo_ID: the 0x28-byte skill-info block the 0x50..0x57 value functions read */
    public static final byte[] skillInfo = new byte[0x28];
    public static int skillIdx;
    public static int skillInfoType;

    private Fr() {
    }

    /** FR_Load @0x70100 */
    public static void load() {
        battle = Res.loadDes("/FR/_FR_BA");
        player = Res.loadDes("/FR/_FR_PL");
        enemy = Res.loadDes("/FR/_FR_EN");
        summon = Res.loadDes("/FR/_FR_SU");
        skill = Res.loadDes("/FR/_FR_SK");
    }

    /** FR_Unload @0x6fff8 */
    public static void unload() {
        battle = null;
        player = null;
        enemy = null;
        summon = null;
        skill = null;
    }

    private static int s16(Obj o, int off) {
        return Bytes.s16(o.m, off);
    }

    private static int s8(Obj o, int off) {
        return o.m[off];
    }

    private static int u8(Obj o, int off) {
        return o.m[off] & 0xff;
    }

    private static int s32(Obj o, int off) {
        return Bytes.s32(o.m, off);
    }

    private static int s16(byte[] b, int off) {
        return Bytes.s16(b, off);
    }

    /**
     * FR_Calc @0x7048c: id, self, target, skill index, skill-info type (0 none, 1 hero skill, 2 summon arms skill, 3 summon skill),
     * 'hero' (param_6: the struct skill info is read from) and 'mon' (param_7).
     */
    public static int calc(int id, Obj self, Obj tgt, int sIdx, int infoType, Obj hero, Obj mon) {
        skillIdx = (byte) sIdx;
        skillInfoType = (byte) infoType;
        Obj sm = Pool.summon;
        if (infoType == 0) {
            Bytes.fill(skillInfo, 0, 0, 0x28);
        } else if (infoType == 1) {
            Bytes.copy(hero.m, sIdx * 0x28 + 0x294, skillInfo, 0, 0x28);
        } else if (infoType == 2) {
            Bytes.fill(skillInfo, 0, 0, 0x28);
            int k = sIdx * 0x1a;
            Bytes.w16(skillInfo, 4, s16(hero, k + 0x614));
            Bytes.w16(skillInfo, 6, s16(hero, k + 0x616));
            Bytes.w16(skillInfo, 10, s16(hero, k + 0x61a));
            Bytes.w16(skillInfo, 0xc, s16(hero, k + 0x61c));
            Bytes.copy(hero.m, k + 0x618 + 5, skillInfo, 0x14, 4);
            Bytes.copy(hero.m, k + 0x618 + 10, skillInfo, 0x18, 8);
            Bytes.copy(hero.m, k + 0x62a, skillInfo, 0x20, 4);
        } else if (infoType == 3) {
            Bytes.fill(skillInfo, 0, 0, 0x28);
            int k = sIdx * 0x1c;
            Bytes.w16(skillInfo, 4, s16(sm, k + 0x2cc));
            Bytes.w16(skillInfo, 6, s16(sm, k + 0x2ce));
            Bytes.w16(skillInfo, 10, s16(sm, k + 0x2d2));
            Bytes.w16(skillInfo, 0xc, s16(sm, k + 0x2d4));
            skillInfo[0x24] = sm.m[k + 0x2e6];
            Bytes.copy(sm.m, k + 0x2d0 + 6, skillInfo, 0x14, 4);
            Bytes.copy(sm.m, k + 0x2d0 + 10, skillInfo, 0x18, 8);
            Bytes.copy(sm.m, k + 0x2e2, skillInfo, 0x20, 4);
        }
        byte[] f;
        switch ((id - 1000) / 1000) {
            case 0:
                f = battle;
                break;
            case 1:
                f = enemy;
                break;
            case 2:
                f = player;
                break;
            case 3:
                f = skill;
                break;
            default:
                f = summon;
                break;
        }
        return run(f, (id - 1000) % 1000, self, tgt, hero, mon);
    }

    /** the shared evaluator of FR_Calc / FR_CalcByFormula (record k of file f) */
    private static int run(byte[] f, int k, Obj self, Obj tgt, Obj hero, Obj mon) {
        int pMin;
        int pMax;
        int pCnt;
        int pTok;
        if (k < 1) {
            pMin = 5;
            pMax = 9;
            pCnt = 0xd;
            pTok = 0xe;
        } else {
            int p = 3;
            for (int i = 0; i < k; i++) {
                p += 2 + (f[p] & 0xff);
            }
            pMin = p + 2;
            pMax = p + 6;
            pCnt = p + 10;
            pTok = p + 0xb;
        }
        int min = Bytes.s32(f, pMin);
        int max = Bytes.s32(f, pMax);
        int n = f[pCnt] & 0xff;
        // value stack: kind[], val[] (the original keeps 5-byte entries in a 500-byte array)
        int[] kind = new int[100];
        int[] val = new int[100];
        int sp = 0;
        for (int t = 0; t < n; t++) {
            int b = f[pTok] & 0xff;
            if ((b & 0x10) == 0) {
                if (b == 0 || b == 0xc) {
                    kind[sp] = b;
                    val[sp] = Bytes.s32(f, pTok + 1);
                    sp++;
                }
                pTok += 5;
            } else {
                int top = num(kind[sp - 1], val[sp - 1], self, tgt, hero, mon);
                int snd = num(kind[sp - 2], val[sp - 2], self, tgt, hero, mon);
                int r = 0;
                switch (b) {
                    case 0x11:
                        r = snd + top;
                        break;
                    case 0x12:
                        r = snd - top;
                        break;
                    case 0x13:
                        r = top * snd;
                        break;
                    case 0x14:
                        r = top == 0 ? 0 : snd / top;
                        break;
                    case 0x15:
                        r = top == 0 ? 0 : snd % top;
                        break;
                    case 0x16:
                        r = snd ^ top;
                        break;
                    default:
                        break;
                }
                kind[sp - 2] = 0;
                val[sp - 2] = r;
                sp--;
                pTok++;
            }
        }
        int res = num(kind[sp - 1], val[sp - 1], self, tgt, hero, mon);
        if (res < min) {
            res = min;
        } else if (max < res) {
            res = max;
        }
        return res;
    }

    /** FR_GetNumberInStack @0x71458: a constant, or a value function evaluated now; any other kind reads as 0 */
    private static int num(int kind, int v, Obj self, Obj tgt, Obj hero, Obj mon) {
        if (kind == 0) {
            return v;
        }
        if (kind == 0xc) {
            return valFunc(v, self, tgt, hero, mon);
        }
        return 0;
    }

    /** FR_GetValFunc @0x707bc (self = param_2, target = param_3, hero = param_4, monster = param_5) */
    private static int valFunc(int fn, Obj self, Obj tgt, Obj hero, Obj mon) {
        if (fn == 0) {
            return 0;
        }
        Obj sm = Pool.summon;
        switch (fn) {
            case 1:
                return s8(self, 0x94);
            case 2:
                return u8(hero, 0x1ad);
            case 3:
                return u8(hero, 0x1af);
            case 4:
                return u8(hero, 0x1ae);
            case 5:
                return u8(hero, 0x1ac);
            case 6:
                return u8(sm, 0x215);
            case 7:
                return u8(sm, 0x214);
            case 8:
                return u8(sm, 0x217);
            case 9:
                return u8(sm, 0x216);
            case 10:
                return s16(hero, 0x1b0);
            case 0xb:
                return s8(self, 0xcc);
            case 0xc:
                return s8(self, 0xd2);
            case 0xd:
                return s16(self, 0xce);
            case 0xe:
                return s32(hero, 0x21c);
            case 0xf:
                return s16(hero, 0x1c0);
            case 0x10:
                if (Obj.structType(self) == 2) {
                    return s16(hero, 0x1b4);
                }
                if (Obj.structType(self) == 4) {
                    return s16(sm, 0x21c);
                }
                return 0;
            case 0x11:
                if (Obj.structType(self) == 2) {
                    return s16(hero, 0x1bc);
                }
                if (Obj.structType(self) == 4) {
                    return s16(sm, 0x224);
                }
                return 0;
            case 0x12:
                return s16(self, 0xd6);
            case 0x13:
                return s16(self, 0xda);
            case 0x14:
                return s16(self, 0xde);
            case 0x15:
                return s16(self, 0xe8);
            case 0x16:
                return s16(self, 0xe2);
            case 0x17:
                return s16(self, 0xee);
            case 0x18:
                return s32(sm, 0xa4);
            case 0x19:
                return s16(self, 0xac);
            case 0x1a:
                return s16(hero, 0x1d0);
            case 0x1b:
                return s16(self, 0xbc);
            case 0x1c:
                return s16(self, 0xb4);
            case 0x1d:
                return s16(self, 0xc4);
            case 0x1e:
                return s16(hero, 0x1d2);
            case 0x1f:
                return s8(hero, 0x20e);
            case 0x20:
                return s16(hero, 0x1b6);
            case 0x21:
                return s16(hero, 0x1de);
            case 0x22:
                return s16(hero, 0x1dc);
            case 0x23:
                return s16(hero, 0x1ca);
            case 0x24:
                return s16(self, 0xb8);
            case 0x25:
                if (Obj.structType(self) == 2) {
                    return s16(hero, 0x1cc);
                }
                if (Obj.structType(self) == 4) {
                    return s16(sm, 0x234);
                }
                return 0;
            case 0x26:
                return s16(self, 0xba);
            case 0x27:
                return s16(self, 0xbe);
            case 0x28:
                return s16(self, 0xde);
            case 0x29:
                return s16(hero, 0x1b0);
            case 0x2a:
                return s16(self, 200);
            case 0x2b:
                if (Obj.structType(self) == 2) {
                    return u8(hero, 0x1af);
                }
                if (Obj.structType(self) == 4) {
                    return u8(sm, 0x217);
                }
                return 0;
            case 0x2c:
                return s8(self, 0x94);
            case 0x2d:
                return s16(self, 0xd4);
            case 0x2e:
                return s16(self, 0xe4);
            case 0x2f:
                return s16(self, 0xe6);
            case 0x30:
                return s16(self, 0xea);
            case 0x31:
                return s16(self, 0xec);
            case 0x32:
                if (Obj.structType(self) == 2) {
                    return s16(hero, 0x1b8);
                }
                if (Obj.structType(self) == 4) {
                    return s16(sm, 0x220);
                }
                return 0;
            case 0x33:
                if (Obj.structType(self) == 2) {
                    return s16(hero, 0x1b2);
                }
                if (Obj.structType(self) == 4) {
                    return s16(sm, 0x21a);
                }
                return 0;
            case 0x34:
                return s16(sm, 0xd4);
            case 0x35:
                return s16(tgt, 0xb0);
            case 0x36:
                return s16(tgt, 0xb2);
            case 0x37:
                return s16(tgt, 0xc2);
            case 0x38:
                return s8(tgt, 0x94);
            case 0x39:
                return s16(tgt, 0xd8);
            case 0x3a:
                return s16(tgt, 0xdc);
            case 0x3b:
                return s16(tgt, 0xe0);
            case 0x3c:
                if (Obj.structType(tgt) == 2) {
                    return s16(hero, 0x1c6);
                }
                if (Obj.structType(tgt) == 4) {
                    return s16(sm, 0x22e);
                }
                return 0;
            case 0x3d:
                if (Obj.structType(tgt) == 2) {
                    return s32(hero, 0xa8);
                }
                if (Obj.structType(tgt) == 4) {
                    return s32(sm, 0xa8);
                }
                return 0;
            case 0x3e:
                if (tgt.m[0x16b] < 4) {
                    return tgt.m[0x16b] < 2 ? 0 : 0x1e;
                }
                return 100;
            case 0x3f:
                return G.game[1];
            case 0x40:
                return s32(self, 0xf0);
            case 0x41:
                if (Obj.structType(self) != 2) {
                    return Rng.rand(0, 0x14) - 10;
                }
                if (G.equip[4] != null) {
                    int w = s16(G.equip[4], 0x72);
                    return Rng.rand(0, w << 1) - w;
                }
                return 0;
            case 0x42:
                return s16(mon, 0xba);
            case 0x43:
                return s16(mon, 0xb2);
            case 0x44:
                return s16(mon, 0xc2);
            case 0x45:
                return s16(mon, 0xca);
            case 0x46:
                return s16(mon, 0xd0);
            case 0x47:
                return s32(mon, 0xa8);
            case 0x48:
                return s16(mon, 0xd4);
            case 0x49:
                return s16(mon, 0xd8);
            case 0x4a:
                return s16(mon, 0xe4);
            case 0x4b:
                return s16(mon, 0xe6);
            case 0x4c:
                return s16(mon, 0xe0);
            case 0x4d:
                return s16(mon, 0xea);
            case 0x4e:
                return s16(mon, 0xec);
            case 0x4f:
                return s16(mon, 0xdc);
            case 0x50:
                return skillInfo[0x24];
            case 0x51:
            case 0x70:
                if (Obj.structType(self) == 2) {
                    return calc(0xbbc, self, tgt, skillIdx, skillInfoType, hero, mon);
                }
                if (Obj.structType(self) == 4) {
                    return calc(0x138b, self, tgt, skillIdx, skillInfoType, hero, mon);
                }
                if (mon.m[0x308] == 0) {
                    return s16(mon, mon.m[0x40e] * 2 + 0x1c2);
                }
                return mon.m[mon.m[0x40e] + 0x1ce];
            case 0x52:
                return skillInfo[0x12];
            case 0x53:
                return s16(skillInfo, 4);
            case 0x54:
                return s16(skillInfo, 10);
            case 0x55:
                return skillInfo[0x13];
            case 0x56:
                if (Obj.structType(self) == 2) {
                    return s16(skillInfo, hero.m[0xd0d] * 2 + 0x18);
                }
                if (Obj.structType(self) == 4) {
                    return s16(skillInfo, sm.m[0x63a] * 2 + 0x18);
                }
                return 0;
            case 0x57:
                if (Obj.structType(self) == 2) {
                    return skillInfo[hero.m[0xd0d] + 0x20];
                }
                if (Obj.structType(self) == 4) {
                    return skillInfo[sm.m[0x63a] + 0x20];
                }
                return 0;
            case 0x5e:
                return G.equip[4] != null ? s16(G.equip[4], 0x6e) : 0;
            case 0x5f:
                return G.equip[0] != null ? s16(G.equip[0], 0x6e) : 0;
            case 0x60:
                return G.equip[0] != null ? s16(G.equip[0], 0x70) : 0;
            case 0x61:
                return G.equip[1] != null ? s16(G.equip[1], 0x6e) : 0;
            case 0x62:
                return G.equip[1] != null ? s16(G.equip[1], 0x70) : 0;
            case 0x63:
                return G.equip[2] != null ? s16(G.equip[2], 0x6e) : 0;
            case 0x64:
                return G.equip[2] != null ? s16(G.equip[2], 0x70) : 0;
            case 0x65:
                return G.equip[3] != null ? s16(G.equip[3], 0x6e) : 0;
            case 0x66:
                return G.equip[3] != null ? s16(G.equip[3], 0x70) : 0;
            case 0x67:
                return G.equip[5] != null ? s16(G.equip[5], 0x6a) : 0;
            case 0x68:
                return G.equip[6] != null ? s16(G.equip[6], 0x6a) : 0;
            case 0x6e:
                if (Obj.structType(self) == 2) {
                    return s16(hero, 0x1c2);
                }
                if (Obj.structType(self) == 4) {
                    return s16(sm, 0x22a);
                }
                return 0;
            case 0x6f:
                if (Obj.structType(self) == 2) {
                    return calc(0xbbc, self, tgt, skillIdx, skillInfoType, hero, mon);
                }
                if (Obj.structType(self) == 4) {
                    return calc(0x138b, self, tgt, skillIdx, skillInfoType, hero, mon);
                }
                return s16(self, 0x118);
            default:
                return 0;
        }
    }
}
