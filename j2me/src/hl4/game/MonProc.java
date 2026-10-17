package hl4.game;

import hl4.sys.Bytes;

/**
 * Per-tick monster logic (MONSTER_preProcess @0xa2fa0, MONSTER_endProcess @0xa3b8c and what they call), written from the disassembly.
 * The SPR struct numeric fields of a character live in its bytes (frame +0x8c = SPR+0x6c, blend on/alpha +0x8e/+0x8f).
 * Main states (+0x166): 0 stand, 1 walk, 2 hit, 3 attack, 4 die, 5 conceal, 7 teleport, 8 skill/knock, 9 dead (regen), 10 patrol, 0x14 boss.
 * Branches that need systems not ported yet stop with "later".
 */
public final class MonProc {
    private MonProc() {
    }

    private static void later(String what) {
        throw new IllegalStateException(what + ": later");
    }

    // ---- SPR helpers on the character bytes -------------------------------------------------------------------------------------------------
    static int frame(Char o) {
        return o.m[0x8c];
    }

    static void initFrame(Char o) {
        o.m[0x8c] = 0;
    }

    static void setBlend(Char o, int on, int alpha) {
        o.m[0x8f] = (byte) alpha;
        o.m[0x8e] = (byte) on;
        o.spr.f6f = alpha & 0xff;                               // the blend bytes live in the embedded sprite struct (Char + 0x20)
        o.spr.f6e = on & 0xff;
    }

    /** SPR_isEndFrame(spr, anim, dir) */
    static boolean isEndFrame(Char o, int anim, int dir) {
        return o.spr.isEndFrame(anim, dir, o.m[0x8c]);
    }

    /** SPR_repeatFrame(spr, anim, dir) @0xc005c */
    static void repeatFrame(Char o, int anim, int dir) {
        int f = (o.m[0x8c] + 1) & 0xff;
        o.m[0x8c] = (byte) f;
        if ((byte) f > o.spr.maxFrame(anim, dir)) {
            o.m[0x8c] = 0;
        }
    }

    // ---- small accessors -------------------------------------------------------------------------------------------------------------------
    static int motionIdx(Char o) {
        return o.m[0x167];
    }

    static int motionDir(Char o) {
        return o.m[0x168];
    }

    static int category(Char o) {
        return o.m[0x1ae];
    }

    /** MONSTER_getConcealedMotionIDX @0xa485c */
    static int concealedMotion(Char o) {
        int c = category(o);
        return c == 4 ? 4 : (c == 7 ? 3 : -1);
    }

    /** MONSTER_getConcealInMotionIDX @0xa4880 */
    static int concealInMotion(Char o) {
        int c = category(o);
        return c == 4 ? 5 : (c == 7 ? 4 : -1);
    }

    /** MONSTER_getConcealOutMotionIDX @0xa48ac */
    static int concealOutMotion(Char o) {
        int c = category(o);
        return c == 4 ? 6 : (c == 7 ? 5 : -1);
    }

    /** MONSTER_getTeleportInMotionIDX @0xa49c4 */
    static int teleportInMotion(Char o) {
        return category(o) == 5 ? 4 : -1;
    }

    /** MONSTER_getTeleportOutMotionIDX @0xa49e0 (returns the category itself, 5, when it matches) */
    static int teleportOutMotion(Char o) {
        int c = category(o);
        return c == 5 ? c : -1;
    }

    /** MONSTER_getAttackedMotionIDX @0xa47bc: table C.2.16758 indexed by the category */
    private static final byte[] ATTACKED_MOTION = {2, 2, 2, 2, 2, 2, 1, 1, 1, 2, 2};

    static int attackedMotion(Char o) {
        return ATTACKED_MOTION[category(o)];
    }

    /** MONSTER_isWaitAction @0xa494c: AI action 3 with a running +0x28e counter (counted down here) */
    static boolean isWaitAction(Char o) {
        if (o.m[0x29e] != 3) {
            return false;
        }
        int v = Bytes.s16(o.m, 0x28e);
        if (v > 0) {
            Bytes.w16(o.m, 0x28e, v - 1);
            return true;
        }
        return false;
    }

    /** MONSTER_setMoveFrameSkip @0xa4998: true once per (+0x1ac + 3) ticks (+0x360 counts down in endProcess) */
    static boolean setMoveFrameSkip(Char o) {
        if (Bytes.s16(o.m, 0x360) > 0) {
            return false;
        }
        Bytes.w16(o.m, 0x360, o.m[0x1ac] + 3);
        return true;
    }

    /** MONSTER_lookHero @0x9ef84 */
    static int lookHero(Char o) {
        return (byte) Chars.turnDirection(o, Pool.hero);
    }

    /** MONSTER_setNextMotionDirection(o, motion, dir, p4) @0x9f028 */
    static void setNextMotionDirection(Char o, int motion, int dir, int p4) {
        Chars.setNextMotionDirection(o, motion, dir);
        o.m[0x340] = (byte) p4;
        setBlend(o, 0, 0xff);
    }

    /** MONSTER_changeMotionAtOnce @0xa09c4 */
    static void changeMotionAtOnce(Char o) {
        o.m[0x168] = o.m[0x16a];
        o.m[0x167] = o.m[0x169];
        o.m[0x33f] = o.m[0x340];
        chageMainState(o);
        initFrame(o);
    }

    /** MONSTER_standMotionAtOnce @0xa09f4 */
    static void standMotionAtOnce(Char o) {
        Monsters.setStandMotion(o);
        changeMotionAtOnce(o);
    }

    /** MONSTER_walkCount(o, n) @0x9e740 */
    static void walkCount(Char o, int n) {
        int v = (n * (o.m[0x34c] & 0xff)) & 0xff;
        o.m[0x34a] = (byte) v;
        o.m[0x34b] = (byte) v;
    }

    /** MONSTER_chageMainState @0xa0934: the main state from the motion and the category; the attack-property record of an attack / skill motion */
    static void chageMainState(Char o) {
        int s = (byte) Chars.getMainState(o);
        if (s == 8) {
            Chars.setMainState(o, 8);
        } else {
            Chars.setMainState(o, (byte) mainStateByCategory(o));
        }
        Bytes.w32(o.m, 0x2b8, 0);
        o.hitTable = null;                              // +0x2b4
        Bytes.fill(o.m, 0x2bc, 0xff, 0x44);
        o.hitCells = null;                              // +0x2c0 (inside the 0xff fill)
        int ms = o.m[0x166] & 0xff;
        if (ms == 8 || ms == 3) {
            Attk.getProperty(o, 0x2b4, motionIdx(o));
        }
    }

