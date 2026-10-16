package hl4.game;

import hl4.gfx.Blit;
import hl4.gfx.Frame;
import hl4.gfx.Fx;
import hl4.gfx.Gbm;
import hl4.gfx.Sprite;
import hl4.sys.Bytes;

/**
 * The map's background layer (Map_Draw_Tile @0xa8b68, Map_Draw_Buffer_Area_Copy @0xa7950, _Map_Draw_Bottom_Object @0xa7718, Map_drawTileList @0xa8ab8).
 * The 'back' frame is a persistent picture of the map at the camera position: when the camera moved (+0xbc/0xc0 = delta) the picture is shifted
 * (CUSTOM_grpCopyArea) and the exposed strips are filled with the background colour and re-drawn tile by tile; flag 9 redraws everything.
 * The original draws each tile row into a scratch strip frame and copies the exposed part; here one 16x16 scratch tile is drawn whole and the
 * exposed part of it is copied (same pixels: the tile blit never sees a clip edge).
 */
public final class MapDraw {
    private MapDraw() {
    }

    private static final Frame tileBuf = new Frame(16, 16);

    /** aDrawcnt @0xe1954 and aDrawdirection_List @0xe195e (CONFIRMED by reading the .data bytes) */
    private static final int[] DRAW_CNT = {0, 1, 1, 1, 1, 2, 2, 2, 2, 1};
    private static final int[] DRAW_DIR = {0, 0, 1, 0, 2, 0, 3, 0, 4, 0, 1, 3, 2, 3, 1, 4, 2, 4, 9, 0};

    private static int s32(int o) {
        return Bytes.s32(G.map, o);
    }

    /** CUSTOM_grpCopyArea(frame, dx, dy, w, h, sx, sy) @0x28f18 for the non-negative arguments the map code passes (memmove, so overlap is fine) */
    private static void copyArea(Frame f, int dx, int dy, int w, int h, int sx, int sy) {
        if (w <= 0 || h <= 0) {
            return;
        }
        for (int y = 0; y < h; y++) {
            int s = (sy + (dy > sy ? h - 1 - y : y)) * f.w + sx;
            int d = (dy + (dy > sy ? h - 1 - y : y)) * f.w + dx;
            System.arraycopy(f.px, s, f.px, d, w);
        }
    }

    /** Map_Draw_Buffer_Area_Copy(frame, dir, r, g, b): shift the picture by the camera delta and fill the exposed area with the background colour */
    private static void areaCopy(Frame f, int dir, int r, int g, int b) {
        int sw = Glob.gameW;
        int sh = Glob.gameH;
        int ax = s32(188);
        int ay = s32(192);
        if (ax < 0) {
            ax = -ax;
        }
        if (ay < 0) {
            ay = -ay;
        }
        switch (dir) {
            case 1:
                copyArea(f, ax, 0, sw - ax, sh, 0, 0);
                Gfx.fillRectRGB(f, 0, 0, ax, sh, r, g, b);
                break;
            case 2:
                copyArea(f, 0, 0, sw - ax, sh, ax, 0);
                Gfx.fillRectRGB(f, sw - ax, 0, ax, sh, r, g, b);
                break;
            case 3:
                copyArea(f, 0, ay, sw, sh - ay, 0, 0);
                Gfx.fillRectRGB(f, 0, 0, sw, ay, r, g, b);
                break;
            case 4:
                copyArea(f, 0, 0, sw, sh - ay, 0, ay);
                Gfx.fillRectRGB(f, 0, sh - ay, sw, ay, r, g, b);
                break;
            case 5:
                copyArea(f, ax, ay, sw - ax, sh - ay, 0, 0);
                Gfx.fillRectRGB(f, 0, 0, ax, sh, r, g, b);
                Gfx.fillRectRGB(f, 0, 0, sw, ay, r, g, b);
                break;
            case 6:
                copyArea(f, 0, ay, sw - ax, sh - ay, ax, 0);
                Gfx.fillRectRGB(f, sw - ax, 0, ax, sh, r, g, b);
                Gfx.fillRectRGB(f, 0, 0, sw, ay, r, g, b);
                break;
            case 7:
                copyArea(f, ax, 0, sw - ax, sh - ay, 0, ay);
                Gfx.fillRectRGB(f, 0, 0, ax, sh, r, g, b);
                Gfx.fillRectRGB(f, 0, sh - ay, sw, ay, r, g, b);
                break;
            case 8:
                copyArea(f, 0, 0, sw - ax, sh - ay, ax, ay);
                Gfx.fillRectRGB(f, sw - ax, 0, ax, sh, r, g, b);
                Gfx.fillRectRGB(f, 0, sh - ay, sw, ay, r, g, b);
                break;
            case 9:
                Gfx.fillRectRGB(f, 0, 0, sw, sh, r, g, b);
                break;
            default:
                break;
        }
    }

