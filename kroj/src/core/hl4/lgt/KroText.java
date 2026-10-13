package hl4.lgt;

import hl4.core.VnFont;
import hl4.cpu.Cpu;
import hl4.cpu.Memory;

/**
 * Kroisen Vietnamese text. Vietnamese words are stored in the game data as 2-byte codes
 * (lead 0xC9..0xFE, trail 0xA1..0xFE, index = (lead - 0xC9) * 94 + trail - 0xA1 into dict.txt; an
 * entry may end with a space), so every "byte >= 0xA0 starts a 2-byte character" rule of the game
 * keeps working and a word is one unbreakable unit for the game's own line breaking.
 * <p>
 * The game's text module (bytes > 0x9f = 2-byte KS X 1001 character, '|NN' / '|cNN' / '|' colour codes):
 * <ul>
 * <li>measure 0x28e14(str, mode): replaced as a whole ({@link #measure}, same result for Korean text);
 * a Vietnamese code adds the width of its dict entry instead of the Hangul cell width;</li>
 * <li>draw 0x296d8: its loop sends a 2-byte character whose lead is not Hangul (0xB0..0xC8) to
 * 0x297e8 ("cmp r2, #0xa4; beq jamo; b next"), so Vietnamese codes were skipped. That compare is
 * replaced by a BL to {@link #draw}: each letter of the word is rendered from the VnFont into a cell
 * bitmap and drawn with the game's own glyph routine 0x292ec (clip, alpha, gradient and outline stay
 * the game's), x advances by the letter widths. Lead 0xA4 still goes to the jamo path (0x297ee).</li>
 * </ul>
 */
final class KroText {
    static final int LEAD0 = 0xC9, TRAIL0 = 0xA1, TRAILS = 94;
    static final int HLE_MEASURE = 120, HLE_DRAW = 121, HLE_ASCII = 122;

    // text module (binary.mod 0002CCCB v01.00.03)
    static final int MEASURE = 0x28e14;
    static final int DRAW_SITE = 0x297e8, DRAW_NEXT = 0x297ec, DRAW_JAMO = 0x297ee;
    static final int GLYPH = 0x292ec;
    static final int STUB_DRAW = 0x5f000, STUB_ASCII = 0x5f008; // free space after .text (0x1000..0x5efd7), BL range
    // ASCII branch of the draw loop: "ldr r3, [sp, #8]; ldr r0, [sp, #0x24]; str r3, [sp]; ... bl 0x28ed8; x += 6"
    static final int ASCII_SITE = 0x29aa0, ASCII_DRAW = 0x29aa4, ASCII_DONE = 0x29ab8;
    static final int ASCII_W = 0x15008d6, HANGUL_W = 0x15006c2; // advance widths (6 / 10)
    static final int CELL_W = 0x15006c4, CELL_H = 0x15008e5;    // glyph bitmap of 0x292ec (10 x 12, LSB first)

    private final LgtRuntime rt;
    private final Cpu cpu;
    private final Memory mem;
    private final VnFont font;
    private final String[] dict;
    private final int[] width;
    private final int spaceW;
    private int buf; // guest glyph bitmap (cell)
    private final int[] glyphArgs = new int[5];
    private int measureOrig0, measureOrig1;

    KroText(LgtRuntime rt, VnFont font, String[] dict) {
        this.rt = rt;
        this.cpu = rt.cpu;
        this.mem = rt.mem;
        this.font = font;
        this.dict = dict;
        int sw = font.advance(' ');
        spaceW = sw > 0 ? sw : 3;
        width = new int[dict.length];
        for (int i = 0; i < dict.length; i++) width[i] = measure(dict[i]);
    }

    private int adv(char c) {
        if (c == ' ') return spaceW;
        int a = font.advance(c);
        if (a < 0) a = font.advance('?');
        return a > 0 ? a : 0;
    }

    private int measure(String s) {
        int w = 0;
        for (int i = 0; i < s.length(); i++) w += adv(s.charAt(i));
        return w;
    }

    /** dict index of a 2-byte code, or -1 when it is not a Vietnamese code */
    int code(int lead, int trail) {
        if (lead < LEAD0 || trail < TRAIL0 || trail >= TRAIL0 + TRAILS) return -1;
        int i = (lead - LEAD0) * TRAILS + (trail - TRAIL0);
        return i < dict.length ? i : -1;
    }

