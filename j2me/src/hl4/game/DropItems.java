package hl4.game;

import hl4.sys.Bytes;

/** The items lying on the ground (_dropedItem): DropItem_process @0x9940c.., DropItem_draw @0x992f0, DropItem_endProcess @0x99bb8, DropItem_drawItems @0x99cac. */
public final class DropItems {
    private DropItems() {
    }

    private static final int[] SCRATCH = new int[4];

    /** DropItem_process(o) @0xd140c: ages the item, blinks it after 0x172 frames and removes it at 0x191 */
    private static void process(Obj o) {
        byte[] m = o.m;
        int f = (short) (Bytes.s16(m, 0x20) + 1);
        Bytes.w16(m, 0x20, f);
        if (f > 0x172) {
            int v = m[0x18] & 0xff;
            Chars.setVisible(o, v > 1 ? 0 : 1 - v);
        }
        if ((short) Bytes.s16(m, 0x20) >= 0x191) {
            G.dropedItem.remove(o);
        }
    }

    /** DropItem_endProcess: a removed item zeroes its own next link, which ends the walk (as in the original) */
    static void endProcess() {
        if (G.dropedItem.isEmpty()) {
            return;
        }
        for (Obj o = G.dropedItem.head; o != null; o = o.next) {
            process(o);
        }
    }

    /** DropItem_set(o) @0xd16cc: OBJ_set(o, ..) (free flag 1) + DropItem_init(o, 0, 0, -1, 0, 0, 0, 0, 0, 0) */
    public static void set(Obj o) {
        o.objSet(1);
        Maps.dropItemInit(o, 0, 0, 0xff, 0, 0, 0, 0, 0, 0);
    }

    /** DropItem_return(o) @0xd1684: DropItem_init(o, 0, 0, -1, 0, 0, 0, 0, 0, 0), then OBJ_return(o) (tail call) */
    public static void ret(Obj o) {
        Maps.dropItemInit(o, 0, 0, 0xff, 0, 0, 0, 0, 0, 0);
        Obj.ret(o);
    }

    /** DropItem_draw(o, ox, oy) @0xd12f0: shadow + item icon; on a water tile (property 7) the splash sprite of the common effects (animation 0x16) */
    private static void draw(Obj o, int ox, int oy) {
        byte[] m = o.m;
        int x = Bytes.s32(m, 8);
        int y = Bytes.s32(m, 0xc);
        int tx = x < 0 ? x + 15 : x;
        int ty = y < 0 ? y + 15 : y;
        int prop = Coll.tileProp((byte) ((tx << 20) >> 24), (byte) ((ty << 20) >> 24));
        if (m[0x18] == 0) {
            return;
        }
        int px = ox + x;
        int py = oy + y;
        ObjDraw.shadow((short) (px + 8), (short) (py + 10), 0, 0);
        hl4.gfx.Blit.drawImg(Gfx.scr, px + 8, py + 8, 13, 13, Icons.itemIcon[0], m[0x25] * 13, 0, 0, 0, 0x12);
        if ((byte) prop == 7) {
            hl4.gfx.Sprite cs = TEffects.commonSpr;
            cs.f6c = cs.drawSprite(hl4.gfx.GfxOut.I, Gfx.scr, 0x16, 0, cs.f6c, px + 8, py + 10, 0, 0) & 0xff;
            cs.repeatFrame(0x16, 0);
        }
    }

    /** DropItem_drawItems */
    static void drawItems() {
        if (G.dropedItem.isEmpty()) {
            return;
        }
        byte[] g = G.map;
        int ox = (Bytes.s16(g, 0x2e0) - Bytes.s32(g, 156)) + Bytes.s32(g, 172);
        int oy = (Bytes.s16(g, 0x2e2) - Bytes.s32(g, 160)) + Bytes.s32(g, 176);
        for (Obj o = G.dropedItem.head; o != null; o = o.next) {
            draw(o, ox, oy);
        }
    }

    /** DropItem_drawInfos @0x99c0c: the name balloon of the first item under the hero */
    static void drawInfos() {
        if (G.dropedItem.isEmpty()) {
            return;
        }
        byte[] g = G.map;
        int ox = (Bytes.s16(g, 0x2e0) - Bytes.s32(g, 156)) + Bytes.s32(g, 172);
        int oy = (Bytes.s16(g, 0x2e2) - Bytes.s32(g, 160)) + Bytes.s32(g, 176);
        for (Obj o = G.dropedItem.head; o != null; o = o.next) {
            if (drawInfo(o, ox, oy)) {
                return;
            }
        }
    }

    /** _checkDir @0xe2028 (.data): two balloon sides to try per facing (item -> hero) 0..3: 2/3 left/right low, 5/6 left/right high */
    private static final byte[] CHECK_DIR = {2, 3, 2, 3, 6, 3, 5, 2};
    /** ITEMNAME_COLOR @0xd9ab0 */
    private static final int[] NAME_RGB = {255, 255, 255, 255, 216, 0, 0, 176, 255, 181, 85, 255, 255, 33, 42, 62, 255, 36};
    /** the "%s %s" buffer (sp+0x45 in the original), NUL padded to the 100 bytes it is drawn with */
    private static final byte[] BAL = new byte[100];
    private static final byte[] TMP = new byte[100];

    private static byte[] field(byte[] m, int off, int max) {
        int n = 0;
        while (n < max && m[off + n] != 0) {
            TMP[n] = m[off + n];
            n++;
        }
        for (int i = n; i < TMP.length; i++) {
            TMP[i] = 0;
        }
        return TMP;
    }