    /** MONSTER_getMainStateByCategory @0xa6524 */
    static int mainStateByCategory(Char o) {
        int mo = o.m[0x167] & 0xff;
        if (mo == 100) {
            return 9;
        }
        int m = (byte) mo;
        int cat = category(o);
        switch (cat) {
            case 0:
            case 1:
            case 2:
            case 3:
                if (mo == 0) {
                    return 0;
                }
                if (m == 1) {
                    return 1;
                }
                if (m == 2) {
                    return 2;
                }
                if (m == 3) {
                    return 4;
                }
                if (m < 3) {
                    return -1;
                }
                if (Boss.isShield(o) && m == 10) {
                    return 5;
                }
                return 3;
            case 4:
                if (mo == 0) {
                    return 0;
                }
                if (m == 1) {
                    return 1;
                }
                if (m == 2) {
                    return 2;
                }
                if (m == 3) {
                    return 4;
                }
                if (m == 4 || m == 5 || m == 6) {
                    return 5;
                }
                return m < 6 ? -1 : 3;
            case 5:
                if (mo == 0) {
                    return 0;
                }
                if (m == 1) {
                    return 1;
                }
                if (m == 2) {
                    return 2;
                }
                if (m == 3) {
                    return 4;
                }
                if (m == 4 || m == 5) {
                    return 7;
                }
                return m < 5 ? -1 : 3;
            case 6:
                if (mo == 0) {
                    return 0;
                }
                if (m == 1) {
                    return 2;
                }
                if (m == 2) {
                    return 4;
                }
                return m < 2 ? -1 : 3;
            case 7:
                if (mo == 0) {
                    return 0;
                }
                if (m == 1) {
                    return 2;
                }
                if (m == 2) {
                    return 4;
                }
                if (m == 3 || m == 4 || m == 5) {
                    return 5;
                }
                return m < 5 ? -1 : 3;
            case 8:
                if (mo == 0) {
                    return 0;
                }
                if (m == 1) {
                    return 2;
                }
                if (m == 2) {
                    return 4;
                }
                return -1;
            case 9:
                if (mo == 0) {
                    return 0;
                }
                if (m == 1) {
                    return 1;
                }
                if (m == 2) {
                    return 2;
                }
                return -1;
            case 10:
                if (m == -1) {
                    return 3;
                }
                if (mo == 0) {
                    return 0;
                }
                if (m == 1) {
                    return 2;
                }
                if (m == 2) {
                    return 4;
                }
                if (m == 3 || m == 4) {
                    return 0x14;
                }
                return m < 4 ? -1 : 3;
            default:
                return -1;
        }
    }

    // ---- MONSTER_aggroProcess @0xa4a5c ---------------------------------------------------------------------------------------------------
    static void aggroProcess(Char o) {
        byte[] m = o.m;
        int a = Bytes.s16(m, 0x32e);
        int b = Bytes.s16(m, 0x330);
        if (a > b) {
            m[0x32d] = 1;
        } else if (a < b) {
            m[0x32d] = 2;
        } else if ((a & 0xffff) == 0) {
            m[0x32d] = 0;
        }
        if ((short) G.globalFrame % 20 == 0) {
            int v = Bytes.s16(m, 0x32e);
            Bytes.w16(m, 0x32e, v > 0 ? v - (Bytes.s32(m, 0xa8) >> 5) : 0);
            v = Bytes.s16(m, 0x330);
            Bytes.w16(m, 0x330, v > 0 ? v - (Bytes.s32(m, 0xa8) >> 5) : 0);
        }
        if (Chars.isHideBuff(Pool.hero)) {
            Bytes.w16(m, 0x32e, 0);
            m[0x32d] = 0;
            Bytes.w16(m, 0x330, 0);
        }
    }

    // ---- MONSTER_preProcess @0xa2fa0 -------------------------------------------------------------------------------------------------------
    public static void preProcess(Char o) {
        if (Event.nextScene() != 0 || Monsters.isBlank(o)) {
            return;
        }
        if ((byte) Chars.getMainState(o) == 9) {
            return;
        }
        if (Chars.process(o, false)) {
            chageMainState(o);
        }
        aggroProcess(o);
        Chars.buffProcess(o);
        Chars.preProcessTargetEffects(o);
        if (Monsters.isDie(o) || Chars.isStunBuff(o)) {
            return;
        }
        byte[] m = o.m;
        if (m[0x327] != 0) {
            setNextMotionDirection(o, (byte) attackedMotion(o), lookHero(o), 0);
            changeMotionAtOnce(o);
            return;
        }
        if (Bytes.s16(G.map, 0x356) > 0 && m[0x170] == 0) {
            return;
        }
        int x = Bytes.s32(m, 8);
        int y = Bytes.s32(m, 0xc);
        if (x < 0) {
            x += 0xf;
        }
        if (y < 0) {
            y += 0xf;
        }
        m[0x16e] = (byte) Coll.tileProp((byte) (x << 0x14 >> 0x18), (byte) (y << 0x14 >> 0x18));
        short g = (short) G.globalFrame;
        if (g % 80 == 0) {
            Chars.addHp(o, Bytes.s16(m, 0xd0));
        } else if (g % 20 == 0) {
            Chars.addHp(o, Bytes.s16(m, 0xca));
        }
        if (Bytes.s16(m, 0xc6) > 0) {
            Chars.addHp(o, Bytes.s16(m, 0xc6));
            Bytes.w16(m, 0xc6, 0);
        }
        int s = Chars.getMainState(o);
        if ((s & 0xff) == 0) {
            return;
        }
        s = (byte) s;
        if (s == 1) {
            if (m[0x1ae] == 6 || Chars.isIcedBuff(o)) {
                return;
            }
            walkPreProcess(o, m[0x168]);
        } else if (s == 7) {
            if (Chars.isIcedBuff(o)) {
                return;
            }
            walkPreProcess(o, m[0x168]);
        } else if (s == 3) {
            attackPreProcess(o);
        } else if (s == 8) {
            attackPreProcess(o);
            checkSummon(o);
        }
    }

