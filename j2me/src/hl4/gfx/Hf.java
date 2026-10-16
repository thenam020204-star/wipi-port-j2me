package hl4.gfx;

/**
 * HF text engine (HF_DrawTextAnchorH2Color @0x7588c and helpers).  Strings are raw EUC-KR byte arrays with the game's inline
 * codes: bytes >= 0x80 start a two-byte Hangul syllable (11 px advance), ';' = line break (or a space when 'semicolon' is set,
 * TF_semiReturn), '=' and ' ' advance 4, and the ten COLOR_CHANGE characters switch the text colour.  All text is drawn pixel by
 * pixel into the frame clipped to the FRAME (not OutR) with the current font colour (HF_DrawPixel with _isNoDrawLine = 1, the
 * only mode HF_Initialize ever sets).  Verified against the original by tools/e_golden.py hf (docs/phaseE_selftest.txt).
 */
public final class Hf {
    private static final String CC = "&|${}'\"`^<";
    private static final int[] CC_RGB = {
        255, 144, 0, 137, 137, 137, 0, 0, 0, 255, 255, 255, 0, 191, 243, 255, 216, 0, 0, 176, 255, 181, 85, 255, 255, 33, 42, 62, 255, 36
    };
    /** ORB_COLOR jamo table (51 bytes @0xd8e28 + 0x11c): index = trail byte - 0xa1 */
    private static final byte[] JAMO = {
        0, 1, 0, 2, 0, 0, 3, 4, 5, 0, 5, 0, 0, 0, 19, 20, 6, 7, 8, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 0, 1, 2, 3, 4, 7, 8,
        9, 10, 11, 12, 15, 16, 17, 18, 19, 20, 23, 24, 25, 26
    };

    /** _fontColor (RGB565 value written by HF_DrawPixel) */
    public static int fontColor;
    /** _applyColorChange (initial value 1; HF_SetColorChange has no caller) */
    public static boolean applyColorChange = true;
    /** bSemicolon (TF_semiSet / TF_semiReturn) */
    public static int semicolon;

    private static HnfFont font;
    private static final int[] rows = new int[16];

    private Hf() {
    }

    public static void init() {
        if (font == null) {
            font = new HnfFont();
        }
    }

    /** HF_SetColor(r, g, b) with _isNoDrawLine = 1: only the font colour changes. */
    public static void setColor(int r, int g, int b) {
        fontColor = Prim.pixel(r, g, b);
    }

    public static int realWidthEng(int ft, int c) {
        return HnfFont.latinWidth(ft, c);
    }

    public static int heightEng(int ft) {
        return ft == 0 ? 11 : 8;
    }

    public static int heightHan() {
        return 11;
    }

    private static boolean isEscape(int c) {
        return c == 0x3b || CC.indexOf((char) c) >= 0;
    }

    /** HF_GetColorBy(c, noDrawLineAndKeep): RGB565 of a colour escape char or -1; may set fontColor (as the original does). */
    private static int colorBy(int c, boolean setFont) {
        int i = CC.indexOf((char) c);
        if (i < 0) {
            return -1;
        }
        int px = Prim.pixel(CC_RGB[i * 3], CC_RGB[i * 3 + 1], CC_RGB[i * 3 + 2]);
        if (setFont) {
            fontColor = px;
        }
        return px;
    }

    /** HF_GetWidth2(str, end, start, maxW, font). */
    public static int width2(byte[] s, int end, int i, int maxW, int ft) {
        if (i < end) {
            int w = 1;
            int nw;
            do {
                int b = s[i] & 0xff;
                if ((b & 0x80) == 0) {
                    nw = w;
                    if (b == 0x3b) {
                        if (semicolon != 1) {
                            break;
                        }
                        i++;
                        nw = w + 4;
                    } else {
                        if (b == 0) {
                            break;
                        }
                        if (!isEscape(b)) {
                            if (b == 0x3d || b == 0x20) {
                                nw = w + 4;
                            } else {
                                nw = realWidthEng(ft, b) + w + (ft == 0 ? 1 : -1);
                            }
                            i++;
                        } else {
                            i++;
                        }
                    }
                } else {
                    i += 2;
                    nw = w + 11;
                }
                if (maxW > 0 && nw > maxW) {
                    return w;
                }
                w = nw;
            } while (i < end);
            if (nw != 1) {
                return nw;
            }
        }
        return 0;
    }

