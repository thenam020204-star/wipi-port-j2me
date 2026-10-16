package hl4.game;

import hl4.sys.Bytes;

/** Hero skill / quick-slot use (HERO_check_skilluse @0x8a3d0 and its per-class parts).  The class parts and the item / summon slots are not ported yet and stop with "later". */
public final class HeroSkill {
    private HeroSkill() {
    }

    /** CHAR_addEmoticonEffect(h, h, 0, 3, x, y, -1, 0) @0x4c608 (CHAR_targetEffect): the "cannot do that" bubble over the hero */
    private static void emoticon(Char h) {
        throw new IllegalStateException("CHAR_addEmoticonEffect (CHAR_targetEffect): later");
    }

    /** HERO_check_skilluse(h, p2 = slot kind, p3 = skill / item index, p4 = direction, p5 = repeat flag): 1 when an action started */
    public static int checkSkillUse(Char h, int p2, int p3, int p4, int p5) {
        byte[] m = h.m;
        m[p4 + 0x226] = 0;
        if (p3 < 0) {
            return 0;
        }
        if (m[0x167] == 4) {
            return 0;
        }
        if (((m[0x167] & 0xff) - 1) >= 0 && ((m[0x167] & 0xff) - 1) <= 2) {
            if (p2 == 1) {
                if (m[0x189] == 1) {
                    throw new IllegalStateException("HERO_check_skilluse: summon slot: later");
                }
                p3 = 0x10;
            }
            if (p5 == 0 && p2 >= 2) {
                throw new IllegalStateException("HERO_check_skilluse: quick-slot item / arms: later");
            }
        }
        if (m[0x189] == 0 && m[0x167] == 8 && p2 > 1) {
            return 0;
        }
        int c = m[0x20e];
        int off = p3 * 0x28;
        if (p5 == 0 && c == 0 && Bytes.s16(m, 0x2ba + off) < Bytes.s16(m, 0x29e + off)) {
            if ((p3 & 0xff) == 0 && m[0x16b] == 0) {
                return 0;
            }
            if (m[0x16b] == 2 && p3 == 0) {
                return 0;
            }
            emoticon(h);
            return 0;
        }
        if (c != 0 && p2 > 0) {
            return 0;
        }
        if (c != 0 && m[0xd0c] != p3) {
            return 0;
        }
        int cost;
        if (p2 == 0) {
            cost = (short) Fr.calc(0x3f8, h, null, p3, 1, h, null);
        } else if (p2 == 1 && m[0x189] == 0) {
            cost = (short) Fr.calc(0x3fc, h, null, p3, 1, h, null);
        } else {
            cost = 0;
        }
        if (m[0x10c] != 0 && m[0x10e] == 3) {
            cost = 0;                               // CHAR_isAdrenalinBuff
        }
        boolean go;
        if (p3 < 0xc) {
            if (p5 != 0 || m[0x20e] != 0) {
                go = true;
            } else if (m[0x2b8 + off] < 1) {
                emoticon(h);
                return 0;
            } else {
                go = cost <= Bytes.s32(m, 0x218);
            }
        } else if (p5 != 0 || m[0x20e] != 0) {
            go = true;
        } else {
            go = cost <= Bytes.s32(m, 0x218);
        }
        if (!go) {
            emoticon(h);
            return 0;
        }
        switch (m[0x16b]) {
            case 0:
                throw new IllegalStateException("HERO_check_skilluseHK (attack / skill start): later");
            default:
                throw new IllegalStateException("HERO_check_skilluseHG/SK/SM: later");
        }
    }
}