    // ---- MONSTER_endProcess @0xa3b8c -------------------------------------------------------------------------------------------------------
    public static void endProcess(Char o) {
        int st = Chars.getMainState(o) & 0xff;
        if (Event.nextScene() != 0 || Monsters.isBlank(o)) {
            return;
        }
        byte[] m = o.m;
        airProcess(o);
        int c360 = Bytes.s16(m, 0x360);
        if (c360 > 0) {
            Bytes.w16(m, 0x360, c360 - 1);
        }
        int mi;
        int md;
        if (st == 9) {
            mi = 0;
            md = 0;
        } else {
            md = motionDir(o) & 0xff;
            mi = motionIdx(o) & 0xff;
        }
        Chars.endProcessTargetEffects(o);
        if (Bytes.s16(G.map, 0x356) > 0 && m[0x170] == 0) {
            Bytes.w16(m, 0x360, 4);
            return;
        }
        if (Monsters.isBoss(o)) {
            later("Boss_endProcess");
        }
        int s = (byte) st;
        boolean repeat = false;
        if (st == 0) {
            if (!isWaitAction(o)) {
                ai(o);
            } else if (mi != 0 && (byte) concealedMotion(o) != (byte) mi) {
                endTail(o);
                return;
            }
            repeat = setMoveFrameSkip(o);
        } else if (s == 8) {
            if (isEndFrame(o, (byte) mi, (byte) md)) {
                Chars.setMainState(o, 0);
                Monsters.setStandMotion(o);
                Bytes.w16(m, 0x28e, 0);
                m[0x3eb] = 0;
            } else {
                repeat = true;
            }
        } else if ((byte) Chars.getMainState(o) == 9) {
            if (Event.isEvent() || Monsters.noMoreRegen(o)) {
                return;
            }
            int c = m[0x325];
            if (c > 0) {
                setBlend(o, 1, (7 - c) * 0x28);
                m[0x325]--;
                return;
            }
            if (c == 0) {
                setBlend(o, 0, 0xff);
                Monsters.init(o, (byte) Monsters.getIndex(o), Bytes.s16(m, 0x1ba), Bytes.s16(m, 0x1bc));
                m[0x325]--;
                return;
            }
            int v = (Bytes.s16(m, 0x1e8) - 1) & 0xffff;
            Bytes.w16(m, 0x1e8, v);
            if ((short) v > 0) {
                return;
            }
            Monsters.regen(o);
            return;
        } else if (s == 1) {
            if (Event.isEvent()) {
                Monsters.setStandMotion(o);
            } else {
                repeat = setMoveFrameSkip(o);
                ai(o);
            }
        } else if (s == 5) {
            later("MONSTER_endProcess: conceal state 5");
        } else if ((byte) Chars.getMainState(o) == 7) {
            later("MONSTER_endProcess: teleport state 7");
        } else if (s == 2) {
            if (!isEndFrame(o, (byte) mi, (byte) md)) {
                repeat = true;
            } else {
                int v = (m[0x36c] - 1) & 0xff;
                m[0x36c] = (byte) v;
                if ((byte) v <= 0) {
                    Monsters.setStandMotion(o);
                }
            }
        } else if (s == 3) {
            if (Monsters.isBoss(o)) {
                later("Boss_AI_Attack");
            }
            if (!isEndFrame(o, m[0x167], m[0x168])) {
                repeat = true;
            } else if (m[0x30a] == 1) {
                m[0x30a] = 0;
                later("MONSTER_selfdie");
            } else if (m[0x3eb] != 0) {
                m[0x3eb]--;
                initFrame(o);
            } else {
                Bytes.w16(m, 0x28e, 0);
                m[0x29e] = (byte) 0xff;
                Chars.setMainState(o, 0);
                Monsters.setStandMotion(o);
                int d = m[0x3f9];
                if (d > 0) {
                    Chars.setDirection(o, (byte) (d - 1));
                    m[0x3f9] = 0;
                }
                ai(o);
            }
        } else if (s == 4) {
            if (!isEndFrame(o, (byte) mi, (byte) md)) {
                repeat = true;
            } else if (m[0x325] < 0) {
                MonDie.die(o);
            } else {
                setBlend(o, 1, 0xf0 - (0xc - m[0x325]) * 0x14);
                int v = (m[0x325] - 1) & 0xff;
                m[0x325] = (byte) v;
                if (v == 3) {
                    Chars.initBuff(o);
                }
            }
        } else if (s == 10) {
            if (!Event.isEvent()) {
                later("MONSTER_patrolEndProcess");
            }
        } else if (s == 0x14) {
            later("MONSTER_endProcess: boss state 0x14");
        }
        if (repeat) {
            repeatFrame(o, (byte) mi, (byte) md);
        }
        endTail(o);
    }

    /** the tail of MONSTER_endProcess (0xa3d38): removable boss escorts lose their HP; the summon list is released when empty */
    private static void endTail(Char o) {
        if (Monsters.bossIsRemoveMon(o)) {
            int v = o.m[0x3e9] & 0xff;
            if (v != 0) {
                o.m[0x3e9] = (byte) (v - 1);
            } else {
                Monsters.addHp(o, -Bytes.s32(o.m, 0xa8), 0, 0, 1);
            }
        }
    }

    /** the airborne part of MONSTER_endProcess (+0x327 air state 2 rising / 3 falling, +0x328 height from AIR_HEIGHT, +0x329 step) */
    private static void airProcess(Char o) {
        byte[] m = o.m;
        int st = m[0x327] & 0xff;
        if (st == 0) {
            return;
        }
        later("MONSTER_endProcess: airborne state");
    }

    // ---- MONSTER_AI @0xa3b2c ----------------------------------------------------------------------------------------------------------------
    static void ai(Char o) {
        if (Event.isEvent() || Chars.isStunBuff(o)) {
            return;
        }
        if (o.m[0x32a] != 0 || o.m[0x327] != 0) {
            return;
        }
        aiAction(o);
        EnemyAi.process(o);
    }

    /** HERO_canDamage @0x7725c: not in motion 6, not airborne, not piercing / dodging / teleporting */
    static boolean heroCanDamage() {
        byte[] h = Pool.hero.m;
        if (h[0x167] == 6 || h[0x20f] != 0 || h[0x211] != 0) {
            return false;
        }
        if (h[0x189] == 0 && h[0x167] == 8) {
            return false;                               // HERO_isStatusMoveDodge
        }
        return !(h[0x189] == 1 && ((h[0x167] & 0xff) - 8 & 0xff) <= 1);     // HERO_isStatusMoveTeleport
    }

    /** Boss_isLast @0xcf3a0 */
    static boolean bossIsLast(Char o) {
        return Monsters.isBoss(o) && G.map[0x376] != 0;
    }

    private static int skillIdx(Char o) {
        return o.m[0x40e];
    }

    /** MONSTER_getCurSkillMotionIdx @0xa4710 */
    static int curSkillMotion(Char o) {
        return o.m[0x1af + skillIdx(o)];
    }

    /** MONSTER_getCurSkillType @0xa46f4 */
    static int curSkillType(Char o) {
        return o.m[0x1a8 + skillIdx(o)];
    }

    /** MONSTER_getCurSkillEffectIdx @0xa4730 */
    static int curSkillEffect(Char o) {
        return o.m[0x1b3 + skillIdx(o)];
    }

    /** MONSTER_getWalkMotionIDX @0xa476c: table C.1.16749 by category */
    private static final byte[] WALK_MOTION = {1, 1, 1, 1, 1, 1, -1, -1, -1, 1, 1};

    static int walkMotion(Char o) {
        return WALK_MOTION[category(o)];
    }

    /** the direction count of motion a of the character's sprite (SPR+0x44/+0x48 entry +8) */
    static int motionDirs(Char o, int a) {
        return o.spr.cif.frameStart[(byte) a].length;
    }

    /** MONSTER_setAttackMotionDirection(o, motion, dir) @0x9f138 */
    static void setAttackMotionDirection(Char o, int motion, int dir) {
        o.m[0x348] = 1;
        setNextMotionDirection(o, motion, dir, 0);
    }

    /** MONSTER_walk(o, steps, dir) @0x9f0a8: steps 0 = UM_Rand(1, 3) */
    static void walk(Char o, int n, int dir) {
        if (category(o) == 6) {
            return;
        }
        if (n == 0) {
            n = (byte) hl4.sys.Rng.rand(1, 3);
        }
        walkCount(o, n);
        if ((byte) Chars.getMainState(o) == 7) {
            return;
        }
        setNextMotionDirection(o, (byte) walkMotion(o), dir, 0);
    }

