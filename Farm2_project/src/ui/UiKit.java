import wipi.host.Txt;

/**
 * Look of the new multi-screen UI (replaces the 176x202 frames of the original
 * art): palette, panels, title ribbons, buttons, list rows and text.
 *
 * Everything is drawn with opaque MIDP primitives straight on the target
 * surface: no allocation, no per-pixel blending (an alpha layer drawn every
 * frame over a background that is not redrawn would darken step by step).
 * Text uses few colours (DARK, WHITE, SUB): the bitmap font keeps one glyph
 * atlas per colour (6 cached).
 */
final class UiKit {
    static final int BG = 0xFFF7E4;     // panel paper
    static final int BG2 = 0xF4E4C1;    // inset / alternate row
    static final int LINE = 0xC79A5B;   // light border
    static final int DARK = 0x4B2E14;   // outline
    static final int SHADOW = 0x24160A;
    static final int TEXT = DARK;  // text colours are kept to DARK/WHITE/SUB: each colour costs a glyph atlas
    static final int SUB = 0x8A6744;
    static final int ACC = 0x5CAF3A;    // green accent
    static final int ACC_D = 0x37761D;
    static final int ACC_L = 0x9EDC74;
    static final int SEL = 0xFFD24A;    // focus
    static final int SEL_D = 0xC98A00;
    static final int WHITE = 0xFFFFFF;
    static final int RED = 0xD23B2A;
    static final int BLUE = 0x2266C8;

    static final int LEFT = 4, HCENTER = 1, RIGHT = 8;

    static int tick; // animation counter, advanced once per UI frame by UiPaint

    private static int cx, cy, cw, ch; // saved clip

    static javax.microedition.lcdui.Graphics m(org.kwis.msp.lcdui.Graphics g) {
        return g.g;
    }

    static void saveClip(org.kwis.msp.lcdui.Graphics g) {
        javax.microedition.lcdui.Graphics m = g.g;
        cx = m.getClipX();
        cy = m.getClipY();
        cw = m.getClipWidth();
        ch = m.getClipHeight();
    }

    static void restoreClip(org.kwis.msp.lcdui.Graphics g) {
        g.g.setClip(cx, cy, cw, ch);
    }

    /** Rounded paper panel with outline and a 2 px drop shadow. */
    static void panel(org.kwis.msp.lcdui.Graphics g, int x, int y, int w, int h) {
        javax.microedition.lcdui.Graphics m = g.g;
        m.setColor(SHADOW);
        m.fillRoundRect(x + 2, y + 2, w, h, 12, 12);
        m.setColor(DARK);
        m.fillRoundRect(x, y, w, h, 12, 12);
        m.setColor(LINE);
        m.fillRoundRect(x + 2, y + 2, w - 4, h - 4, 10, 10);
        m.setColor(BG);
        m.fillRoundRect(x + 3, y + 3, w - 6, h - 6, 9, 9);
    }

    /** Panel with a green title ribbon; returns the y where the content starts. */
    static int titled(org.kwis.msp.lcdui.Graphics g, int x, int y, int w, int h, String title) {
        panel(g, x, y, w, h);
        ribbon(g, x + 3, y + 3, w - 6, title);
        return y + 3 + rh() + 3;
    }

    static int rh() {
        return Txt.height() + 6;
    }

    static void ribbon(org.kwis.msp.lcdui.Graphics g, int x, int y, int w, String title) {
        javax.microedition.lcdui.Graphics m = g.g;
        int h = rh();
        m.setColor(ACC_D);
        m.fillRoundRect(x, y, w, h, 9, 9);
        m.setColor(ACC);
        m.fillRoundRect(x + 1, y + 1, w - 2, h - 3, 8, 8);
        m.setColor(ACC_L);
        m.drawLine(x + 5, y + 2, x + w - 6, y + 2);
        if (title != null) {
            shadow(g, title, x + w / 2, y + 3, WHITE, DARK, HCENTER);
        }
    }

    /** Push button; the focused one is yellow. */
    static void button(org.kwis.msp.lcdui.Graphics g, int x, int y, int w, int h, String s, boolean focus) {
        javax.microedition.lcdui.Graphics m = g.g;
        m.setColor(focus ? SEL_D : LINE);
        m.fillRoundRect(x, y, w, h, 8, 8);
        m.setColor(focus ? SEL : BG2);
        m.fillRoundRect(x + 1, y + 1, w - 2, h - 3, 7, 7);
        if (focus) {
            m.setColor(0xFFE98F);
            m.drawLine(x + 4, y + 2, x + w - 5, y + 2);
        }
        text(g, s, x + w / 2, y + (h - 1 - Txt.height()) / 2, focus ? DARK : SUB, HCENTER);
    }

