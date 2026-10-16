package hl4.game;

import hl4.gfx.Frame;
import hl4.gfx.Fx;
import hl4.gfx.Gbm;
import hl4.gfx.GfxOut;
import hl4.sys.Bytes;

/** Map_drawMidddleObject @0xa8200 and the per-type draws it dispatches to (NPC_draw @0xb8ce0, CHAR_drawShadow @0x49450). */
public final class ObjDraw {
    private ObjDraw() {
    }

    private static final boolean[] fxOn = new boolean[1];

    /** CHAR_drawShadow(x, y, shadowIdx, shrink%) */
    public static void shadow(int x, int y, int idx, int shrink) {
        if (shrink < 100) {
            Gbm g = Imgs.shadow[idx];
            Fx.drawEffectClip(Gfx.scr, x, y, g.aux, g.h, g, 0, 0, 0, 0, 100 - shrink, 100 - shrink, 3, 0xff, 0x12);
        }
    }

    /** NPC_notDrawShadow @0xa8b98 (INFERRED: +0x160 is the NPC kind) */
    private static boolean notDrawShadow(Char c) {
        int k = Bytes.u16(c.m, 0x160);
        int motion = c.m[0x167];
        if (k == 3) {
            return motion == 6;
        }
        if (k == 0x28) {
            return motion == 0;
        }
        return false;
    }

    /** NPC_draw(npc, ox, oy) */
    public static void npc(Char c, int ox, int oy) {
        byte[] m = c.m;
        if (m[0x18] == 0) {
            return;
        }
        int frame = m[0x8c];
        int x = Bytes.s32(m, 8) + ox;
        int y = Bytes.s32(m, 0xc) + oy;
        if (!notDrawShadow(c)) {
            shadow((short) x, (short) (y - 2), 0, 0);
        }
        if (G.map[0x68] == 2) {
            c.spr.setControlPal(0, 0, 0, 0xff, 0x10);
        }
        int dbg = m[0x172];
        int p4 = dbg <= 4 ? 2 : 0;
        int p5 = dbg <= 4 ? 3 : 1;
        c.spr.drawNpc(GfxOut.I, Gfx.scr, m[0x167], p4, p5, m[0x168], frame, x, y, 0, 0, -1);
        byte[] e = G.event;
        if (e[0x1fe] == 0 && m[0x1c4] != 0) {
            int q = e[(m[0x1c4] & 0xff) + 0x824] & 0xff;
            int u = q - 1;
            if (u > 3) {
                m[0x171] = (byte) 0xff;
            } else {
                m[0x171] = (byte) (q == 4 ? 10 : q + 7);
            }
        }
        if (m[0x171] != -1) {
            Imgs.imo.f68 = 0;                                       // SPR_set_restore_pal
            Imgs.imo.drawSprite(GfxOut.I, Gfx.scr, m[0x171], 0, frame, Bytes.s32(m, 0x20) + x, y - 0x20 + Bytes.s32(m, 0x24), 0, 0);
        }
        if (m[0x16e] == 7) {
            throw new IllegalStateException("NPC_draw tile property 7 (common effect sprite): later");
        }
    }

    private static int opacityType(int a) {
        a &= 0xff;
        if (a == 0xff) {
            return 0;
        }
        if (a > 0xe9) {
            return 1;
        }
        if (a > 0xc2) {
            return 2;
        }
        if (a > 0x9b) {
            return 3;
        }
        if (a > 0x74) {
            return 4;
        }
        return a > 0x4d ? 5 : 6;
    }