    /** CHAR_isDiagnal(a, b) @0xa48e8: |dx| * 3 / (|dy| * 2) == 1 (same row: false) */
    static boolean isDiagnal(Obj a, Obj b) {
        int ay = Bytes.s32(a.m, 0xc);
        int by = Bytes.s32(b.m, 0xc);
        if (ay == by) {
            return false;
        }
        int dy = Um.abs(ay - by);
        int dx = Um.abs(Bytes.s32(a.m, 8) - Bytes.s32(b.m, 8));
        return dx * 3 / (dy * 2) == 1;
    }

    private static final int[] rc = new int[4];

    private static int collideMoveRect(Char o, int x, int y, int w, int h) {
        byte[] m = o.m;
        return Um.collision(Bytes.s32(m, 0x148), Bytes.s32(m, 0x14c), Bytes.s32(m, 0x150), Bytes.s32(m, 0x154), x, y, w, h, rc);
    }

    /** MONSTER_isHeroNear(o, useMoveRect) @0xa0fa0: the move rect touches the hero's move rect (p != 0) or hit rect; p == 0 also faces direction 0 */
    static boolean isHeroNear(Char o, int p) {
        Char h = Pool.hero;
        int c;
        if (p != 0) {
            c = collideMoveRect(o, Bytes.s32(h.m, 0x148), Bytes.s32(h.m, 0x14c), Bytes.s32(h.m, 0x150), Bytes.s32(h.m, 0x154));
        } else {
            c = collideMoveRect(o, Chars.getHittedRectX(h), Chars.getHittedRectY(h), Chars.getHittedRectW(h), Chars.getHittedRectH(h));
        }
        if (c == 0 || isDiagnal(o, h)) {
            return false;
        }
        if (p == 0) {
            Chars.setDirection(o, 0);
        }
        return true;
    }

    /** MONSTER_isNearWithHero @0xa601c: the move rect overlaps the hero's hit rect (not diagonally) */
    static boolean isNearWithHero(Char o) {
        Char h = Pool.hero;
        int hx = Chars.getHittedRectX(h);
        int hy = Chars.getHittedRectY(h);
        int hw = Chars.getHittedRectW(h);
        int hh = Chars.getHittedRectH(h);
        int c1 = collideMoveRect(o, hx, hy, hw, hh);
        int c2 = collideMoveRect(o, hx, hy, hw, hh);
        if (c1 >= 0 && c2 >= 0) {
            return false;
        }
        return !isDiagnal(o, h);
    }

    /** MONSTER_chase @0xa1044: steps toward the hero (true = it moves or already touches him) */
    static boolean chase(Char o) {
        byte[] m = o.m;
        int d = (byte) Chars.getDirection(o);
        int opp = (byte) (Um.turnOpposite(d) & 0xff);
        Char h = Pool.hero;
        int hw = Chars.moveRectHalfW(h, opp);
        int ow = Chars.moveRectHalfW(o, d);
        int hh = Chars.moveRectHalfH(h, opp);
        int oh = Chars.moveRectHalfH(o, d);
        int heroX = Bytes.s32(h.m, 8);
        int ox = Bytes.s32(m, 8);
        int heroY = Bytes.s32(h.m, 0xc);
        int oy = Bytes.s32(m, 0xc);
        int rnd = hl4.sys.Rng.random();
        if (!canMove(o)) {
            Monsters.setStandMotion(o);
            return false;
        }
        if (Chars.isHideBuff(h) || !heroCanDamage()) {
            m[0x29e] = (byte) 0xff;
            m[0x3eb] = 0;
            Bytes.w16(m, 0x28e, 0);
            walk(o, 2, rnd & 3);
            return false;
        }
        int v = (m[0x34a] - 1) & 0xff;
        m[0x34a] = (byte) v;
        if ((v & 0x80) == 0 && !(Monsters.isBoss(o) && m[0x30c] == 0x32)) {
            if (Coll.charIsMoveable(o, (byte) Chars.getDirection(o))) {
                return true;
            }
            Monsters.setStandMotion(o);
            return false;
        }
        if (isNearWithHero(o)) {
            return true;
        }
        boolean near = isHeroNear(o, 1);
        int dx = heroX - ox;
        int dy = heroY - oy;
        if (near) {
            int nd;
            if (dy != 0) {
                nd = (dy >>> 31) ^ 1;
            } else {
                nd = dx >= 0 ? 3 : 2;
            }
            Monsters.setStandMotion(o);
            Chars.setDirection(o, nd & 0xff);
            return false;
        }
        int adx = Um.abs(dx);
        int ady = Um.abs(dy);
        int sel;
        if (((ady - adx + 0xe) & 0xffffffffL) <= 0x1c) {
            int hd = dx > 0 ? 3 : 2;
            int vd = dy > 0 ? 1 : 0;
            boolean a = Coll.charIsMoveable(o, hd);
            boolean b = Coll.charIsMoveable(o, vd);
            if (b && a) {
                sel = hl4.sys.Rng.random() > 0 ? hd : vd;
            } else {
                sel = a ? hd : vd;
            }
        } else if (adx >= ady) {
            sel = dx > 0 ? 3 : 2;
        } else {
            sel = dy > 0 ? 1 : 0;
        }
        int wsum = (byte) (ow + hw);
        int hsum = (byte) (oh + hh);
        if (!(adx > wsum && ady > hsum) && adx != ady) {
            if (adx > wsum || ady >= adx) {
                if (ady <= hsum && ady > adx) {
                    if (dx > 0 && Coll.charIsMoveable(o, 3)) {
                        sel = 3;
                    } else if (dx < 0 && Coll.charIsMoveable(o, 2)) {
                        sel = 2;
                    }
                }
            } else {
                if (dy > 0 && Coll.charIsMoveable(o, 1)) {
                    sel = 1;
                } else if (dy < 0 && Coll.charIsMoveable(o, 0)) {
                    sel = 0;
                }
            }
        }
        int dir = (byte) sel;
        if (!Coll.charIsMoveable(o, dir)) {
            dir = (byte) Chars.turnCorner(o, dir);
        }
        if (dir >= 0) {
            walk(o, 2, dir);
        } else {
            walk(o, 4, rnd & 3);
        }
        return true;
    }

    /** MONSTER_canMove @0x9edb8 */
    static boolean canMove(Char o) {
        return !Chars.isIcedBuff(o);
    }

    /** MONSTER_wandering @0xa1484: an aggroed monster chases, otherwise it walks a random way once its step count runs out */
    static void wandering(Char o) {
        if (!canMove(o)) {
            return;
        }
        if (o.m[0x32d] != 0) {
            chase(o);
            return;
        }
        int r = hl4.sys.Rng.rand(0, 3);
        int v = (o.m[0x34a] - 1) & 0xff;
        o.m[0x34a] = (byte) v;
        if ((v & 0x80) != 0) {
            walk(o, 0, (byte) r);
        }
    }