    /** Highlight bar of a focused list row. */
    static void focusRow(org.kwis.msp.lcdui.Graphics g, int x, int y, int w, int h) {
        javax.microedition.lcdui.Graphics m = g.g;
        m.setColor(SEL_D);
        m.fillRoundRect(x, y, w, h, 8, 8);
        m.setColor(SEL);
        m.fillRoundRect(x + 1, y + 1, w - 2, h - 2, 7, 7);
    }

    static void fill(org.kwis.msp.lcdui.Graphics g, int rgb, int x, int y, int w, int h) {
        javax.microedition.lcdui.Graphics m = g.g;
        m.setColor(rgb);
        m.fillRect(x, y, w, h);
    }

    /** Small rounded label (name tag, chips). */
    static void pill(org.kwis.msp.lcdui.Graphics g, int x, int y, String s, int bg, int edge, int fg) {
        javax.microedition.lcdui.Graphics m = g.g;
        int w = Txt.width(s) + 14, h = Txt.height() + 5;
        m.setColor(edge);
        m.fillRoundRect(x, y, w, h, h, h);
        m.setColor(bg);
        m.fillRoundRect(x + 1, y + 1, w - 2, h - 2, h - 2, h - 2);
        shadow(g, s, x + 7, y + 2, fg, edge, LEFT);
    }

    static int pillW(String s) {
        return Txt.width(s) + 14;
    }

    /** Down/right pointing "more" arrow, blinking. */
    static void arrow(org.kwis.msp.lcdui.Graphics g, int x, int y, int rgb) {
        if ((tick >> 2 & 1) != 0) {
            y++;
        }
        javax.microedition.lcdui.Graphics m = g.g;
        m.setColor(rgb);
        m.fillTriangle(x - 4, y, x + 4, y, x, y + 5);
    }

    /** Left/right arrows of a scrollable field. */
    static void sideArrows(org.kwis.msp.lcdui.Graphics g, int x0, int x1, int yc, int rgb) {
        int d = (tick >> 2 & 1);
        javax.microedition.lcdui.Graphics m = g.g;
        m.setColor(rgb);
        m.fillTriangle(x0 - d, yc, x0 + 5 - d, yc - 5, x0 + 5 - d, yc + 5);
        m.fillTriangle(x1 + d, yc, x1 - 5 + d, yc - 5, x1 - 5 + d, yc + 5);
    }

    static void text(org.kwis.msp.lcdui.Graphics g, String s, int x, int y, int rgb, int anchor) {
        if (s == null) {
            return;
        }
        if ((anchor & HCENTER) != 0) {
            x -= Txt.width(s) / 2;
        } else if ((anchor & RIGHT) != 0) {
            x -= Txt.width(s);
        }
        Txt.draw(g.g, s, 0, s.length(), x, y, rgb);
    }

    static void shadow(org.kwis.msp.lcdui.Graphics g, String s, int x, int y, int rgb, int sh, int anchor) {
        if (s == null) {
            return;
        }
        if ((anchor & HCENTER) != 0) {
            x -= Txt.width(s) / 2;
        } else if ((anchor & RIGHT) != 0) {
            x -= Txt.width(s);
        }
        Txt.draw(g.g, s, 0, s.length(), x + 1, y + 1, sh);
        Txt.draw(g.g, s, 0, s.length(), x, y, rgb);
    }

    /** Text with a 1 px outline (over the map). */
    static void outline(org.kwis.msp.lcdui.Graphics g, String s, int x, int y, int rgb, int anchor) {
        if (s == null) {
            return;
        }
        if ((anchor & HCENTER) != 0) {
            x -= Txt.width(s) / 2;
        } else if ((anchor & RIGHT) != 0) {
            x -= Txt.width(s);
        }
        javax.microedition.lcdui.Graphics m = g.g;
        int n = s.length();
        Txt.draw(m, s, 0, n, x - 1, y, 0);
        Txt.draw(m, s, 0, n, x + 1, y, 0);
        Txt.draw(m, s, 0, n, x, y - 1, 0);
        Txt.draw(m, s, 0, n, x, y + 1, 0);
        Txt.draw(m, s, 0, n, x, y, rgb);
    }

