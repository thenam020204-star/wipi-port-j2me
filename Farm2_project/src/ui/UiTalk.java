import org.kwis.msp.lcdui.Graphics;
import org.kwis.msp.lcdui.Image;

import wipi.host.Txt;

/**
 * Dialogue box of the new UI: a bottom sheet across the whole width with
 * word wrap, 3+ lines, name tag, portrait standing on the box, favour meter
 * of the heroines and a "next" arrow.
 *
 * Replaces the bodies of NPC.DrawTalk, NPC.MakeNameBuf, GameCanvas.DrawTalk
 * and GameCanvas.DrawPopup. The typing logic of NPC.DrawTalk (5 characters
 * per frame, control codes /C /N /P /Z /S /Y, talk[] state, popup hand-over,
 * DRepaint) is copied unchanged; only the layout differs: the line width is
 * the box width and lines break between words.
 */
final class UiTalk {
    // geometry (logical pixels), from layout()
    static int by, bottom, tx, ty, tw, th, lines, lh;
    static final int X0 = 2, Y0 = 2; // text origin inside TBuff
    static String name = "";
    private static char last; // last character typed (word-wrap state)

    static void layout(GameCanvas gm) {
        lh = Txt.height() + 3;
        bottom = gm.h + gm.bh; // bottom of the map (it fills the screen; the margin below is not ours)
        int need = 3 * lh + 2 * Y0 + 14;
        by = bottom - 48;
        if (bottom - by < need) {
            by = bottom - need;
        }
        tx = 10;
        ty = by + 7;
        tw = gm.getWidth() - 26; // right margin keeps the "next" arrow off the text
        th = bottom - ty - 5;
        lines = (th - Y0) / lh;
    }

    static int color(int k) {
        switch (k) {
        case 1:
            return UiKit.RED;
        case 2:
            return UiKit.ACC_D;
        case 3:
            return UiKit.BLUE;
        case 4:
            return 0xA0181A;
        default:
            return UiKit.DARK;
        }
    }

    /** NPC.MakeNameBuf: the name is drawn as a tag by DrawTalk; no buffer needed. */
    static void MakeNameBuf(NPC n, int who, String s) {
        name = s.length() <= 0 ? (who == 0 ? n.gm.c.name : n.name[who - 1]) : s;
        n.NameBuf = null;
    }