    /** MONSTER_actPattern(o, o+0x29f) @0xa1508: pattern byte p0 = +0x29f, p1 = +0x2a0 */
    static void actPattern(Char o) {
        byte[] m = o.m;
        int p0 = m[0x29f] & 0xff;
        int p1 = m[0x2a0] & 0xff;
        if (p1 == 0) {
            if (p0 == 0) {
                m[0x35c] = 1;                           // MONSTER_setAttackCommand(o, 1)
                if (!chase(o)) {
                    Bytes.w16(m, 0x28e, 0);
                } else {
                    Chars.setMainState(o, 1);
                    m[0x167] = (byte) walkMotion(o);
                }
            } else if ((byte) p0 == 1) {
                Chars.setMainState(o, 1);
                m[0x167] = (byte) walkMotion(o);
                m[0x35c] = 0;
                later("MONSTER_getaway");
            } else if ((byte) p0 == 2) {
                m[0x35c] = 0;
                Chars.setMainState(o, 1);
                m[0x167] = (byte) walkMotion(o);
                wandering(o);
            }
            return;
        }
        if (category(o) != 5) {
            return;
        }
        later("MONSTER_actPattern: teleport category");
    }

    /** MONSTER_setConcealMotion(o, mode) @0xa6914: 0 conceal in, 1 conceal out when the hero is not on top of it */
    static void setConcealMotion(Char o, int mode) {
        if (mode == 0) {
            if (Monsters.isConceal(o)) {
                return;
            }
            setNextMotionDirection(o, (byte) concealInMotion(o), 0, 0);
        } else if (mode == 1) {
            later("MONSTER_setConcealMotion(1) (MONSTER_checkMoveRect)");
        }
        Monsters.attackCancel(o);
        o.m[0x35c] = 1;                                 // MONSTER_resetAttackCommand
        o.m[0x36e] = 0;
    }

    /** MONSTER_decidedToUseNextSkill(o, force) @0xa2090: true when the current skill starts (attack motion set, hero position recorded) */
    static boolean decidedToUseNextSkill(Char o, int force) {
        byte[] m = o.m;
        int md = m[0x168] & 0xff;
        int sm = curSkillMotion(o) & 0xff;
        if (Chars.isHideBuff(Pool.hero)) {
            return false;
        }
        if (!Monsters.isBoss(o) && Monsters.isConceal(o)) {
            return false;
        }
        if (sm == 0) {
            return force != 0;
        }
        if (force != 0 && !heroInAttackRange(o, (byte) sm)) {
            m[0x346] = 0;
            int c = m[0x349] & 0xff;
            if ((byte) c > 1) {
                m[0x349] = 0;
                return false;
            }
            m[0x349] = (byte) (c + 1);
            return false;                               // MONSTER_setAttackDelay is empty
        }
        m[0x349] = 0;
        if ((motionDirs(o, sm) & 0xff) <= 2) {
            md = 0;
        }
        setAttackMotionDirection(o, (byte) sm, (byte) md);
        Bytes.w32(m, 0x354, Bytes.s32(Pool.hero.m, 8));
        Bytes.w32(m, 0x358, Bytes.s32(Pool.hero.m, 0xc));
        return true;
    }

    /** MONSTER_distanceWithHero @0xa1784: the gap to the hero in tiles along the facing axis (20 when the hero cannot be targeted) */
    static int distanceWithHero(Char o) {
        Char h = Pool.hero;
        int dir = Um.turnDirection(Bytes.s32(o.m, 8), Bytes.s32(o.m, 0xc), Bytes.s32(h.m, 8), Bytes.s32(h.m, 0xc)) & 0xff;
        int d = (byte) dir;
        int opp = (byte) Um.turnOpposite(d);
        int hw = Chars.moveRectHalfW(h, opp);
        int ow = Chars.moveRectHalfW(o, d);
        int hh = Chars.moveRectHalfH(h, opp);
        int oh = Chars.moveRectHalfH(o, d);
        int ax = Chars.getAbsX(o, h);
        int ay = Chars.getAbsY(o, h);
        byte[] hm = h.m;
        if (hm[0xa44] > 0 || Chars.isHideBuff(h) || hm[0xd16] != 0 || hm[0x20f] != 0) {
            return 0x14;
        }
        int v;
        if (((dir - 2) & 0xffffffffL) <= 1) {
            v = ax - (byte) (ow + hw);
        } else {
            v = ay - (byte) (oh + hh);
        }
        if (v < 0) {
            v += 0xf;
        }
        return (byte) (v >> 4);
    }

    /** C.10.17162 / C.11.17163: per direction (0..3) x, y, w, h of the two probe rectangles of a motion without own directions */
    private static final byte[] RANGE_A = {-8, -120, 16, 120, -8, 0, 16, 120, -120, -8, 120, 16, 0, -8, 120, 16};
    private static final byte[] RANGE_B = {-48, -72, 96, 72, -48, 0, 96, 72, -72, -48, 72, 96, 0, -48, 72, 96};

    /** Monster_canAttack @0x390b8 */
    static boolean canAttack() {
        byte[] h = Pool.hero.m;
        return h[0x167] != 6 && h[0xa44] == 0;
    }

    private static final int[] hr = new int[4];

    private static void heroHitRect(int[] r) {
        Char h = Pool.hero;
        r[0] = Chars.getHittedRectX(h);
        r[1] = Chars.getHittedRectY(h);
        r[2] = Chars.getHittedRectW(h);
        r[3] = Chars.getHittedRectH(h);
    }

    /** o+0x310: the hero's hit rectangle (skill types 1..5) or -1s */
    private static void store310(Char o, boolean hero) {
        if (hero) {
            for (int k = 0; k < 4; k++) {
                Bytes.w32(o.m, 0x310 + 4 * k, hr[k]);
            }
        } else {
            Bytes.fill(o.m, 0x310, 0xff, 0x10);
        }
    }

    private static boolean hit(int x, int y, int w, int h) {
        return Um.collision(x, y, w, h, hr[0], hr[1], hr[2], hr[3], rc) < 0;
    }

