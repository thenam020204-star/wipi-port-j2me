package hl4.game;

import hl4.gfx.SprProps;
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

    /** ATTKPRO_getProperty(spr, hero+0x22c, motion): the record of the motion (pointer, count) */
    private static void getProperty(Char h, int motion) {
        h.hitTable = null;
        int n = 0;
        SprProps pr = h.spr.props;
        if (motion != -1 && pr != null) {
            int idx = pr.idx[motion & 0xff];
            if (idx != -1 && idx >= 0 && idx < pr.n && pr.slots[idx] != null) {
                h.hitTable = pr.slots[idx];
                n = h.hitTable.length;
            }
        }
        Bytes.w32(h.m, 0x230, n);
    }

    /** ATTKPRO_getHitProperty(table, frame, hero+0x234): the entry of the frame (id, count, cells) or -1s */
    private static boolean getHitProperty(Char h, int frame) {
        byte[] m = h.m;
        SprProps.Entry[] t = h.hitTable;
        int n = Bytes.s32(m, 0x230);
        if (n <= 0) {
            return true;
        }
        for (int i = 0; i < n; i++) {
            if (t[i].id == (short) frame) {
                Bytes.w16(m, 0x234, t[i].id);
                Bytes.w16(m, 0x236, t[i].nB);
                h.hitCells = t[i].cells;
                return true;
            }
        }
        Bytes.w32(m, 0x234, -1);
        Bytes.w32(m, 0x238, -1);
        h.hitCells = null;
        return false;
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
        if (m[0x166] == 1) {
            if (m[0x167] != 8) {
                throw new IllegalStateException("HERO_process: walking (HERO_set_move / HERO_Key_Release): later");
            }
        } else if (m[0x167] == 0) {
            HeroCtl.moveStop(h);
        }
        checkExtraFrame(h);
        if (m[0x166] == 3) {
            throw new IllegalStateException("HERO_check_attack_frame: later");
        }
        switch (m[0x16b]) {
            case 0:
                break;                      // HERO_HK_process: empty in the original
            default:
                throw new IllegalStateException("HERO_HG/SK/SM_process: later");
        }
        Chars.preProcessTargetEffects(h);
        if (G.event[0x1fe] == 0) {
            throw new IllegalStateException("Map_Check_Collision_Hero_Eventzone / EVENT_CheckEvent: later");
        }
        if (!Event.isEvent()) {
            throw new IllegalStateException("Map_Check_CollisionZone_Move_hero / Map_Check_Hero_Eventzone_Move: later");
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
            throw new IllegalStateException("CHAR_buffProcess / Hero_processUseDrug / regeneration: later");
        }
        G.globalFrame++;
        if (G.globalFrame > 9999) {
            G.globalFrame = 0;
        }
        if (Bytes.s32(m, 0xa4) < 1) {
            throw new IllegalStateException("MainGameFrame_GameOver: later");
        }
        if (Opt.v[7] == 1 && m[0x28c] != 0) {
            throw new IllegalStateException("HERO_extra_set_particles: later");
        }
    }

    // ---- HERO_endProcess -----------------------------------------------------------------------------------------------------------------

    private static void advanceFrame(Char h) {
        byte[] m = h.m;
        m[0x8c]++;
        if (m[0x8c] > h.spr.maxFrame(m[0x167], m[0x168])) {
            m[0x8c] = 0;
        }
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
            throw new IllegalStateException("HERO_endProcess outside events (Hero_end_skilluse / HK..SM_endprocess): later");
        }
        if (G.event[0x5eb] == 0) {
            int d = (byte) dir;
            if (m[0xa31] != 0) {
                if (!h.spr.isEndFrame(m[0x167], d, m[0x8c]) || (m[0xa31] & 0xff) == 0xff) {
                    advanceFrame(h);
                    return;
                }
                m[0xa31]--;
                if (m[0xa31] != 0) {
                    advanceFrame(h);
                    return;
                }
            }
            m[0x8c] = (byte) h.spr.maxFrame(m[0x167], d);
            return;
        }
        throw new IllegalStateException("Event_Heromove: later");
    }
}
