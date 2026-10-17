package hl4.game;

import hl4.gfx.Frame;
import hl4.gfx.Hf;
import hl4.sys.Bytes;

/**
 * Popup boxes / dialog window state (PopupBox_* @0xcbb10..) and the yes/no popup painter (drawPopup_skt @0x62604).
 * Box struct (48 bytes): s32 x, y, w, h; +0x10 type, +0x11 p3, +0x12 skin kind, +0x13 item count (+0x14: text lines of a type 1 box);
 * +0x18 Focus struct (see Focus: +0x18 cursor, +0x19 highlighted item, +0x1a state, +0x1c key); +0x24 pointer to the caption text (here: the
 * parallel fields evtText / boxText, the byte slot stays 0); +0x28 s32 caption length (200); +0x2c open flag.
 */
public final class Popup {
    /** _evtBox (48 bytes): the event dialog box state; +0x2c = open flag */
    public static final byte[] evtBox = new byte[48];
    /** _box (48 bytes) @0x14da08: the game frame's popup (game over ...) */
    public static final byte[] box = new byte[48];

    /** the +0x24 caption pointer of evtBox / box (the original keeps it in the struct; the byte slot stays 0) */
    public static byte[] evtText;
    public static byte[] boxText;

    private Popup() {
    }

    /** the +0x24 caption pointer of a box (evtBox / box only) */
    private static byte[] textOf(byte[] box) {
        return box == evtBox ? evtText : box == Popup.box ? boxText : null;
    }

    /** PopupBox_end(box) @0xcbb10: closes the box, resets the menu-state stack and the Touch skin state */
    public static int end(byte[] box) {
        box[0x2c] = 0;
        Touch.setFrame(0x65);                       // stack pointer = 0 and state 0x65
        return 1;
    }

