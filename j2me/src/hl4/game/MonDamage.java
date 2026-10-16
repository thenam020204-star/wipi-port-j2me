package hl4.game;

import hl4.sys.Bytes;

/**
 * A monster being hit: MONSTER_isImmune @0xa49f8, MONSTER_addHp @0xa3568, MONSTER_setDie @0xa3428, MONSTER_attacked @0x9e820, MONSTER_knockBack @0xa2c30,
 * MONSTER_setAttackedMotion @0xa2978 and the small helpers they use.  Written from the disassembly; boss / summon branches stop with "later".
 */
public final class MonDamage {
    private MonDamage() {
    }

    private static void later(String what) {
        throw new IllegalStateException(what + ": later");
    }

    /** C.27.17684 @0xd8d51: [m[0x1e7]][kind] -> 0 none, 1 immune, 2 resists, ... */
    private static final byte[] IMMUNE = {1, 1, 1, 1, 2, 4, 2, 2, 1, 1, 1, 1, 0, 1, 0, 3, 0, 0, 1, 2, 1, 1, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 2, 0, 0, 2, 2, 0, 0,
            1, 1, 0, 1, 0, 0, 1, 1, 1, 2, 0, 0, 1, 0, 0, 3, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

    /** C.3.16767 @0xd8dd7 / C.2.16758 @0xd8de2: death / hit motion by monster kind (+0x1ae) */
    private static final byte[] DEATH_MOTION = {3, 3, 3, 3, 3, 3, 2, 2, 2, -1, 3};
    private static final byte[] HIT_MOTION = {2, 2, 2, 2, 2, 2, 1, 1, 1, 2, 2};

    /** MONSTER_isImmune(o, kind) */
    static int isImmune(Obj o, int kind) {
        int t = o.m[0x1e7] & 0xff;
        if (t > 7) {
            return 0;
        }
        return IMMUNE[t * 10 + kind];
    }

    /** MONSTER_getDeathMotionIDX @0xa480c */
    static int deathMotion(Obj o) {
        return DEATH_MOTION[o.m[0x1ae]];
    }

    /** MONSTER_getAttackedMotionIDX @0xa47bc */
    static int hitMotion(Obj o) {
        return HIT_MOTION[o.m[0x1ae]];
    }

    /** MONSTER_attacked @0x9e820: the monster reacts to a hit */
    static void attacked(Char o) {
        byte[] m = o.m;
        m[0x16d] = 3;
        m[0x2ac] = 2;
        Bytes.w16(m, 0x28e, 0);
        m[0x3eb] = 0;
        m[0x35c] = 1;                                   // MONSTER_setAttackCommand(o, 1), MONSTER_resetAttackCommand(o)
        m[0x35c] = 1;
        m[0x348] = 0;
    }

    /** MONSTER_lookHero @0x9ef84: direction towards the hero */
    static int lookHero(Char o) {
        return (byte) Chars.turnDirection(o, Pool.hero);
    }

    /** MONSTER_setAttackedMotion(o, p) @0xa2978 */
    static void setAttackedMotion(Char o, int p) {
        byte[] m = o.m;
        if (m[0x324] != 0) {
            m[0x324] = 0;
        }
        if (Monsters.isObject(o)) {
            MonProc.setNextMotionDirection(o, (byte) hitMotion(o), 0, 0);
            return;
        }
        int mot = hitMotion(o);
        int dir = lookHero(o);
        MonProc.setNextMotionDirection(o, (byte) mot, dir, 0);
        MonProc.changeMotionAtOnce(o);
        Monsters.attackCancel(o);
        if (p != 0) {
            int im = isImmune(o, 9);
            if (im == 1) {
                m[0x36c] = 0;
                m[0x176] = 1;
            } else if (isImmune(o, 9) == 2) {
                m[0x36c] = 2;
                m[0x176] = 1;
            } else {
                m[0x36c] = 3;
            }
        }
        attacked(o);
    }

    private static final int[] kx = new int[1];
    private static final int[] ky = new int[1];

    /** MONSTER_knockBack(o, power, dir) @0xa2c30: the monster is pushed away from the hero by 'power' pixels (>4: power - 4) and plays its hit motion */
    static void knockBack(Char o, int power, int dirIn) {
        byte[] m = o.m;
        int d = Um.turnDirection(Chars.getX(o), Chars.getY(o), Chars.getX(Pool.hero), Chars.getY(Pool.hero));
        int opp = Um.turnOpposite((byte) d) & 0xff;
        m[0x2ac] = 2;
        if (Monsters.isDie(o)) {
            return;
        }
        if (opp == 0xff) {
            opp = 0;
        }
        if (Monsters.getCurHp(o) <= 0) {
            return;
        }
        if (Monsters.bodyRemain(o)) {
            return;
        }
        if (Monsters.isObject(o)) {
            MonProc.setNextMotionDirection(o, (byte) hitMotion(o), 0, 0);
            return;
        }
        Bytes.w32(m, 0x368, 0);
        Bytes.w32(m, 0x364, 0);
        if (power == 0) {
            setAttackedMotion(o, 1);
            return;
        }
        if (power == 1) {
            setAttackedMotion(o, power);
            return;
        }
        int r5 = (power > 4 ? power - 4 : power) & 0xff;
        r5 = (byte) r5;
        int dir = (byte) opp;
        Bytes.w32(m, 0x364, (byte) Um.vecX(dir) * r5);
        Bytes.w32(m, 0x368, (byte) Um.vecY(dir) * r5);
        setAttackedMotion(o, 1);
        kx[0] = Bytes.s32(m, 0x364);
        ky[0] = Bytes.s32(m, 0x368);
        boolean can = Coll.charCanMove(o, kx, ky, true, true);
        Bytes.w32(m, 0x364, kx[0]);
        Bytes.w32(m, 0x368, ky[0]);
        if (can) {
            Chars.setPosition(o, Bytes.s32(m, 0x364), Bytes.s32(m, 0x368));
        }
        Bytes.w32(m, 0x364, (byte) Um.vecX(dir) * r5);
        Bytes.w32(m, 0x368, (byte) Um.vecY(dir) * r5);
        m[0x175] = 0;
    }

    /** MONSTER_addHp(o, delta, knock, kind, v) @0xa3568 */
    static boolean addHp(Char o, int delta, int knock, int kind, int v) {
        byte[] m = o.m;
        if (knock >= 0) {
            knockBack(o, knock, (byte) Chars.getDirection(o));
        }
        if (kind == 1) {
            Bytes.w16(m, 0x32e, Bytes.u16(m, 0x32e) - delta);
        } else if (kind == 2) {
            Bytes.w16(m, 0x330, Bytes.u16(m, 0x330) - delta);
        }
        int hp = Bytes.s32(m, 0xa4);
        if (hp > 0) {
            Bytes.w32(m, 0xa4, delta + hp);
        }
        if (m[0x327] == 3 || m[0x327] == 1) {
            if (Bytes.s32(m, 0xa4) <= 0) {
                Bytes.w32(m, 0xa4, 1);
            }
        }
        if (Monsters.getCurHp(o) <= 0) {
            Bytes.w32(m, 0xa4, 0);
        }
        return setDie(o, v);
    }

    /** MONSTER_setDie(o, v) @0xa3428: true when the monster is (now) dead */
    static boolean setDie(Char o, int v) {
        byte[] m = o.m;
        if (Monsters.isDie(o) || m[0x30a] != 0) {
            return true;
        }
        if (m[0x324] != 0) {
            m[0x324] = 0;
        }
        if (Monsters.getCurHp(o) > 0) {
            return false;
        }
        int mot;
        if (!Monsters.isBoss(o)) {
            mot = deathMotion(o);
        } else {
            later("Boss_setDie");
            mot = 0;
        }
        MonProc.setNextMotionDirection(o, (byte) mot, 0, 0);
        MonProc.changeMotionAtOnce(o);
        Monsters.attackCancel(o);
        Chars.initBuff(o);
        Bytes.w16(m, 0x28e, 0);
        m[0x3eb] = 0;
        m[0x29e] = (byte) 0xff;
        m[0x166] = 4;
        m[0x40f] = (byte) v;
        if (m[0x35e] != 0) {
            later("MONSTER_setDie: summoned monster (_summonList)");
        }
        MonProc.ai(o);
        m[0x325] = 0xc;
        return true;
    }
}
