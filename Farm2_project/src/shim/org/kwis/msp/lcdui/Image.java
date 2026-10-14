package org.kwis.msp.lcdui;

import java.io.IOException;

import wipi.host.Res;

/**
 * WIPI-Java Image on top of MIDP 2.0.
 * Supports PNG (via MIDP) and SKVM "LBMP" bitmaps (2/8/16 bpp + optional 1-bit
 * vertical-plane transparency mask), decoded here because no J2ME device can.
 */
public class Image {
    /** Backing MIDP image (mutable for createImage(w,h), immutable otherwise). */
    public javax.microedition.lcdui.Image img;
    final boolean mutable;
    final int w, h;
    /** ARGB cache, only filled when the image is drawn with alpha < 255. */
    int[] argb;

    Image(javax.microedition.lcdui.Image img, boolean mutable) {
        this.img = img;
        this.mutable = mutable;
        this.w = img.getWidth();
        this.h = img.getHeight();
    }

    /** Host bridge: wraps the host back buffer as a mutable WIPI image. */
    public static Image hostWrap(javax.microedition.lcdui.Image m) {
        return new Image(m, true);
    }

    public static Image createImage(int w, int h) {
        return new Image(javax.microedition.lcdui.Image.createImage(w, h), true);
    }

    public static Image createImage(String name) throws IOException {
        byte[] b = Res.readAll(name);
        if (b == null) {
            throw new IOException(name);
        }
        return createImage(b, 0, b.length);
    }

    public static Image createImage(byte[] data, int off, int len) {
        if (len >= 24 && data[off] == 'L' && data[off + 1] == 'B' && data[off + 2] == 'M' && data[off + 3] == 'P') {
            return decodeLbmp(data, off, len);
        }
        return new Image(javax.microedition.lcdui.Image.createImage(data, off, len), false);
    }

    public static Image createImage(Image src) {
        return new Image(javax.microedition.lcdui.Image.createImage(src.img), false);
    }

    public Graphics getGraphics() {
        if (!mutable) {
            throw new IllegalStateException();
        }
        return new Graphics(img.getGraphics(), this);
    }

    public int getWidth() {
        return w;
    }

    public int getHeight() {
        return h;
    }

    public boolean isMutable() {
        return mutable;
    }

    /** ARGB pixels of this image (cached; only immutable images keep the cache). */
    int[] pixels() {
        if (argb != null && !mutable) {
            return argb;
        }
        int[] p = new int[w * h];
        img.getRGB(p, 0, w, 0, 0, w, h);
        if (!mutable) {
            argb = p;
        }
        return p;
    }

    private static int le32(byte[] d, int o) {
        return (d[o] & 0xff) | ((d[o + 1] & 0xff) << 8) | ((d[o + 2] & 0xff) << 16) | ((d[o + 3] & 0xff) << 24);
    }

    /** Header: "LBMP" u32 type, u32 w, u32 h, u32 size, u32 mask (little endian). */
    static Image decodeLbmp(byte[] d, int off, int len) {
        int type = le32(d, off + 4);
        int w = le32(d, off + 8);
        int h = le32(d, off + 12);
        int size = le32(d, off + 16);
        int mask = le32(d, off + 20);
        int p = off + 24;
        int plane = ((h + 7) >> 3) * w;
        int n = w * h;
        int[] out = new int[n];
        int i = 0;
        if (type == 16) {
            for (i = 0; i < n; i++) {
                int c = (d[p + 2 * i] & 0xff) | ((d[p + 2 * i + 1] & 0xff) << 8);
                int r = (c >> 11) & 31, g = (c >> 5) & 63, b = c & 31;
                out[i] = 0xff000000 | (((r << 3) | (r >> 2)) << 16) | (((g << 2) | (g >> 4)) << 8) | ((b << 3) | (b >> 2));
            }
        } else if (type == 8) {
            for (i = 0; i < n; i++) {
                int c = d[p + i] & 0xff;
                int r = (c >> 5) & 7, g = (c >> 2) & 7, b = c & 3;
                out[i] = 0xff000000 | ((r * 255 / 7) << 16) | ((g * 255 / 7) << 8) | (b * 85);
            }
        } else if (type == 2) {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int po = p + (y >> 3) * w + x;
                    int bit = y & 7;
                    int hi = (d[po] >> bit) & 1;
                    int lo = (d[po + size] >> bit) & 1;
                    int gray = 255 - (hi * 2 + lo) * 85;
                    out[y * w + x] = 0xff000000 | (gray << 16) | (gray << 8) | gray;
                }
            }
        } else {
            throw new IllegalArgumentException("LBMP type " + type);
        }
        boolean alpha = false;
        if (mask == 1) {
            int m = p + (type == 2 ? size * 2 : size);
            if (m + plane <= off + len) {
                alpha = true;
                for (int y = 0; y < h; y++) {
                    int row = m + (y >> 3) * w;
                    int bit = 1 << (y & 7);
                    int o = y * w;
                    for (int x = 0; x < w; x++) {
                        if ((d[row + x] & bit) != 0) {
                            out[o + x] = 0;
                        }
                    }
                }
            }
        }
        Image im = new Image(javax.microedition.lcdui.Image.createRGBImage(out, w, h, alpha), false);
        return im;
    }
}
