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
}
