package hl4.game;

import hl4.sys.Bytes;
import hl4.sys.Rng;

/**
 * A monster attacking the hero: MONSTER_attackHero @0xa4b68 with the damage / effect helpers it calls (BattleProc_getSkillDamage @0xcc478,
 * BattleProc_setSkillEff_Physical @0xcc2e4, CHAR_heroAddHp @0x397bc, IncDecGaugeList_add @0x892ac, HERO_initMotionAtDamaged @0x7a36c,
 * HERO_setDamaged @0x7a2d0, CHAR_heroMotionDamaged @0x3965c, MainGameFrame_initMonsterInfo @0x893bc).  Written from the disassembly.
 */
public final class MonBattle {
    private MonBattle() {
    }

    private static void later(String what) {
        throw new IllegalStateException(what + ": later");
    }

    /** MainGameFrame_initMonsterInfo(o, ticks, keep) @0x893bc: the monster whose HP bar is shown (_game+8, the original keeps a pointer) */
    public static Char monInfo;

    static void initMonsterInfo(Char o, int t, int keep) {
        byte[] g = G.game;
        if (keep != 0 && monInfo != null) {
            return;
        }
        boolean on = o != null && t > 0;
        Bytes.w32(g, 0xc, 0);
        monInfo = o;
        g[6] = (byte) (on ? 1 : 0);
        g[5] = (byte) (on ? t : 0);
    }

    /** IncDecGaugeList_add(kind, cur, max, delta) @0x892ac: the HP (0) / SP (1) bar change animation state at _game+0x10 / +0x20 */
    static void gaugeAdd(int kind, int cur, int max, int delta) {
        byte[] g = G.game;
        int b = kind == 0 ? 0x10 : 0x20;
        int r4 = Bytes.s32(g, kind == 0 ? 0x38 : 0x3c);
        int r6 = Bytes.s32(g, kind == 0 ? 0x30 : 0x34);
        int v = (delta + cur) * 0x4c / max;
        if (g[b + 0xe] != 0) {
            g[b + 0xe] = 0;
            g[b + 0xd] = (byte) kind;
            int w = r4 - v;
            if (w < 1) {
                w = 1;
            }
            g[b + 0xc] = 0;
            Bytes.w32(g, b, r6 + v - r4);
            Bytes.w32(g, b + 4, w);
            Bytes.w32(g, b + 8, 2);
            return;
        }
        int w = r4 - v;
        int c = Bytes.s32(g, b + 4);
        if (w >= 0) {
            c += w;
        }
        Bytes.w32(g, b, r6 + v - r4);
        Bytes.w32(g, b + 4, c);
    }

    /** CHAR_heroAddHp(n) @0x397bc: during events only heals; damage records the HP bar change first */
    static void heroAddHp(int n) {
        Char h = Pool.hero;
        byte[] m = h.m;
        if (Event.isEvent()) {
            if (n < 0) {
                return;
            }
        } else {
            int hp = Bytes.s32(m, 0xa4);
            if (hp > 0 && n < 0) {
                int a = n < 0 ? -n : n;
                int d = hp < a ? -hp : n;
                gaugeAdd(0, Bytes.s32(m, 0xa4), Bytes.s32(m, 0xa8), d);
            }
        }
        int hp = n + Bytes.s32(m, 0xa4);
        Bytes.w32(m, 0xa4, hp);
        if (hp > Bytes.s32(m, 0xa8)) {
            Bytes.w32(m, 0xa4, Bytes.s32(m, 0xa8));
        }
        if (Bytes.s32(m, 0xa4) <= 0) {
            Bytes.w32(m, 0xa4, 0);
        }
    }

    /** HERO_initMotionAtDamaged(h) @0x7a36c */
    static void initMotionAtDamaged(Char h) {
        byte[] m = h.m;
        m[0x20e] = 0;
        HeroCtl.moveStop(h);
        Bytes.w16(m, 0x1b6, 0);
        m[0xd0a] = 0;
        m[0x214] = 0;
        m[0xa30] = 0;
        Particles.curCount = 0;                         // destroyAllParticles
        if (hl4.gfx.Sprite.lcdEff[8] != 0) {
            later("DM_ReleaseLCDEffect");
        }
    }

