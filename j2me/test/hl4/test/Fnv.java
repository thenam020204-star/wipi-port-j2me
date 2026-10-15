package hl4.test;

/** FNV-1a 32 (same function in tools/j2me_golden.py). */
public final class Fnv {
    private Fnv() {
    }

    public static int bytes(byte[] b, int off, int len) {
        int h = 0x811c9dc5;
        for (int i = 0; i < len; i++) {
            h ^= b[off + i] & 0xff;
            h *= 0x01000193;
        }
        return h;
    }

    public static int shorts(short[] s, int len) {
        int h = 0x811c9dc5;
        for (int i = 0; i < len; i++) {
            h ^= s[i] & 0xff;
            h *= 0x01000193;
            h ^= (s[i] >> 8) & 0xff;
            h *= 0x01000193;
        }
        return h;
    }
}
