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
}
