package hl4.game;

import hl4.gfx.Blit;
import hl4.gfx.Frame;
import hl4.gfx.Fx;
import hl4.gfx.Gbm;
import hl4.gfx.Sprite;
import hl4.sys.Bytes;

/**
 * Placed map objects (MAPOBJ_drawTopMiddle @0xac5bc, _Map_Draw_Bottom_Object @0xa7718, Map_drawTileList): an object is an image of an OBJ image set,
 * anchored bottom-centre (DM_CalcAnchorGbm 0x14) at its position minus the camera.  Layers 0/1 (more-bottom, bottom) are painted into the back picture
 * only where it was exposed (dir/delta), layers 2/3 are painted whole every frame onto the screen.
 */
public final class MapObjDraw {
    private MapObjDraw() {
    }

    /** aDrawcount @0xe1972 / aDrawdir_List @0xe197c (the same bytes as the tile tables) */
    private static final int[] CNT = {0, 1, 1, 1, 1, 2, 2, 2, 2, 1};
    private static final int[] DIRS = {0, 0, 1, 0, 2, 0, 3, 0, 4, 0, 1, 3, 2, 3, 1, 4, 2, 4, 9, 0};
    private static final int[] ALPHA = {0xff, 0xea, 0xc3, 0x9c, 0x75, 0x4e, 0x27};
    private static final int[] OUT = new int[4];

    /** the effect id for the high 5 bits of the opacity byte; -1 = not in the table */
    private static int effectId(int v) {
        if (v >= 1 && v <= 0xf) {
            return v;
        }
        if (v == 0x10) {
            return 0x13;
        }
        if (v == 0x11) {
            return 0x10;
        }
        if (v == 0x12) {
            return 0x12;
        }
        return -1;
    }

