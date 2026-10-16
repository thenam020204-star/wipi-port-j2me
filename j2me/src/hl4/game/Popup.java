package hl4.game;

import hl4.gfx.Frame;
import hl4.gfx.Hf;
import hl4.sys.Bytes;

/**
 * Popup boxes / dialog window state (PopupBox_* @0xcbb10..) and the yes/no popup painter (drawPopup_skt @0x62604).
 * Box struct (48 bytes): s32 x, y, w, h; +0x10 type, +0x11 p3, +0x12 skin kind, +0x13 item count; +0x18 focus {s32 sel, s32 0x9c, s32 0};
 * +0x24 s32; +0x28 s32 (200); +0x2c open flag.
 */
public final class Popup {
    /** _evtBox (48 bytes): the event dialog box state; +0x2c = open flag */
    public static final byte[] evtBox = new byte[48];
    /** _box (48 bytes) @0x14da08: the game frame's popup (game over ...) */
    public static final byte[] box = new byte[48];

    private Popup() {
    }

    /** PopupBox_end(box) @0xcbb10: closes the box, resets the menu-state stack and the Touch skin state */
    public static int end(byte[] box) {
        box[0x2c] = 0;
        Touch.setFrame(0x65);                       // stack pointer = 0 and state 0x65
        return 1;
    }

    /** PopupBox_setBox(box, type, w, h, count) @0xcbf8c = setBoxWithFocusValue(box, type, w, h, count, 0) */
    public static void setBox(byte[] box, int type, int w, int h, int count) {
        setBoxWithXyPos(box, type, -1, Glob.gameW, Glob.gameH, w, h, count, 0);
    }

    /** PopupBox_setBoxWithXyPos(box, type, p3, areaW, areaH, w, h, count, focus) @0xcbca8: centred in the area; the type picks the skin state */
    public static void setBoxWithXyPos(byte[] box, int type, int p3, int areaW, int areaH, int w, int h, int count, int focus) {
        Bytes.w32(box, 0, (areaW - w) / 2);
        box[0x11] = (byte) p3;
        Bytes.w32(box, 4, (areaH - h) / 2);
        Bytes.w32(box, 8, w);
        Bytes.w32(box, 0xc, h);
        Bytes.w32(box, 0x28, 200);
        box[0x10] = (byte) type;
        box[0x13] = (byte) count;
        if (type == 2) {
            box[0x12] = 6;
            Touch.setFrame(0x36);
        } else if (type == 3) {
            box[0x12] = 7;
            Touch.setFrame(0x3a);
        } else if (type == 4) {
            box[0x12] = 0;
            Touch.setFrame(0x3a);
        } else if (type == 5) {
            box[0x12] = 8;
            Touch.setFrame(0x3a);
        } else if (type == 1) {
            throw new IllegalStateException("PopupBox_setBoxWithXyPos type 1 (state table C.0.18057): later");
        }
        Bytes.w32(box, 0x24, 0);
        Bytes.w32(box, 0x18, (byte) focus);         // Focus_Init(box + 0x18, focus) @0x6f430
        Bytes.w32(box, 0x1c, 0x9c);
        Bytes.w32(box, 0x20, 0);
        box[0x2c] = 1;
    }

    /** drawPopupYesNo(text, sel, w, kind) @0x62ac0 = drawPopup_skt(text, sel, w, kind, null) */
    public static void drawYesNo(byte[] text, int sel, int w, int kind) {
        drawSkt(text, sel, w, kind, null);
    }

    /**
     * drawPopup_skt(text, sel, w, kind, sub) @0x62604: the skin of the current Touch state, the message (grey 0x89, centred, wrapped to w - 4)
     * and, when sel != -1, the two buttons.  Three or more lines move the text up (and switch the skin for kind 0 / 1 past three lines).
     */
    public static void drawSkt(byte[] text, int sel, int w, int kind, byte[] sub) {
        Frame f = Gfx.scr;
        int maxW = w - 4;
        int len = Strs.len(text);
        int lines = Hf.lineCount(text, 0, len, maxW, 0);
        int pad = sel == -1 ? 8 : 0x1c;
        int up;
        if (lines <= 2) {
            up = 0;
        } else {
            boolean many = lines > 3;
            if (kind == 0 && many) {
                Touch.setFrame(0x38);
                up = (lines >> 1) * 9 - 6;
            } else {
                up = 8;
                if (kind == 1 && many) {
                    Touch.setFrame(0x3d);
                    up = (lines >> 1) * 9 - 2;
                }
            }
        }
        if (Touch.getState() == Touch.NONE) {
            return;
        }
        Touch.draw(f);
        Hf.setColor(0x89, 0x89, 0x89);
        int[] a = Touch.validExtra(0x3c5d, 0x3c0f);
        Hf.drawAnchorH2(f, Glob.centerX, Touch.cy - 5 + a[1] - up, maxW, 0, text, 0, 0, len, 1);
        if (sub != null) {
            int tw = Hf.width2(text, len, 0, maxW, 0);
            int y = Glob.centerY + 0xd - ((lines * 0xe + 0x12 + pad) >> 1);
            Hf.drawAnchorH2(f, Glob.centerX + (tw >> 1), y, maxW, 0, sub, 0, 0, Strs.len(sub), 0);
        }
        if (sel == -1) {
            return;
        }
        if (kind == 0) {
            throw new IllegalStateException("drawPopup_skt kind 0 (OK / WAIT button, area 0x3b08): later");
        }
        if (G.npcUi[2] == 0x3d) {
            throw new IllegalStateException("drawPopup_skt NpcUi yes/no labels (_txtNpcUi 0x3e / 0x3f): later");
        }
        int[] b = Touch.extra(0x3b7d);
        drawYesNoSelectBox(f, b[0], b[1] - 1, b[2], b[3], Strs.YES, sel == 0 || sel == 1 ? 1 - sel : 0);
        b = Touch.extra(0x3b38);
        drawYesNoSelectBox(f, b[0], b[1] - 1, b[2], b[3], Strs.NO, sel == 1 ? 1 : 0);
    }

    /** drawYesNoSelectBox(frame, x, y, w, h, text, selected) @0x62538: the selected one gets UI image 0xcf and black text, else grey 0x89 */
    static void drawYesNoSelectBox(Frame f, int x, int y, int w, int h, byte[] text, int selected) {
        if ((byte) selected != 0) {
            Touch.drawImage(f, 0xcf, x, y);
            Hf.setColor(0, 0, 0);
        } else {
            Hf.setColor(0x89, 0x89, 0x89);
        }
        Hf.drawAnchorH(f, Touch.cx + 1 + x + (w >> 1), Touch.cy - 5 + y + (h >> 1), text, Strs.len(text), 0, 1);
    }
}