    /**
     * HF_GetByteLenPrintable(str, len, start, maxW, nLines, font) @0x84ea8: number of bytes (from 'start') that fill the next
     * 'nLines' lines of width maxW (wrapping rules identical to the drawing code).
     */
    public static int byteLenPrintable(byte[] s, int len, int start, int maxW, int nLines, int ft) {
        int lines = 0;
        int cur = 0;
        int i = start;
        for (;;) {
            if (!(lines < nLines && i < len)) {
                return i - start;
            }
            int b = s[i] & 0xff;
            if ((b & 0x80) != 0) {
                if (maxW < cur + 10) {
                    lines++;
                    cur = 0;
                } else {
                    cur += 11;
                    i += 2;
                }
                continue;
            }
            if (b != 0x20 || cur != 0) {
                if (!isEscape(b)) {
                    if (b == 0) {
                        return i - start;
                    }
                    int w = realWidthEng(ft, b);
                    if (maxW < w + cur) {
                        lines++;
                        cur = 0;
                        continue;
                    }
                    if (b == 0x3d || b == 0x20) {
                        cur += 4;
                    } else {
                        cur += w + (ft == 0 ? 1 : -1);
                    }
                } else if (b == 0x3b) {
                    lines++;
                    cur = 0;
                }
            }
            i++;
        }
    }

    /**
     * HF_GetNextLen(str, len, start, off) @0x74e08: the typewriter step of the dialog text: from position start + off, one printable glyph is taken
     * (spaces, escape codes and '=' ride along) and the position of the next glyph, relative to start, is returned (len - start at the end).
     */
    public static int nextLen(byte[] s, int len, int start, int off) {
        int i = off + start;
        boolean seen = false;
        while (i < len) {
            int b = s[i] & 0xff;
            if ((b & 0x80) == 0) {
                if (b != 0x20 && !isEscape(b) && b != 0x3d) {
                    if (seen) {
                        return (1 - start) + i;
                    }
                    seen = true;
                }
                i++;
            } else {
                if (seen) {
                    return (2 - start) + i;
                }
                seen = true;
                i += 2;
            }
        }
        return len - start;
    }

    /** HF_GetWidth(str, len, font) = HF_GetWidth2(str, len, 0, 0, font). */
    public static int width(byte[] s, int len, int ft) {
        return width2(s, len, 0, 0, ft);
    }

    /** HF_DrawPixel with _isNoDrawLine = 1: frame-clipped store of fontColor. */
    private static void pixel(Frame f, int x, int y) {
        if (x >= 0 && y >= 0 && x < f.w && y < f.h) {
            f.px[y * f.w + x] = (short) fontColor;
        }
    }

    /** HF_DrawTextAnchorH(frame, x, y, str, len, font, anchor): single line, no wrapping. */
    public static void drawAnchorH(Frame f, int x, int y, byte[] s, int len, int ft, int anchor) {
        int off = 0;
        if (anchor == 1) {
            off = width(s, len, ft) >> 1;
        } else if (anchor == 2) {
            off = width(s, len, ft);
        }
        drawColor(f, x - off, y, 1000, ft, s, 0, 0, len, 0, -1);
    }

    /** HF_DrawText(frame, x, y, str, font): whole string, end = 1000. */
    public static void drawText(Frame f, int x, int y, byte[] s, int ft) {
        drawColor(f, x, y, 1000, ft, s, 0, 0, 1000, 0, -1);
    }

