package hl4.gfx;

import hl4.sys.Bytes;

/**
 * GBM image (docs/phaseC_formats.md section 3, tools/gbm.py): body = u8 type, u16 w, u16 h, u16 aux, u16 ncolors,
 * ncolors x u16 RGB565, pixels.  type 0x0c = 8 bpp (stride w), type 0x0b = 4 bpp (stride (w+1)/2, HIGH nibble = left pixel).
 * Pixels stay in the (shared) file array: no per-image copy.  A palette colour equal to SKIP_COL (0xF81F) is transparent.
 * pal = current palette (the original's +0x14), palBase = base palette (+0xc); both are the same array until a palette op runs.
 */
public final class Gbm {
    public static final int SKIP_COL = 0xF81F;

    public final int type;
    public final int w;
    public final int h;
    public final int aux;
    public short[] palBase;
    public short[] pal;
    public final byte[] data;
    public int palFlag;          // the original's +0x1c byte: 'palette changed' (1 after DM_SetChagePalBlendEx)
    public final int pixOff;

    public Gbm(byte[] file, int off, int size) {
        data = file;
        type = file[off] & 0xff;
        if (type != 0x0c && type != 0x0b) {
            throw new IllegalArgumentException("GBM type " + type);
        }
        w = Bytes.u16(file, off + 1);
        h = Bytes.u16(file, off + 3);
        aux = Bytes.u16(file, off + 5);
        int nc = Bytes.u16(file, off + 7);
        short[] p = new short[nc];
        for (int i = 0; i < nc; i++) {
            p[i] = (short) Bytes.u16(file, off + 9 + 2 * i);
        }
        palBase = p;
        pal = p;
        pixOff = off + 9 + 2 * nc;
        int stride = type == 0x0c ? w : (w + 1) >> 1;
        if (pixOff + stride * h > off + size) {
            throw new IllegalArgumentException("GBM body truncated");
        }
    }

    /** RGB565 colour of source pixel (u, v), or -1 when outside the image. Transparency is NOT applied here. */
    public int colorAt(int u, int v) {
        if (u < 0 || v < 0 || u >= w || v >= h) {
            return -1;
        }
        if (type == 0x0c) {
            return pal[data[pixOff + v * w + u] & 0xff] & 0xffff;
        }
        int b = data[pixOff + v * ((w + 1) >> 1) + (u >> 1)] & 0xff;
        return pal[(u & 1) == 0 ? b >> 4 : b & 0xf] & 0xffff;
    }

    /** getMergedGbmData (@0xd2af0): u32 count, then count x {u32 size, body}.  Returns the images in order. */
    public static Gbm[] loadMerged(byte[] file) {
        int n = Bytes.s32(file, 0);
        Gbm[] out = new Gbm[n];
        int off = 4;
        for (int i = 0; i < n; i++) {
            int size = Bytes.s32(file, off);
            out[i] = new Gbm(file, off + 4, size);
            off += 4 + size;
        }
        if (off != file.length) {
            throw new IllegalArgumentException("GBM container size mismatch");
        }
        return out;
    }
}