    private static final char[] nb = new char[12];

    /** Draws an integer without allocating; returns its width. */
    static int num(org.kwis.msp.lcdui.Graphics g, int v, int x, int y, int rgb, int anchor) {
        int k = nb.length;
        boolean neg = v < 0;
        long u = neg ? -(long) v : v;
        do {
            nb[--k] = (char) ('0' + (int) (u % 10));
            u /= 10;
        } while (u > 0);
        if (neg) {
            nb[--k] = '-';
        }
        int w = 0;
        for (int i = k; i < nb.length; i++) {
            w += Txt.charWidth(nb[i]);
        }
        if ((anchor & HCENTER) != 0) {
            x -= w / 2;
        } else if ((anchor & RIGHT) != 0) {
            x -= w;
        }
        for (int i = k; i < nb.length; i++) {
            x += Txt.drawChar(g.g, nb[i], x, y, rgb);
        }
        return w;
    }

    /** Width of a money value written with '.' thousands separators. */
    static int moneyW(int v) {
        int k = nb.length, w = 0, d = 0;
        long u = v < 0 ? -(long) v : v;
        do {
            w += Txt.charWidth((char) ('0' + (int) (u % 10)));
            u /= 10;
            if (++d % 3 == 0 && u > 0) {
                w += Txt.charWidth('.');
            }
        } while (u > 0);
        return v < 0 ? w + Txt.charWidth('-') : w;
    }

    private static final char[] mb = new char[20];

    /** Draws a money value with '.' separators at (x, y) left-aligned; returns its width. */
    static int money(org.kwis.msp.lcdui.Graphics g, int v, int x, int y, int rgb) {
        int k = mb.length, d = 0;
        long u = v < 0 ? -(long) v : v;
        do {
            mb[--k] = (char) ('0' + (int) (u % 10));
            u /= 10;
            if (++d % 3 == 0 && u > 0) {
                mb[--k] = '.';
            }
        } while (u > 0);
        if (v < 0) {
            mb[--k] = '-';
        }
        int x0 = x;
        for (int i = k; i < mb.length; i++) {
            x += Txt.drawChar(g.g, mb[i], x, y, rgb);
        }
        return x - x0;
    }

    /** "+n" / "-n" with a dark outline (floating numbers over the map). */
    static void signed(org.kwis.msp.lcdui.Graphics g, int v, int x, int y, int rgb) {
        for (int d = 0; d < 4; d++) {
            int dx = d == 0 ? -1 : d == 1 ? 1 : 0, dy = d == 2 ? -1 : d == 3 ? 1 : 0;
            int xx = x + dx;
            if (v > 0) {
                xx += Txt.drawChar(g.g, '+', xx, y + dy, DARK);
            }
            num(g, v, xx, y + dy, DARK, LEFT);
        }
        int xx = x;
        if (v > 0) {
            xx += Txt.drawChar(g.g, '+', xx, y, rgb);
        }
        num(g, v, xx, y, rgb, LEFT);
    }

    /** Small right-pointing triangle marking the focused row. */
    static void pointer(org.kwis.msp.lcdui.Graphics g, int x, int yc) {
        int d = (tick >> 2 & 1);
        javax.microedition.lcdui.Graphics m = g.g;
        m.setColor(DARK);
        m.fillTriangle(x + d, yc - 4, x + d, yc + 4, x + 4 + d, yc);
    }

    // ------------------------------------------------------------ word wrap

    /** Line starts/lengths of the last wrap(). */
    static final int[] ls = new int[48], ll = new int[48];

    /**
     * Word-wraps s into lines of at most maxw pixels ('\n' and '@' break);
     * results in ls/ll, returns the line count.
     */
    static int wrap(String s, int maxw) {
        int n = s.length(), lines = 0, i = 0;
        while (i < n && lines < ls.length) {
            int start = i, w = 0, lastSp = -1;
            while (i < n) {
                char c = s.charAt(i);
                if (c == '\n' || c == '@') {
                    break;
                }
                int cw = Txt.charWidth(c);
                if (w + cw > maxw && i > start) {
                    if (c == ' ') {
                        break;
                    }
                    if (lastSp > start) {
                        i = lastSp;
                    }
                    break;
                }
                if (c == ' ') {
                    lastSp = i;
                }
                w += cw;
                i++;
            }
            int end = i;
            while (end > start && s.charAt(end - 1) == ' ') {
                end--;
            }
            ls[lines] = start;
            ll[lines] = end - start;
            lines++;
            if (i < n && (s.charAt(i) == ' ' || s.charAt(i) == '\n' || s.charAt(i) == '@')) {
                i++;
            }
        }
        return lines;
    }