    /** PopupBox_setBox(box, type, w, h, count) @0xcbf8c = setBoxWithFocusValue(box, type, w, h, count, 0) */
    public static void setBox(byte[] box, int type, int w, int h, int count) {
        setBoxWithFocusValue(box, type, w, h, count, 0);
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
            box[0x12] = 9;
            int idx = Glob.gameW / 300 * 6 + (byte) count - 1;      // C.0.18057 @0xd94b0, indexed on the stack copy of the table
            if (idx < 0 || idx >= TYPE1_STATE.length) {
                throw new IllegalStateException("PopupBox_setBoxWithXyPos type 1: count outside the state table: later");
            }
            Touch.setFrame(TYPE1_STATE[idx]);
        }
        Bytes.w32(box, 0x24, 0);
        if (box == evtBox) {
            evtText = null;
        } else if (box == Popup.box) {
            boxText = null;
        }
        Focus.init(box, 0x18, focus);
        box[0x2c] = 1;
    }

    /** C.0.18057 @0xd94b0: Touch state of a type 1 box, [gameW / 300 * 6 + count - 1] */
    private static final int[] TYPE1_STATE = {30, 31, 32, 33, 34, 35, 30, 31, 32, 33, 34, 3};

    /** PopupBox_setBoxWithFocusValue(box, type, w, h, count, focus) @0xbbf28: centred on the whole game area */
    public static void setBoxWithFocusValue(byte[] box, int type, int w, int h, int count, int focus) {
        setBoxWithXyPos(box, type, -1, Glob.gameW, Glob.gameH, w, h, count, focus);
    }

    /** PopupBox_setBoxSub(box, areaW, areaH, w, count, lines, focus) @0xbbe30: a type 1 (choice) box with count lines of options under a
     * caption of `lines` text lines (stored at +0x14); height = 19 * count + 7 + (lines == 0 ? 4 : lines <= 1 ? 0x15 : 19 * lines + 2).
     */
    public static void setBoxSub(byte[] box, int areaW, int areaH, int w, int count, int lines, int focus) {
        int c = (byte) count;
        int l = (byte) lines;
        box[0x14] = (byte) l;
        int cap;
        if (l == 0) {
            cap = 4;
        } else if (l - 1 <= 0) {
            cap = 0x15;
        } else {
            cap = 19 * l + 2;
        }
        setBoxWithXyPos(box, 1, -1, areaW, areaH, w, 19 * c + 7 + cap, c, (byte) focus);
    }

    /** PopupBox_setBoxAlert(box, w, count, lines, focus) @0xbbed4: setBoxSub centred on the game area */
    public static void setBoxAlert(byte[] box, int w, int count, int lines, int focus) {
        setBoxSub(box, Glob.gameW, Glob.gameH, w, count, lines, (byte) focus);
    }

    /** PopopBox_key(box, key) @0xbbb20 (sic): keys of an open box.  Returns 2 = not handled, 1 = the box was closed, 0 = select pressed.
     * Up / down (-1, 0x32 / -2, 0x38) move the cursor with wrap-around, OK (-5, 0x35) starts the select state, the pointer area codes
     * 0x3b21.. pick an item (a second tap on the cursor item selects), -0x10 closes (not a choice box), types 0 and 2 close on any / OK.
     */
    public static int key(byte[] box, int key) {
        if (box[0x2c] == 0) {
            return 2;
        }
        if (!Focus.canInputKey(box, 0x18)) {
            return 2;
        }
        int t = box[0x10] & 0xff;
        if (t == 0) {
            end(box);
            return 1;
        }
        if ((byte) t == 2) {
            if (key == -5 || key == 0x35 || key == -0x10) {
                end(box);
                return 1;
            }
            return 2;
        }
        if ((byte) t != 1 && (t < 3 || t > 5)) {
            return 2;
        }
        if (key == 0x3cd1 || key <= 0x3a97) {
            // plain keys
        } else {
            int a = key - 0x3b21;
            if (a >= 0 && a <= 5) {
                if (box[0x18] == a) {
                    key = -5;
                } else {
                    box[0x18] = (byte) a;
                }
            }
        }
        if (key == -5 || key == 0x35) {
            Focus.pressSelectKey(box, 0x18, key);
            return 0;
        }
        if (key == -1 || key == 0x32) {
            int v = (box[0x18] - 1) & 0xff;
            box[0x18] = (byte) v;
            if ((v & 0x80) != 0) {
                box[0x18] = (byte) ((box[0x13] & 0xff) - 1);
            }
            return 2;
        }
        if (key == -2 || key == 0x38) {
            int v = (byte) ((box[0x18] & 0xff) + 1);
            box[0x18] = (byte) v;
            if (box[0x13] - 1 < v) {
                box[0x18] = 0;
            }
            return 2;
        }
        if (key == -0x10 && (byte) t != 1) {
            end(box);
            return 1;
        }
        return 2;
    }

    /** PopupBox_window(type, p2, x, y, w, h, count) @0xbb090: y of the first row below the frame of that box type (type 9 grows with count rows) */
    public static int window(int type, int p2, int x, int y, int w, int h, int count) {
        int c = (byte) count;
        if (type == 9 && c > 0) {
            return y + (c - 1 > 0 ? 19 * c - 2 : 0x11) + 6;
        }
        int a;
        if (type == 0) {
            return y + 1 + 6;
        } else if (type == 4 || type == 2 || type == 3) {
            return y + 0xd + 6;
        } else if (type == 5) {
            a = 0xb;
        } else if (type == 1 || type == 7) {
            a = type + 0xb;
        } else if (type == 6) {
            a = 0x11;
        } else if (type == 8) {
            a = 0x1e;
        } else {
            a = 0;
        }
        return y + a + 6;
    }

    /** PopupBox_outline(type, p2, x, y, w, h) @0xbb17c = PopupBox_window(type, p2, x, y, w, h, 0) */
    public static int outline(int type, int p2, int x, int y, int w, int h) {
        return window(type, p2, x, y, w, h, 0);
    }

    /** drawMainMenuBox_H(x, y, w, h, text, sel) @0x62bbc: a button: round rect (selected: lime on black text, else grey) and an optional centred label.
     * (The original's text length arguments 0x40 / 100 / 200 are maxima of a NUL-terminated string; Java strings carry no NUL, hence the min.)
     */
    public static void drawMainMenuBoxH(int x, int y, int w, int h, byte[] text, int sel) {
        if ((sel & 0xff) != 0) {
            Gfx.setFg(0xd3, 0xee, 0x3c);
            Hf.setColor(0, 0, 0);
        } else {
            Gfx.setFg(0x46, 0x46, 0x46);
            Hf.setColor(0x89, 0x89, 0x89);
        }
        EventDraw.roundRect(Gfx.scr, x, y, w, h);
        if (text != null) {
            Hf.drawAnchorH(Gfx.scr, x + (w >> 1), y + 3, text, Math.min(0x40, Strs.len(text)), 0, 1);
        }
    }

    /** PopupBox_buttonSlot(p1, x, y, w, h, sel) @0xbb638 = drawMainMenuBox_H(x, y, w, h, null, sel) */
    public static void buttonSlot(int p1, int x, int y, int w, int h, int sel) {
        drawMainMenuBoxH(x, y, w, h, null, sel);
    }

    /** PopupBox_select(p1, x, y, w, h, count, cur) @0xbb7f8: count buttons stacked downwards (h + 2 apart); button cur is the lit one */
    public static void select(int p1, int x, int y, int w, int h, int count, int cur) {
        int n = (byte) count;
        if (n > 0) {
            int i = 0;
            do {
                buttonSlot(p1, x, y, w, h, (byte) cur == (byte) i ? 1 : 0);
                i = (i + 1) & 0xff;
                y += h + 2;
            } while (n != (byte) i);
        }
    }

    /** PopupBox_selectString(texts, x, y, w, h, start, end, sel, anchor) @0xbb49c: the labels texts[start..end) at the Touch anchors 0x3c8d + k
     * (lime for the selected one, else grey 0x89), then the selection frame of anchor 0x3b21 + sel.  x, y, w, h are unused by the original.
     */
    public static void selectString(byte[][] texts, int x, int y, int w, int h, int start, int end, int sel, int anchor) {
        int s = (short) start;
        int en = (short) end;
        int sl = (byte) sel;
        if (s < en) {
            int idx = s & 0xffff;
            int i = 0;
            do {
                int[] a = Touch.extra(s + 0x3c8d + i);
                if (idx == (sl & 0xffff)) {
                    Hf.setColor(0xd3, 0xee, 0x3c);
                } else {
                    Hf.setColor(0x89, 0x89, 0x89);
                }
                Hf.drawAnchorH(Gfx.scr, (short) a[0] + Touch.cx, Touch.cy - 5 + (short) a[1], texts[s + i], Math.min(100, Strs.len(texts[s + i])), 0, anchor);
                i++;
                idx = (idx + 1) & 0xffff;
            } while (en != (short) idx);
        }
        Gfx.setFg(0xd3, 0xee, 0x3c);
        int[] b = Touch.extra(sl + 0x3b21);
        Touch.drawRoundRect(Touch.cx + 1 + (short) b[0], Touch.cy + 1 + (short) b[1], (short) b[2] - 2, (short) b[3] - 2);
    }

    /** PopupBox_draw(box, texts) @0xbb88c: the open box: the Touch skin, then by type: 0 frame; 2 frame + caption (text, +0x24); 1 / 3 choice
     * labels with the selection frame; 4 / 5 frame + buttons + labels + cursor arrow.  texts = the label strings; the caption is the +0x24 text of the box.
     */
    public static void draw(byte[] box, byte[][] texts) {
        if (box[0x2c] == 0) {
            return;
        }
        if (box[0x10] == 1) {
            Gfx.setClip(0, 0, Glob.gameW, Touch.cy + 0x61);
        }
        Touch.draw(Gfx.scr);
        int t = box[0x10] & 0xff;
        if (t == 1) {
            Gfx.setClip(0, 0, Glob.gameW, Glob.gameH);
            t = box[0x10] & 0xff;
        }
        int x = Bytes.s32(box, 0);
        int y = Bytes.s32(box, 4);
        int w = Bytes.s32(box, 8);
        int h = Bytes.s32(box, 0xc);
        if (t == 0) {
            outline(box[0x12], 0, x, y, w, h);
        } else if ((byte) t == 2) {
            outline(box[0x12], 1, x, y, w, h);
            byte[] text = textOf(box);
            if (text != null) {
                Hf.setColor(0xff, 0xff, 0xff);
                Hf.drawAnchorH(Gfx.scr, x + (w + (w >>> 31) >> 1), y + 6, text, Math.min(Bytes.s32(box, 0x28), Strs.len(text)), 0, 1);
            }
        } else if ((byte) t == 1 || (byte) t == 3) {
            selectString(texts, x + 6, 0, w - 0xc, 0x11, 0, box[0x13], box[0x18], 1);
        } else if (t - 4 >= 0 && t - 4 < 2) {
            int u = outline(box[0x12], 1, x, y, w, h);
            select(1, x + 6, u, w - 0xc, 0x11, box[0x13], box[0x19]);
            selectString(texts, x + 6, u, w - 0xc, 0x11, 0, (short) box[0x13], box[0x19], 1);
            Focus.pointerRight(box, 0x18, x + 6, u, w - 0xc, 0x11, 1);
        }
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
