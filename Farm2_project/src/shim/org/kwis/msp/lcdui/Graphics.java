package org.kwis.msp.lcdui;

/**
 * WIPI-Java Graphics mapped onto a MIDP Graphics.
 *
 * Differences from MIDP handled here:
 *  - anchors: WIPI TOP is 0 (MIDP needs an explicit vertical bit), VCENTER is
 *    allowed for text as well;
 *  - setAlpha(a): alpha blending for fills, images, lines and text;
 *  - getRGBPixels / setRGBPixels on the target surface.
 */
public class Graphics {
    public static final int HCENTER = 1;
    public static final int VCENTER = 2;
    public static final int LEFT = 4;
    public static final int RIGHT = 8;
    public static final int TOP = 0;
    public static final int BOTTOM = 32;
    public static final int BASELINE = 64;

    private static final int M_HCENTER = javax.microedition.lcdui.Graphics.HCENTER;
    private static final int M_LEFT = javax.microedition.lcdui.Graphics.LEFT;
    private static final int M_RIGHT = javax.microedition.lcdui.Graphics.RIGHT;
    private static final int M_TOP = javax.microedition.lcdui.Graphics.TOP;

    public javax.microedition.lcdui.Graphics g;
    /** Surface drawn on (null only for detached graphics). */
    Image target;
    int alpha = 255;
    int color;
    Font font;

    /** Grow-only scratch buffer for alpha blending (per Graphics: the game draws from two threads). */
    private int[] scratch = new int[0];

    public Graphics(javax.microedition.lcdui.Graphics g, Image target) {
        this.g = g;
        this.target = target;
        this.font = Font.getDefaultFont();
    }

    /** Called by the host before every paint() of the screen surface. */
    public void reset(int cx, int cy, int cw, int ch) {
        g.translate(-g.getTranslateX(), -g.getTranslateY());
        g.setClip(cx, cy, cw, ch);
        g.setColor(0);
        g.setStrokeStyle(javax.microedition.lcdui.Graphics.SOLID);
        color = 0;
        alpha = 255;
        font = Font.getDefaultFont();
    }

    private int[] scratch(int n) {
        if (scratch.length < n) {
            scratch = new int[n];
        }
        return scratch;
    }

    // ------------------------------------------------------------------ state

    public void setColor(int rgb) {
        color = rgb & 0xffffff;
        g.setColor(color);
    }

    public void setColor(int r, int gg, int b) {
        setColor(((r & 0xff) << 16) | ((gg & 0xff) << 8) | (b & 0xff));
    }

    public int getColor() {
        return color;
    }

    public void setFont(Font f) {
        font = f == null ? Font.getDefaultFont() : f;
    }

    public Font getFont() {
        return font;
    }

    public void setAlpha(int a) {
        if (a < 0) {
            a = 0;
        } else if (a > 255) {
            a = 255;
        }
        alpha = a;
    }

    public int getAlpha() {
        return alpha;
    }

    public void setClip(int x, int y, int w, int h) {
        g.setClip(x, y, w, h);
    }

    public void clipRect(int x, int y, int w, int h) {
        g.clipRect(x, y, w, h);
    }

    public int getClipX() {
        return g.getClipX();
    }

    public int getClipY() {
        return g.getClipY();
    }

    public int getClipWidth() {
        return g.getClipWidth();
    }

    public int getClipHeight() {
        return g.getClipHeight();
    }

    public void translate(int x, int y) {
        g.translate(x, y);
    }

    public int getTranslateX() {
        return g.getTranslateX();
    }

    public int getTranslateY() {
        return g.getTranslateY();
    }

    // ------------------------------------------------------------------ shapes

    public void fillRect(int x, int y, int w, int h) {
        if (alpha >= 255) {
            g.fillRect(x, y, w, h);
            return;
        }
        if (alpha == 0 || w <= 0 || h <= 0) {
            return;
        }
        // intersect with clip first so the scratch buffer stays small
        int cx = g.getClipX(), cy = g.getClipY();
        int x2 = Math.min(x + w, cx + g.getClipWidth());
        int y2 = Math.min(y + h, cy + g.getClipHeight());
        if (x < cx) {
            x = cx;
        }
        if (y < cy) {
            y = cy;
        }
        w = x2 - x;
        h = y2 - y;
        if (w <= 0 || h <= 0) {
            return;
        }
        int band = 16;
        if (band > h) {
            band = h;
        }
        int[] buf = scratch(w * band);
        int px = (alpha << 24) | color;
        for (int i = w * band - 1; i >= 0; i--) {
            buf[i] = px;
        }
        for (int yy = y; yy < y + h; yy += band) {
            int bh = Math.min(band, y + h - yy);
            g.drawRGB(buf, 0, w, x, yy, w, bh, true);
        }
    }

    public void drawRect(int x, int y, int w, int h) {
        if (alpha >= 255) {
            g.drawRect(x, y, w, h);
        } else if (alpha > 0) {
            fillRect(x, y, w + 1, 1);
            fillRect(x, y + h, w + 1, 1);
            fillRect(x, y + 1, 1, h - 1);
            fillRect(x + w, y + 1, 1, h - 1);
        }
    }

    public void drawLine(int x1, int y1, int x2, int y2) {
        if (alpha == 0) {
            return;
        }
        if (alpha < 255 && (x1 == x2 || y1 == y2)) {
            fillRect(Math.min(x1, x2), Math.min(y1, y2), Math.abs(x2 - x1) + 1, Math.abs(y2 - y1) + 1);
            return;
        }
        g.drawLine(x1, y1, x2, y2);
    }

