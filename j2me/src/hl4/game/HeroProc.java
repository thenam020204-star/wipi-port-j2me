package hl4.game;

import hl4.gfx.SprProps;
import hl4.gfx.Sprite;
import hl4.sys.Bytes;

/**
 * The hero's per-tick work: HERO_process @0x8ce2c (pre-process), HERO_endProcess @0x8e808, HERO_check_extra_frame @0x8b2a8 (frame events of the attack property
 * table), HERO_check_pierce @0x86ea8, HERO_serch_move_area @0x8b824, HERO_touchProcess @0x8c784 (touch screen path: only the idle case).
 * Paths that need the not yet ported hero control (walking, skills, event zones, buffs/drugs) stop with "later" where the original would enter them.
 */
public final class HeroProc {
    private static final byte[] M = G.map;

    private HeroProc() {
    }

    // ---- attack property of the current frame (HERO_check_extra_frame) -----------------------------------------------------------------------

    /** ATTKPRO_getProperty(spr, hero+0x22c, motion) */
    private static void getProperty(Char h, int motion) {
        Attk.getProperty(h, 0x22c, motion);
    }

    /** ATTKPRO_getHitProperty(hero+0x22c, frame, hero+0x234) */
    private static boolean getHitProperty(Char h, int frame) {
        return Attk.getHitProperty(h, 0x22c, frame);
    }

    /** word 'i' of group 'dir' of cell k (the groups of the original's 0x44-byte cells; a direction past the 4th group reads on into the next cell) */
    private static int gv(SprProps.Cell[] cells, int k, int dir, int i) {
        int off = 4 + 16 * dir + 4 * i;
        int c = k + off / 0x44;
        int w = off % 0x44;
        if (c >= cells.length) {
            return 0;
        }
        if (w < 4) {
            return cells[c].type << 8;
        }
        return cells[c].g[(w - 4) >> 4][((w - 4) >> 2) & 3];
    }

    /** HERO_check_extra_frame(h) @0x8b2a8: the property cells of the current frame (hit box, shake, flags, particles) */
    public static void checkExtraFrame(Char h) {
        byte[] m = h.m;
        int dir = m[0x168] & 0xff;
        int motion = m[0x167];
        if (motion < 0) {
            return;
        }
        getProperty(h, motion);
        m[0x287] = (byte) 0xff;
        m[0x280] = 0;
        m[0x283] = 0;
        m[0x284] = 0;
        m[0x28c] = 0;
        if (h.hitTable == null) {
            return;
        }
        int frame = m[0x8c];
        if (!getHitProperty(h, frame)) {
            return;
        }
        Bytes.fill(m, 0x23c, 0, 0x44);
        int c5 = 0;
        if (Bytes.s16(m, 0x234) == frame && Bytes.s16(m, 0x236) > 0) {
            int n = Bytes.s16(m, 0x236);
            SprProps.Cell[] cells = h.hitCells;
            for (int k = 0; k < n; k++) {
                int t = cells[k].type;
                int a = gv(cells, k, dir, 0);
                int b = gv(cells, k, dir, 1);
                if (t == 3 || t == 4) {
                    cells[k].store(m, 0x23c);
                } else if (t == 5) {
                    if (a < 1) {
                        if (b > 0) {
                            Cam.setShake(1, (byte) b, 1);
                        }
                    } else if (b < 1) {
                        Cam.setShake(1, (byte) a, 0);
                    } else {
                        Cam.setShake(1, (byte) a, 2);
                    }
                } else if (t == 6) {
                    m[0x280] = 1;
                    m[0x281] = (byte) a;
                    m[0x282] = (byte) b;
                } else if (t == 7) {
                    m[0x285] = 1;
                    m[0x286] = (byte) a;
                } else if (t == 8) {
                    m[0x283] = 1;
                    m[0x284] = (byte) a;
                } else if (t == 9) {
                    m[0x287] = (byte) a;
                    m[0x288] = (byte) b;
                } else if (t == 10) {
                    M[0x34e] = (byte) a;
                    c5 = (byte) b;
                    M[0x34f] = (byte) c5;
                } else if (t == 11) {
                    Bytes.w16(M, 0x356, a);
                    m[0x170] = 1;
                } else if (t == 12) {
                    m[0x289] = 1;
                    m[0x28a] = (byte) a;
                    m[0x28b] = (byte) b;
                } else if (t == 13) {
                    m[0x28c] = 1;
                    Bytes.w16(m, 0x28e, a);
                    Bytes.w16(m, 0x290, b);
                    m[0x28d] = (byte) gv(cells, k, dir, 2);
                }
            }
            if (c5 != 0) {
                return;
            }
        }
        M[0x34e] = (byte) 0xff;
    }

