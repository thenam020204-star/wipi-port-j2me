package hl4.core;

import hl4.cpu.Cpu;
import hl4.cpu.Memory;

/**
 * Host replacement ("HLE") of the game's text engine, used by the Vietnamese build.
 *
 * The original engine (same C source in both binaries) knows two glyph kinds: single byte
 * ASCII (5x11 font, advance 6) and double byte EUC-KR Hangul (composed 10x11, advance 11),
 * plus control bytes: ';' new line, ' '/'=' 4px space and colour codes &|${}'"`^&lt;.
 *
 * This class re-implements the three layout primitives (measure, fit, draw) with:
 *  - Vietnamese text stored as double byte codes (lead 0xC9.., trail 0xA1..) indexing a
 *    dictionary of syllables/characters, so all original "high byte = 2 byte char"
 *    string handling in the game keeps working and byte lengths stay close to Korean;
 *  - proportional glyphs from the user's Vietnamese bitmap font;
 *  - word wrapping (consistent between measure, fit and draw so paging logic agrees).
 * Hangul and the small ASCII font are still drawn by the original routine (one glyph per call).
 */
public abstract class TextEngine {
    public static final int LINE_H = 13;       // 11px glyph + 2
    public static final int LEAD0 = 0xC9, TRAIL0 = 0xA1;

    protected final Runtime rt;
    protected final Cpu cpu;
    protected final Memory mem;
    public VnFont font;
    public String[] dict;
    private int[] dictW;
    public int spaceW = 3;
    public int yOff = 1;
    public static boolean DEBUG;

    // guest routines / data (set by the platform)
    public int fnDrawOrig, fnColorCode, fnSetColor, fnCtx, fnPutPixel;
    public int addrEnabled, addrCodes;

    private int scratch;
    private final int[] ppArgs = new int[8];

    /** Apply a colour value returned by the colour-code routine to the text graphics context. */
    protected abstract void applyColor(int color);

    protected TextEngine(Runtime rt, VnFont font, String[] dict) {
        this.rt = rt;
        this.cpu = rt.cpu;
        this.mem = rt.mem;
        this.font = font;
        this.dict = dict;
        yOff = 8 - font.baseline();
        if (font.advance(' ') > 0) spaceW = font.advance(' ');   // put the baseline on row 8 of the original 11px cell
        dictW = new int[dict.length];
        for (int i = 0; i < dict.length; i++) {
            String s = dict[i];
            int w = 0;
            for (int j = 0; j < s.length(); j++) {
                char c = s.charAt(j);
                int a = c == ' ' ? spaceW : font.advance(c);
                w += a < 0 ? 6 : a;
            }
            dictW[i] = w;
        }
    }

    private int scratch() {
        if (scratch == 0) scratch = rt.alloc(64);
        return scratch;
    }

    // ------------------------------------------------------------------
    // tokens

    static final int T_END = 0, T_NL = 1, T_CTRL = 2, T_SPACE = 3, T_ASCII = 4, T_VN = 5, T_KOR = 6;
    private int tt, tlen, tw, tcode;

    static boolean isCtrl(int c) {
        switch (c) {
            case '&': case '|': case '$': case '{': case '}': case '\'': case '"': case '`': case '^': case '<':
                return true;
        }
        return false;
    }

    private int asciiW(int c, int f) {
        if (f != 0) return 4;
        int a = font.advance((char) c);
        return a < 0 ? 6 : a;
    }