    /** the palette step in front of every tile blit (original: _lcdEff[8] clear -> DM_RestorePalette, else tint) */
    private static void tilePalette(Gbm img) {
        int[] le = Sprite.lcdEff;
        if (le[8] == 0) {
            Fx.restorePalette(img);
        } else {
            if (img.palFlag == 1) {
                Fx.restorePalette(img);
            }
            Fx.setPalBlendEx(img, (short) (le[0] | le[1] << 8), (short) (le[2] | le[3] << 8), (short) (le[4] | le[5] << 8), le[7], le[6]);
        }
    }

    /**
     * One map cell: tile idx = layer A bits 0..4, bit 5 = quarter-turn blit, bits 6/7 = mirror code (0x10 none, 4 = flip 2, 8 = flip 1, 0xc = flip 3);
     * layer B bits 2..3 pick the tileset.  Drawn whole into the scratch tile.  false when the cell shows nothing (no tileset image).
     */
    private static boolean drawCellToBuf(int a, int b, int tw, int th) {
        int ts = (b & 0xc) >> 2;
        if (ts >= G.map[0x270]) {
            return false;
        }
        Gbm img = Maps.tiles[ts];
        if (img == null) {
            return false;
        }
        int mir;
        if ((a & 0x80) == 0) {
            mir = (a & 0x40) == 0 ? 0x10 : 4;
        } else {
            mir = (a & 0x40) == 0 ? 8 : 0xc;
        }
        int flip = mir == 8 ? 1 : (mir == 0xc ? 3 : (mir == 4 ? 2 : 0));
        tilePalette(img);
        int srcY = th * (a & 0x1f);
        if ((a & 0x20) == 0) {
            Blit.drawImg(tileBuf, 0, 0, tw, th, img, 0, srcY, flip, 0, 0);
        } else {
            Fx.drawImgEx(tileBuf, 0, 0, tw, th, img, 0, srcY, flip, 0x5a, 0xff, 0, 0);
        }
        return true;
    }

    /** the cells under the screen rectangle (rx, ry, rw, rh): each is copied from the scratch tile, clipped to the rectangle */
    private static void drawRect(Frame back, int rx, int ry, int rw, int rh) {
        byte[] m = G.map;
        int tw = m[0x7e];
        int th = m[0x7f];
        int mw = m[0x7a];
        int mh = m[0x7b];
        int offX = s32(172);
        int offY = s32(176);
        int camX = s32(156);
        int camY = s32(160);
        // world rectangle covered by the screen rectangle
        int wx0 = rx - offX + camX;
        int wy0 = ry - offY + camY;
        int wx1 = wx0 + rw;
        int wy1 = wy0 + rh;
        if (wx0 < 0) {
            wx0 = 0;
        }
        if (wy0 < 0) {
            wy0 = 0;
        }
        if (wx1 > mw * tw) {
            wx1 = mw * tw;
        }
        if (wy1 > mh * th) {
            wy1 = mh * th;
        }
        if (wx0 >= wx1 || wy0 >= wy1) {
            return;
        }
        byte[] la = Maps.layerA;
        byte[] lb = Maps.layerB;
        for (int row = wy0 / th; row * th < wy1; row++) {
            for (int col = wx0 / tw; col * tw < wx1; col++) {
                int ci = mw * row + col;
                if (!drawCellToBuf(la[ci] & 0xff, lb[ci] & 0xff, tw, th)) {
                    continue;
                }
                int cx0 = col * tw < wx0 ? wx0 : col * tw;
                int cx1 = (col + 1) * tw > wx1 ? wx1 : (col + 1) * tw;
                int cy0 = row * th < wy0 ? wy0 : row * th;
                int cy1 = (row + 1) * th > wy1 ? wy1 : (row + 1) * th;
                int dx = cx0 - camX + offX;
                for (int y = cy0; y < cy1; y++) {
                    System.arraycopy(tileBuf.px, (y - row * th) * tw + (cx0 - col * tw), back.px, (y - camY + offY) * back.w + dx, cx1 - cx0);
                }
            }
        }
    }