    /** the draw of one middle/top map object (shared by both loops of Map_drawMidddleObject) */
    private static boolean mapObj(Obj o, int camX, int camY, boolean[] fxOn) {
        byte[] m = G.map;
        int idx = Maps.objectImgIdx(Maps.mapObjImgId(o));
        int flags = o.m[0x24];
        int opa = o.m[0x25] & 0xff;
        int kind = m[0x34e] & 0xff;
        if (kind == 0 || kind == 1) {
            if (!Opt.particle() && m[0x34f] != 0) {
                m[0x34f] = (byte) 0xff;
            }
            opa = opacityType(m[0x34f]) & 7;
            fxOn[0] = true;
        }
        if (Pool.hero.m[0xa46] != 0) {
            return false;
        }
        int ox = Bytes.s32(m, 172);
        int oy = Bytes.s32(m, 176);
        if (kind == 0xff) {
            MapObjDraw.drawTopMiddle(Gfx.scr, o, idx, flags, (byte) opa, camX, camY, ox, oy, 9, 0, 0);
        } else if (kind < 2) {
            int id = o.m[0x27];
            boolean b = id == 13 || id == 10;
            if (id != 13 && id != 10 && id != 0x32 && id != 0x6e && id != -0x7a && id != -0x79 && id != -0x39 && id != -0x37) {
                int d = b ? 1 : 0;
                MapObjDraw.drawTopMiddle(Gfx.scr, o, idx, flags, (byte) opa, camX, camY, ox, oy, 9, d, d);
                if (fxOn[0]) {
                    MapObjDraw.drawTopMiddle(Gfx.scr, o, idx, flags, (byte) (opa | 0x40), camX, camY, ox, oy, 9, d, d);
                }
            }
        }
        return true;
    }

    /** Map_drawMidddleObject @0xa8200 */
    public static void drawMiddle() {
        byte[] m = G.map;
        int shX = Bytes.s16(m, 0x2e0);
        int shY = Bytes.s16(m, 0x2e2);
        int camX = Bytes.s32(m, 156);
        int camY = Bytes.s32(m, 160);
        int ox = (shX - camX) + Bytes.s32(m, 172);
        int oy = (shY - camY) + Bytes.s32(m, 176);
        fxOn[0] = false;
        if (!G.targetEffectList.isEmpty()) {
            throw new IllegalStateException("Map_drawMidddleObject: TargetEffect_draw: later");
        }
        for (Obj o = G.objList.head; o != null; o = o.next) {
            int t = Obj.structType(o);
            if (t == 1) {
                npc((Char) o, ox, oy);
            } else if (t == 2) {
                HeroDraw.draw((Char) o, ox, oy);
            } else if (t == 3) {
                monster((Char) o, ox, oy, 0);
            } else if (t == 4) {
                throw new IllegalStateException("SUMMON_draw: later");
            } else if (t == 7) {
                mapObj(o, camX - shX, camY - shY, fxOn);
            } else if (t == 0x10) {
                if (Pool.hero.m[0xa46] == 0) {
                    throw new IllegalStateException("SprObject_draw: later");
                }
            } else if (t == 6) {
                throw new IllegalStateException("TargetEffect_draw: later");
            }
        }
        if (Particles.curCount > 0) {
            throw new IllegalStateException("paintParticles: later");
        }
        for (Obj o = G.topObjectList.head; o != null; o = o.next) {
            mapObj(o, camX - shX, camY - shY, fxOn);
        }
    }

    /** MONSTER_draw(monster, ox, oy, concealPass) @0xaf148; MONSTER_drawBottom is the same with pass 1.  Blank placeholders draw nothing (live monsters: Phase E6). */
    private static void monster(Char c, int ox, int oy, int pass) {
        if (Monsters.isBlank(c)) {
            return;
        }
        throw new IllegalStateException("MONSTER_draw (live monster): later (E6)");
    }

    /** Map_drawBottomMonster @0xa8068: only monsters draw here */
    public static void drawBottomMonster() {
        for (Obj o = G.objList.head; o != null; o = o.next) {
            if (Obj.structType(o) == 3) {
                monster((Char) o, (Bytes.s16(G.map, 0x2e0) - Bytes.s32(G.map, 156)) + Bytes.s32(G.map, 172),
                        (Bytes.s16(G.map, 0x2e2) - Bytes.s32(G.map, 160)) + Bytes.s32(G.map, 176), 1);
            }
        }
    }
}
