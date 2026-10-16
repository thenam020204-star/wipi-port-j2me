package hl4.game;

import hl4.sys.Bytes;

/** The items lying on the ground (_dropedItem): DropItem_process @0x9940c.., DropItem_draw @0x992f0, DropItem_endProcess @0x99bb8, DropItem_drawItems @0x99cac. */
public final class DropItems {
    private DropItems() {
    }

    /** DropItem_process(o): ages the item, blinks it after 0x172 frames and removes it at 0x191 */
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

    /** DropItem_draw(o, ox, oy) */
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
            throw new IllegalStateException("DropItem_draw: water effect (common effect sprite 0x16): later");
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
}