    // ---- collision helpers --------------------------------------------------------------------------------------------------------------

    private static final int[] OUT = new int[4];

    /** HERO_check_pierce @0x86ea8: true while the hero passes through monsters (post-hit invulnerability, piercing/dodging/teleport moves) */
    public static boolean checkPierce(Char h) {
        byte[] m = h.m;
        if (m[0xa44] != 0) {
            return true;
        }
        if (m[0x211] != 0) {
            return true;
        }
        if (m[0x189] == 0 && m[0x167] == 8 && m[0xd0c] == 3) {
            return true;
        }
        return m[0x189] == 1 && (m[0x167] & 0xff) - 8 >= 0 && (m[0x167] & 0xff) - 8 < 2;
    }

    /** Map_Check_Collision_Move_Two_Rect @0xa5cfc: shortens the pending move (s32 at m[ox], m[oy]) so rect 1 does not enter rect 2; 0 when it was shortened */
    public static int moveTwoRect(int p1, int p2, int p3, int p4, int p5, int p6, int p7, int p8, byte[] m, int ox, int oy) {
        int l20 = Bytes.s32(m, ox);
        int l1c = Bytes.s32(m, oy);
        if (l1c == 0 && l20 == 0) {
            return 0;
        }
        int a = l20 != 0 ? 1 : 0;
        int b = l1c != 0 ? 1 : 0;
        int sel;
        if ((a & b) == 0) {
            if (a != 0) {
                sel = ~l20 >>> 31;
            } else if (b != 0) {
                sel = l1c < 0 ? 2 : 3;
            } else {
                sel = 0;
            }
        } else {
            return 1;                       // diagonal: no case in the original's switch
        }
        int[] o = OUT;
        switch (sel) {
            case 0: {
                if (l20 < 0) {
                    l20 = -l20;
                }
                if (Um.collision(p1 - l20, p2, l20, p4, p5, p6, p7, p8, o) < 0) {
                    Bytes.w32(m, ox, (o[2] + o[0]) - p1);
                    return 0;
                }
                break;
            }
            case 1: {
                p3 = p3 + p1;
                if (Um.collision(p3, p2, l20, p4, p5, p6, p7, p8, o) < 0) {
                    Bytes.w32(m, ox, o[0] - p3);
                    return 0;
                }
                break;
            }
            case 2: {
                l1c = l1c < 0 ? -l1c : l1c;
                if (Um.collision(p1, p2 - l1c, p3, l1c, p5, p6, p7, p8, o) < 0) {
                    Bytes.w32(m, oy, (o[3] + o[1]) - p2);
                    return 0;
                }
                break;
            }
            default: {
                p4 = p4 + p2;
                if (Um.collision(p1, p4, p3, l1c, p5, p6, p7, p8, o) < 0) {
                    Bytes.w32(m, oy, o[1] - p4);
                    return 0;
                }
                break;
            }
        }
        return 1;
    }

    /** HERO_serch_move_area(h) @0x8b824: the pending move (+0x158/+0x15c) is cut by the live monsters' move rectangles, then by the summon's */
    public static void searchMoveArea(Char h) {
        byte[] m = h.m;
        int px = Bytes.s32(m, 0x158);
        int py = Bytes.s32(m, 0x15c);
        if (G.monsterList.isEmpty()) {
            return;
        }
        int w = Bytes.s32(m, 0x30);
        int hh = Bytes.s32(m, 0x34);
        int x = Bytes.s32(m, 0x28) + h.x();
        int y = Bytes.s32(m, 0x2c) + h.y();
        for (int i = 0;; i++) {
            if (i > 0x7e) {
                Obj s = Pool.summon;
                moveTwoRect(x, y, w, hh, Bytes.s32(s.m, 0x148), Bytes.s32(s.m, 0x14c), Bytes.s32(s.m, 0x150), Bytes.s32(s.m, 0x154), m, 0x158, 0x15c);
                return;
            }
            Obj o = G.monsterList.a[i];
            if (o == null || Monsters.bossNotCheckCollision(o) || Monsters.isDie(o) || Monsters.isHidding(o) || Monsters.isConceal((Char) o) || Monsters.isTeleport((Char) o)) {
                continue;
            }
            moveTwoRect(x, y, w, hh, Bytes.s32(o.m, 0x148), Bytes.s32(o.m, 0x14c), Bytes.s32(o.m, 0x150), Bytes.s32(o.m, 0x154), m, 0x158, 0x15c);
            if (Bytes.s32(m, 0x158) == px || Bytes.s32(m, 0x15c) == py) {
                continue;
            }
            return;
        }
    }