    /** Decode the token at str[p]; ctrlAsGlyph: colour codes are printable (codes disabled in draw). */
    private void token(int str, int p, int f, boolean ctrlAsGlyph) {
        int c = mem.read8(str + p);
        if (c == 0) {
            tt = T_END;
            tlen = 0;
            tw = 0;
            return;
        }
        if (c >= 0x80) {
            int c2 = mem.read8(str + p + 1);
            tlen = 2;
            if (c >= LEAD0 && c2 >= TRAIL0) {
                int k = (c - LEAD0) * 94 + (c2 - TRAIL0);
                if (k < dict.length) {
                    tt = T_VN;
                    tcode = k;
                    tw = dictW[k];
                    return;
                }
            }
            tt = T_KOR;
            tcode = (c << 8) | c2;
            tw = 11;
            return;
        }
        tlen = 1;
        tcode = c;
        if (c == ';') {
            tt = T_NL;
            tw = 0;
        } else if (isCtrl(c) && !ctrlAsGlyph) {
            tt = T_CTRL;
            tw = 0;
        } else if (c == ' ' || c == '=') {
            tt = T_SPACE;
            tw = (c == ' ' && f == 0) ? spaceW : 4;
        } else {
            tt = T_ASCII;
            tw = asciiW(c, f);
        }
    }

    /** Width of the word starting at p, or -1 when it contains Hangul (character wrapping). */
    private int wordWidth(int str, int p, int end, int f, boolean ctrlAsGlyph) {
        int w = 0;
        while (p < end) {
            token(str, p, f, ctrlAsGlyph);
            if (tt == T_END || tt == T_NL || tt == T_SPACE) break;
            if (tt == T_KOR) return -1;
            w += tw;
            p += tlen;
        }
        return w;
    }

    // ------------------------------------------------------------------
    // line layout

    private int lineEndPos, lineWidth;

    /**
     * Lay out one line starting at p. Sets lineEndPos (start of the next line: after ';' or at
     * the wrap point; == position of NUL/end when the text ends) and lineWidth (sum of advances,
     * trailing spaces excluded when wrapping). Returns true when the text continues.
     */
    private boolean layoutLine(int str, int p, int end, int maxW, int f, boolean skipLead, boolean ctrlAsGlyph) {
        int w = 0, wAtSpaceStart = -1;
        boolean inWord = false;
        while (p < end) {
            token(str, p, f, ctrlAsGlyph);
            if (tt == T_END) break;
            if (tt == T_NL) {
                lineEndPos = p + 1;
                lineWidth = w;
                return true;
            }
            if (tt == T_CTRL) {
                p += tlen;
                continue;
            }
            if (tt == T_SPACE) {
                inWord = false;
                if (w == 0 && skipLead) {
                    p += tlen;
                    continue;
                }
                if (maxW > 0 && w > 0 && w + tw - 1 > maxW) {
                    lineEndPos = p;
                    lineWidth = wAtSpaceStart >= 0 ? wAtSpaceStart : w;
                    return true;
                }
                if (wAtSpaceStart < 0) wAtSpaceStart = w;
                w += tw;
                p += tlen;
                continue;
            }
            // printable
            if (maxW > 0 && w > 0) {
                if (!inWord && tt != T_KOR) {
                    int stt = tt, stl = tlen, stw = tw, stc = tcode;
                    int ww = wordWidth(str, p, end, f, ctrlAsGlyph);
                    tt = stt; tlen = stl; tw = stw; tcode = stc;
                    if (ww > 0 && w + ww - 1 > maxW && ww - 1 <= maxW) {
                        lineEndPos = p;
                        lineWidth = wAtSpaceStart >= 0 ? wAtSpaceStart : w;
                        return true;
                    }
                }
                if (w + tw - 1 > maxW) {
                    lineEndPos = p;
                    lineWidth = w;
                    return true;
                }
            }
            inWord = tt != T_KOR;
            wAtSpaceStart = -1;
            w += tw;
            p += tlen;
        }
        lineEndPos = p;
        lineWidth = w;
        return false;
    }

    // ------------------------------------------------------------------
    // HLE entry points (arguments as in the original functions)

    /** measure(str, end, start, maxW, font): width of the first line (+1), 0 when empty. */
    public void measure() {
        int str = cpu.arg(0), end = cpu.arg(1), start = cpu.arg(2), maxW = cpu.arg(3), f = cpu.arg(4);
        rt.ret(measure(str, end, start, maxW, f, false));
    }

    private int measure(int str, int end, int start, int maxW, int f, boolean skipLead) {
        if (start >= end) return 0;
        layoutLine(str, start, end, maxW, f, skipLead, false);
        return lineWidth == 0 ? 0 : lineWidth + 1;
    }

