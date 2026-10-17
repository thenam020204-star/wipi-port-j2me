package hl4.game;

import hl4.gfx.Img;
import hl4.gfx.Sprite;
import hl4.sys.Bytes;
import hl4.sys.Res;
import hl4.sys.Rng;

/** Non-player characters (the pooled 0x1cc-byte structs of type 1): NPC_* @0xb8990..0xba354.  +0x160 data id, +0x1a8 walk count, +0x1aa/+0x1ac move delay, +0x1b0 data record. */
public final class Npcs {
    public static int heartFrame;
    public static int cannonFrame;

    private Npcs() {
    }

    /** NPC_setMoveDelay @0xb8990 */
    public static void setMoveDelay(Obj n) {
        Bytes.w16(n.m, 0x1aa, Bytes.s16(n.m, 0x1ac));
    }

    /** NPC_set(n) @0xba354 / NPC_return's reset: pool-slot state (no data record attached) */
    public static void set(Char n) {
        Chars.set(n);
        Chars.init(n, 0, 0, 0, 0, 0, ((Bytes.s16(n.m, 0x160) - 0x29) & 0xffff) > 1 ? 1 : 0, 4);
        n.m[0x1a8] = 4;
        setMoveDelay(n);
        n.m[0x1b4] = 0;
        n.m[0x1c3] = 0;
        Bytes.w32(n.m, 0x1b8, 0);
        Bytes.w32(n.m, 0x1bc, 0);
        n.m[0x1c0] = 0;
        n.m[0x1c1] = 0;
        n.m[0x1c2] = 0;
        n.m[0x1c5] = 0;
        heartFrame = 0;
        cannonFrame = 0;
        n.m[0x171] = (byte) 0xff;
        n.m[0x1c8] = 0;
    }

    /**
     * NPC_return(n) @0xba330 (= MP_return case 1): CHAR_destroy, then the NPC_set body WITHOUT clearing the data record: a slot that still names its
     * event record goes back to that record's stand / walk state (record +6: 1..10 walk, 0 / 11.. stand with a direction; +4 == 5: facing 0).
     */
    public static void ret(Char n) {
        Chars.destroy(n);
        Chars.init(n, 0, 0, 0, 0, 0, ((Bytes.s16(n.m, 0x160) - 0x29) & 0xffff) > 1 ? 1 : 0, 4);
        n.m[0x1a8] = 4;
        setMoveDelay(n);
        if (n.evRef >= 0) {
            int r = Event.rec(n.evRef);
            int b = Event.e[r + 6] & 0xff;
            if (b - 1 < 10) {
                setMove(n, 1);
            } else {
                if (b < 0xb) {
                    Chars.setDirection(n, (byte) b);
                } else {
                    Chars.setDirection(n, (byte) (b - 0xb));
                }
                setStand(n);
            }
            if (Event.e[r + 4] == 5) {
                Chars.setDirection(n, 0);
            }
        }
        n.m[0x1b4] = 0;
        n.m[0x1c3] = 0;
        Bytes.w32(n.m, 0x1b8, 0);
        Bytes.w32(n.m, 0x1bc, 0);
        n.m[0x1c0] = 0;
        n.m[0x1c1] = 0;
        n.m[0x1c2] = 0;
        n.m[0x1c5] = 0;
        heartFrame = 0;
        cannonFrame = 0;
        n.m[0x171] = (byte) 0xff;
        n.m[0x1c8] = 0;
    }

    private static String n3(int v) {
        return v < 10 ? "00" + v : (v < 100 ? "0" + v : String.valueOf(v));
    }