    void install() {
        expect(MEASURE, 0xb5f0);          // push {r4, r5, r6, r7, lr}
        expect(DRAW_SITE, 0x2aa4);        // cmp r2, #0xa4
        expect(GLYPH, 0xb5f0);
        expect(ASCII_SITE, 0x9b02);       // ldr r3, [sp, #8]
        expect(ASCII_SITE + 2, 0x9809);   // ldr r0, [sp, #0x24]
        for (int a = STUB_DRAW; a < STUB_ASCII + 8; a += 4)
            if (mem.read32(a) != 0) throw new hl4.cpu.CpuException("KroText: stub area in use");
        measureOrig0 = mem.read32(MEASURE);
        measureOrig1 = mem.read32(MEASURE + 4);
        rt.hookThumb(MEASURE, HLE_MEASURE);
        cpu.makeStub(STUB_DRAW, (LgtRuntime.CAT_HLE << 16) | HLE_DRAW);
        rt.patchBL(DRAW_SITE, STUB_DRAW);
        cpu.makeStub(STUB_ASCII, (LgtRuntime.CAT_HLE << 16) | HLE_ASCII);
        rt.patchBL(ASCII_SITE, STUB_ASCII);
        buf = rt.alloc(64);
        rt.log("KroText: " + dict.length + " words, font " + font.height + "px");
    }

    private void expect(int addr, int half) {
        if (mem.read16(addr) != half) throw new hl4.cpu.CpuException("KroText: unexpected code at " + LgtRuntime.hex(addr));
    }

    /**
     * 0x28e14(str, mode): mode 1 = "raw" width (every byte one ASCII cell, colour codes included),
     * otherwise the drawn width. Literal translation of the ARM code (including its quirk: after
     * "|cNN" the second digit is counted once more as a drawn ASCII character).
     */
    void measure() {
        rt.ret(measure(cpu.r[0], cpu.r[1] & 0xFF));
    }

    int measure(int str, int mode) {
        if (str == 0) return 0;
        int a = mem.read8(ASCII_W);
        int sp = vietnamese(str) ? spaceW : a; // ASCII space between Vietnamese words: font space
        int vis = 0, raw = 0;
        if (mem.read8(str) != 0) {
            int i = 1;
            while (true) {
                int prev = mem.read8(str + i - 1);
                if (prev == '|' && digit(mem.read8(str + i)) && digit(mem.read8(str + i + 1))) {
                    raw += 3 * a;
                    i += 2;
                } else if (prev == '|' && mem.read8(str + i) == 'c' && digit(mem.read8(str + i + 1)) && digit(mem.read8(str + i + 2))) {
                    raw += 4 * a;
                    i += 2;
                } else if (prev == '|') {
                    raw += a;
                } else if (prev > 0x9f) {
                    int k = code(prev, mem.read8(str + i));
                    i++;
                    vis += k >= 0 ? width[k] : mem.read8(HANGUL_W);
                    raw += 2 * a;
                } else {
                    vis += prev == ' ' ? sp : a;
                    raw += a;
                }
                int next = mem.read8(str + i);
                i++;
                if (next == 0) break;
            }
        }
        return mode == 1 ? raw : vis;
    }

    /**
     * Debug (harness, before install): compare the original ARM 0x28e14 with {@link #measure(int, int)}
     * on every NUL terminated string of .text plus colour-code edge cases. Returns the mismatch count.
     */
    int selfCheck(int textStart, int textEnd) {
        // run the original entry (restored for the check, interpreter only)
        int s0 = mem.read32(MEASURE), s1 = mem.read32(MEASURE + 4);
        mem.write32(MEASURE, measureOrig0);
        mem.write32(MEASURE + 4, measureOrig1);
        try {
            return selfCheck0(textStart, textEnd);
        } finally {
            mem.write32(MEASURE, s0);
            mem.write32(MEASURE + 4, s1);
        }
    }