    /**
     * MAPOBJ_drawTopMiddle(frame, obj, imgIdx, flags (+0x24), opa (+0x25), camX, camY, offX, offY, dir, dx, dy).
     * flags: bit 4 quarter turn, bits 2..3 / 0..1 scale code (x1, x2, x4, x8) of the two axes, bits 5/6 mirror.  opa: low 3 bits translucency,
     * high 5 bits blend effect.
     */
    public static void drawTopMiddle(Frame f, Obj o, int imgIdx, int flags, int opa, int camX, int camY, int offX, int offY, int dir, int dx, int dy) {
        if (o.m[Obj.VISIBLE] == 0 || imgIdx < 0 || Maps.objImgs == null || imgIdx >= Maps.objImgs.length) {
            return;
        }
        Gbm img = Maps.objImgs[imgIdx];
        if (img == null) {
            return;
        }
        int rot = (flags & 0x10) == 0 ? 0 : 0x5a;
        boolean fx = (flags & 0x10) != 0;
        int scX;
        switch ((flags & 0xc) >> 2) {
            case 2:
                scX = 400;
                fx = true;
                break;
            case 3:
                scX = 800;
                fx = true;
                break;
            case 1:
                scX = 200;
                fx = true;
                break;
            default:
                scX = 100;
                break;
        }
        int scY;
        switch (flags & 3) {
            case 2:
                scY = 400;
                fx = true;
                break;
            case 3:
                scY = 800;
                fx = true;
                break;
            case 1:
                scY = 200;
                fx = true;
                break;
            default:
                scY = 100;
                break;
        }
        int iw = img.aux;
        int ih = img.h;
        byte[] m = o.m;
        if (o.evRef >= 0) {
            int r = Event.rec(o.evRef);
            Bytes.w32(m, 0xc, Bytes.s16(Event.e, r + 2));
            Bytes.w32(m, 8, Bytes.s16(Event.e, r));
        }
        boolean strip = (m[0x2a] & 0xff) < 2;
        int n = strip ? CNT[dir] : 1;
        for (int k = 0; k < n; k++) {
            int sd = DIRS[dir * 2 + k];
            if (sd == 0) {
                continue;
            }
            int rx = 0;
            int ry = 0;
            int rw = Glob.gameW;
            int rh = Glob.gameH;
            if (strip) {
                int ax = dx < 0 ? -dx : dx;
                int ay = dy < 0 ? -dy : dy;
                switch (sd) {
                    case 1:
                        rw = ax;
                        break;
                    case 2:
                        rx = Glob.gameW - ax;
                        rw = ax;
                        break;
                    case 3:
                        rh = ay;
                        break;
                    case 4:
                        ry = Glob.gameH - ay;
                        rh = ay;
                        break;
                    default:
                        break;
                }
            }
            int px = (m[0x28] + Bytes.s32(m, 8)) - camX;
            int py = (m[0x29] + Bytes.s32(m, 0xc)) - camY;
            px -= iw >> 1;                                  // DM_CalcAnchorGbm anchor 0x14: centre horizontally, bottom vertically
            py -= ih;
            int dw;
            int dh;
            int sx;
            int sy;
            int ddx;
            int ddy;
            if (Um.collision(rx, ry, rw, rh, px, py, iw, ih, OUT) >= 0) {       // every layer: objects outside the rectangle are skipped
                continue;
            }
            if (strip) {
                dw = OUT[2];
                dh = OUT[3];
                sy = OUT[1] - py;
                sx = OUT[0] - px;
                ddx = offX + OUT[0];
                ddy = offY + OUT[1];
            } else {
                dw = iw;
                dh = ih;
                ddx = offX + px;
                ddy = offY + py;
                sx = 0;
                sy = 0;
            }
            int[] le = Sprite.lcdEff;
            if (le[8] == 0) {
                Fx.restorePalette(img);
            } else {
                if (img.palFlag == 1) {
                    Fx.restorePalette(img);
                }
                Fx.setPalBlendEx(img, (short) (le[0] | le[1] << 8), (short) (le[2] | le[3] << 8), (short) (le[4] | le[5] << 8), le[7], le[6]);
            }
            int mir;
            if ((flags & 0x40) == 0) {
                mir = (flags & 0x20) == 0 ? 0x10 : 4;
            } else {
                mir = 8;
            }
            if ((flags & 0x60) == 0x60) {
                mir = 0xc;
            }
            if (strip) {
                if (mir == 8) {
                    sx = (-sx - dw) + img.aux;
                } else if (mir == 0xc) {
                    sx = (-sx - dw) + img.aux;
                    sy = (-sy - dh) + img.h;
                } else if (mir == 4) {
                    sy = (-sy - dh) + img.h;
                }
            }
            int a = opa & 7;
            int alpha = a <= 6 ? ALPHA[a] : 0xff;
            boolean fx2 = fx || (a >= 1 && a <= 6);
            int eff = (opa & 0xf8) >> 3;
            int id = effectId(eff);
            if (id >= 0) {
                eff = id;
                fx2 = true;
            }
            if (!fx2) {
                Blit.drawImg(f, ddx, ddy, dw, dh, img, sx, sy, mir == 8 ? 1 : (mir == 0xc ? 3 : (mir == 4 ? 2 : 0)), 0, 0);
            } else if (rot == 0x5a) {
                Fx.drawEffectClip(f, ddx, ddy, dw, dh, img, sx, sy, mir, 0x5a, scY, scX, eff, alpha, 0);
            } else {
                Fx.drawEffectClip(f, ddx, ddy, dw, dh, img, sx, sy, mir, rot, scX, scY, eff, alpha, 0);
            }
        }
    }

    /** _Map_Draw_Bottom_Object @0xa7718: the more-bottom and bottom layers, only on frames where the back picture was scrolled / redrawn */
    public static void drawBottom(Frame back) {
        byte[] m = G.map;
        if (m[0x95] != 0) {
            int camX = Bytes.s32(m, 156);
            int camY = Bytes.s32(m, 160);
            int offX = Bytes.s32(m, 172);
            int offY = Bytes.s32(m, 176);
            int dir = m[0x95];
            int dx = Bytes.s32(m, 188);
            int dy = Bytes.s32(m, 192);
            for (int pass = 0; pass < 2; pass++) {
                LList l = pass == 0 ? G.moreBottomObjectList : G.bottomObjectList;
                for (Obj o = l.head; o != null; o = o.next) {
                    int idx = Maps.objectImgIdx(Maps.mapObjImgId(o));
                    drawTopMiddle(back, o, idx, o.m[0x24], o.m[0x25], camX, camY, offX, offY, dir, dx, dy);
                }
            }
        }
        m[0x95] = 0;
    }
}
