package hl4.game;

import hl4.gfx.Frame;
import hl4.gfx.Prim;

/**
 * The game's frame buffers and the DM_* primitives built on the WIPI-C ones.  Two offscreen buffers exist in the original: _lcdG+0x10
 * ('scr', everything is drawn here and this is what gets flushed) and _lcdG+0x14 ('back', a prepared background that DM_CopyOffScreen
 * copies into 'scr').  DM_SetCliptoGBM sets ONE global clip rectangle (OutR, right/bottom exclusive) used by the game's own blits.
 */
public final class Gfx {
    public static Frame scr;
    public static Frame back;

    private Gfx() {
    }

    public static void init() {
        scr = new Frame(Glob.gameW, Glob.gameH);
        back = new Frame(Glob.gameW, Glob.gameH);
    }

    /** DM_SetCliptoGBM(x0, y0, x1, y1) */
    public static void setClip(int l, int t, int r, int b) {
        scr.setClip(l, t, r, b);
        back.setClip(l, t, r, b);
    }

    /** DM_SetFgColor */
    public static void setFg(int r, int g, int b) {
        Prim.fg = Prim.pixel(r, g, b);
    }

    /** DM_FillRectRGB(frame, x, y, w, h, r, g, b) */
    public static void fillRectRGB(Frame f, int x, int y, int w, int h, int r, int g, int b) {
        Prim.fg = Prim.pixel(r, g, b);
        Prim.fillRect(f, x, y, w, h);
    }

    /** DM_FillRect(frame, x, y, w, h): current foreground colour */
    public static void fillRect(Frame f, int x, int y, int w, int h) {
        Prim.fillRect(f, x, y, w, h);
    }

    /** DM_DrawRectRGB(frame, x, y, w, h, r, g, b): outline of w x h pixels (the platform call takes the inclusive extent w-1, h-1). */
    public static void drawRectRGB(Frame f, int x, int y, int w, int h, int r, int g, int b) {
        Prim.fg = Prim.pixel(r, g, b);
        Prim.drawRect(f, x, y, w - 1, h - 1);
    }

    /** DM_DrawLine(frame, x1, y1, x2, y2): current foreground colour */
    public static void drawLine(Frame f, int x1, int y1, int x2, int y2) {
        Prim.drawLine(f, x1, y1, x2, y2);
    }

    /** DM_DrawLineRGB(frame, x1, y1, x2, y2, r, g, b) */
    public static void drawLineRGB(Frame f, int x1, int y1, int x2, int y2, int r, int g, int b) {
        Prim.fg = Prim.pixel(r, g, b);
        Prim.drawLine(f, x1, y1, x2, y2);
    }

    /**
     * DM_FillRectAlphaRGB(frame, x, y, w, h, alpha, r, g, b) @0x6287c (RGB565 path): blends the colour over the frame, clipped to the
     * frame only (not OutR).  Per channel: (dst * (255 - alpha) + src * alpha) with the original's shifts/masks.
     */
    public static void fillRectAlpha(Frame f, int x, int y, int w, int h, int alpha, int r, int g, int b) {
        alpha &= 0xff;
        if (alpha == 0) {
            return;
        }
        if (x < 0) {
            w += x;
            x = 0;
        }
        if (y < 0) {
            h += y;
            y = 0;
        }
        if (f.w < w + x) {
            w = f.w - x;
        }
        if (f.h < h + y) {
            h = f.h - y;
        }
        int inv = 0xff - alpha;
        short[] p = f.px;
        for (int yy = 0; yy < h; yy++) {
            int o = (y + yy) * f.w + x;
            for (int xx = 0; xx < w; xx++) {
                int v = p[o + xx] & 0xffff;
                int G = ((((v & 0x7e0) >> 3) * inv + alpha * g) >> 5) & 0x7e0;
                int R = (((v >> 8) & 0xf8) * inv + r * alpha) & 0xf800;
                int B = (((v & 0x1f) * 8 * inv + alpha * b) << 16) >>> 27;
                p[o + xx] = (short) (G | R | B);
            }
        }
    }

    /**
     * DM_CopyOffScreen(dx, dy, w, h, sx, sy) = CUSTOM_grpCopyFrameBuffer(scr, dx, dy, w, h, back, sx, sy) @0x37aac: row copy back -> scr,
     * clipped against the destination only (the source is read as is).
     */
    public static void copyOffScreen(int dx, int dy, int w, int h, int sx, int sy) {
        Frame dst = scr;
        Frame src = back;
        if (dx < 0) {
            sx -= dx;
            w += dx;
            dx = 0;
        }
        if (dy < 0) {
            h += dy;
            sy -= dy;
            dy = 0;
        }
        if (sx < 0) {
            w += sx;
            dx -= sx;
        }
        if (sx < 0) {
            sx = 0;
        }
        if (sy < 0) {
            h += sy;
            dy -= sy;
            sy = 0;
        }
        if (dst.w < w + dx) {
            w -= (w + dx) - dst.w;
        }
        if (dst.h < h + dy) {
            h -= (h + dy) - dst.h;
        }
        if (w <= 0) {
            return;
        }
        for (int y = 0; y < h; y++) {
            System.arraycopy(src.px, (sy + y) * src.w + sx, dst.px, (dy + y) * dst.w + dx, w);
        }
    }
}