    private int selfCheck0(int textStart, int textEnd) {
        rt.log("MEASURECHECK widths ascii=" + mem.read8(ASCII_W) + " hangul=" + mem.read8(HANGUL_W));
        int bad = 0, n = 0;
        int scratch = rt.alloc(64);
        String[] extra = {"|06abc|", "|c12x|c3", "|", "||", "|0", "|c1", "a|c99", "\u00b0\u00a1|12\u00c9\u00a1"};
        for (int a = textStart; a < textEnd; a++) {
            int str;
            if (a < textEnd - 1 && mem.read8(a) != 0 && (a == textStart || mem.read8(a - 1) == 0)) str = a;
            else continue;
            for (int mode = 0; mode < 2; mode++) {
                int o = cpu.call(MEASURE | 1, str, mode), h = measure(str, mode);
                n++;
                if (o != h && bad++ < 10) rt.log("MEASURECHECK " + LgtRuntime.hex(str) + " mode " + mode + " arm=" + o + " hle=" + h);
            }
        }
        for (int e = 0; e < extra.length; e++) {
            String t = extra[e];
            for (int i = 0; i < t.length(); i++) mem.write8(scratch + i, t.charAt(i));
            mem.write8(scratch + t.length(), 0);
            for (int mode = 0; mode < 2; mode++) {
                int o = cpu.call(MEASURE | 1, scratch, mode), h = measure(scratch, mode);
                n++;
                if (o != h && bad++ < 20) rt.log("MEASURECHECK extra " + e + " mode " + mode + " arm=" + o + " hle=" + h);
            }
        }
        rt.log("MEASURECHECK " + n + " calls, " + bad + " mismatches");
        return bad;
    }

    /** true when the NUL terminated string contains a Vietnamese 2-byte code */
    boolean vietnamese(int str) {
        int i = 0;
        while (true) {
            int b = mem.read8(str + i);
            if (b == 0) return false;
            if (b > 0x9f) {
                if (b >= LEAD0 && mem.read8(str + i + 1) >= TRAIL0) return true;
                i += 2;
            } else i++;
        }
    }

    /**
     * BL at 0x29aa0 (ASCII character r2 of the draw loop): a space of a Vietnamese string advances by the
     * font's space width instead of the 6 px ASCII cell; anything else runs the two replaced loads and
     * returns to the original glyph call.
     */
    void ascii() {
        int sp = cpu.r[13];
        if (cpu.r[2] == ' ' && vietnamese(mem.read32(sp + 0x28))) {
            mem.write32(sp + 0x24, mem.read32(sp + 0x24) + spaceW);
            cpu.r[14] = ASCII_DONE | 1;
            return;
        }
        cpu.r[3] = mem.read32(sp + 0x08);
        cpu.r[0] = mem.read32(sp + 0x24);
        // lr = ASCII_DRAW (return address of the BL)
    }

    private static boolean digit(int c) {
        return c >= '0' && c <= '9';
    }

    /** BL at 0x297e8 inside the draw loop: r2 = lead, r1 = trail, x/y/colour on the caller's frame. */
    void draw() {
        int lead = cpu.r[2], trail = cpu.r[1];
        if (lead == 0xA4) {
            cpu.r[14] = DRAW_JAMO | 1;
            return;
        }
        cpu.r[14] = DRAW_NEXT | 1; // continue with the next character
        int k = code(lead, trail);
        if (k < 0) return;
        int sp = cpu.r[13];
        int x = mem.read32(sp + 0x24), y = mem.read32(sp + 0x20);
        int fg = mem.read32(sp + 0x10), c2 = mem.read32(sp + 0x08);
        String s = dict[k];
        if (KroGame.TEXTLOG) rt.log("vi draw " + s + " @" + x + "," + y + " fg=" + LgtRuntime.hex(fg));
        int cw = mem.read8(CELL_W), ch = mem.read8(CELL_H);
        int nb = (cw * ch + 7) >> 3;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ' ') {
                x += spaceW;
                continue;
            }
            int[] px = font.pixels(c);
            if (px == null) {
                c = '?';
                px = font.pixels(c);
            }
            if (px != null) {
                mem.fill(buf, 0, nb);
                for (int j = 0; j < px.length; j++) {
                    int gx = px[j] & 0xFF, gy = px[j] >> 8;
                    if (gx >= cw || gy >= ch) continue;
                    int bit = gy * cw + gx;
                    mem.write8(buf + (bit >> 3), mem.read8(buf + (bit >> 3)) | (1 << (bit & 7)));
                }
                glyphArgs[0] = x;
                glyphArgs[1] = y;
                glyphArgs[2] = buf;
                glyphArgs[3] = fg;
                glyphArgs[4] = c2;
                cpu.call(GLYPH | 1, glyphArgs);
            }
            x += adv(c);
        }
        mem.write32(sp + 0x24, x);
    }
}
