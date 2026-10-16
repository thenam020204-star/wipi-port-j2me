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
}