    /** NPC_dataLoad(n, id) @0xaa10c: record id of /NPC/_NPCG_DAT (16 bytes each) */
    public static void dataLoad(Char n, int id) {
        byte[] d = Res.load("/NPC/_NPCG_DAT");
        if (d == null) {
            return;
        }
        byte[] m = n.m;
        int b = id * 16;
        Bytes.w16(m, 0x160, id);
        Bytes.w16(m, 0x162, d[b + 2]);
        m[0x16b] = d[b + 3];
        Bytes.w16(m, 0x188, d[b + 4]);
        for (int j = 0; j < 5; j++) {
            Bytes.w16(m, 0x18a + 2 * j, d[b + 5 + j]);
        }
        Bytes.w16(m, 0x198, d[b + 10]);
        for (int j = 0; j < 5; j++) {
            Bytes.w16(m, 0x19a + 2 * j, d[b + 11 + j]);
        }
        Bytes.w16(m, 0x1ac, 0x14);
        Bytes.w32(m, 0x34, 0x10);
        Bytes.w32(m, 0x30, 0x10);
        Bytes.w32(m, 0x2c, -8);
        Bytes.w32(m, 0x28, -8);
    }

    /** NPC_spriteLoad @0xaa098: CIF of the sprite id (+0x162) */
    public static void spriteLoad(Char n) {
        int sid = Bytes.s16(n.m, 0x162);
        if (sid != -1) {
            n.spr = new Sprite(Res.load("/H4/CIF/_H_" + n3(sid) + "_CIF"), 1);
        }
    }

    /** loadMonsterImage(spr, set, id, pal) @0xbcee0 */
    public static void loadMonsterImage(Sprite spr, int set, int id, int pal) {
        if (id != -1) {
            spr.setImages(set, Img.loadSprSet("/H4/" + n3(set) + "/_HIMG_" + n3(set) + "_" + n3(id), pal, Maps.timeReturn(G.map[0x68])));
        }
    }

    /** NPC_imageLoad @0xa9e54 */
    public static void imageLoad(Char n) {
        byte[] m = n.m;
        int cnt = n.spr.nSets;
        int i = 0;
        while (i < cnt) {
            int id = Bytes.s16(m, 0x188 + 2 * i);
            if (id == -1) {
                Bytes.w16(m, 0x188 + 2 * i, Bytes.s16(m, 0x188 + 2 * (i + 1)));
                id = Bytes.s16(m, 0x188 + 2 * i);
            }
            loadMonsterImage(n.spr, i, id, -1);
            if (i > 5) {
                if (i != 6) {
                    loadMonsterImage(n.spr, i, 0, -1);
                } else {
                    i++;
                    loadMonsterImage(n.spr, 6, Bytes.s16(m, 0x160) == 0x22 ? 1 : 0, -1);
                    continue;
                }
            }
            i++;
        }
    }

    /** NPC_load(n, id) @0xaa214 */
    public static void load(Char n, int id) {
        n.m[0x165] = 0;
        dataLoad(n, id);
        spriteLoad(n);
        imageLoad(n);
    }

    /** NPC_init(n, idx, x, y) @0xa9d10 */
    public static void init(Char n, int idx, int x, int y) {
        byte[] m = n.m;
        Chars.init(n, 0, idx, x, y, 0, (((Bytes.s16(m, 0x160) - 0x29) & 0xffff) > 1) ? 1 : 0, 4);
        m[0x1a8] = 4;
        setMoveDelay(n);
        if (n.evRef >= 0) {
            int r = Event.rec(n.evRef);
            int t = Event.e[r + 6] & 0xff;
            if (t - 1 < 10 && t >= 1) {
                setMove(n, 1);
            } else {
                if (t < 0xb) {
                    Chars.setDirection(n, (byte) t);
                } else {
                    Chars.setDirection(n, (byte) (t - 0xb));
                }
                setStand(n);
            }
            if (Event.e[Event.rec(n.evRef) + 4] == 5) {
                Chars.setDirection(n, 0);
            }
        }
        m[0x1b4] = 0;
        m[0x1c3] = 0;
        Bytes.w32(m, 0x1b8, 0);
        Bytes.w32(m, 0x1bc, 0);
        m[0x1c0] = 0;
        m[0x1c1] = 0;
        m[0x1c2] = 0;
        m[0x1c5] = 0;
        heartFrame = 0;
        cannonFrame = 0;
        m[0x171] = (byte) 0xff;
        m[0x1c8] = 0;
    }