    /** the strip a draw direction exposes (1 left, 2 right, 3 top, 4 bottom, 9 everything) */
    private static void drawStrip(Frame back, int d) {
        int sw = Glob.gameW;
        int sh = Glob.gameH;
        int ax = s32(188);
        int ay = s32(192);
        if (ax < 0) {
            ax = -ax;
        }
        if (ay < 0) {
            ay = -ay;
        }
        switch (d) {
            case 1:
                drawRect(back, 0, 0, ax, sh);
                break;
            case 2:
                drawRect(back, sw - ax, 0, ax, sh);
                break;
            case 3:
                drawRect(back, 0, 0, sw, ay);
                break;
            case 4:
                drawRect(back, 0, sh - ay, sw, ay);
                break;
            case 9:
                drawRect(back, 0, 0, sw, sh);
                break;
            default:
                break;
        }
    }

    /** Map_Draw_Tile(back) @0xa8b68 */
    public static void drawTile(Frame back) {
        byte[] m = G.map;
        if (m[0x270] < 1) {
            return;
        }
        int dir = m[0x94] & 0xff;
        if (dir != 9) {
            int dx = s32(156) - s32(164);
            int dy = s32(160) - s32(168);
            Bytes.w32(m, 188, dx);
            Bytes.w32(m, 192, dy);
            if (dx == 0 && dy == 0) {
                dir = 0;
            } else if (dx < 0) {
                dir = dy == 0 ? 1 : (dy < 0 ? 5 : 7);
            } else if (dx > 0) {
                dir = dy == 0 ? 2 : (dy < 0 ? 6 : 8);
            } else {
                dir = dy < 0 ? 3 : 4;
            }
            m[0x94] = (byte) dir;
            m[0x95] = (byte) dir;
        }
        int sub = m[0x95];
        areaCopy(back, dir, s32(0x6c), s32(0x70), s32(0x74));
        for (int i = 0; i < DRAW_CNT[sub]; i++) {
            drawStrip(back, DRAW_DIR[sub * 2 + i]);
        }
        Bytes.w32(m, 168, s32(160));
        Bytes.w32(m, 164, s32(156));
    }

    /** the back picture -> scr with the shake offset (_map s16 0x2e0/0x2e2), the middle of MainGameFrame_Render @0x9c694 */
    public static void copyToScreen() {
        byte[] m = G.map;
        int sw = Glob.gameW;
        int sh = Glob.gameH;
        int sx = Bytes.s16(m, 0x2e0);
        int sy = Bytes.s16(m, 0x2e2);
        if (Bytes.s16(m, 0x44) != 0x60) {
            if (sx < 0) {
                if (sy < 0) {
                    Gfx.copyOffScreen(0, 0, sx + sw, sy + sh, -sx, -sy);
                } else {
                    Gfx.copyOffScreen(0, sy, sx + sw, sh, -sx, 0);
                }
            } else if (sy < 0) {
                Gfx.copyOffScreen(sx, 0, sw - sx, sy + sh, 0, -sy);
            } else {
                Gfx.copyOffScreen(sx, sy, sw, sh, 0, 0);
            }
            return;
        }
        if (Bytes.s32(m, 0x2e0) == 0) {
            Gfx.copyOffScreen(0, 0, sw, sh, 0, 0);
            return;
        }
        // map type 0x60: the exposed border is filled with (11, 11, 11)
        int ax = sx < 0 ? -sx : sx;
        int ay = sy < 0 ? -sy : sy;
        if (sx < 0) {
            if (sy == 0) {
                Gfx.copyOffScreen(0, 0, sx + sw, sh, -sx, 0);
                Gfx.fillRectRGB(Gfx.scr, sx + sw, 0, ax, sh, 0xb, 0xb, 0xb);
                return;
            }
        } else if (sx == 0) {
            if (sy < 0) {
                Gfx.copyOffScreen(0, 0, sw, sy + sh, 0, -sy);
                Gfx.fillRectRGB(Gfx.scr, 0, sy + sh, sw, ay, 0xb, 0xb, 0xb);
                return;
            }
            if (sy != 0) {
                Gfx.copyOffScreen(0, sy, sw, sh - sy, 0, 0);
                Gfx.fillRectRGB(Gfx.scr, 0, 0, sw, ay, 0xb, 0xb, 0xb);
                return;
            }
        } else if (sy == 0) {
            Gfx.copyOffScreen(sx, 0, sw - sx, sh, 0, 0);
            Gfx.fillRectRGB(Gfx.scr, 0, 0, ax, sh, 0xb, 0xb, 0xb);
            return;
        }
        Gfx.copyOffScreen(0, 0, sw, sh, 0, 0);
    }
}