    /** DropItem_checkBalloonPos(x, y, w, h) @0xe0ab4 */
    private static boolean balloonFits(int x, int y, int w, int h) {
        return (x | y) >= 0 && x + 2 + w <= Glob.gameW && y + 2 + h <= Glob.gameH;
    }

    /** DropItem_drawBox(x, y, w, h, light) @0xe0b18: a box with cut corners (alpha 0xb2), white or dark blue */
    private static void drawBox(int x, int y, int w, int h, int light) {
        hl4.gfx.Frame f = Gfx.scr;
        int r = light != 0 ? 0xff : 0x1a;
        int gg = light != 0 ? 0xff : 0x26;
        int b = light != 0 ? 0xff : 0x3e;
        Gfx.fillRectAlpha(f, x + 1, y, w - 2, h, 0xb2, r, gg, b);
        Gfx.fillRectAlpha(f, x, y + 1, 1, h - 2, 0xb2, r, gg, b);
        Gfx.fillRectAlpha(f, x - 1 + w, y + 1, 1, h - 2, 0xb2, r, gg, b);
    }

    /** DropItem_drawInfo(item, ox, oy) @0xe0f58: when the hero stands on the item, "name skin" in a white balloon beside it */
    private static boolean drawInfo(Obj o, int ox, int oy) {
        byte[] m = o.m;
        int ix = Bytes.s32(m, 8);
        int iy = Bytes.s32(m, 0xc);
        if (!heroOnItem(o)) {
            return false;
        }
        int wn = hl4.gfx.Hf.width(field(m, 0x45, 0x1b), 100, 1);
        int ws = hl4.gfx.Hf.width(field(m, 0x2a, 0x1b), 100, 1);
        Char h = Pool.hero;
        int dir = (byte) Um.turnDirection(ix + 8, iy + 0x10, Chars.getX(h), Chars.getY(h));
        if (dir == -1) {
            return false;
        }
        int w = wn + ws + 4;
        int half = w >> 1;
        int sx = ox + ix;
        int sy = oy + iy;
        int bx = sx + 8 - half;
        int by = sy - 0x13;
        int side = 0;
        for (int k = 0; k < 2; k++) {
            side = CHECK_DIR[dir * 2 + k];
            switch (side) {
                case 2:
                    bx = sx - w;
                    by = sy - 0xb;
                    break;
                case 3:
                    bx = sx + 0x10;
                    by = sy - 0xb;
                    break;
                case 5:
                    bx = sx - w;
                    by = sy - 0x13;
                    break;
                case 6:
                    bx = sx + 0x10;
                    by = sy - 0x13;
                    break;
                default:
                    break;
            }
            if (balloonFits(bx, by, w, 0x10)) {
                break;
            }
        }
        if (bx < 0) {
            bx = 0;
        }
        drawBox(bx, by, w, 0xe, 1);
        int cx = bx + half;
        hl4.gfx.Frame f = Gfx.scr;
        if (side == 6 || side == 3) {
            Gfx.fillRectAlpha(f, cx - 2, by + 0x10, 1, 2, 0x32, 0xff, 0xff, 0xff);
            Gfx.fillRectAlpha(f, cx - 3, by + 0x10, 1, 3, 0x32, 0xff, 0xff, 0xff);
        } else {
            Gfx.fillRectAlpha(f, cx + 2, by + 0x10, 1, 2, 0x32, 0xff, 0xff, 0xff);
            Gfx.fillRectAlpha(f, cx + 3, by + 0x10, 1, 3, 0x32, 0xff, 0xff, 0xff);
        }
        int gr = m[0x29];
        if (gr == 0) {
            hl4.gfx.Hf.setColor(0, 0, 0);
        } else {
            if (gr < 0) {
                throw new IllegalStateException("DropItem_drawInfo: negative grade reads before ITEMNAME_COLOR (undefined)");
            }
            int k = gr > 4 ? 5 : gr;
            hl4.gfx.Hf.setColor(NAME_RGB[k * 3], NAME_RGB[k * 3 + 1], NAME_RGB[k * 3 + 2]);
        }
        int n = 0;
        for (int i = 0; i < 0x1b && m[0x45 + i] != 0; i++) {
            BAL[n++] = m[0x45 + i];
        }
        BAL[n++] = ' ';
        for (int i = 0; i < 0x1b && m[0x2a + i] != 0; i++) {
            BAL[n++] = m[0x2a + i];
        }
        if (n > 0x1a) {
            throw new IllegalStateException("DropItem_drawInfo: \"%s %s\" overruns the 0x1b-byte stack buffer of the original: later");
        }
        while (n < BAL.length) {
            BAL[n++] = 0;
        }
        hl4.gfx.Hf.drawAnchorH(f, cx, by + 2, BAL, 100, 0, 1);
        return true;
    }

    /** MainGameFrame_checkRectWithHero(x, y, 16, 16): the item's rectangle meets the hero's, shifted half a tile in the facing direction */
    static boolean heroOnItem(Obj o) {
        Char h = Pool.hero;
        int dir = h.m[0x168];
        int hx = Chars.getX(h) - 8 + (byte) (Um.vecX(dir) * 8);
        int hy = Chars.getY(h) - 8 + (byte) (Um.vecY(dir) * 8);
        return Um.collision(Bytes.s32(o.m, 8), Bytes.s32(o.m, 0xc), 16, 16, hx, hy, 16, 16, SCRATCH) < 0;
    }
}
