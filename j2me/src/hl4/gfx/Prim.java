package hl4.gfx;

/**
 * WIPI-C 2D primitives as the game uses them (MC_grpFillRect / MC_grpDrawRect / MC_grpDrawLine with the context at _lcdG+0x18).
 * The platform clips to the frame buffer only (the game's own clip rectangle OutR is not known to it).  Semantics are
 * INFERRED from the game's own use (docs/phaseE.md section 2): FillRect covers w x h; DrawRect covers the INCLUSIVE extent
 * x..x+w, y..y+h (the game passes w-1/h-1 and pairs it with an inner fill of w-2); DrawLine is Bresenham, both ends included.
 * 'fg' is the platform foreground pixel (context index 1); text colour escapes change it as a side effect (HF).
 */
public final class Prim {
    public static int fg;

    private Prim() {
    }

    public static void fillRect(Frame f, int x, int y, int w, int h, int px) {
        int x0 = x < 0 ? 0 : x;
        int y0 = y < 0 ? 0 : y;
        int x1 = x + w > f.w ? f.w : x + w;
        int y1 = y + h > f.h ? f.h : y + h;
        if (x1 <= x0 || y1 <= y0) {
            return;
        }
        short v = (short) px;
        short[] p = f.px;
        for (int yy = y0; yy < y1; yy++) {
            int o = yy * f.w;
            for (int xx = x0; xx < x1; xx++) {
                p[o + xx] = v;
            }
        }
    }

    public static void fillRect(Frame f, int x, int y, int w, int h) {
        fillRect(f, x, y, w, h, fg);
    }

    /** MC_grpDrawRect(frame, x, y, w, h): outline of the inclusive extent. */
    public static void drawRect(Frame f, int x, int y, int w, int h) {
        if (w < 0 || h < 0) {
            return;
        }
        fillRect(f, x, y, w + 1, 1, fg);
        fillRect(f, x, y + h, w + 1, 1, fg);
        fillRect(f, x, y, 1, h + 1, fg);
        fillRect(f, x + w, y, 1, h + 1, fg);
    }

    public static void drawLine(Frame f, int x0, int y0, int x1, int y1) {
        int dx = x1 > x0 ? x1 - x0 : x0 - x1;
        int dy = y1 > y0 ? y0 - y1 : y1 - y0;     // -|dy|
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        for (;;) {
            fillRect(f, x0, y0, 1, 1, fg);
            if (x0 == x1 && y0 == y1) {
                break;
            }
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

    /** MC_grpGetPixelFromRGB (RGB565: R5 G6 B5 from the high bits). */
    public static int pixel(int r, int g, int b) {
        return ((r >> 3) << 11) | ((g >> 2) << 5) | (b >> 3);
    }
}