    /** fit(str, end, start, maxW, lines, font): bytes making up `lines` lines. */
    public void fit() {
        int str = cpu.arg(0), end = cpu.arg(1), start = cpu.arg(2), maxW = cpu.arg(3);
        int lines = cpu.arg(4), f = cpu.arg(5);
        int p = start;
        for (int n = 0; n < lines && p < end; n++) {
            boolean more = layoutLine(str, p, end, maxW, f, true, false);
            p = lineEndPos;
            if (!more) break;
        }
        rt.ret(p - start);
    }

    /** draw(gfx, x, y, maxW, font, str, start, skip, end, align) with colour -1 */
    public void drawNoColor() {
        draw(cpu.arg(0), cpu.arg(1), cpu.arg(2), cpu.arg(3), cpu.arg(4), cpu.arg(5), cpu.arg(6), cpu.arg(7), cpu.arg(8), cpu.arg(9), -1);
    }

    /** draw(gfx, x, y, maxW, font, str, start, skip, end, align, color) */
    public void drawColor() {
        draw(cpu.arg(0), cpu.arg(1), cpu.arg(2), cpu.arg(3), cpu.arg(4), cpu.arg(5), cpu.arg(6), cpu.arg(7), cpu.arg(8), cpu.arg(9), cpu.arg(10));
    }

    private int[] ctx = new int[6];
    private int gfx, fontSel;

    /** Observer of every text draw (host UI overlays). */
    public interface Sink {
        void text(int gfx, int x, int y, int maxW, int font, int align, int color, String s);
    }

    public Sink sink;

    /** Decode game text bytes [start, end) to a Java string (control bytes kept). */
    public String decode(int str, int start, int end) {
        StringBuffer sb = new StringBuffer();
        for (int i = start; i < end; i++) {
            int c = mem.read8(str + i);
            if (c == 0) break;
            if (c >= 0x80 && i + 1 < end) {
                int c2 = mem.read8(str + i + 1);
                if (c >= LEAD0 && c2 >= TRAIL0) {
                    int k = (c - LEAD0) * 94 + (c2 - TRAIL0);
                    sb.append(k < dict.length ? dict[k] : "?");
                } else sb.append('#');
                i++;
            } else sb.append((char) c);
        }
        return sb.toString();
    }

    public void draw(int gfx, int x0, int y0, int maxW, int f, int str, int start, int skip, int end, int align, int color) {
        if (sink != null) sink.text(gfx, x0, y0, maxW, f, align, color, decode(str, start + skip, end));
        if (mem.read8(addrEnabled) == 0) return;
        this.gfx = gfx;
        this.fontSel = f;
        int sc = scratch();
        cpu.call(fnCtx | 1, sc, gfx);
        for (int i = 0; i < 6; i++) ctx[i] = mem.read32(sc + i * 4);
        boolean codeFlag = color == -1;
        if (color != -1) cpu.call(fnSetColor | 1, color);
        boolean codes = mem.read8(addrCodes) != 0;
        if (codes) {
            for (int i = 0; i < start; i++) {
                int c = mem.read8(str + i);
                if (isCtrl(c)) colorCode(c, codeFlag);
            }
        }
        boolean ctrlAsGlyph = !codes;
        int drawFrom = start + skip;
        int p = start, y = y0;
        while (true) {
            // new line
            int lineX = x0;
            if (align == 1) lineX = x0 - measure(str, end, p, maxW, f, true) / 2;
            else if (align == 2) lineX = x0 - measure(str, end, p, maxW, f, true);
            boolean more = layoutLine(str, p, end, maxW, f, true, ctrlAsGlyph);
            int lineEnd = lineEndPos;
            if (DEBUG) rt.log("TE line " + p + ".." + lineEnd + " w=" + lineWidth + " x=" + lineX + " y=" + y + " more=" + more);
            // render tokens of this line
            int x = lineX, w = 0;
            int q = p;
            while (q < lineEnd) {
                token(str, q, f, ctrlAsGlyph);
                if (tt == T_END || tt == T_NL) break;
                if (tt == T_CTRL) {
                    if (codes) colorCode(tcode, codeFlag);
                } else if (tt == T_SPACE) {
                    if (w != 0) {
                        x += tw;
                        w += tw;
                    }
                } else {
                    if (q >= drawFrom) glyph(str, q, x, y);
                    x += tw;
                    w += tw;
                }
                q += tlen;
            }
            if (!more) break;
            p = lineEnd;
            y += LINE_H;
            if (p >= end) break;
        }
    }