    /** HERO_cancelCasting(h) @0xc7550: a cast in motion 10 / 11 drops its effect (CHAR_removeTargetEffect) */
    static void cancelCasting(Char h) {
        int mo = h.m[0x167];
        if (mo == 10 || mo == 11) {
            later("HERO_cancelCasting (CHAR_removeTargetEffect)");
        }
    }

    /** HERO_setDamaged(h) @0x7a2d0: counts hits (+0xa42); the 10th knocks the hero down (motion 6) and returns true */
    static boolean setDamaged(Char h) {
        byte[] m = h.m;
        m[0x16d] = 2;
        cancelCasting(h);
        int ip = m[0xa2c] & 0xff;
        if (ip != 0) {
            return false;
        }
        int c = (m[0xa42] + 1) & 0xff;
        m[0xa42] = (byte) c;
        if (m[0x167] > 6) {
            G.map[0x34e] = (byte) 0xff;
            G.map[0x34f] = (byte) ip;
        }
        if ((byte) c <= 9) {
            return false;
        }
        m[0xa42] = (byte) ip;
        HeroCtl.setMotion(h, 6, m[0x168]);
        return true;
    }

    /** CHAR_heroMotionDamaged(dir) @0x3965c (+ HERO_playSoundMotion: sound only) */
    static void heroMotionDamaged(int dir) {
        HeroCtl.setMotion(Pool.hero, 4, dir);
    }

    /** BattleProc_setSkillEff_Physical(self, tgt, kind, val, b, skill, infoType, hero, mon) @0xcc2e4: the damage formula of an attack kind */
    static int physical(Obj self, Char tgt, int kind, int skill, int infoType, Obj hero, Obj mon) {
        int id;
        switch (kind) {
            case 0x23:
            case 0x35:
                id = 0xfa6;
                break;
            case 0x30:
                id = 0xfb1;
                break;
            case 0x33:
                id = 0xfa7;
                break;
            case 0x34:
                id = 0xfa9;
                break;
            case 0x36:
                id = 0xfa3;
                break;
            case 0x37:
                id = 0xfa0;
                break;
            case 0x38:
                id = 0xfa1;
                break;
            case 0x39:
            case 0x3a:
                later("BattleProc_setSkillEff_Physical: fire / ice kinds (CHAR_removeEtcStatus)");
                id = -1;
                break;
            case 0x3b:
                id = 0xfaa;
                break;
            case 0x3c:
                id = 0xfa5;
                break;
            default:
                id = -1;
                break;
        }
        return Fr.calc(id, self, tgt, (byte) skill, (byte) infoType, hero, mon);
    }

    /** BattleProc_getSkillDamage(self, tgt, skill, infoType, hero, mon) @0xcc478 (monster attacker: the kind of its current skill, tried four times) */
    static int getSkillDamage(Char self, Char tgt, int skill, int infoType, Obj hero, Obj mon) {
        int st = Obj.structType(self);
        if (st != 3) {
            later("BattleProc_getSkillDamage: hero / summon attacker");
        }
        int res = -1;
        for (int i = 0; i < 4; i++) {
            Char mo = (Char) mon;
            int kind = mo.m[0x1be + mo.m[0x40e]] & 0xff;
            if (kind == 0) {
                continue;
            }
            boolean magic = ((kind - 0x29) & 0xff) <= 9 && kind != 0x30;
            if (magic) {
                later("BattleProc_setSkillEff_Magical");
            } else if (kind == 0x23 || ((kind - 0x33) & 0xff) <= 9 || kind == 0x30) {
                res = physical(self, tgt, kind, skill, infoType, hero, mon);
            }
        }
        return res;
    }

