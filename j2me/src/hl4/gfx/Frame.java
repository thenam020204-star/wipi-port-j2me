package hl4.gfx;

/**
 * RGB565 frame buffer with the clip rectangle of the original ("OutR", set by DM_SetCliptoGBM): (clipL, clipT, clipR, clipB),
 * right/bottom EXCLUSIVE.  The original frame is 240x400 (game area 240x320 + virtual keypad); this port allocates the
 * 240x320 game area only, so every drawing routine also clamps to the frame (documented deviation, no visible effect).
 */
public final class Frame {
    public final short[] px;
    public final int w;
    public final int h;
    public int clipL;
    public int clipT;
    public int clipR;
    public int clipB;

    public Frame(int w, int h) {
        this.w = w;
        this.h = h;
        px = new short[w * h];
        setClip(0, 0, w, h);
    }

    public void setClip(int l, int t, int r, int b) {
        clipL = l;
        clipT = t;
        clipR = r;
        clipB = b;
    }
}
