package hl4.game;

import hl4.gfx.Frame;
import hl4.gfx.Hf;
import hl4.sys.Bytes;

/** The cut scene overlay (Event_Draw_Event @0x6b758 and the Event_Draw_* it calls): letter-box bars, screen tint, popups, dialog windows, skip label. */
public final class EventDraw {
    private EventDraw() {
    }

    private static final byte[] SKIP = Strs.ascii("&SKIP");

    /** drawRoundRect(frame, x, y, w, h) @0x72b00: four edge lines and the inside, all in the foreground colour */
    public static void roundRect(Frame f, int x, int y, int w, int h) {
        Gfx.drawLine(f, x + 1, y, x + w - 2, y);
        Gfx.drawLine(f, x + 1, y + h - 1, x + w - 2, y + h - 1);
        Gfx.drawLine(f, x, y + 1, x, y + h - 2);
        Gfx.drawLine(f, x + w - 1, y + 1, x + w - 1, y + h - 2);
        Gfx.fillRect(f, x + 1, y + 1, w - 2, h - 2);
    }

    /** Event_Draw_Screen(frame, dy) @0x6a290: the event's own screen tint (+0x5ec on, +0x600 alpha, colour ints at +0x5f0/0x5f4/0x5f8) */
    private static void screen(Frame f, int dy) {
        byte[] e = G.event;
        if (e[0x5ec] != 0) {
            Gfx.fillRectAlpha(f, 0, dy, Glob.gameW, Glob.gameH - 2 * dy, e[0x600], Bytes.s32(e, 0x5f0), Bytes.s32(e, 0x5f4), Bytes.s32(e, 0x5f8));
        }
    }

    /** Event_Draw_Popup @0x6ab24 (modes other than 2): the panel (touch frame 0x39) and, once the counter passed 2, the text centred in 130 / 150 pixels */
    private static void drawPopup(Frame f) {
        byte[] e = G.event;
        int lines = Bytes.s32(e, 0x598);
        Touch.setFrame(0x39);
        if (e[0x58c] == -1) {
            throw new IllegalStateException("Event_Draw_Popup: item popup text: later");
        }
        int x = Glob.gameW >> 1;
        if (e[0x516] == 2) {
            throw new IllegalStateException("Event_drawSubQuestPopup: later");
        }
        if (lines < 2) {
            x -= Bytes.s32(e, 0x59c) / 2;
        } else {
            x -= 0x41;
        }
        int y = (Glob.gameH >> 1) + lines * -7;
        Touch.draw(f);                                          // Event_drawActivePopup: the panel
        if ((byte) Bytes.s32(e, 0x20c) > 2) {
            Hf.setColor(0xff, 0xff, 0xff);
            Hf.drawAnchorH2(f, x, y + 2, 0x82, 0, Event.popupText, 0, 0, 1000, 0);
        }
    }

    /** Event_Draw_Event(frame) */
    public static void draw(Frame f) {
        byte[] e = G.event;
        int w = Glob.gameW;
        int h = Glob.gameH;
        int bar = e[0x93e] * 6;                                     // MainFrame_isQcif() is false: 6 rows per step
        if (bar > 0) {
            Gfx.fillRectRGB(f, 0, 0, w, bar, 0xb, 0xb, 0xb);
            Gfx.fillRectRGB(f, 0, bar, w, 1, 0x9a, 0x9a, 0x9a);
            Gfx.fillRectRGB(f, 0, h - bar, w, bar, 0xb, 0xb, 0xb);
            Gfx.fillRectRGB(f, 0, h - bar - 1, w, 1, 0x9a, 0x9a, 0x9a);
        }
        screen(f, 0);
        if (e[0x514] != 0 && Bytes.s32(e, 0x598) > 0) {
            drawPopup(f);
        }
        if (Popup.evtBox[0x2c] != 0 && e[0x1fe] != 0 && e[0x1ff] != 0) {
            throw new IllegalStateException("Event_Draw_Alert_Popup: later");
        }
        if (e[0x5dc] == 0) {
            boolean active = false;
            for (int i = 0; i < 4; i++) {
                int s = e[0x510 + i];
                if (s != -1) {
                    if (e[0x504] == s) {
                        active = true;
                    }
                    Dialog.drawScriptWindow(f, s, active);
                    Dialog.drawTalk(f, e[0x510 + i], active);
                }
            }
        } else {
            Dialog.drawNarration(f);
        }
        int b = e[0x93e];
        if (e[0x93f] == 0) {
            if (b > 0) {
                e[0x93e] = (byte) (b - 1);
            }
        } else if (b <= 4) {
            e[0x93e] = (byte) (b + 1);
        }
        int ec = Bytes.s32(e, 0x200);
        if (e[0x1fe] != 0 && e[0x514] == 0 && e[0x1ff] == 0 && e[0x60f] == 0 && G.game[0x120] == 0 && ec != 0x24 && ec != 0x25 && ec != 0x26) {
            int lc = LoadFrame.loadCnt;
            if (lc == 0 || lc > 9) {
                Gfx.setFg(0, 0, 0);
                roundRect(f, w - 0x20, h - 10, 0x2a, 0xb);
                Hf.drawAnchorH(f, w - 0x1c, h - 10, SKIP, 0x40, 0, 0);
            }
        }
    }

    /** Event_Draw_SubTitle(frame) @0x6a4a4 */
    public static void subTitle(Frame f) {
        byte[] e = G.event;
        if (e[0x608] == 0) {
            return;
        }
        byte[] t = It.getString(e[0x60b]);
        if (t == null) {
            return;
        }
        if (t.length == 5 && t[0] == 'V' && t[1] == 'A' && t[2] == 'L' && t[3] == 'U' && t[4] == 'E') {
            e[0x608] = 0;
            return;
        }
        if (e[0x60c] != -1) {
            int half = e[0x60e] / 2;
            int step = half == 0 ? 0 : (byte) (200 / half);
            Gfx.fillRectAlpha(f, 0, (Glob.gameH >> 1) - 0xb, Glob.gameW, 0x14, e[0x60d] * step, 0xb, 0xb, 0xb);
        }
        Hf.setColor(0xff, 0xff, 0xff);
        Hf.drawAnchorH2(f, Glob.gameW >> 1, (Glob.gameH >> 1) - 6, Glob.gameW, 0, t, 0, 0, t.length, 1);
        if (e[0x60a] == 0) {
            e[0x608] = (byte) (e[0x60d] - 1) > 0 ? (byte) 1 : 0;
            e[0x60d]--;
        } else {
            e[0x60a] = (byte) (e[0x60d] + 1) < e[0x60e] / 2 ? (byte) 1 : 0;
            e[0x60d]++;
        }
    }

    /** the fade-to-black of a skipped scene (_event+0x207 on, +0x208 step, +0x209 steps): the tail of MainGameFrame_Render and Event_Draw_Skip_Effect @0x6a1b4 */
    public static void skipFade(Frame f) {
        byte[] e = G.event;
        if (e[0x207] != 0) {
            e[0x208]++;
            int cur = e[0x208];
            int mx = e[0x209];
            int alpha;
            if (cur <= mx) {
                alpha = cur == mx ? 0xff : (cur * (0xff / mx)) & 0xff;
            } else {
                alpha = 0xff;
                e[0x208] = e[0x209];
            }
            Gfx.fillRectAlpha(f, 0, 0, Glob.gameW, Glob.gameH, alpha, 0xb, 0xb, 0xb);
        }
    }
}