    /** MONSTER_attackHero(o, skill, x, y) @0xa4b68: hit / block / critical rolls, damage, effects; returns the damage */
    static int attackHero(Char o, int skill, int x, int y) {
        Char h = Pool.hero;
        byte[] m = o.m;
        byte[] hm = h.m;
        if (Event.isEvent() || !MonProc.canAttack() || !MonProc.heroCanDamage()) {
            return 0;
        }
        int t = m[0x1be + skill];
        int acc;
        if (t == 0x34) {
            acc = (byte) Fr.calc(0x3f7, o, h, skill, 0, h, o);
        } else if (t == 0x35) {
            acc = (byte) Fr.calc(0x3f5, o, h, skill, 0, h, o);
        } else if (t == 0x33) {
            acc = (byte) Fr.calc(0x3f6, o, h, skill, 0, h, o);
        } else {
            acc = 0;
        }
        if (!Rng.ratio(acc)) {
            // miss: the 'miss' common effect on the hero (0x20)
            TEffects.charTargetEffect(o, h, 1, 9, 0x20, 0, 0, -1, 0, 0, m[0x168], Bytes.s32(hm, 0x20), Bytes.s32(hm, 0x24), 0);
            initMonsterInfo(o, 0x5a, 1);
            return 0;
        }
        int blockRate = 0;
        int t2 = m[0x1be + skill];
        if (t2 >= 0x33) {
            if (t2 <= 0x34) {
                blockRate = (byte) Fr.calc(0x3f1, o, h, skill, 0, h, o);
            } else if (t2 == 0x35) {
                blockRate = (byte) Fr.calc(0x3ef, o, h, skill, 0, h, o);
            }
        }
        int crit = (byte) Fr.calc(0x3f3, o, h, skill, 0, h, o);
        int dmg;
        boolean critical;
        if (Rng.ratio(crit)) {
            m[0x308] = 0;
            int d0 = getSkillDamage(o, h, skill, 0, h, o);
            Bytes.w32(m, 0xf0, d0);
            dmg = Fr.calc(0x3ec, o, h, skill, 0, h, o);
            critical = true;
        } else {
            m[0x308] = 0;
            dmg = getSkillDamage(o, h, skill, 0, h, o);
            critical = false;
        }
        Bytes.w32(m, 0xf0, dmg);
        int reflect = 0;
        if (m[0x114] != 0 && m[0x116] == 3) {                   // CHAR_isReflectBuff
            reflect = Fr.calc(0x3f9, o, o, skill, 0, h, o) & 0xffff;
        }
        boolean skipStatus;
        if (Rng.ratio(blockRate)) {
            // block: the 'block' common effect (0x1d), SP gain
            TEffects.charTargetEffect(o, h, 1, 9, 0x1d, 0, 0, -1, 0, 0, hm[0x168], Bytes.s32(hm, 0x20), Bytes.s32(hm, 0x24), 0);
            dmg = Fr.calc(0x3e9, o, h, skill, 0, h, o);
            HeroProc.addSp(h, Bytes.s16(hm, 0x1ce));
            skipStatus = hm[0x189] == 0;
        } else {
            skipStatus = false;
        }
        if (!skipStatus) {
            if (m[0x1ca + skill] > 0 && Rng.ratio((byte) Fr.calc(0x3fd, o, h, skill, 0, h, o))) {
                m[0x308] = 1;
                later("BattleProc_setSkillEff (status effect of the attack)");
            }
        }
        int ip = hm[0x189] & 0xff;
        int neg;
        if (dmg <= 0) {
            neg = -1;
            dmg = 1;
        } else {
            neg = -dmg;
        }
        if (ip == 0) {
            Bytes.w32(m, 0xf0, dmg);
            int v = Bytes.u16(hm, 0x1c4);
            Bytes.w16(hm, 0x1c4, v + Fr.calc(0x3fb, o, h, skill, 0, h, o));
        }
        heroAddHp(neg);
        TEffects.addEffect(h, critical ? 5 : 4, 0, dmg);
        int rf = (short) reflect;
        if (rf > 0) {
            later("MONSTER_attackHero: reflect (MONSTER_addHp)");
        }
        initMotionAtDamaged(h);
        if (!setDamaged(h)) {
            int k = hm[0x175];
            if (k <= 0) {
                heroMotionDamaged(hm[0x168]);
            } else if (Pool.hero.m[0xa2c] == 0) {
                later("MONSTER_knockHeroBack");
            }
            if (hm[0x1ed] != 0) {
                later("MONSTER_attackHero: summon retarget");
            }
        }
        hm[0x175] = 0;
        if (x == 1 && y == 1) {
            x = Bytes.s32(hm, 8);
            y = Bytes.s32(hm, 0xc);
        }
        int rx = Rng.rand(-3, 3);
        int ry = Rng.rand(-3, 3);
        TEffects.addCommonEffect(o, h, 1, 0x10, rx + x, ry + y, 0, -1);
        initMonsterInfo(o, 0x5a, 1);
        return dmg;
    }
}
