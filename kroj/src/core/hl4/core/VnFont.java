package hl4.core;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

/**
 * Proportional 1-bpp bitmap font in the "fontcus" container format:
 * <pre>
 *   u2 utfLen, u1[utfLen] charset (modified UTF-8), s1 height, s1[n] advance widths,
 *   atlas bits: row-major, row length = sum(widths), LSB first, continuous bit stream.
 * </pre>
 * Each glyph cell is advance pixels wide; the advance already contains the spacing column.
 */
public final class VnFont {
    public final int height;
    private final char[] chars;      // sorted
    private final int[] glyphOf;     // index into widths/offsets for sorted chars
    private final int[] widths, offsets;
    private final int atlasW;
    private final byte[] bits;
    /** per glyph: packed pixel list (x | y << 8) of set pixels */
    private final int[][] pixels;

    public VnFont(byte[] data) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        String cs = in.readUTF();
        height = in.readByte();
        int n = cs.length();
        widths = new int[n];
        offsets = new int[n];
        int w = 0;
        for (int i = 0; i < n; i++) {
            widths[i] = in.readByte();
            offsets[i] = w;
            w += widths[i];
        }
        atlasW = w;
        int nbytes = (height * atlasW + 7) / 8;
        bits = new byte[nbytes];
        in.readFully(bits);
        // sorted lookup
        chars = new char[n];
        glyphOf = new int[n];
        for (int i = 0; i < n; i++) {
            char c = cs.charAt(i);
            int j = i - 1;
            while (j >= 0 && chars[j] > c) {
                chars[j + 1] = chars[j];
                glyphOf[j + 1] = glyphOf[j];
                j--;
            }
            chars[j + 1] = c;
            glyphOf[j + 1] = i;
        }
        pixels = new int[n][];
    }

    private int find(char c) {
        int lo = 0, hi = chars.length - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            char m = chars[mid];
            if (m == c) return glyphOf[mid];
            if (m < c) lo = mid + 1;
            else hi = mid - 1;
        }
        return -1;
    }

    /** Bottom ink row of capital letters (baseline). */
    public int baseline() {
        int[] p = pixels('H');
        int b = height - 1;
        if (p != null && p.length > 0) {
            b = 0;
            for (int i = 0; i < p.length; i++) b = Math.max(b, p[i] >> 8);
        }
        return b;
    }

    public boolean has(char c) {
        return find(c) >= 0;
    }

    /** Advance width of c, or -1 when the font has no glyph for it. */
    public int advance(char c) {
        int g = find(c);
        return g < 0 ? -1 : widths[g];
    }

    /** Set pixels of the glyph as packed (x | y << 8), or null when missing. */
    public int[] pixels(char c) {
        int g = find(c);
        if (g < 0) return null;
        int[] p = pixels[g];
        if (p == null) {
            int cnt = 0;
            int[] tmp = new int[widths[g] * height];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < widths[g]; x++) {
                    int bit = y * atlasW + offsets[g] + x;
                    if ((bits[bit >> 3] & (1 << (bit & 7))) != 0) tmp[cnt++] = x | (y << 8);
                }
            }
            p = new int[cnt];
            System.arraycopy(tmp, 0, p, 0, cnt);
            pixels[g] = p;
        }
        return p;
    }
}