    /** NPC_create(eventRecord k, eventIdx) @0xaa23c */
    public static void create(int k, int evIdx) {
        Char n = (Char) Pool.getNew(Pool.NPC);
        if (n == null) {
            return;
        }
        n.evRef = k;
        int r = Event.rec(k);
        load(n, Event.e[r + 5]);
        init(n, (byte) G.npcList.count, Bytes.s16(Event.e, r) + 8, Bytes.s16(Event.e, r + 2) + 8);
        G.objList.addObject(n);
        G.npcList.add(n);
        n.m[Obj.EVENT] = (byte) evIdx;
        n.m[0x1c4] = 0;
        int t = Event.e[r + 4] & 0xff;
        if (t > 6) {
            n.m[0x1c4] = (byte) t;
        }
    }

    /** NPC_setStand @0xb8c84 */
    public static void setStand(Obj n) {
        int d = Chars.getDirection(n);
        n.m[0x169] = 0;
        if (n.m[0x172] > 4 && d > 4) {
            n.m[0x16a] = (byte) (d - 5);
        } else {
            n.m[0x16a] = (byte) d;
        }
    }

    /** NPC_setWalk @0xb8c30 */
    public static void setWalk(Obj n, int d) {
        n.m[0x169] = 1;
        if (n.m[0x172] > 4 && d > 4) {
            n.m[0x16a] = (byte) (d - 5);
        } else {
            n.m[0x16a] = (byte) d;
        }
    }

    /** NPC_walkCount @0xb89a8: steps * step length */
    public static void walkCount(Obj n, int steps) {
        byte v = (byte) (steps * n.m[0x1a8]);
        n.m[0x1a6] = v;
        n.m[0x1a7] = v;
    }

    /** NPC_walk(n, steps, dir) @0xb8c3c */
    public static void walk(Obj n, int steps, int dir) {
        if (steps == 0) {
            steps = (byte) Rng.rand(1, 5);
        }
        walkCount(n, steps);
        n.m[0x169] = 1;
        if (n.m[0x172] > 4 && dir > 4) {
            n.m[0x16a] = (byte) (dir - 5);
        } else {
            n.m[0x16a] = (byte) dir;
        }
    }

    /** NPC_setMove(n, p2) @0xb9134: the idle wander AI chooses a direction according to the data record's move type (+6 of the event record) */
    public static void setMove(Char n, int p2) {
        throw new IllegalStateException("NPC_setMove: later (needs CHAR_canMove)");
    }

