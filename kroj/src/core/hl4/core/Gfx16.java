package hl4.core;

import hl4.cpu.Memory;

/** Drawing primitives on RGB565 framebuffers stored in guest memory. */
public final class Gfx16 {
    private static int[] rgb565ToArgb;

    public static int[] table() {
        if (rgb565ToArgb == null) {
            int[] t = new int[65536];
            for (int p = 0; p < 65536; p++) {
                int r = (p >> 11) & 0x1F, g = (p >> 5) & 0x3F, b = p & 0x1F;
                r = (r * 255 + 15) / 31;
                g = (g * 255 + 31) / 63;
                b = (b * 255 + 15) / 31;
                t[p] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
            rgb565ToArgb = t;
        }
        return rgb565ToArgb;
    }

    public static int toRgb565(int r, int g, int b) {
        return (((r & 0xFF) >> 3) << 11) | (((g & 0xFF) >> 2) << 5) | ((b & 0xFF) >> 3);
    }

    /** A 16bpp surface plus clip. */
    public static final class Surface {
        public int buf, width, height, bpl;
        public int clipX1, clipY1, clipX2, clipY2; // inclusive-exclusive: [x1,x2) [y1,y2)
        public int tx, ty;

        public void setClip(int x, int y, int w, int h) {
            clipX1 = Math.max(0, x);
            clipY1 = Math.max(0, y);
            clipX2 = Math.min(width, x + w);
            clipY2 = Math.min(height, y + h);
        }
    }

    public static void putPixel(Memory m, Surface s, int x, int y, int pixel) {
        if (x < s.clipX1 || y < s.clipY1 || x >= s.clipX2 || y >= s.clipY2) return;
        m.write16(s.buf + y * s.bpl + x * 2, pixel);
    }

    public static void fillRect(Memory m, Surface s, int x, int y, int w, int h, int pixel) {
        int x1 = Math.max(x, s.clipX1), y1 = Math.max(y, s.clipY1);
        int x2 = Math.min(x + w, s.clipX2), y2 = Math.min(y + h, s.clipY2);
        if (x1 >= x2 || y1 >= y2) return;
        pixel &= 0xFFFF;
        int word = pixel | (pixel << 16);
        int[][] pages = m.pages;
        for (int yy = y1; yy < y2; yy++) {
            int a = s.buf + yy * s.bpl + x1 * 2, end = s.buf + yy * s.bpl + x2 * 2;
            if ((a & 1) != 0) {
                for (; a < end; a += 2) m.write16(a, pixel);
                continue;
            }
            if ((a & 2) != 0 && a < end) {
                m.write16(a, pixel);
                a += 2;
            }
            while (end - a >= 4) {
                int[] p = pages[a >>> 16];
                if (p == null) {
                    m.write32(a, word); // faults like a normal access
                    a += 4;
                    continue;
                }
                int i = (a & 0xFFFF) >> 2;
                int n = Math.min((end - a) >> 2, 16384 - i);
                for (int k = 0; k < n; k++) p[i + k] = word;
                a += n << 2;
            }
            if (a < end) m.write16(a, pixel);
        }
    }

    public static void drawLine(Memory m, Surface s, int x0, int y0, int x1, int y1, int pixel) {
        int dx = Math.abs(x1 - x0), sx = x0 < x1 ? 1 : -1;
        int dy = -Math.abs(y1 - y0), sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        int guard = 0;
        while (true) {
            putPixel(m, s, x0, y0, pixel);
            if (x0 == x1 && y0 == y1) break;
            if (++guard > 100000) break;
            int e2 = 2 * err;
            if (e2 >= dy) {
                err += dy;
                x0 += sx;
            }
            if (e2 <= dx) {
                err += dx;
                y0 += sy;
            }
        }
    }

    /** Outline of rectangle covering (x,y)-(x+w,y+h) inclusive (MIDP semantics). */
    public static void drawRect(Memory m, Surface s, int x, int y, int w, int h, int pixel) {
        drawLine(m, s, x, y, x + w, y, pixel);
        drawLine(m, s, x, y + h, x + w, y + h, pixel);
        drawLine(m, s, x, y, x, y + h, pixel);
        drawLine(m, s, x + w, y, x + w, y + h, pixel);
    }

    /** Copy a region from src surface into dst surface (handles overlap). */
    public static void copy(Memory m, Surface dst, int dx, int dy, int w, int h, Surface src, int sx, int sy) {
        // clip against source bounds
        if (sx < 0) {
            dx -= sx;
            w += sx;
            sx = 0;
        }
        if (sy < 0) {
            dy -= sy;
            h += sy;
            sy = 0;
        }
        if (sx + w > src.width) w = src.width - sx;
        if (sy + h > src.height) h = src.height - sy;
        // clip against destination clip
        if (dx < dst.clipX1) {
            int d = dst.clipX1 - dx;
            dx += d;
            sx += d;
            w -= d;
        }
        if (dy < dst.clipY1) {
            int d = dst.clipY1 - dy;
            dy += d;
            sy += d;
            h -= d;
        }
        if (dx + w > dst.clipX2) w = dst.clipX2 - dx;
        if (dy + h > dst.clipY2) h = dst.clipY2 - dy;
        if (w <= 0 || h <= 0) return;
        // same result as copying through a snapshot: pick the row order so that overlapping
        // rows are read before they are overwritten, and memmove each row
        if (dst.buf == src.buf && dy > sy) {
            for (int row = h - 1; row >= 0; row--) m.move(dst.buf + (dy + row) * dst.bpl + dx * 2, src.buf + (sy + row) * src.bpl + sx * 2, w * 2);
        } else {
            for (int row = 0; row < h; row++) m.move(dst.buf + (dy + row) * dst.bpl + dx * 2, src.buf + (sy + row) * src.bpl + sx * 2, w * 2);
        }
    }

    /** Arc drawing (angles in degrees, counter clockwise, MIDP semantics). */
    /**
     * Draw src onto dst skipping pixels equal to key (colour keyed image); alpha 0..255 blends the
     * drawn pixels with the destination (255 = opaque). Clipped like {@link #copy}. No allocation.
     */
    public static void drawKeyed(Memory m, Surface dst, int dx, int dy, int w, int h, Surface src, int sx, int sy, int key, int alpha) {
        if (sx < 0) {
            dx -= sx;
            w += sx;
            sx = 0;
        }
        if (sy < 0) {
            dy -= sy;
            h += sy;
            sy = 0;
        }
        if (sx + w > src.width) w = src.width - sx;
        if (sy + h > src.height) h = src.height - sy;
        if (dx < dst.clipX1) {
            int d = dst.clipX1 - dx;
            dx += d;
            sx += d;
            w -= d;
        }
        if (dy < dst.clipY1) {
            int d = dst.clipY1 - dy;
            dy += d;
            sy += d;
            h -= d;
        }
        if (dx + w > dst.clipX2) w = dst.clipX2 - dx;
        if (dy + h > dst.clipY2) h = dst.clipY2 - dy;
        if (w <= 0 || h <= 0 || alpha <= 0) return;
        int a5 = (alpha + 4) >> 3;
        for (int row = 0; row < h; row++) {
            int sa = src.buf + (sy + row) * src.bpl + sx * 2;
            int da = dst.buf + (dy + row) * dst.bpl + dx * 2;
            for (int i = 0; i < w; i++, sa += 2, da += 2) {
                int p = m.read16(sa);
                if (p == key) continue;
                if (a5 < 32) p = blend(m.read16(da), p, a5);
                m.write16(da, p);
            }
        }
    }

    /** RGB565 blend: d + (s - d) * a / 32 (a = 0..32). */
    public static int blend(int d, int s, int a) {
        int s2 = (s | (s << 16)) & 0x07E0F81F;
        int d2 = (d | (d << 16)) & 0x07E0F81F;
        int r = (d2 + (((s2 - d2) * a) >> 5)) & 0x07E0F81F;
        return (r | (r >>> 16)) & 0xFFFF;
    }

    public static void arc(Memory m, Surface s, int x, int y, int w, int h, int start, int arcAngle, int pixel, boolean fill) {
        if (w <= 0 || h <= 0) return;
        double cx = x + w / 2.0, cy = y + h / 2.0, rx = w / 2.0, ry = h / 2.0;
        if (arcAngle < 0) {
            start += arcAngle;
            arcAngle = -arcAngle;
        }
        boolean full = arcAngle >= 360;
        start = ((start % 360) + 360) % 360;
        for (int yy = y; yy <= y + h; yy++) {
            for (int xx = x; xx <= x + w; xx++) {
                double nx = (xx + 0.5 - cx) / rx, ny = (yy + 0.5 - cy) / ry;
                double d = nx * nx + ny * ny;
                boolean inside;
                if (fill) inside = d <= 1.0;
                else {
                    double nx2 = (xx + 0.5 - cx) / Math.max(rx - 1, 0.5), ny2 = (yy + 0.5 - cy) / Math.max(ry - 1, 0.5);
                    inside = d <= 1.0 && nx2 * nx2 + ny2 * ny2 >= 1.0;
                }
                if (!inside) continue;
                if (!full) {
                    double ang = Math.toDegrees(atan2(-(yy + 0.5 - cy) / ry, (xx + 0.5 - cx) / rx));
                    if (ang < 0) ang += 360;
                    double rel = ang - start;
                    if (rel < 0) rel += 360;
                    if (rel > arcAngle) continue;
                }
                putPixel(m, s, xx, yy, pixel);
            }
        }
    }

    // CLDC 1.1 has no Math.atan2
    static double atan2(double y, double x) {
        if (x > 0) return atan(y / x);
        if (x < 0) return y >= 0 ? atan(y / x) + Math.PI : atan(y / x) - Math.PI;
        if (y > 0) return Math.PI / 2;
        if (y < 0) return -Math.PI / 2;
        return 0;
    }

    static double atan(double v) {
        boolean neg = v < 0;
        if (neg) v = -v;
        boolean inv = v > 1;
        if (inv) v = 1 / v;
        // polynomial approximation, max error ~1e-5 rad
        double v2 = v * v;
        double r = v * (0.9998660 + v2 * (-0.3302995 + v2 * (0.1801410 + v2 * (-0.0851330 + v2 * 0.0208351))));
        if (inv) r = Math.PI / 2 - r;
        return neg ? -r : r;
    }

    /** Convert a 16bpp region to ARGB. */
    public static void toArgb(Memory m, int buf, int bpl, int x, int y, int w, int h, int[] out, int outOff, int outStride) {
        int[] t = table();
        int[][] pages = m.pages;
        if (((buf | bpl | (x * 2)) & 3) == 0 && (w & 1) == 0) {
            // fast path: whole words (2 pixels) that never straddle a 64KB page
            for (int row = 0; row < h; row++) {
                int a = buf + (y + row) * bpl + x * 2;
                int o = outOff + row * outStride;
                int end = o + w;
                while (o < end) {
                    int[] p = pages[a >>> 16];
                    int i = (a & 0xFFFF) >> 2;
                    int n = Math.min((end - o) >> 1, 16384 - i);
                    for (int k = 0; k < n; k++) {
                        int v = p[i + k];
                        out[o++] = t[v & 0xFFFF];
                        out[o++] = t[v >>> 16];
                    }
                    a += n << 2;
                }
            }
            return;
        }
        for (int row = 0; row < h; row++) {
            int a = buf + (y + row) * bpl + x * 2;
            int o = outOff + row * outStride;
            for (int col = 0; col < w; col++, a += 2) {
                int[] p = pages[a >>> 16];
                int px = (p[(a & 0xFFFF) >> 2] >>> ((a & 2) << 3)) & 0xFFFF;
                out[o + col] = t[px];
            }
        }
    }
}