    /**
     * MONSTER_heroInAttackRange(o, motion) @0xa1b00: is the hero inside the hit area of the current skill's first hit cell (cell type 3: its own
     * rectangle for the facing; type 4: the probe rectangles RANGE_A / RANGE_B, tried for all four directions when the motion has at most 2).
     * Sets +0x346 (in range) and +0x326 (the direction that hit, -1 none); the probe rectangles are recorded at +0x410 / +0x420.
     */
    static boolean heroInAttackRange(Char o, int motion) {
        byte[] m = o.m;
        int sm = curSkillMotion(o) & 0xff;
        int st = (byte) curSkillType(o);
        m[0x326] = (byte) 0xff;
        int md = m[0x168] & 0xff;
        if (Event.isEvent() || !canAttack() || !heroCanDamage()) {
            return false;
        }
        int nd = motionDirs(o, sm) & 0xff;
        if (nd <= 2) {
            md = 0;
        }
        Attk.getProperty(o, 0x2b4, (byte) sm);
        if (o.hitTable == null) {
            return false;
        }
        hl4.gfx.SprProps.Cell cell = o.hitTable[0].cells[0];      // ATTKPRO_getFirstHitProperty
        int t = (byte) cell.type;
        int ox = Bytes.s32(m, 8);
        int oy = Bytes.s32(m, 0xc);
        if (t == 3) {
            if (st != 6 && st != 0 && st != 7) {
                return false;
            }
            int[] g = cell.g[md];
            heroHitRect(hr);
            boolean r = hit(ox + g[0], oy + g[1], g[2], g[3]);
            m[0x346] = (byte) (r ? 1 : 0);
            return r;
        }
        if (t != 4) {
            return false;
        }
        boolean hitType = st == 4 || st == 1;
        boolean keepRect = hitType || st == 2 || st == 3 || st == 5;
        if (nd > 2) {
            if (Monsters.isBoss(o)) {
                later("MONSTER_heroInAttackRange_B");
            }
            m[0x326] = (byte) md;
            int a = md * 4;
            heroHitRect(hr);
            store310(o, keepRect);
            boolean r = hit(ox + RANGE_A[a], oy + RANGE_A[a + 1], RANGE_A[a + 2], RANGE_A[a + 3]);
            if (st == 3) {
                m[0x346] = (byte) (r ? 1 : 0);
                if (r) {
                    return true;
                }
            }
            if (hitType) {
                m[0x346] = (byte) (r ? 1 : 0);
                return r;
            }
            boolean r2 = hit(ox + RANGE_B[a], oy + RANGE_B[a + 1], RANGE_B[a + 2], RANGE_B[a + 3]);
            if (st == 5 || st == 2 || st == 3) {
                boolean v = r || r2;
                m[0x346] = (byte) (v ? 1 : 0);
                return v;
            }
            return false;
        }
        if (Monsters.isBoss(o)) {
            later("MONSTER_heroInAttackRange_B");
        }
        for (int i = 0; i < 4; i++) {
            int a = i * 4;
            int ax = ox + RANGE_A[a];
            int ay = oy + RANGE_A[a + 1];
            Bytes.w32(m, 0x410, ax);
            Bytes.w32(m, 0x414, ay);
            Bytes.w32(m, 0x418, RANGE_A[a + 2]);
            Bytes.w32(m, 0x41c, RANGE_A[a + 3]);
            heroHitRect(hr);
            store310(o, keepRect);
            boolean r = hit(ax, ay, RANGE_A[a + 2], RANGE_A[a + 3]);
            if (st == 3) {
                m[0x346] = (byte) (r ? 1 : 0);
                if (r) {
                    m[0x326] = (byte) i;
                    return true;
                }
            }
            if (hitType) {
                m[0x346] = (byte) (r ? 1 : 0);
                if (!r) {
                    continue;
                }
            }
            int bx = ox + RANGE_B[a];
            int by = oy + RANGE_B[a + 1];
            Bytes.w32(m, 0x420, bx);
            Bytes.w32(m, 0x424, by);
            Bytes.w32(m, 0x428, RANGE_B[a + 2]);
            Bytes.w32(m, 0x42c, RANGE_B[a + 3]);
            boolean v = hit(bx, by, RANGE_B[a + 2], RANGE_B[a + 3]) || r;
            if (st == 5 || st == 2 || st == 3) {
                m[0x346] = (byte) (v ? 1 : 0);
                if (!v) {
                    continue;
                }
            }
            if (m[0x346] == 1) {
                m[0x326] = (byte) i;
            }
            return v;
        }
        return false;
    }

    private static void enforceFromParam(Char o) {
        o.m[0x309] = (byte) (o.m[0x2a1] == 1 ? 1 : 0);
    }

    private static int notEnforce(Char o) {
        int e = o.m[0x309] & 0xff;
        return e <= 1 ? 1 - e : 0;
    }

    /** MONSTER_AI_Action @0xa3624: carries out the action chosen by the AI script (+0x29e action 1..11, parameters +0x29f/+0x2a0/+0x2a1) */
    static int aiAction(Char o) {
        byte[] m = o.m;
        int act = m[0x29e];
        switch (act) {
            case 1:
                actPattern(o);
                return 0;
            case 2: {
                int d = Chars.getDirection(o) & 0xff;
                int p = m[0x29f] & 0xff;
                if (p == 0) {
                    d = Tab.TURN_L90[(byte) d] & 0xff;
                } else if (p == 1) {
                    d = Tab.TURN_R90[(byte) d] & 0xff;
                } else if (p == 3) {
                    d = Um.turnOpposite((byte) d) & 0xff;
                } else if (p == 4) {
                    d = Chars.turnDirection(o, Pool.hero) & 0xff;
                }
                Chars.setDirection(o, (byte) d);
                Bytes.w16(m, 0x28e, 0);
                return 0;
            }
            case 3:
                if ((byte) mainStateByCategory(o) == 5) {
                    return 0;
                }
                walkCount(o, 0);
                if (m[0x167] != 0) {
                    Chars.setMainState(o, 0);
                    Monsters.setStandMotion(o);
                    standMotionAtOnce(o);
                }
                return 0;
            case 4: {
                if (Chars.isHideBuff(Pool.hero) || !heroCanDamage()) {
                    Bytes.w16(m, 0x28e, 0);
                    m[0x2a8] = 2;
                    return 0;
                }
                if ((byte) Chars.getMainState(o) == 3 && Bytes.s16(m, 0x28e) != 0) {
                    return 1;
                }
                int p = m[0x2a0];
                if (p == 1) {
                    Chars.setDirection(o, (byte) Chars.turnDirection(o, Pool.hero));
                    m[0x326] = (byte) Chars.turnDirection(o, Pool.hero);
                } else if (p == 2 || p == 3) {
                    Chars.setDirection(o, p);
                } else if (p == 4) {
                    Chars.setDirection(o, 0);
                } else if (p == 5) {
                    Chars.setDirection(o, 1);
                }
                if (Monsters.isBoss(o)) {
                    m[0x326] = m[0x168];
                    m[0x3ed] = m[0x168];
                }
                if (bossIsLast(o)) {
                    m[0x309] = 1;
                } else {
                    enforceFromParam(o);
                }
                if (!decidedToUseNextSkill(o, notEnforce(o))) {
                    m[0x2a8] = 2;
                    Bytes.w16(m, 0x28e, 0);
                    initFrame(o);
                    return 0;
                }
                Chars.setMainState(o, 3);
                initFrame(o);
                return 0;
            }
            case 5: {
                m[0x309] = (byte) (m[0x2a0] == 1 ? 1 : 0);
                m[0x40e] = m[0x29f];
                if (Monsters.isDie(o)) {
                    return 1;
                }
                if ((byte) Chars.getMainState(o) == 3 && Bytes.s16(m, 0x28e) != 0) {
                    return 1;
                }
                Chars.setDirection(o, 0);
                if (!decidedToUseNextSkill(o, notEnforce(o))) {
                    m[0x2a8] = 2;
                    Bytes.w16(m, 0x28e, 0);
                    initFrame(o);
                    return 0;
                }
                Chars.setMainState(o, 3);
                m[0x30a] = 1;
                m[0x40f] = 1;
                initFrame(o);
                return 0;
            }
            case 7: {
                int n = -Bytes.s32(m, 0xa8) * m[0x29f] / 100;
                Monsters.addHp(o, n, 0, 0, 1);
                return 0;
            }
            case 9: {
                if (Boss.isShield(o)) {
                    later("Boss_shieldProcess");
                    return 1;
                }
                int p = m[0x2a0] & 0xff;
                if (p == 0) {
                    setConcealMotion(o, 0);
                    m[0x29e] = (byte) 0xff;
                    return 0;
                }
                if (p == 1) {
                    if (Monsters.isBoss(o) && m[0x3ef] != 0 || (byte) mainStateByCategory(o) == 5) {
                        setConcealMotion(o, 1);
                    }
                    m[0x29e] = (byte) 0xff;
                    return 0;
                }
                return 1;
            }
            case 11:
                m[0x29e] = (byte) 0xff;
                Bytes.w16(m, 0x28e, 0);
                return 0;
            default:
                return 0;
        }
    }

