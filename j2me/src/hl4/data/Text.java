package hl4.data;

import hl4.sys.Bytes;

/** The two string-table formats (docs/phaseC_formats.md section 5).  Strings are raw EUC-KR with the game's inline control codes. */
public final class Text {
    private Text() {
    }

    /** MrgText_load @0x9e5c4: s8 count, count x {u16be len, bytes[len]}; no terminator. */
    public static byte[][] parseMrg(byte[] b) {
        int n = b[0];
        if (n < 0) {
            n = 0;
        }
        byte[][] out = new byte[n][];
        int off = 1;
        for (int i = 0; i < n; i++) {
            int ln = ((b[off] & 0xff) << 8) | (b[off + 1] & 0xff);
            out[i] = new byte[ln];
            System.arraycopy(b, off + 2, out[i], 0, ln);
            off += 2 + ln;
        }
        return out;
    }

    /** getLinkedText @0x9e514: u16 size, u16 count, u16 offset[count] (absolute), NUL-terminated strings.  Offset of string k. */
    public static int linkedOffset(byte[] b, int k) {
        return Bytes.u16(b, 4 + 2 * k);
    }

    public static int linkedCount(byte[] b) {
        return Bytes.u16(b, 2);
    }

    /** String k up to (excluding) its NUL (or the end of the buffer). */
    public static byte[] linked(byte[] b, int k) {
        int o = linkedOffset(b, k);
        int e = o;
        while (e < b.length && b[e] != 0) {
            e++;
        }
        byte[] r = new byte[e - o];
        System.arraycopy(b, o, r, 0, e - o);
        return r;
    }

    /** All strings of a LinkedText file as separate NUL-padded arrays (the text engine may read a byte past the end of a string). */
    public static byte[][] linkedAll(byte[] b) {
        int n = linkedCount(b);
        byte[][] out = new byte[n][];
        for (int i = 0; i < n; i++) {
            byte[] s = linked(b, i);
            byte[] p = new byte[s.length + 8];
            System.arraycopy(s, 0, p, 0, s.length);
            out[i] = p;
        }
        return out;
    }
}
