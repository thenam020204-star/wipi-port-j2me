package hl4.data;

import hl4.sys.Bytes;

/**
 * H4/PAL/_H_###_PAL palette-swap files and DM_ChangePalette (docs/phaseC_pal.md; verified 196 files / 1568 cases).
 * File: u8 header (ignored), then pairs {u32le old, u32le new} (0x00RRGGBB); the loader reads pos = 1, 9, ... while pos < size
 * (a trailing partial pair reads past the end: zero-padded here).
 */
public final class Pal {
    private Pal() {
    }

    /** Returns 2 ints per pair: old888, new888. */
    public static int[] parse(byte[] b) {
        int n = b.length > 1 ? (b.length - 1 + 7) / 8 : 0;
        int[] out = new int[2 * n];
        for (int i = 0; i < n; i++) {
            int pos = 1 + 8 * i;
            out[2 * i] = u32(b, pos);
            out[2 * i + 1] = u32(b, pos + 4);
        }
        return out;
    }

    private static int u32(byte[] b, int p) {
        int v = 0;
        for (int i = 3; i >= 0; i--) {
            v = (v << 8) | (p + i < b.length ? b[p + i] & 0xff : 0);
        }
        return v;
    }

    /** old888 -> 565 as DM_ChangePalette computes it (green keeps its top 5 bits only). */
    public static int to565Old(int v) {
        return ((v >> 8) & 0xf800) | ((v & 0xff) >> 3) | ((v >> 5) & 0x7c0);
    }

    public static int to565New(int v) {
        return (((v >> 16) & 0xf8) << 8) | ((v & 0xff) >> 3) | (((v >> 8) & 0xf8) << 3);
    }

    /** DM_ChangePalette on the body palette and the working palette (both modified for the FIRST matching entry of each pair). */
    public static void apply(short[] body, short[] work, int[] pairs) {
        if (body.length == 0) {
            return;
        }
        for (int k = 0; k < pairs.length; k += 2) {
            int o = to565Old(pairs[k]);
            for (int i = 0; i < body.length; i++) {
                if ((body[i] & 0xffff) == o) {
                    short n = (short) to565New(pairs[k + 1]);
                    body[i] = n;
                    work[i] = n;
                    break;
                }
            }
        }
    }
}