    // ---- event zones / regeneration ------------------------------------------------------------------------------------------------------

    private static final int[] zoneBuf = new int[4];

    /** Map_Check_Collision_Hero_Eventzone @0xa59c8: index of the event-zone record (types 1, 3, 5, 6) the hero rectangle, shifted by its pending move, overlaps most; -1 when none */
    public static int eventZoneCollision(int p1, int p2, int p3, int p4, byte[] m, int ox, int oy) {
        byte[] e = G.event;
        int cnt = e[8];
        if (cnt < 1) {
            return -1;
        }
        int vx = Bytes.s32(m, ox);
        int vy = Bytes.s32(m, oy);
        if (vx != 0 && vy == 0) {
            p1 += vx;
        } else if (vx == 0 && vy != 0) {
            p2 += vy;
        }
        int[] z = zoneBuf;
        z[0] = 0;
        z[1] = 0;
        z[2] = 0;
        z[3] = 0;
        int n = 0;
        int tw = M[0x7e];
        int th = M[0x7f];
        for (int k = 1; k < cnt; k++) {
            int r = Event.rec(k);
            int t = e[r + 7];
            if (t != 1 && t != 3 && t != 5 && t != 6) {
                continue;
            }
            int w = t == 5 ? tw * 3 : tw;
            int hh = t == 6 ? th * 3 : th;
            int a = Um.collision(p1, p2, p3, p4, Bytes.s16(e, r), Bytes.s16(e, r + 2), w, hh, OUT);
            if (a < 0 && n < 2) {
                z[n] = a;
                z[n + 2] = k;
                n++;
            }
        }
        if (n == 1) {
            return z[2];
        }
        if (n != 2) {
            return -1;
        }
        int a0 = z[0] < 0 ? -z[0] : z[0];
        int a1 = z[1] < 0 ? -z[1] : z[1];
        if (a0 != a1 && a0 <= a1) {
            return z[3];
        }
        return z[2];
    }

    /** Map_Check_Hero_Eventzone_Move @0xa5f20: the pending move is cut by every solid event-zone record (flag +4 set, type != 7) */
    public static void eventZoneMove(int p1, int p2, int p3, int p4, byte[] m, int ox, int oy) {
        byte[] e = G.event;
        int cnt = e[8];
        for (int k = 1; k < cnt; k++) {
            int r = Event.rec(k);
            if (e[r + 4] == 0 || e[r + 7] == 7) {
                continue;
            }
            moveTwoRect(p1, p2, p3, p4, Bytes.s16(e, r), Bytes.s16(e, r + 2), M[0x7e], M[0x7f], m, ox, oy);
        }
    }