    private void colorCode(int c, boolean codeFlag) {
        int r = cpu.call(fnColorCode | 1, c, codeFlag ? 1 : 0);
        if (r != -1 && codeFlag) applyColor(r);
    }

    /** Draw the token decoded by the last token() call at str[q]. */
    private void glyph(int str, int q, int x, int y) {
        if (tt == T_VN) {
            String s = dict[tcode];
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == ' ') {
                    x += spaceW;
                    continue;
                }
                x += vnGlyph(c, x, y);
            }
        } else if (tt == T_ASCII && fontSel == 0 && font.has((char) tcode)) {
            vnGlyph((char) tcode, x, y);
        } else {
            // Hangul, small font or unknown glyph: let the original routine draw it
            int sc = scratch() + 32;
            mem.write8(sc, mem.read8(str + q));
            if (tlen == 2) mem.write8(sc + 1, mem.read8(str + q + 1));
            mem.write8(sc + tlen, 0);
            cpu.call(fnDrawOrig | 1, new int[]{gfx, x, y, 1000, fontSel, sc, 0, 0, tlen, 0, -1});
        }
    }

    private int vnGlyph(char c, int x, int y) {
        int[] px = font.pixels(c);
        if (px == null) return 0;
        int[] a = ppArgs;
        a[0] = ctx[0];
        a[1] = ctx[1];
        a[2] = ctx[2];
        a[3] = ctx[3];
        a[4] = ctx[4];
        a[5] = ctx[5];
        for (int i = 0; i < px.length; i++) {
            a[6] = x + (px[i] & 0xFF);
            a[7] = y + yOff + (px[i] >> 8);
            cpu.call(fnPutPixel | 1, a);
        }
        return font.advance(c);
    }

    // ------------------------------------------------------------------
    // encoding helper (for tools/tests)

    private java.util.Hashtable dictIndex;

    private static boolean isWordChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= 0xC0 && c != 0x2026);
    }

    /** Encode a Vietnamese string into the game's byte form (same scheme as vi/build_vi.py). */
    public byte[] encode(String s) {
        if (dictIndex == null) {
            dictIndex = new java.util.Hashtable();
            for (int i = dict.length - 1; i >= 0; i--) dictIndex.put(dict[i], new Integer(i));
        }
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (isWordChar(c)) {
                int j = i;
                boolean ascii = true;
                while (j < s.length() && isWordChar(s.charAt(j))) {
                    if (s.charAt(j) >= 0x80) ascii = false;
                    j++;
                }
                String w = s.substring(i, j);
                Integer k = (Integer) dictIndex.get(w);
                if (k != null && (!ascii || w.length() >= 3)) {
                    byte[] b = encodeCode(k.intValue());
                    out.write(b, 0, 2);
                } else {
                    for (int q = 0; q < w.length(); q++) putChar(out, w.charAt(q));
                }
                i = j;
                continue;
            }
            putChar(out, c);
            i++;
        }
        return out.toByteArray();
    }

    private void putChar(java.io.ByteArrayOutputStream out, char c) {
        if (c < 0x80) {
            out.write(c);
            return;
        }
        Integer k = (Integer) dictIndex.get(String.valueOf(c));
        if (k == null) {
            out.write('?');
            return;
        }
        byte[] b = encodeCode(k.intValue());
        out.write(b, 0, 2);
    }

    public static byte[] encodeCode(int k) {
        return new byte[]{(byte) (LEAD0 + k / 94), (byte) (TRAIL0 + k % 94)};
    }
}
