package hl4.game;

import hl4.gfx.Frame;
import hl4.gfx.Gbm;
import hl4.gfx.Img;
import hl4.gfx.Blit;
import hl4.sys.Bytes;

/**
 * Focus (cursor) struct of the menus / popups, a byte[] at offset o (the original embeds it, e.g. at _evtBox + 0x18):
 * +0 s8 cursor, +1 s8 highlighted item (-100 = none), +2 s8 state (-100 = "returned", 3 = select key pressed and counting down, 0 = idle),
 * +4 s32 the key that was pressed.
 */
public final class Focus {
    private Focus() {
    }

    /** Focus_Init(focus, value) @0x5f430: cursor = value, nothing highlighted, idle */
    public static void init(byte[] b, int o, int value) {
        b[o + 1] = -100;
        b[o + 2] = 0;
        b[o] = (byte) value;
    }

    /** Focus_CursorInit(focus) @0x5f40c */
    public static void cursorInit(byte[] b, int o) {
        b[o + 6] = 0;
        b[o] = 0;
        b[o + 3] = 0;
        b[o + 5] = 0;
        b[o + 1] = 0;
        b[o + 2] = 0;
        b[o + 4] = 0;
    }

    /** Focus_canInputKey(focus) @0x5f448: the state byte is not positive (no select animation running, not returned... -100 counts too) */
    public static boolean canInputKey(byte[] b, int o) {
        return b[o + 2] <= 0;
    }

    /** Focus_return(focus) @0x5f45c: the state is -100 */
    public static boolean isReturn(byte[] b, int o) {
        return b[o + 2] == -100;
    }

    /** Focus_endProcess(focus) @0x5f470: counts the select state down; true on the tick it reaches 0 */
    public static boolean endProcess(byte[] b, int o) {
        if (isReturn(b, o) || b[o + 2] <= 0) {
            return false;
        }
        int v = (byte) (b[o + 2] - 1);
        b[o + 2] = (byte) v;
        return v <= 0;
    }

    /** Focus_pressSelectKey(focus, key) @0x5f4bc: remembers the key and starts the 3 tick select state */
    public static void pressSelectKey(byte[] b, int o, int key) {
        if (!isReturn(b, o) && canInputKey(b, o)) {
            Bytes.w32(b, o + 4, key);
            b[o + 2] = 3;
        }
    }

    /** MainFrame_isQcif() @0x88ca4: the game area is at most 176 wide */
    public static boolean isQcif() {
        return Glob.gameW <= 0xb0;
    }

    /** Focus_pointer(focus, x, y, p4, h, right) @0x5fb04: the cursor arrow (common image 2) beside an item; skipped while the cursor is negative.
     * right != 0 draws it pointing right at x + 5 (+1 while the state is above 1), otherwise mirrored at x - 5 + p4.
     */
    public static void pointer(byte[] b, int o, int x, int y, int p4, int h, int right) {
        int st = b[o + 2];
        Gbm img = Imgs.cm[2];
        int ih = img.h;
        if (b[o] < 0) {
            return;
        }
        boolean moved = st > 1;
        y = y - (ih + (ih >>> 31) >> 1) + (h + (h >>> 31) >> 1);
        if (moved) {
            y++;
        }
        Frame f = Gfx.scr;
        if ((right & 0xff) != 0) {
            x += moved ? 5 : 4;
            Img.draw(f, x + 5, y, img, 0x21);
        } else {
            x = x - (moved ? 5 : 4) + p4;
            Blit.drawImg(f, x - 5, y, img.aux, img.h, img, 0, 0, 1, 0, 9);      // DM_DrawImageMirror(.., 8, 9): mirror 8 = flip 1
        }
    }

    /** Focus_pointerLeft(focus, x, y, p4, h, k, right) @0x5fc7c: the arrow for the item (cursor - k), one row (h + 1) per step */
    public static void pointerLeft(byte[] b, int o, int x, int y, int p4, int h, int k, int right) {
        int cur = b[o];
        int d;
        if ((byte) k <= cur) {
            d = (byte) ((b[o] & 0xff) - (byte) k);
        } else {
            d = (byte) (b[o] & 0xff);
        }
        pointer(b, o, x, y + h * d + d, p4, h, right);
    }

    /** Focus_pointerRight(focus, x, y, p4, h, right) @0x5fef0: Focus_pointerLeft with k = 0 and right = 1 (QCIF: the given right) */
    public static void pointerRight(byte[] b, int o, int x, int y, int p4, int h, int right) {
        if (isQcif()) {
            pointerLeft(b, o, x, y, p4, h, 0, right);
        } else {
            pointerLeft(b, o, x, y, p4, h, 0, 1);
        }
    }
}