    /** NPC.DrawTalk: types up to 5 characters of n.Talk into n.TBuff. */
    static void DrawTalk(NPC n, Graphics g) {
        GameCanvas gm = n.gm;
        layout(gm);
        int[] talk = n.talk;
        if (talk[3] <= 0) {
            if (n.TBuff == null || n.TBuff.getWidth() != tw || n.TBuff.getHeight() != th) {
                n.TBuff = Image.createImage(tw, th);
                n.tg = n.TBuff.getGraphics();
            }
            n.tg.g.setColor(UiKit.BG);
            n.tg.g.fillRect(0, 0, tw, th);
            talk[4] = X0;
            talk[5] = 0;
            talk[1] = -1;
            if (talk[3] == 0) {
                talk[6] = 0;
            }
            talk[3] = 1;
            talk[5] = talk[5] + Y0;
            talk[2] = talk[0];
            last = ' ';
        }
        int limit = tw - X0;
        int ymax = Y0 + lines * lh;
        gm.DRepaint = true;
        int col = color(talk[6]);
        int k = 0;
        while (k < 5) {
            boolean has = n.Talk.length() > talk[0];
            char c = has ? n.Talk.charAt(talk[0]) : 0;
            if (has && c == '/') {
                talk[0] = talk[0] + 1;
                c = n.Talk.charAt(talk[0]);
                if (c == 'C') {
                    talk[0] = talk[0] + 1;
                    c = n.Talk.charAt(talk[0]);
                    talk[6] = Integer.parseInt(n.Talk.substring(talk[0], talk[0] + 1));
                    col = color(talk[6]);
                    talk[0] = talk[0] + 1;
                }
                if (c == 'N') {
                    int who = Integer.parseInt(n.Talk.substring(talk[0] + 1, talk[0] + 3));
                    n.Talk = who >= 0
                            ? n.Talk.substring(0, talk[0] - 1) + n.name[who] + "/C0"
                                    + n.Talk.substring(talk[0] + 3, n.Talk.length())
                            : n.Talk.substring(0, talk[0] - 1) + gm.c.name + "/C0"
                                    + n.Talk.substring(talk[0] + 3, n.Talk.length());
                    talk[0] = talk[0] - 1;
                    talk[6] = 2;
                    col = color(talk[6]);
                    c = 0;
                }
                if (c == 'P') {
                    n.popup = (byte) Integer.parseInt(n.Talk.substring(talk[0] + 1, talk[0] + 3));
                    talk[0] = talk[0] + 3;
                    c = 0;
                }
                if (c == 'Z') {
                    n.popup1 = (byte) Integer.parseInt(n.Talk.substring(talk[0] + 1, talk[0] + 3));
                    talk[0] = talk[0] + 3;
                    c = 0;
                }
                if (c == 'S') {
                    gm.sn.SoundPlay(Integer.parseInt(n.Talk.substring(talk[0] + 1, talk[0] + 3)), false);
                    talk[0] = talk[0] + 3;
                    c = 0;
                }
                if (c == 'Y') {
                    has = false;
                    if (talk[4] != X0) {
                        talk[4] = limit; // forces the line break below
                    }
                    --k;
                } else {
                    has = talk[0] < n.Talk.length();
                    c = has ? n.Talk.charAt(talk[0]) : 0;
                }
            }
            if (talk[4] == X0 && has && c == ' ') {
                --k;
            } else if (has) {
                int cw = Txt.charWidth(c);
                // word wrap: a word that does not fit starts the next line
                int need = c != ' ' && last == ' ' ? wordWidth(n, talk[0]) : cw;
                if (talk[4] > X0 && talk[4] + (need > limit - X0 ? cw : need) > limit) {
                    talk[4] = X0;
                    talk[5] = talk[5] + lh;
                    last = ' ';
                    if (talk[5] >= ymax) {
                        talk[1] = 0;
                        gm.DRepaint = false;
                        return; // the character starts the next page
                    }
                }
                if (talk[4] != X0 || c != ' ') { // a space that wrapped is dropped
                    talk[4] = talk[4] + Txt.drawChar(n.tg.g, c, talk[4], talk[5], col);
                    last = c;
                }
            }
            talk[0] = talk[0] + 1;
            if (talk[4] >= limit) {
                talk[4] = X0;
                talk[5] = talk[5] + lh;
                last = ' ';
                if (talk[5] >= ymax) {
                    talk[1] = 0;
                    gm.DRepaint = false;
                    k = 100;
                }
            }
            if (talk[0] >= n.Talk.length()) {
                talk[1] = 1;
                gm.DRepaint = false;
                k = 100;
                if (n.popup >= 0) {
                    if (gm.m.maxpop > 0) {
                        gm.State = 9;
                    } else {
                        gm.m.LoadPopup(n.popup, 0, 0);
                    }
                } else if (n.popup1 >= 0) {
                    if (gm.m.maxpop > 0) {
                        gm.State = 9;
                    } else {
                        gm.m.LoadPopup(n.popup1, 0, 1);
                    }
                }
            }
            ++k;
        }
    }

    /** Pixel width of the word starting at i (control codes skipped, /Nxx counted as the name). */
    static int wordWidth(NPC n, int i) {
        String s = n.Talk;
        int len = s.length(), w = 0;
        while (i < len) {
            char c = s.charAt(i);
            if (c == ' ') {
                break;
            }
            if (c == '/' && i + 1 < len) {
                char d = s.charAt(i + 1);
                if (d == 'C') {
                    i += 3;
                    continue;
                }
                if (d == 'P' || d == 'Z' || d == 'S') {
                    i += 4;
                    continue;
                }
                if (d == 'Y') {
                    break;
                }
                if (d == 'N' && i + 3 < len) {
                    int who = num2(s, i + 2);
                    String nm = who >= 0 && who < n.name.length ? n.name[who] : n.gm.c.name;
                    if (nm != null) {
                        w += Txt.width(nm);
                    }
                    i += 4;
                    continue;
                }
            }
            w += Txt.charWidth(c);
            i++;
        }
        return w;
    }

    private static int num2(String s, int i) {
        char a = s.charAt(i), b = s.charAt(i + 1);
        if (a == '-') {
            return -(b - '0');
        }
        return (a - '0') * 10 + (b - '0');
    }