    /** HF_DrawTextAnchorH2 / HF_DrawText3 (colour = -1 keeps the current colour). */
    public static void drawAnchorH2(Frame f, int x, int y, int maxW, int ft, byte[] s, int start, int skip, int end, int anchor) {
        drawColor(f, x, y, maxW, ft, s, start, skip, end, anchor, -1);
    }

    /** x of the next line after a break: re-anchored for 1/2 (width of the rest of the line), reset for 0, unchanged otherwise. */
    private static int xAfterBreak(int anchor, int x0, int xNow, byte[] s, int end, int i, int start, int maxW, int ft) {
        if (anchor == 1) {
            return x0 - (width2(s, end, i, maxW, ft) >> 1);
        }
        if (anchor == 2) {
            return x0 - width2(s, end, start, maxW, ft);
        }
        return anchor == 0 ? x0 : xNow;
    }

    /** HF_DrawTextAnchorH2Color(frame, x, y, maxW, font, str, start, skip, end, anchor, color). */
    public static void drawColor(Frame f, int x0, int y0, int maxW, int ft, byte[] s, int start, int skip, int end, int anchor,
                                 int color) {
        init();
        if (color != -1) {
            fontColor = color;
        }
        int x = x0;
        if (anchor == 1) {
            x = x0 - (width2(s, end, start, maxW, ft) >> 1);
        } else if (anchor == 2) {
            x = x0 - width2(s, end, start, maxW, ft);
        }
        boolean keep = color == -1;
        int i;
        if (applyColorChange && start > 0) {
            for (i = 0; i < start; i++) {
                int c = colorBy(s[i] & 0xff, keep);
                if (c != -1 && keep) {
                    Prim.fg = c;
                }
            }
        }
        int cur = 0;
        int y = y0;
        i = start;
        // ---- skip phase: lay out 'skip' bytes without drawing (only the line position advances) ----
        while (i < skip + start) {
            int b = s[i] & 0xff;
            if ((b & 0x80) == 0) {
                int col;
                if (!applyColorChange || (col = colorBy(b, keep)) == -1) {
                    if (cur != 0 || b != 0x20) {
                        int w = realWidthEng(ft, b);
                        boolean brk;
                        if (maxW < w + cur) {
                            y += 2 + heightHan();
                            brk = true;
                            if (b == 0x3b) {
                                i++;
                            }
                        } else if (b != 0x3b) {
                            cur += w - 1;
                            i++;
                            continue;
                        } else {
                            y += 2 + heightHan();
                            i++;
                            brk = true;
                        }
                        if (brk) {
                            x = xAfterBreak(anchor, x0, x, s, end, i, start, maxW, ft);
                            cur = 0;
                        }
                        continue;
                    }
                    i++;       // leading blank on a fresh line is skipped
                } else {
                    if (keep) {
                        Prim.fg = col;
                    }
                    i++;
                }
            } else {
                if (maxW < cur + 10) {
                    y += 2 + heightHan();
                    x = xAfterBreak(anchor, x0, x, s, end, i, start, maxW, ft);
                    cur = 0;
                } else {
                    i += 2;
                    cur += 11;
                }
            }
        }
        // ---- draw phase ----
        if (i < end && i < end + start) {
            int c = s[i] & 0xff;
            if (c != 0) {
                x += cur;
                do {
                    if ((c & 0x80) == 0) {
                        if (cur == 0 && c == 0x20) {
                            i++;
                        } else {
                            if (c == 0x3b) {
                                c = semicolon == 1 ? 0x20 : 0x3b;
                            }
                            int w = realWidthEng(ft, c);
                            boolean rebreak = false;
                            if (maxW < w + cur) {
                                y += 2 + heightHan();
                                if (c == 0x3b) {
                                    i++;
                                }
                                rebreak = true;
                            } else if (c == 0x3b) {
                                y += 2 + heightHan();
                                i++;
                                rebreak = true;
                            } else {
                                int col;
                                if (!applyColorChange || (col = colorBy(c, keep)) == -1) {
                                    if (c == 0x3d || c == 0x20) {
                                        x += 4;
                                        cur += 4;
                                        i++;
                                    } else {
                                        int ch = s[i] & 0xff;
                                        i++;
                                        int hh = heightEng(ft);
                                        int ww = realWidthEng(ft, c);
                                        if (ch >= 32 && ch <= 126) {
                                            font.latin(ft, ch, rows);
                                            for (int r = 0; r < hh; r++) {
                                                int bits = rows[r];
                                                for (int k = 0; k < ww; k++) {
                                                    if (((bits << k) & 0x80) != 0) {
                                                        pixel(f, x + k, y + r);
                                                    }
                                                }
                                            }
                                        }
                                        if (ft == 0) {
                                            x += 1 + ww;
                                            cur += 1 + realWidthEng(0, c);
                                        } else {
                                            x += -1 + ww;
                                            cur += -1 + realWidthEng(ft, c);
                                        }
                                    }
                                } else {
                                    if (keep) {
                                        Prim.fg = col;
                                    }
                                    i++;
                                }
                            }
                            if (rebreak) {
                                x = xAfterBreak(anchor, x0, x, s, end, i, start, maxW, ft);
                                cur = 0;
                            }
                        }
                    } else {
                        if (maxW < cur + 10) {
                            y += 2 + heightHan();
                            x = xAfterBreak(anchor, x0, x, s, end, i, start, maxW, ft);
                            cur = 0;
                        } else {
                            int code = johabCode(c, s[i + 1] & 0xff);
                            font.compose(code, rows);
                            for (int r = 0; r < 11; r++) {
                                int bits = rows[r];
                                for (int k = 0; k < 16; k++) {
                                    if (((bits >> (15 - k)) & 1) != 0) {
                                        pixel(f, x + k, y + r);
                                    }
                                }
                            }
                            x += 11;
                            cur += 11;
                            i += 2;
                        }
                    }
                    if (i < end && i < end + start) {
                        c = s[i] & 0xff;
                    } else {
                        c = 0;
                    }
                } while (c != 0);
            }
        }
    }