    /** the hero's per-tick timers once the cut scene is over: buff/status countdowns (CHAR_buffProcess @0xdbe3c), drug timers (Hero_processUseDrug @0x492d0), HP/SP regeneration, skill and item cool-downs */
    private static void regen(Char h) {
        byte[] m = h.m;
        for (int i = 0; i < 3; i++) {
            int o = i * 8;
            if (m[0xf4 + o] == 1) {
                int v = Bytes.s16(m, 0xfa + o) - 1;
                Bytes.w16(m, 0xfa + o, v);
                if (v < 0) {
                    throw new IllegalStateException("CHAR_removeBuff (buff expiry): later");
                }
            }
        }
        for (int i = 0; i < 4; i++) {
            int o = i * 8;
            if (m[0x10c + o] == 1) {
                int v = Bytes.s16(m, 0x112 + o) - 1;
                Bytes.w16(m, 0x112 + o, v);
                if (v < 0) {
                    throw new IllegalStateException("CHAR_removeEtcStatus (status expiry): later");
                }
            }
        }
        for (int o = 0xa3a; o <= 0xa3e; o += 2) {
            if (Bytes.s16(m, o) > 0) {
                Bytes.w16(m, o, Bytes.s16(m, o) - 1);
            }
        }
        if (m[0x166] == 0) {
            if (m[0xd0a] > 0x50) {
                addSp(h, Bytes.s16(m, 0x1ba));
                Chars.addHp(h, Bytes.s16(m, 0xd0));
                m[0xd0a] = 0;
            }
            m[0xd0a]++;
        } else {
            m[0xd0a] = 0;
        }
        if (G.globalFrame % 20 == 0) {
            addSp(h, Bytes.s16(m, 0x1be));
            Chars.addHp(h, Bytes.s16(m, 0xca));
            if (Bytes.s32(m, 0xd30) < 1) {
                Bytes.w32(m, 0xd30, 0);
            } else {
                Chars.addHp(h, Bytes.s32(m, 0xd2c));
                Bytes.w32(m, 0xd30, Bytes.s32(m, 0xd30) - Bytes.s32(m, 0xd2c));
            }
        }
        if (Bytes.s16(m, 0xc6) > 0) {
            throw new IllegalStateException("HERO_process: instant heal effect (CHAR_addCommonEffect): later");
        }
        if (Bytes.s16(m, 0x1c4) > 0) {
            addSp(h, Bytes.s16(m, 0x1c4));
            Bytes.w16(m, 0x1c4, 0);
        }
        for (int i = 0; i < 16; i++) {
            int o = i * 0x28;
            if (m[0x2b8 + o] > 0 && Bytes.s16(m, 0x2ba + o) < Bytes.s16(m, 0x29e + o)) {
                Bytes.w16(m, 0x2ba + o, Bytes.s16(m, 0x2ba + o) + 1);
            }
        }
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 6; j++) {
                int b = 0x1f4 + i + j * 2;
                if (m[b] > 1) {
                    Obj it = Items.itemSearch(8, m[b + 0xc]);
                    if (it != null && Bytes.s16(it.m, 0x26a) < Bytes.s16(it.m, 0x268)) {
                        Bytes.w16(it.m, 0x26a, Bytes.s16(it.m, 0x26a) + 1);
                    }
                }
            }
        }
    }

    /** HERO_addSp(h, n) @0x86a2c */
    public static void addSp(Char h, int n) {
        int v = n + Bytes.s32(h.m, 0x218);
        int max = Bytes.s32(h.m, 0x21c);
        Bytes.w32(h.m, 0x218, max < v ? max : v);
    }

    // ---- HERO_touchProcess ---------------------------------------------------------------------------------------------------------------

    /** HERO_touchProcess(h) @0x8c784: the touch-screen steering; with no touch target and no touch destination (+0xd18/+0xd1c = -1) it does nothing */
    public static void touchProcess(Char h) {
        byte[] m = h.m;
        if (Bytes.s32(m, 0xd20) != 0) {
            throw new IllegalStateException("HERO_touchProcess: touch target (not ported: keypad build)");
        }
        if (Bytes.s32(m, 0xd18) < 0 || Bytes.s32(m, 0xd1c) < 0) {
            return;
        }
        throw new IllegalStateException("HERO_touchProcess: touch destination (not ported: keypad build)");
    }

    /**
     * HERO_process, walking: the per-tick step is 0x30 / (frames of the walk animation) px (2/3 on tile property 7, halved by a slow buff); a touch destination
     * (+0xd18 / +0xd1c >= 0) can shorten the last step to the remaining distance - with the keypad both are -1 and the full step is taken
     */
    private static void walkStep(Char h) {
        byte[] m = h.m;
        int dir = m[0x168];
        int sp = 0x30 / (h.spr.maxFrame(m[0x167], dir) + 1);
        if (m[0x16e] == 7) {
            sp = (sp * 2) / 3;
        }
        if (m[0x114] != 0 && m[0x116] == 1) {
            sp = (sp + (sp >>> 31)) >> 1;               // CHAR_isSlowBuff
        }
        int tx = Bytes.s32(m, 0xd18);
        int ty = Bytes.s32(m, 0xd1c);
        if (tx >= 0 || ty >= 0) {
            throw new IllegalStateException("HERO_process: touch destination step (not ported: keypad build)");
        }
        HeroCtl.setMove(h, sp, dir);
    }

    // ---- HERO_process --------------------------------------------------------------------------------------------------------------------

    /** HERO_process(h) @0x8ce2c */
    public static void process(Char h) {
        byte[] m = h.m;
        if (Bytes.s16(M, 0x356) > 0 && m[0x170] == 0) {
            return;
        }
        if (m[0x166] == 4) {
            return;
        }
        int bt = M[0x79] & 0xff;
        if (bt - 1 >= 0 && bt - 1 < 2) {
            m[0xa41] = (byte) (G.equip[4] == null ? 2 : 1);
        } else {
            m[0xa41] = 0;
        }
        Chars.process(h, false);
        touchProcess(h);
        if (m[0x166] != 0xf && m[0xa42] != 0) {
            m[0xa43]++;
            if (m[0xa43] > 0x63) {
                m[0xa43] = 0;
                m[0xa42] = 0;
            }
        }
        if (m[0x166] == 0xf && m[0x167] == 6) {
            m[0xa42]++;
            if (m[0xa42] > 0x14) {
                m[0xd16] = 0;
                m[0x166] = 0;
            }
        }
        if (m[0xa44] != 0) {
            m[0xa44]++;
            if (m[0xa44] > 0x28) {
                m[0xa44] = 0;
            }
        }
        if (m[0x20f] != 0 && m[0x210] <= 0x2c) {
            m[0x210] -= 9;
            if ((m[0x210] & 0x80) != 0) {
                m[0x210] = 0;
            }
        }
        if (m[0x217] > 0) {
            m[0x217]--;
        }
        if (m[0xa30] != 0) {
            throw new IllegalStateException("HERO_process: stamina drain (HERO_minusSp): later");
        }
        int x = h.x();
        int y = h.y();
        if (x < 0) {
            x += 15;
        }
        if (y < 0) {
            y += 15;
        }
        m[0x16e] = (byte) Coll.tileProp((x << 20) >> 24, (y << 20) >> 24);
        boolean skipStop = false;
        if (m[0x166] == 1) {
            if (m[0x167] == 8) {
                skipStop = true;
            } else {
                walkStep(h);
            }
        }
        if (!skipStop && m[0x167] == 0) {
            HeroCtl.moveStop(h);
        }
        checkExtraFrame(h);
        if (m[0x166] == 3) {
            HeroAttack.checkAttackFrame(h);
        }
        switch (m[0x16b]) {
            case 0:
                break;                      // HERO_HK_process: empty in the original
            default:
                throw new IllegalStateException("HERO_HG/SK/SM_process: later");
        }
        Chars.preProcessTargetEffects(h);
        int zx = Bytes.s32(m, 0x28) + h.x();
        int zy = Bytes.s32(m, 0x2c) + h.y();
        int zw = Bytes.s32(m, 0x30);
        int zh = Bytes.s32(m, 0x34);
        if (G.event[0x1fe] == 0) {
            int idx = eventZoneCollision(zx, zy, zw, zh, m, 0x158, 0x15c);
            Bytes.w32(G.event, 4, idx);
            Event.checkEvent(idx);
        }
        if (!Event.isEvent()) {
            HeroColl.zoneMoveHero(zx, zy, zw, zh, m, 0x158, 0x15c);
            eventZoneMove(zx, zy, zw, zh, m, 0x158, 0x15c);
        }
        if (Bytes.s32(m, 0x158) == 0 && Bytes.s32(m, 0x15c) == 0 && m[0x167] != 0 && m[0x167] != 2
                && (Bytes.s32(m, 0xd18) >= 0 || Bytes.s32(m, 0xd1c) >= 0)) {
            throw new IllegalStateException("HERO_Key_Release (touch): later");
        }
        if (!checkPierce(h)) {
            searchMoveArea(h);
        }
        if (Event.isEvent() || m[0x167] != 6) {
            Bytes.w32(m, 8, h.x() + Bytes.s32(m, 0x158));
            Bytes.w32(m, 0xc, h.y() + Bytes.s32(m, 0x15c));
            HeroCtl.moveStop(h);
        }
        if (!Event.isEvent()) {
            regen(h);
        }
        G.globalFrame++;
        if (G.globalFrame > 9999) {
            G.globalFrame = 0;
        }
        if (Bytes.s32(m, 0xa4) < 1) {
            MainGameFrame.gameOver();
        }
        if (Opt.v[7] == 1 && m[0x28c] != 0) {
            Particles.extraSetParticles(m[0x28d], h.x() + Bytes.s16(m, 0x28e), h.y() + Bytes.s16(m, 0x290));
        }
    }

    // ---- HERO_endProcess -----------------------------------------------------------------------------------------------------------------

    private static void advanceFrame(Char h, int d) {
        byte[] m = h.m;
        m[0x8c]++;
        if (m[0x8c] > h.spr.maxFrame(m[0x167], d)) {
            m[0x8c] = 0;
        }
    }

    /** HERO_endProcess outside events: frame stepping, skill end, class end-process, vibration, queued moves */
    private static void endPlay(Char h, int dir) {
        byte[] m = h.m;
        Sprite sp = h.spr;
        int d = (byte) dir;
        if (m[0x166] == 4) {
            if (!sp.isEndFrame(5, 0, m[0x8c])) {
                m[0x8c]++;
            }
        } else {
            if (sp.isEndFrame(m[0x167], d, m[0x8c]) && m[0x166] == 3) {
                if (m[0x20d] == 0x11 ? HeroAttack.endSkillUse(h, m[0xd0c], 1) : HeroAttack.endSkillUse(h, 0, 3)) {
                    return;
                }
            }
            switch (m[0x16b]) {
                case 0:
                    hkEndProcess(h, d);
                    break;
                default:
                    throw new IllegalStateException("HERO_HG/SK/SM_endprocess: later");
            }
        }
        if (m[0x167] < 8) {
            m[0xd0c] = (byte) 0xff;
            m[0x211] = 0;
        }
        if (m[0xa2e] == 1) {
            m[0xa2e] = 0;
            m[0xa2d] = 0;                       // SM_Vibrator(1, 150) when the vibration option is on: no effect on the state
        }
        if (m[0xa2f] > 0) {
            m[0xa2f] = 0;                       // SM_Vibrator: likewise
        }
        if (m[0x285] != 0) {
            HeroCtl.setMove(h, m[0x286], m[0x168]);         // the queued move of the attack frame (cell type 7)
        }
        m[0x285] = 0;
        if (m[0x289] != 0) {
            throw new IllegalStateException("HERO_setTeleportMove: later");
        }
        m[0x289] = 0;
        int c = Bytes.u16(m, 0xd10);
        if (c != 0 && c < 0x8000) {
            Bytes.w16(m, 0xd10, c - 1);
        }
    }

    /** HERO_HK_endprocess @0x81374: the end of a motion step of the first class (fighter) */
    private static void hkEndProcess(Char h, int dir) {
        byte[] m = h.m;
        Sprite sp = h.spr;
        if (!sp.isEndFrame(m[0x167], dir, m[0x8c])) {
            int c = m[0xd0c];
            boolean air = c == 5 ? m[0x20e] > 1 : (c == 10 || c == 2);
            if (air && m[0x20f] != 0 && m[0x8c] == 1) {
                m[0x210] = 0x1e;
            }
            int s = m[0x166] & 0xff;
            if (s - 0xe > 1 || s < 0xe) {
                m[0x8c]++;
            }
            return;
        }
        if (m[0xd0c] == 0) {
            int c = m[0x20e];
            boolean add = false;
            if (c == 1) {
                add = true;
            } else if (c == 2 && m[0x2b8] < 5) {
                add = true;
            } else if (c == 3 && m[0x2b8] >= 5) {
                add = true;
            }
            if (add) {
                Bytes.w16(m, 0x2ba, 0);
                Bytes.w16(m, 0x2ba, Bytes.s16(m, 0x2ba) + (short) Bytes.s16(m, 0x1cc) * (short) Bytes.s16(m, 0x29e) / 100);
            }
        }
        switch (m[0x167]) {
            case 1:
            case 3:
                HeroCtl.setStatusAir(h, 0);
                m[0x20e] = 0;
                m[0x168] = (byte) dir;
                m[0x166] = 1;
                m[0x174] = (byte) 0xff;
                m[0x167] = 1;
                m[0x8c] = 0;
                sp.f6c = 0;
                m[0x229] = 0;
                m[0x226] = 0;
                m[0x227] = 0;
                m[0x228] = 0;
                return;
            case 4:
            case 0x12:
            case 0x1a:
                HeroCtl.setStandMotion(h, 0, 0, dir);
                return;
            case 6:
                HeroCtl.setStandMotion(h, 0, 1, dir);
                return;
            case 8:
                HeroCtl.setStandMotion(h, 1, 0, dir);
                return;
            case 10:
                if (m[0xd0c] == 3) {
                    m[0x167] = 8;
                    m[0x168] = (byte) inverseDirection(m[0x168]);
                    m[0x20e] = 0;
                    m[0x174] = (byte) 0xff;
                    m[0x8c] = 0;
                    m[0x166] = 0;
                    return;
                }
                if (m[0xd0c] == 6) {
                    startCombo(m, 0x18);
                    return;
                }
                break;
            case 0x17:
                if (m[0xd0c] == 6) {
                    startCombo(m, 0x18);
                    return;
                }
                break;
            case 0x18:
            case 0x19:
                if (m[0xd0c] == 6) {
                    startCombo(m, 0x1a);
                    return;
                }
                break;
            case 0x15:
                if (m[0xd0c] == 4) {
                    m[0x8c] = 0;
                }
                return;
            default:
                break;
        }
        if (m[0xa31] != 0) {
            HeroCtl.setMotion(h, m[0x167], dir);
            return;
        }
        if (m[0x22b] != 0) {
            m[0x22b] = 0;
            m[0x168] = m[0x22a];
            dir = m[0x22a];
        }
        HeroCtl.setStandMotion(h, 0, 0, dir);
    }

    private static void startCombo(byte[] m, int motion) {
        m[0x167] = (byte) motion;
        m[0x20e]++;
        m[0x174] = 6;
        m[0x8c] = 0;
        m[0x166] = 3;
        m[0xd0c] = 6;
    }

    /** HERO_get_inverseDirection @0x8662c */
    private static int inverseDirection(int d) {
        if (d == 0) {
            return 1;
        }
        if (d == 1) {
            return 0;
        }
        if (d == 2) {
            return 3;
        }
        if (d == 3) {
            return 2;
        }
        return d;
    }

    /** HERO_endProcess(h) @0x8e808 */
    public static void endProcess(Char h) {
        byte[] m = h.m;
        int dir = m[0x168] & 0xff;
        if (Bytes.s16(M, 0x356) >= 1 && m[0x170] == 0) {
            return;
        }
        int c = m[0xd15];
        if (c <= 0) {
            m[0x226 + (byte) dir] = 0;
        } else {
            m[0xd15] = (byte) (c - 1);
        }
        if (Bytes.s16(m, 0x212) < 1000) {
            Bytes.w16(m, 0x212, Bytes.s16(m, 0x212) + 1);
        }
        if (m[0x167] == 6 && h.spr.maxFrame(6, (byte) dir) <= m[0x8c] + 3 && m[0x166] != 0xf && m[0xa42] == 0) {
            m[0x166] = 0xf;
            m[0xd16] = 1;
        }
        Chars.endProcessTargetEffects(h);
        if (!Event.isEvent()) {
            endPlay(h, dir);
            return;
        }
        if (G.event[0x5eb] == 0) {
            int d = (byte) dir;
            if (m[0xa31] != 0) {
                if (!h.spr.isEndFrame(m[0x167], d, m[0x8c]) || (m[0xa31] & 0xff) == 0xff) {
                    advanceFrame(h, d);
                    return;
                }
                m[0xa31]--;
                if (m[0xa31] != 0) {
                    advanceFrame(h, d);
                    return;
                }
            }
            m[0x8c] = (byte) h.spr.maxFrame(m[0x167], d);
            return;
        }
        Event.heromove();
        if (m[0x173] == 0) {
            advanceFrame(h, (byte) dir);
        }
    }
}
