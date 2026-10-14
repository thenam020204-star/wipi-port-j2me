package wipi.host;

import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;

/**
 * Text engine shared by the WIPI Font shim and the new UI.
 * Glyphs come from the bundled bitmap font (/font/f12.bin, Vietnamese +
 * ASCII); characters it lacks (Korean before localisation) fall back to the
 * system font at the WIPI metric of 12 px per Hangul syllable.
 */
public final class Txt {
    public static BitmapFont bf;
    private static Font sys;
    private static int h = 12;

    static void init() {
        if (bf != null) {
            return;
        }
        String f = Host.appProperty("Farm-Font");
        bf = BitmapFont.load(f == null ? "/font/f12.bin" : f.trim());
        if (bf != null) {
            h = bf.getHeight();
        }
        try {
            sys = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        } catch (Throwable t) {
            sys = Font.getDefaultFont();
        }
    }

    public static int height() {
        return h;
    }

    public static int baseline() {
        return bf != null ? bf.getBaseline() : h - 2;
    }

    private static boolean wide(char c) {
        return c >= 0x1100 && c <= 0x11ff || c >= 0x3130 && c <= 0x318f || c >= 0xac00 && c <= 0xd7a3
                || c >= 0x3000 && c <= 0x303f || c >= 0xff00 && c <= 0xffef || c >= 0x4e00 && c <= 0x9fff;
    }

    public static int charWidth(char c) {
        if (bf != null && bf.hasChar(c)) {
            return bf.charWidth(c);
        }
        if (wide(c)) {
            return 12;
        }
        if (sys == null) {
            init();
        }
        return sys != null ? sys.charWidth(c) : 6;
    }

    public static int width(String s) {
        int n = s.length();
        if (bf != null) {
            boolean all = true;
            for (int i = 0; i < n; i++) {
                if (!bf.hasChar(s.charAt(i))) {
                    all = false;
                    break;
                }
            }
            if (all) {
                return bf.stringWidth(s);
            }
        }
        int w = 0;
        for (int i = 0; i < n; i++) {
            w += charWidth(s.charAt(i));
        }
        return w;
    }

    /** Draws one character with its top-left at (x, y); returns its advance. No allocation for font glyphs. */
    public static int drawChar(Graphics g, char c, int x, int y, int rgb) {
        g.setColor(rgb);
        if (bf != null && bf.hasChar(c)) {
            return bf.drawChar(g, c, x, y);
        }
        if (sys == null) {
            init();
        }
        if (c != ' ') {
            g.setFont(sys);
            g.drawChar(c, x, y, Graphics.TOP | Graphics.LEFT);
        }
        return charWidth(c);
    }

    /** Width of s[off, off+len) without allocating. */
    public static int width(String s, int off, int len) {
        int w = 0;
        for (int i = off, e = off + len; i < e; i++) {
            w += charWidth(s.charAt(i));
        }
        return w;
    }

    /** Draws s[off, off+len) at (x, y) without allocating; returns the end x. */
    public static int draw(Graphics g, String s, int off, int len, int x, int y, int rgb) {
        for (int i = off, e = off + len; i < e; i++) {
            x += drawChar(g, s.charAt(i), x, y, rgb);
        }
        return x;
    }

    /** Draws s with its top-left at (x, y). */
    public static void draw(Graphics g, String s, int x, int y, int rgb) {
        g.setColor(rgb);
        int n = s.length();
        if (bf != null) {
            boolean all = true;
            for (int i = 0; i < n; i++) {
                if (!bf.hasChar(s.charAt(i))) {
                    all = false;
                    break;
                }
            }
            if (all) {
                bf.drawString(g, s, x, y, Graphics.TOP | Graphics.LEFT);
                return;
            }
        }
        if (sys == null) {
            init();
        }
        g.setFont(sys);
        int cx = x;
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            int cw = charWidth(c);
            if (bf != null && bf.hasChar(c)) {
                bf.drawChar(g, c, cx, y);
            } else if (c != ' ') {
                g.drawChar(c, cx, y, Graphics.TOP | Graphics.LEFT);
            }
            cx += cw;
        }
    }
}