    public void drawArc(int x, int y, int w, int h, int s, int a) {
        if (alpha > 0) {
            g.drawArc(x, y, w, h, s, a);
        }
    }

    public void fillArc(int x, int y, int w, int h, int s, int a) {
        if (alpha > 0) {
            g.fillArc(x, y, w, h, s, a);
        }
    }

    public void drawRoundRect(int x, int y, int w, int h, int aw, int ah) {
        if (alpha > 0) {
            g.drawRoundRect(x, y, w, h, aw, ah);
        }
    }

    public void fillRoundRect(int x, int y, int w, int h, int aw, int ah) {
        if (alpha > 0) {
            g.fillRoundRect(x, y, w, h, aw, ah);
        }
    }

    public void setPixel(int x, int y) {
        fillRect(x, y, 1, 1);
    }

    // ------------------------------------------------------------------ images

    private static int ax(int x, int w, int anchor) {
        if ((anchor & HCENTER) != 0) {
            return x - w / 2;
        }
        if ((anchor & RIGHT) != 0) {
            return x - w;
        }
        return x;
    }

    private static int ay(int y, int h, int anchor) {
        if ((anchor & VCENTER) != 0) {
            return y - h / 2;
        }
        if ((anchor & (BOTTOM | BASELINE)) != 0) {
            return y - h;
        }
        return y;
    }

    public void drawImage(Image img, int x, int y, int anchor) {
        if (img == null) {
            throw new NullPointerException();
        }
        x = ax(x, img.w, anchor);
        y = ay(y, img.h, anchor);
        if (alpha >= 255) {
            g.drawImage(img.img, x, y, M_TOP | M_LEFT);
            return;
        }
        if (alpha == 0) {
            return;
        }
        // clip to visible part
        int cx = g.getClipX(), cy = g.getClipY();
        int sx = 0, sy = 0, w = img.w, h = img.h;
        if (x < cx) {
            sx = cx - x;
            w -= sx;
            x = cx;
        }
        if (y < cy) {
            sy = cy - y;
            h -= sy;
            y = cy;
        }
        int r = cx + g.getClipWidth(), b = cy + g.getClipHeight();
        if (x + w > r) {
            w = r - x;
        }
        if (y + h > b) {
            h = b - y;
        }
        if (w <= 0 || h <= 0) {
            return;
        }
        int[] src = img.pixels();
        int[] buf = scratch(w * h);
        int a = alpha;
        int k = 0;
        for (int yy = 0; yy < h; yy++) {
            int s = (sy + yy) * img.w + sx;
            for (int xx = 0; xx < w; xx++) {
                int p = src[s + xx];
                int pa = (p >>> 24) * a / 255;
                buf[k++] = (pa << 24) | (p & 0xffffff);
            }
        }
        g.drawRGB(buf, 0, w, x, y, w, h, true);
    }

    public void drawRegion(Image img, int sx, int sy, int w, int h, int tr, int x, int y, int anchor) {
        x = ax(x, (tr & 4) != 0 ? h : w, anchor);
        y = ay(y, (tr & 4) != 0 ? w : h, anchor);
        g.drawRegion(img.img, sx, sy, w, h, tr, x, y, M_TOP | M_LEFT);
    }

    // ------------------------------------------------------------------ text

    public void drawString(String s, int x, int y, int anchor) {
        if (s == null) {
            throw new NullPointerException();
        }
        if (alpha < 96) {
            return; // faded-out text (WIPI blends it; too costly per glyph here)
        }
        Font f = font;
        int w = f.stringWidth(s);
        x = ax(x, w, anchor);
        if ((anchor & VCENTER) != 0) {
            y -= f.getHeight() / 2;
        } else if ((anchor & BOTTOM) != 0) {
            y -= f.getHeight();
        } else if ((anchor & BASELINE) != 0) {
            y -= f.getBaselinePosition();
        }
        f.draw(this, s, x, y);
    }

    public void drawSubstring(String s, int off, int len, int x, int y, int anchor) {
        drawString(s.substring(off, off + len), x, y, anchor);
    }

    public void drawChar(char c, int x, int y, int anchor) {
        drawString(String.valueOf(c), x, y, anchor);
    }

    public void drawChars(char[] c, int off, int len, int x, int y, int anchor) {
        drawString(new String(c, off, len), x, y, anchor);
    }

    // ------------------------------------------------------------------ pixels

    /** Reads pixels (0xRRGGBB) of the target surface. bpl < w means tightly packed rows. */
    public void getRGBPixels(int x, int y, int w, int h, int[] out, int off, int bpl) {
        if (target == null || w <= 0 || h <= 0) {
            return;
        }
        int scan = bpl < w ? w : bpl;
        x += g.getTranslateX();
        y += g.getTranslateY();
        target.img.getRGB(out, off, scan, x, y, w, h);
        for (int yy = 0; yy < h; yy++) {
            int o = off + yy * scan;
            for (int xx = 0; xx < w; xx++) {
                out[o + xx] &= 0xffffff;
            }
        }
    }

    public void setRGBPixels(int x, int y, int w, int h, int[] in, int off, int bpl) {
        if (w <= 0 || h <= 0) {
            return;
        }
        int scan = bpl < w ? w : bpl;
        int[] buf = scratch(w * h);
        for (int yy = 0; yy < h; yy++) {
            int s = off + yy * scan;
            int d = yy * w;
            for (int xx = 0; xx < w; xx++) {
                buf[d + xx] = 0xff000000 | in[s + xx];
            }
        }
        g.drawRGB(buf, 0, w, x, y, w, h, false);
    }
}