    /** Johab-style code of a KS X 1001 pair as HF_DrawTextAnchorH2Color derives it (@0x85c14..0x85f08), jamo specials included. */
    static int johabCode(int lead, int trail) {
        int k = trail - 0xa1;
        if (lead == 0xa4) {
            if (k >= 0 && k <= 0x32) {
                return jamo(trail);
            }
        } else if (lead == 0xa3 && trail == 0xbf) {
            return (JAMO[0xaf - 0xa1] + 2) << 10;
        }
        if (trail == 0xa4 && lead == 0xa1) {
            return (JAMO[0xaf - 0xa1] + 2) << 10;
        }
        if (trail == 0xa5 && lead == 0xa1) {
            return (JAMO[0xb0 - 0xa1] + 2) << 10;
        }
        int hi = font.hanInfoHi(lead, trail);
        int lo = font.hanInfoLo(lead, trail);
        if (hi == 0xda) {
            return jamo2(lo);
        }
        return (hi << 8) | lo;
    }

    /** lead 0xa4 branch / info 0xda branch with a guard on the range (the 0xa4 caller already checked k <= 0x32). */
    private static int jamo(int trail) {
        return jamo2(trail);
    }

    private static int jamo2(int t) {
        int k = t - 0xa1;
        if (k >= 0 && k < 0x1e) {
            return (JAMO[k] + 2) << 10;
        }
        if (t - 0xbf >= 0 && t - 0xbf < 0x15) {
            int v = JAMO[k] + 3;
            return ((v << 5) & 0xe0) | ((v >> 3) << 8);
        }
        return 0xda00 | t;
    }
}
