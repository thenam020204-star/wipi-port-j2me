package hl4.sys;

/** Little-endian accessors with the exact widths/signedness of the original (UM_ByteToInt16/32 and plain byte reads). */
public final class Bytes {
    private Bytes() {
    }

    public static int u8(byte[] b, int o) {
        return b[o] & 0xff;
    }

    public static int s8(byte[] b, int o) {
        return b[o];
    }

    public static int u16(byte[] b, int o) {
        return (b[o] & 0xff) | ((b[o + 1] & 0xff) << 8);
    }

    public static int s16(byte[] b, int o) {
        return (short) ((b[o] & 0xff) | ((b[o + 1] & 0xff) << 8));
    }

    public static int s32(byte[] b, int o) {
        return (b[o] & 0xff) | ((b[o + 1] & 0xff) << 8) | ((b[o + 2] & 0xff) << 16) | ((b[o + 3] & 0xff) << 24);
    }

    public static int u32(byte[] b, int o) {
        return s32(b, o);
    }

    public static void w8(byte[] b, int o, int v) {
        b[o] = (byte) v;
    }

    public static void w16(byte[] b, int o, int v) {
        b[o] = (byte) v;
        b[o + 1] = (byte) (v >> 8);
    }

    public static void w32(byte[] b, int o, int v) {
        b[o] = (byte) v;
        b[o + 1] = (byte) (v >> 8);
        b[o + 2] = (byte) (v >> 16);
        b[o + 3] = (byte) (v >> 24);
    }

    /** dlib_memset on a struct region */
    public static void fill(byte[] b, int o, int v, int n) {
        for (int i = 0; i < n; i++) {
            b[o + i] = (byte) v;
        }
    }

    /** dlib_memcpy(dst + dOff, src + sOff, n) */
    public static void copy(byte[] src, int sOff, byte[] dst, int dOff, int n) {
        System.arraycopy(src, sOff, dst, dOff, n);
    }
}