    /** NPC_evnetMove(n, dx, dy, speed, dirArg) @0xa96f8: scripted walk to a cell (speed 3 = jump to the absolute cell (dx, dy)) */
    public static void evnetMove(Char n, int dx, int dy, int speed, int dirArg) {
        if (n == null) {
            return;
        }
        byte[] m = n.m;
        if (speed == 3) {
            Bytes.w32(m, 0x1b8, dx * 0x10 + 8);
            Bytes.w32(m, 0x1bc, dy * 0x10 + 8);
        } else {
            Bytes.w32(m, 0x1b8, n.x() + dx * 0x10);
            Bytes.w32(m, 0x1bc, n.y() + dy * 0x10);
        }
        if (n.x() != Bytes.s32(m, 0x1b8) || n.y() != Bytes.s32(m, 0x1bc)) {
            if (speed == -1 || speed == 3) {
                Chars.resetPosition(n, Bytes.s32(m, 0x1b8), Bytes.s32(m, 0x1bc));
                if (n.evRef >= 0) {
                    int r = Event.rec(n.evRef);
                    Bytes.w16(Event.e, r, n.x() - 8);
                    Bytes.w16(Event.e, r + 2, n.y() - 8);
                    if (dirArg < 4) {
                        m[0x1c2] = (byte) dirArg;
                    } else {
                        m[0x1c2] = m[0x172];
                    }
                    int d = Chars.convertDir(n, m[0x1c2]);
                    m[0x16a] = (byte) 0xff;
                    m[0x168] = (byte) d;
                    return;
                }
            } else {
                if (speed == 0) {
                    m[0x1c0] = 4;
                } else if (speed == 1) {
                    m[0x1c0] = 8;
                } else if (speed == 2) {
                    m[0x1c0] = 0x10;
                }
                m[0x1c0] = (byte) speed;
                if (dirArg < 4) {
                    m[0x1c2] = (byte) dirArg;
                } else {
                    m[0x1c2] = m[0x172];
                }
                int d = Um.turnDirection(n.x(), n.y(), Bytes.s32(m, 0x1b8), Bytes.s32(m, 0x1bc));
                if (dirArg == -1) {
                    m[0x1c2] = (byte) d;
                }
                m[0x1c1] = (byte) d;
                Chars.setDirection(n, m[0x1c2]);
                if (Chars.getMotion(n) == 0) {
                    setWalk(n, m[0x1c2]);
                } else {
                    Chars.setNextMotionDirection(n, m[0x167], m[0x1c2]);
                }
                m[0x173] = 0;
                m[0x1c3] = 1;
                if (dirArg == 4) {
                    m[0x173] = 1;
                }
            }
        }
    }

    /** NPC_evnetMoveEnd @0xa8ca8 */
    public static void evnetMoveEnd(Obj n) {
        byte[] m = n.m;
        m[0x1c3] = 0;
        if (m[0x1b4] == 0) {
            return;
        }
        m[0x1c2] = 0;
        Bytes.w32(m, 0x1b8, 0);
        Bytes.w32(m, 0x1bc, 0);
        m[0x1c0] = 0;
        m[0x1c1] = 0;
        if (m[0x173] == 0) {
            int d = Chars.getDirection(n);
            m[0x169] = 0;
            if (m[0x172] > 4 && d > 4) {
                m[0x16a] = (byte) (d - 5);
            } else {
                m[0x16a] = (byte) d;
            }
        }
    }

