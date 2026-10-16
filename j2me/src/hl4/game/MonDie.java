package hl4.game;

import hl4.sys.Bytes;

/**
 * A monster that finished its death motion: MONSTER_die @0xa6f5c, MONSTER_dropExp @0xa50fc and the hero's experience (HERO_add_curExp @0x7c610).
 * Written from the disassembly; the quest-boss, summon and level-up branches stop with "later" until a trace reaches them.
 */
public final class MonDie {
    private MonDie() {
    }

    private static void later(String what) {
        throw new IllegalStateException(what + ": later");
    }

    /** MONSTER_die(o): Map_isTutorialScene is a constant 0 in this build */
    static void die(Char o) {
        byte[] m = o.m;
        Monsters.attackCancel(o);
        if (m[0x40f] == 0) {
            if (Quest.check(0xd, Bytes.s16(m, 0x160), 1) != 0) {
                later("PickUpItem_addInfo (quest monster killed)");
            }
        }
        byte[] qb = Monsters.questBoss;
        if (qb[0] != 0 && qb[1] != 0) {
            if (Bytes.s16(m, 0x160) == qb[0] - 1) {
                later("quest boss killed (Event_Set_SwitchDefault / MONSTER_questBossDel / EVENT_onLoad)");
            }
        }
        Chars.initBuff(o);
        Chars.deleteEffectInList(o);
        Chars.deleteEffectInObjList(o);
        m[0x2aa] = 2;
        Bytes.w16(m, 0x28e, 0);
        m[0x29e] = (byte) 0xff;
        m[0x323] = 0;
        if (m[0x40f] == 0) {
            if (!Monsters.isBoss(o)) {
                dropItem(o);
            }
            dropExp(o);
        }
        m[0x40f] = 0;
        if (m[0x35e] != 0) {
            later("MONSTER_die: summoned monster (_summonList, MONSTER_remove)");
        }
        MonProc.ai(o);
        if (m[0x35e] != 0 || Monsters.noMoreRegen(o)) {
            later("MONSTER_remove");
        }
        Chars.setMainState(o, 9);
        MonProc.setNextMotionDirection(o, 0x64, 1, 0);
        if (MonProc.bossIsLast(o)) {
            G.map[0x37c]++;
        }
    }

    /** MONSTER_dropExp(o): the formula 0x3ed of the monster's experience goes to the hero (and half of it to the summon of a summoner) */
    static void dropExp(Char o) {
        Char h = Pool.hero;
        int exp = Fr.calc(0x3ed, h, o, -1, 0, h, o);
        addCurExp(h, exp);
        if (h.m[0x189] == 1) {
            later("SUMMON_addExp");
        }
        if (h.m[0x16b] == 3 && h.m[0x1ed] != 0) {
            later("MONSTER_dropExp: summon heals");
        }
    }

    /** HERO_add_curExp(hero, exp) @0x7c610 */
    static int addCurExp(Char h, int exp) {
        byte[] m = h.m;
        if (m[0x94] > 0x62) {
            Bytes.w32(m, 0x178, 0);
            return 0;
        }
        int cur = Bytes.s32(m, 0x178) + exp;
        Bytes.w32(m, 0x178, cur);
        int max = Bytes.s32(m, 0x17c);
        if (cur < max || cur == 0) {
            return 0;
        }
        later("HERO_add_curExp: level up");
        return 1;
    }

    /** MONSTER_dropItem(o) @0xa52d0 */
    static void dropItem(Char o) {
        MonDrop.dropItem(o);
    }
}