    /** GameCanvas.DrawTalk: the box, the typed page, name tag, portrait, favour meter. */
    static void DrawTalk(GameCanvas gc, Graphics g) {
        layout(gc);
        NPC n = gc.n;
        int W = gc.getWidth();
        javax.microedition.lcdui.Graphics m = g.g;
        UiKit.saveClip(g);
        m.setClip(0, 0, W, bottom);
        // portrait stands on the box (drawn first: the box covers its feet)
        if (n.Talkimg != null) {
            g.drawImage(n.Talkimg, W - 6, by + 6, 40);
        }
        m.setColor(UiKit.DARK);
        m.fillRoundRect(0, by, W, bottom - by + 16, 16, 16);
        m.setColor(UiKit.LINE);
        m.fillRoundRect(2, by + 2, W - 4, bottom - by + 16, 14, 14);
        m.setColor(UiKit.BG);
        m.fillRoundRect(4, by + 4, W - 8, bottom - by + 16, 12, 12);
        m.fillRect(tx - 4, ty, tw + 8, th);
        if (n.TBuff != null) {
            g.drawImage(n.TBuff, tx, ty, 4);
        }
        if (n.talk != null && n.talk[1] >= 0 && gc.State == 6) {
            UiKit.arrow(g, W - 11, bottom - 11, UiKit.ACC_D);
        }
        // name tag
        UiKit.pill(g, 10, by - Txt.height() / 2 - 2, name, UiKit.ACC, UiKit.DARK, UiKit.WHITE);
        if (gc.opt[46] <= 1 && n.talk != null && n.Heroine(n.talk[7])) {
            favour(gc, g, W);
        }
        UiKit.restoreClip(g);
    }

    /** Favour meter of a heroine (logic of the original: fav[1] counts the "+n" float, fav[2] the level-up bounce). */
    static void favour(GameCanvas gc, Graphics g, int W) {
        NPC n = gc.n;
        javax.microedition.lcdui.Graphics m = g.g;
        byte[] d = n.dat[n.talk[7] - 1];
        int x = UiKit.pillW(name) + 18;
        int y = by - 6;
        int pw = 48 + 4 + 20;
        if (x + pw + 6 > W - 4) {
            x = W - 4 - pw - 6;
        }
        m.setColor(UiKit.DARK);
        m.fillRoundRect(x, y - 2, pw + 6, 12, 12, 12);
        m.setColor(0x5A1020);
        m.fillRect(x + 4, y + 2, 48, 4);
        m.setColor(0xFF5A7A);
        m.fillRect(x + 4, y + 2, d[21] * 48 / 100, 4);
        int lv = d[20];
        int dy = n.fav[2] == 0 ? 0 : n.fav[1] / 3 % 2;
        g.drawImage(gc.im.Reside[6], x + 54, y - 4 - dy, 4);
        UiKit.num(g, lv, x + pw + 3, y - 3 - dy, 0xFFE14A, UiKit.RIGHT);
        if (n.fav[0] != 0 && n.fav[1] <= 20) {
            n.fav[1] = (byte) (n.fav[1] + 1);
            if (n.fav[1] < 18) {
                UiKit.signed(g, n.fav[0], x + 4, y - 16 - n.fav[1] / 2, UiKit.WHITE);
            }
            if (n.fav[1] >= 20) {
                n.fav[2] = 0;
            }
        }
    }

    /** GameCanvas.DrawPopup: choice list (keys and state as the original). */
    static void DrawPopup(GameCanvas gc, Graphics g) {
        if (gc.KeyCode != -999) {
            gc.m.PopKey(gc.KeyCode);
        }
        if (gc.m.PopBuff == null) {
            return;
        }
        String[] items = gc.m.popment;
        int cnt = items.length;
        int rh = Txt.height() + 7;
        int w = 0;
        for (int i = 0; i < cnt; i++) {
            int iw = Txt.width(items[i]);
            if (iw > w) {
                w = iw;
            }
        }
        int W = gc.getWidth();
        w += 40;
        if (w < 96) {
            w = 96;
        }
        if (w > W - 12) {
            w = W - 12;
        }
        int h = cnt * rh + 10;
        int x, y;
        if (gc.n.TBuff != null) {
            layout(gc);
            x = W - w - 8;
            y = by - h - 14;
            if (y < gc.h) {
                y = gc.h;
            }
        } else {
            x = (W - w) / 2;
            y = (gc.getHeight() - h) / 2;
        }
        UiKit.panel(g, x, y, w, h);
        for (int i = 0; i < cnt; i++) {
            int ry = y + 5 + i * rh;
            boolean f = i == gc.m.popcur;
            if (f) {
                UiKit.focusRow(g, x + 5, ry, w - 10, rh);
                UiKit.pointer(g, x + 10, ry + rh / 2);
            }
            UiKit.text(g, items[i], x + 20, ry + 3, f ? UiKit.DARK : UiKit.SUB, UiKit.LEFT);
        }
    }
}