    private static final int[] mvx = new int[1];
    private static final int[] mvy = new int[1];
    private static final int[] diag = new int[2];

    /** MONSTER_walkPreProcess(o, dir) @0xa283c: an aggroed / commanded monster heads diagonally for the hero, otherwise it steps (turning at corners) */
    static void walkPreProcess(Char o, int dir) {
        if (Event.isEvent()) {
            return;
        }
        byte[] m = o.m;
        boolean toHero;
        if (m[0x35c] != 0 && !isHeroNear(o, 1)) {
            toHero = true;
        } else {
            toHero = m[0x32d] != 0;
        }
        if (toHero) {                                   // (MONSTER_isSelfDestruction is always false)
            Bytes.w32(m, 0x354, Bytes.s32(Pool.hero.m, 8));
            Bytes.w32(m, 0x358, Bytes.s32(Pool.hero.m, 0xc));
            if (moveDiagonal(o, 0)) {
                return;
            }
        }
        if (checkAndMove(o, dir, 1)) {
            return;
        }
        if (!Coll.isRectMoveable(o, Bytes.s32(m, 0x148), Bytes.s32(m, 0x14c), Bytes.s32(m, 0x150), Bytes.s32(m, 0x154), true, true)) {
            checkAndMove(o, dir, 0);
            return;
        }
        int d = (byte) Chars.turnCorner(o, dir);
        if (d == -1) {
            standMotionAtOnce(o);
            return;
        }
        checkAndMove(o, d, 1);
    }

    /** MONSTER_isRectMoveable(o, x, y, w, h, withHero) @0xa5c14: with the flag, false when the rectangle overlaps the hero's move rect */
    static boolean isRectMoveable(Char o, int x, int y, int w, int h, boolean withHero) {
        if (!withHero) {
            return true;
        }
        Char hr = Pool.hero;
        return Um.collision(x, y, w, h, Bytes.s32(hr.m, 0x148), Bytes.s32(hr.m, 0x14c), Bytes.s32(hr.m, 0x150), Bytes.s32(hr.m, 0x154), rc) == 0;
    }

    /** MONSTER_checkWithHeroRect(o, &dx, &dy, dir, withHero) @0xa0a2c: a step into the hero (or into a met boss) is cancelled */
    static void checkWithHeroRect(Char o, int[] dx, int[] dy, int dir, boolean withHero) {
        byte[] m = o.m;
        Char t;
        if (withHero) {
            Coll.heroMoveRect();                    // 0xa0a74: the hero's cached move rect is refreshed before it is read
            t = Pool.hero;
        } else {
            t = Maps.boss;
            if (t == null || t == o || !Monsters.meetHero(t)) {
                return;
            }
        }
        int c = Um.collision(Bytes.s32(m, 0x148) + dx[0], Bytes.s32(m, 0x14c) + dy[0], Bytes.s32(m, 0x150), Bytes.s32(m, 0x154),
                Bytes.s32(t.m, 0x148), Bytes.s32(t.m, 0x14c), Bytes.s32(t.m, 0x150), Bytes.s32(t.m, 0x154), rc);
        if (c < 0) {
            dx[0] = 0;
            dy[0] = 0;
            if (withHero) {
                Bytes.w16(m, 0x28e, 0);
                m[0x3eb] = 0;
            }
        }
    }

    /** MONSTER_checkAndMove(o, dir, withMonsters) @0xa0b40: one speed step in dir (2/3 on tile 7, halved when slowed) */
    static boolean checkAndMove(Char o, int dir, int p3) {
        byte[] m = o.m;
        int x = Bytes.s32(m, 0x148);
        int y = Bytes.s32(m, 0x14c);
        mvx[0] = Chars.getMoveX(o, dir);
        mvy[0] = Chars.getMoveY(o, dir);
        if ((byte) Chars.getMainState(o) == 7) {
            if (!isRectMoveable(o, x + mvx[0], y + mvy[0], Bytes.s32(m, 0x150), Bytes.s32(m, 0x154), true)) {
                return false;
            }
            Chars.setPosition(o, mvx[0], mvy[0]);
            return true;
        }
        checkWithHeroRect(o, mvx, mvy, dir, false);
        checkWithHeroRect(o, mvx, mvy, dir, true);
        if (!Coll.charCanMove(o, mvx, mvy, false, p3 != 0)) {
            return false;
        }
        if (m[0x16e] == 7) {
            mvx[0] = mvx[0] * 2 / 3;
            mvy[0] = mvy[0] * 2 / 3;
        }
        if (m[0x114] != 0 && m[0x116] == 1) {           // CHAR_isSlowBuff
            mvy[0] = mvy[0] / 2;
            mvx[0] = mvx[0] / 2;
        }
        Chars.setPosition(o, mvx[0], mvy[0]);
        return true;
    }

    /** MONSTER_moveDiagonal(o, p) @0xa25dc: a speed-long step along the line to the recorded hero position (+0x354/+0x358) */
    static boolean moveDiagonal(Char o, int p) {
        byte[] m = o.m;
        int tx = Bytes.s32(m, 0x354);
        int ty = Bytes.s32(m, 0x358);
        int x = Bytes.s32(m, 8);
        int y = Bytes.s32(m, 0xc);
        int dir = Um.turnDirection(x, y, tx, ty) & 0xff;
        int rx = Bytes.s32(m, 0x148);
        int ry = Bytes.s32(m, 0x14c);
        int speed = m[0x16f];
        boolean tele = (byte) Chars.getMainState(o) == 7;
        if (tele) {
            speed <<= 1;
        }
        diag[0] = 0;
        diag[1] = 0;
        Um.getDiagonal(x, y, tx, ty, speed, diag);
        int dx0 = diag[0] - x;
        int dy0 = diag[1] - y;
        mvx[0] = dx0;
        mvy[0] = dy0;
        if (m[0x16e] == 7) {
            mvx[0] = dx0 * 2 / 3;
            mvy[0] = dy0 * 2 / 3;
        }
        if (m[0x114] != 0 && m[0x116] == 1) {           // CHAR_isSlowBuff
            mvx[0] = mvx[0] / 2;
            mvy[0] = mvy[0] / 2;
        }
        if ((byte) Chars.getMainState(o) == 7) {
            // the teleport test uses the unadjusted step (registers of the original), the move uses the adjusted one
            if (!isRectMoveable(o, dx0 + rx, dy0 + ry, Bytes.s32(m, 0x150), Bytes.s32(m, 0x154), true)) {
                return false;
            }
        } else if (!Coll.charCanMove(o, mvx, mvy, p == 0, true)) {
            if (p != 0) {
                standMotionAtOnce(o);
            }
            Bytes.w16(m, 0x350, 0);
            return false;
        }
        Chars.setDirection(o, (byte) dir);
        Chars.setPosition(o, mvx[0], mvy[0]);
        if (p != 0) {
            int sp = m[0x16f];
            Bytes.w16(m, 0x350, Bytes.s16(m, 0x350) - (sp < 0 ? -sp : sp));
        }
        return true;
    }