    /** Event_getNpcMove @0x6cc20: true while a scripted NPC walk is in progress */
    public static boolean anyEventMove() {
        for (int i = 0; i < 128; i++) {
            Obj o = G.npcList.a[i];
            if (o != null && o.m[0x1c3] != 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Npc_checkColHero(hero) @0xa9900: an NPC whose move rectangle (+0x148..+0x154) the hero now blocks and whose record type (+6) is 1..10 is
     * moved to the first free neighbouring cell (directions 0..3, 16 px; NPC_evnetMove speed 3).  A failed try leaves +0x148/+0x14c (and the
     * +0x1b8/+0x1bc copies) at the last tried cell, as in the original.
     */
    public static void checkColHero(Char hero) {
        for (int i = 0; i < G.npcList.count; i++) {
            Char n = (Char) G.npcList.a[i];
            byte[] m = n.m;
            int x0 = Bytes.s32(m, 0x148);
            int y0 = Bytes.s32(m, 0x14c);
            if (Coll.isRectMoveableTele(hero, x0, y0, Bytes.s32(m, 0x150), Bytes.s32(m, 0x154), true, true, true)) {
                continue;
            }
            if (((evType(n) & 0xff) - 1 & 0xffffffffL) > 9) {
                continue;
            }
            for (int d = 0; d < 4; d++) {
                int x = x0 + ((Um.vecX(d) << 24) >> 20);
                Bytes.w32(m, 0x1b8, x);
                Bytes.w32(m, 0x148, x);
                int y = y0 + ((Um.vecY(d) << 24) >> 20);
                Bytes.w32(m, 0x14c, y);
                Bytes.w32(m, 0x1bc, y);
                if (Coll.isRectMoveableTele(hero, x, y, Bytes.s32(m, 0x150), Bytes.s32(m, 0x154), true, true, true)) {
                    evnetMove(n, (Bytes.s32(m, 0x1b8) << 20) >> 24, (Bytes.s32(m, 0x1bc) << 20) >> 24, 3, m[0x168]);
                    return;
                }
            }
        }
    }

    // ---- per-tick processing -----------------------------------------------------------------------------------------------------------

    private static int evType(Char n) {
        return Event.e[Event.rec(n.evRef) + 6];
    }

    /** NPC_chageMainState @0xb962c: main state from the current motion (0 stand, 1 walk, 5 scripted walk, 6 other) */
    public static void chageMainState(Char n) {
        byte[] m = n.m;
        int c;
        if (m[0x167] == 0) {
            c = m[0x1c3] == 0 ? 0 : 5;
        } else if (m[0x167] != 1) {
            c = 6;
        } else if (m[0x1c3] == 0) {
            c = 1;
        } else {
            if (Bytes.s16(m, 0x160) == 0x17) {
                m[0x167] = 0;
            }
            c = 5;
        }
        m[0x166] = (byte) c;
    }

    /** NPC_standMotionAtOnce @0xb96d0 */
    public static void standMotionAtOnce(Char n) {
        setStand(n);
        Chars.process(n, true);
        chageMainState(n);
    }

    /** NPC_checkAndMove(n, dir, moveType, apply) @0xb9008: one step (walk speed, 1.5x for the fast move types); true when the way is free */
    public static boolean checkAndMove(Char n, int dir, int type, boolean apply) {
        byte[] m = n.m;
        int[] dx = Coll.scratchX;
        int[] dy = Coll.scratchY;
        dx[0] = 0;
        dy[0] = 0;
        if (m[0x1c0] == 0) {
            dx[0] = Chars.getMoveX(n, dir);
        } else {
            dx[0] = m[0x1c0] * Um.vecX(dir);
        }
        if (m[0x1c0] == 0) {
            dy[0] = Chars.getMoveY(n, dir);
        } else {
            dy[0] = m[0x1c0] * Um.vecY(dir);
        }
        if (m[0x1c0] == 0 && type >= 6 && type < 0xb) {
            dy[0] = dy[0] + (dy[0] >> 1);
            dx[0] = dx[0] + (dx[0] >> 1);
        }
        boolean ok = Coll.charCanMove(n, dx, dy, !Event.isEvent(), false);
        if (ok && apply) {
            Chars.setPosition(n, dx[0], dy[0]);
        }
        return ok;
    }

    /** NPC_preProcess(n) @0xb9aa4 */
    public static void preProcess(Char n) {
        byte[] m = n.m;
        int dir = m[0x168];
        int x = n.x();
        int y = n.y();
        if (x < 0) {
            x += 15;
        }
        if (y < 0) {
            y += 15;
        }
        m[0x16e] = (byte) Coll.tileProp((x << 20) >> 24, (y << 20) >> 24);
        if (Chars.process(n, false)) {
            chageMainState(n);
        }
        int st = m[0x166];
        if (st != 0) {
            if (st == 1) {
                if (!Event.isEvent()) {
                    if (!checkAndMove(n, dir, evType(n), true)) {
                        int d = Chars.turnCorner(n, dir);
                        if (d == -1) {
                            standMotionAtOnce(n);
                        } else {
                            checkAndMove(n, d, evType(n), true);
                        }
                    }
                }
            } else {
                int tx = Bytes.s32(m, 0x1b8);
                int ty = Bytes.s32(m, 0x1bc);
                if ((tx >= 1 || ty >= 1) && m[0x1c3] != 0) {
                    Chars.setPosition(n, m[0x1c0] * Um.vecX(m[0x1c1]), m[0x1c0] * Um.vecY(m[0x1c1]));
                    boolean arrived;
                    switch (m[0x1c1]) {
                        case 0:
                            arrived = ty >= n.y();
                            break;
                        case 1:
                            arrived = n.y() >= ty;
                            break;
                        case 2:
                            arrived = tx >= n.x();
                            break;
                        case 3:
                            arrived = n.x() >= tx;
                            break;
                        default:
                            arrived = false;
                            break;
                    }
                    if (arrived) {
                        Chars.resetPosition(n, tx, ty);
                    }
                }
            }
        }
        if (n.evRef >= 0) {
            int r = Event.rec(n.evRef);
            Bytes.w16(Event.e, r, n.x() - 8);
            Bytes.w16(Event.e, r + 2, n.y() - 8);
        }
        Chars.preProcessTargetEffects(n);
    }

    private static boolean advanceFrame(Char n) {
        byte[] m = n.m;
        m[0x8c]++;
        int mx = n.spr.maxFrame(m[0x167], m[0x172]);
        if (m[0x8c] > mx) {
            m[0x8c] = 0;
        }
        return true;
    }

    private static boolean isEndFrame(Char n) {
        return n.spr.maxFrame(n.m[0x167], n.m[0x172]) <= n.m[0x8c];
    }

    private static void holdLastFrame(Char n) {
        byte[] m = n.m;
        m[0x1b4] = (byte) 0xfe;
        m[0x8c] = (byte) n.spr.maxFrame(m[0x167], m[0x172]);
    }

    /** the tail shared by the walk/stand outcomes: next motion + next direction (the 5..7 directions fold onto 0..2 for 8-direction sprites) */
    private static void setNext(Char n, int motion, int dir) {
        byte[] m = n.m;
        m[0x169] = (byte) motion;
        if (m[0x172] > 4 && dir > 4) {
            m[0x16a] = (byte) (dir - 5);
        } else {
            m[0x16a] = (byte) dir;
        }
    }

    /** NPC_endProcess(n) @0xb93c8: frame counter, idle wander decisions and the scripted-walk bookkeeping */
    public static void endProcess(Char n) {
        byte[] m = n.m;
        Chars.endProcessTargetEffects(n);
        int st = m[0x166];
        if (st == 0) {
            int t = evType(n) & 0xff;
            if (Event.isEvent() || (t - 1) < 0 || (t - 1) > 9) {
                advanceFrame(n);
                return;
            }
            int v = Bytes.s16(m, 0x1aa) - 1;
            Bytes.w16(m, 0x1aa, v);
            if ((v & 0x8000) == 0) {
                advanceFrame(n);
                return;
            }
            setMoveDelay(n);
            int b1 = (byte) Rng.rand(0, 3);
            int c3;
            boolean flip;
            if (t == 0) {
                c3 = -1;
                flip = false;
            } else if (t == 6 || t == 1) {
                b1 = (byte) Rng.rand(0, 3);
                c3 = 0;
                flip = false;
            } else if (t == 7 || t == 2) {
                c3 = 0;
                b1 = b1 == 2 ? 3 : 2;
                flip = true;
            } else if (t == 8 || t == 3) {
                c3 = 0;
                b1 = b1 == 0 ? 1 : 0;
                flip = true;
            } else if (t == 9 || t == 4) {
                c3 = 1;
                flip = false;
                b1 = (byte) Chars.turnDirection(n, Pool.hero);
            } else if (t == 10 || t == 5) {
                c3 = 1;
                flip = false;
                b1 = (byte) Um.turnOpposite((byte) Chars.turnDirection(n, Pool.hero));
            } else {
                flip = false;
                c3 = 0;
                if (t > 10) {
                    Chars.setDirection(n, (byte) (t - 0xb));
                    c3 = -1;
                }
            }
            int sh = b1 << 24;
            int d = sh >> 24;
            boolean moved = checkAndMove(n, d, (byte) t, false);
            if (!moved) {
                if (flip) {
                    sh = ((d + 1) % 2 + ((d - (sh >> 31)) & 0xfffffffe)) << 24;
                    d = sh >> 24;
                    moved = checkAndMove(n, d, (byte) t, false);
                }
                if (!moved) {
                    d = Chars.turnCorner(n, sh >> 24);
                    moved = d != -1 && d >= -1 && checkAndMove(n, d, (byte) t, false);
                }
            }
            if (moved) {
                int steps = c3;
                if (steps == 0) {
                    steps = (byte) Rng.rand(1, 5);
                }
                walkCount(n, steps);
                setNext(n, 1, d);
            } else {
                setNext(n, 0, m[0x168]);
            }
            return;
        }
        if (st == 1) {
            int b = m[0x1b4] & 0xff;
            if (b != 0) {
                m[0x1a6]--;
                boolean neg = (m[0x1a6] & 0x80) != 0;
                if (neg) {
                    setStand(n);
                    b = m[0x1b4] & 0xff;
                }
                if (b != 0xff) {
                    if (b > 0xfd) {
                        return;
                    }
                    if (isEndFrame(n)) {
                        m[0x1b4]--;
                        if (m[0x1b4] == 0) {
                            holdLastFrame(n);
                            return;
                        }
                    }
                }
                if (neg) {
                    return;
                }
                advanceFrame(n);
                return;
            }
            m[0x1a6]--;
            if ((m[0x1a6] & 0x80) == 0) {
                advanceFrame(n);
                return;
            }
        } else if (m[0x1c3] == 0) {
            int b = m[0x1b4] & 0xff;
            if (b != 0) {
                if (b != 0xff) {
                    if (b > 0xfd) {
                        return;
                    }
                    if (isEndFrame(n)) {
                        m[0x1b4]--;
                        if (m[0x1b4] == 0) {
                            holdLastFrame(n);
                            return;
                        }
                    }
                }
                advanceFrame(n);
                return;
            }
            if (m[0x173] != 0) {
                return;
            }
        } else {
            if (n.x() != Bytes.s32(m, 0x1b8) || n.y() != Bytes.s32(m, 0x1bc)) {
                advanceFrame(n);
                return;
            }
            m[0x1c3] = 0;
            if (m[0x1b4] == 0) {
                return;
            }
            m[0x1c2] = 0;
            Bytes.w32(m, 0x1b8, 0);
            Bytes.w32(m, 0x1bc, 0);
            m[0x1c0] = 0;
            m[0x1c1] = 0;
            if (m[0x173] != 0) {
                return;
            }
        }
        setNext(n, 0, m[0x168]);
    }

    /** NPC_setNextMotionDirection(n, motion, dir) @0xb96e8: -1 = stand at once */
    public static void setNextMotionDirection(Char n, int motion, int dir) {
        if (motion != -1) {
            setNext(n, motion, dir);
            return;
        }
        standMotionAtOnce(n);
    }

    /** NPC_setLoopNextMotionDirection(n, motion) @0xb9a60: the motion with the current direction */
    public static void setLoopNextMotionDirection(Char n, int motion) {
        if (motion != -1) {
            setNext(n, motion, n.m[0x168]);
            return;
        }
        standMotionAtOnce(n);
    }

    /** NPC_setLoop(n, loops) @0xb8c08: 0xff = none (0), 0 = forever (0xff), else that many loops of the motion */
    public static void setLoop(Obj n, int loops) {
        if (loops == 0xff) {
            n.m[0x1b4] = 0;
        } else if (loops == 0) {
            n.m[0x1b4] = (byte) 0xff;
        } else {
            n.m[0x1b4] = (byte) loops;
        }
        n.m[0x8c] = 0;
    }
}