    /** Draws lines of the last wrap() of s. */
    static void lines(org.kwis.msp.lcdui.Graphics g, String s, int lines, int x, int y, int lh, int rgb, int anchor,
            int width) {
        for (int k = 0; k < lines; k++) {
            int xx = x;
            if ((anchor & HCENTER) != 0) {
                xx = x + (width - Txt.width(s, ls[k], ll[k])) / 2;
            }
            Txt.draw(g.g, s, ls[k], ll[k], xx, y + k * lh, rgb);
        }
    }

    /** Wraps and draws a paragraph centred in [x, x+w); returns the height used. */
    static int para(org.kwis.msp.lcdui.Graphics g, String s, int x, int y, int w, int rgb, boolean center) {
        int n = wrap(s, w);
        int lh = Txt.height() + 3;
        lines(g, s, n, x, y, lh, rgb, center ? HCENTER : LEFT, w);
        return n * lh;
    }

    static int lh() {
        return Txt.height() + 3;
    }

    // ------------------------------------------------------------ composite widgets

    /**
     * Centred message box: title ribbon, word-wrapped message, one or two
     * buttons (focus 0/1; b1 == null: no buttons, an animated wait marker).
     */
    static void dialog(org.kwis.msp.lcdui.Graphics g, int W, int H, String title, String msg, String b1, String b2,
            int focus) {
        int w = W - 24;
        if (w > 236) {
            w = 236;
        }
        int n = wrap(msg, w - 28);
        int lh = lh();
        int bh = Txt.height() + 9;
        int h = 3 + rh() + 10 + n * lh + 8 + (b1 != null ? bh : 8) + 10;
        int x = (W - w) / 2, y = (H - h) / 2;
        int cy = titled(g, x, y, w, h, title) + 7;
        lines(g, msg, n, x + 14, cy, lh, DARK, HCENTER, w - 28);
        cy += n * lh + 8;
        if (b1 == null) {
            dots(g, x + w / 2, cy + 3);
        } else if (b2 != null) {
            int bw = (w - 40) / 2;
            button(g, x + 14, cy, bw, bh, b1, focus == 0);
            button(g, x + w - 14 - bw, cy, bw, bh, b2, focus == 1);
        } else {
            int bw = w / 2;
            button(g, x + (w - bw) / 2, cy, bw, bh, b1, true);
        }
    }

    /** Three bouncing dots (waiting). */
    static void dots(org.kwis.msp.lcdui.Graphics g, int xc, int y) {
        javax.microedition.lcdui.Graphics m = g.g;
        int f = tick >> 2;
        for (int i = 0; i < 3; i++) {
            int dy = (f % 3) == i ? -2 : 0;
            m.setColor((f % 3) == i ? ACC_D : LINE);
            m.fillArc(xc - 13 + i * 10, y + dy, 6, 6, 0, 360);
        }
    }

    /** Notice toast in the middle of the screen with an OK chip. */
    static void toast(org.kwis.msp.lcdui.Graphics g, int W, int H, String s) {
        int maxw = W - 40;
        if (maxw > 220) {
            maxw = 220;
        }
        int n = wrap(s, maxw);
        int tw = 0;
        for (int k = 0; k < n; k++) {
            int lw = Txt.width(s, ls[k], ll[k]);
            if (lw > tw) {
                tw = lw;
            }
        }
        int lh = lh();
        int w = tw + 28;
        if (w < 80) {
            w = 80;
        }
        int h = n * lh + 14 + Txt.height() + 8;
        int x = (W - w) / 2, y = H / 2 - h / 2 - 10;
        panel(g, x, y, w, h);
        lines(g, s, n, x + 14, y + 8, lh, DARK, HCENTER, w - 28);
        int bw = 36, bh = Txt.height() + 5;
        int bx = x + (w - bw) / 2, by = y + h - bh / 2 - 2;
        javax.microedition.lcdui.Graphics m = g.g;
        m.setColor(ACC_D);
        m.fillRoundRect(bx, by, bw, bh, bh, bh);
        m.setColor(ACC);
        m.fillRoundRect(bx + 1, by + 1, bw - 2, bh - 2, bh - 2, bh - 2);
        text(g, "OK", bx + bw / 2, by + 2, WHITE, HCENTER);
    }
}