    /** MONSTER_getHitProperty @0xa2e20 */
    static boolean getHitProperty(Char o) {
        if (o.hitTable == null) {
            return false;
        }
        Attk.getProperty(o, 0x2b4, o.m[0x167]);
        return Attk.getHitProperty(o, 0x2b4, o.m[0x8c]);
    }

    /** MONSTER_attackPreProcess @0xa2e7c: the hit cells of the attack frame (types 3/4 are copied to +0x2c4 and checked against the hero) */
    static void attackPreProcess(Char o) {
        byte[] m = o.m;
        int f = o.m[0x8c];
        if (!Attk.getHitProperty(o, 0x2b4, f)) {
            return;
        }
        if (!getHitProperty(o)) {
            return;
        }
        Bytes.fill(m, 0x2c4, 0, 0x44);
        if (Bytes.s16(m, 0x2bc) != (byte) o.m[0x8c]) {
            return;
        }
        int n = Bytes.s16(m, 0x2be);
        hl4.gfx.SprProps.Cell[] cells = o.hitCells;
        for (int k = 0; k < n; k++) {
            int t = (byte) cells[k].type;
            if (t == 3 || t == 4) {
                cells[k].store(m, 0x2c4);
                checkPatternInAttackFrame(o);
            }
            n = Bytes.s16(m, 0x2be);
        }
    }

    private static final int[] ar = new int[4];

    /**
     * MONSTER_checkAttackRect(o, out) @0xa21b4: is the hero inside the attack cell copied to +0x2c4 (type 3: the cell's own rectangle for the
     * facing; type 4: the probe rectangles against the hero rect recorded at +0x310)
     */
    static boolean checkAttackRect(Char o, int[] out) {
        byte[] m = o.m;
        int st = (byte) curSkillType(o);
        int md = m[0x168] & 0xff;
        if (!heroCanDamage()) {
            return false;
        }
        int d = m[0x326] & 0xff;
        if ((d & 0x80) != 0 || (motionDirs(o, m[0x167]) & 0xff) > 2) {
            d = md;
        } else {
            md = d;
        }
        int ct = m[0x2c5];
        int ox = Bytes.s32(m, 8);
        int oy = Bytes.s32(m, 0xc);
        if (ct == 3) {
            if (st != 6 && st != 0 && st != 7) {
                return false;
            }
            int g = (byte) md * 16;
            heroHitRect(hr);
            return Um.collision(ox + Bytes.s32(m, 0x2c8 + g), oy + Bytes.s32(m, 0x2cc + g), Bytes.s32(m, 0x2d0 + g), Bytes.s32(m, 0x2d4 + g), hr[0], hr[1], hr[2],
                    hr[3], out) < 0;
        }
        if (ct != 4) {
            return false;
        }
        if (Monsters.isBoss(o)) {
            later("MONSTER_checkAttackRect: boss (heroInAttackRange_B)");
        }
        int a = (byte) d * 4;
        int g = (byte) md * 16;
        out[0] = Bytes.s32(m, 0x2c8 + g);
        out[1] = Bytes.s32(m, 0x2cc + g);
        out[2] = 0;
        out[3] = 0;
        for (int k = 0; k < 4; k++) {
            hr[k] = Bytes.s32(m, 0x310 + 4 * k);
        }
        int ax = ox + RANGE_A[a];
        int ay = oy + RANGE_A[a + 1];
        boolean r;
        if (st == 3) {
            r = Um.collision(ax, ay, RANGE_A[a + 2], RANGE_A[a + 3], hr[0], hr[1], hr[2], hr[3], out) < 0;
            if (r) {
                m[0x346] = 1;
                return true;
            }
        } else {
            r = Um.collision(ax, ay, RANGE_A[a + 2], RANGE_A[a + 3], hr[0], hr[1], hr[2], hr[3], ar) < 0;
            if (st == 4 || st == 1) {
                m[0x346] = (byte) (r ? 1 : 0);
                return r;
            }
        }
        int bx = ox + RANGE_B[a];
        int by = oy + RANGE_B[a + 1];
        if (st == 3) {
            if (Um.collision(bx, by, RANGE_B[a + 2], RANGE_B[a + 3], hr[0], hr[1], hr[2], hr[3], out) < 0) {
                m[0x346] = 1;
                return true;
            }
            return false;
        }
        boolean r2 = Um.collision(bx, by, RANGE_B[a + 2], RANGE_B[a + 3], hr[0], hr[1], hr[2], hr[3], ar) < 0;
        if (st == 5 || st == 2) {
            boolean v = r || r2;
            m[0x346] = (byte) (v ? 1 : 0);
            return v;
        }
        return false;
    }

    private static final int[] cr = new int[4];

    /** MONSTER_checkPatternInAttackFrame @0xa71ac: on an attack frame, the attack of the current skill (direct hit or an effect on the map) */
    static void checkPatternInAttackFrame(Char o) {
        byte[] m = o.m;
        int st = (byte) curSkillType(o);
        int ox = Bytes.s32(m, 8);
        int oy = Bytes.s32(m, 0xc);
        int dir = Chars.getDirection(o) & 0xff;
        int d326 = m[0x326] & 0xff;
        if ((d326 & 0x80) == 0) {
            dir = d326;
        }
        int enforce = m[0x309] & 0xff;
        // MONSTER_checkCanAttack @0xa24e4
        for (int k = 0; k < 4; k++) {
            cr[k] = 0;
        }
        boolean can = checkAttackRect(o, cr);
        int effIdx = -1;
        int x = 0;
        int y = 0;
        boolean direct = st == 6 || st == 0 || st == 7;
        if (can || !direct) {
            effIdx = (byte) curSkillEffect(o);
            x = cr[0] + (cr[2] >> 1);
            y = cr[1] + (cr[3] >> 1);
        }
        if (!can) {
            if (direct || enforce == 0) {
                return;
            }
        } else if (direct) {
            MonBattle.attackHero(o, m[0x40e], x, y);
            return;
        }
        if (st == 3) {
            if (Monsters.isBoss(o)) {
                later("MONSTER_checkPatternInAttackFrame: boss attack (Boss_AI_AttackDirSet)");
            }
            TEffects.addSunkenOnMap(o, effIdx, 0, 1, -1, m[0x40e], 0, (byte) dir, x, y, 0, 0, 0, 0);
        } else if (st == 4 || st == 1 || st == 5 || st == 2) {
            later("MONSTER_checkPatternInAttackFrame: bullet / lulker skill type " + st);
        }
    }

    static void checkSummon(Char o) {
        later("MONSTER_checkSummon");
    }
}
