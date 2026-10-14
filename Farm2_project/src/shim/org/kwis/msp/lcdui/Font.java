package org.kwis.msp.lcdui;

import wipi.host.Txt;

/**
 * WIPI-Java Font. Metrics and drawing go through wipi.host.Txt so that the
 * same bitmap font is used on every handset (system fonts on real phones do
 * not carry Vietnamese/Korean glyphs and differ in size).
 */
public class Font {
    public static final int FACE_SYSTEM = 0;
    public static final int FACE_MONOSPACE = 32;
    public static final int FACE_PROPORTIONAL = 64;
    public static final int STYLE_PLAIN = 0;
    public static final int STYLE_BOLD = 1;
    public static final int STYLE_ITALIC = 2;
    public static final int STYLE_UNDERLINED = 4;
    public static final int SIZE_SMALL = 8;
    public static final int SIZE_MEDIUM = 0;
    public static final int SIZE_LARGE = 16;

    private static Font def;
    private static Font mono;

    private final int face, style, size;

    private Font(int face, int style, int size) {
        this.face = face;
        this.style = style;
        this.size = size;
    }

    public static synchronized Font getDefaultFont() {
        if (def == null) {
            def = new Font(FACE_SYSTEM, STYLE_PLAIN, SIZE_MEDIUM);
        }
        return def;
    }

    public static synchronized Font getFont(int face, int style, int size) {
        if (face == FACE_MONOSPACE && style == STYLE_PLAIN && size == SIZE_SMALL) {
            if (mono == null) {
                mono = new Font(face, style, size);
            }
            return mono;
        }
        return new Font(face, style, size);
    }

    public int getFace() {
        return face;
    }

    public int getStyle() {
        return style;
    }

    public int getSize() {
        return size;
    }

    public int getHeight() {
        return Txt.height();
    }

    public int getBaselinePosition() {
        return Txt.baseline();
    }

    public int stringWidth(String s) {
        return Txt.width(s);
    }

    public int substringWidth(String s, int off, int len) {
        return Txt.width(s.substring(off, off + len));
    }

    public int charWidth(char c) {
        return Txt.charWidth(c);
    }

    public int charsWidth(char[] c, int off, int len) {
        return Txt.width(new String(c, off, len));
    }

    void draw(Graphics g, String s, int x, int y) {
        Txt.draw(g.g, s, x, y, g.color);
    }
}
