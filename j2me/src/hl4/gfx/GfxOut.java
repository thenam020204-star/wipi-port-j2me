package hl4.gfx;

/** SpriteOut that really draws (the game's DM_* calls issued by SPR_drawSpriteNPC). */
public final class GfxOut implements SpriteOut {
    public static final GfxOut I = new GfxOut();
    /** MenuOptions_particle(): particle effects option (set by the options menu; default 1). */
    public static int particleOpt = 0;

    private GfxOut() {
    }

    public void restorePalette(Gbm img) {
        Fx.restorePalette(img);
    }

    public void setPalBlendEx(Gbm img, int p1, int p2, int p3, int p4, int p5) {
        Fx.setPalBlendEx(img, p1, p2, p3, p4, p5);
    }

    public void drawImg(Frame f, int x, int y, int w, int h, Gbm img, int sx, int sy, int flip, int rot, int anchor) {
        Blit.drawImg(f, x, y, w, h, img, sx, sy, flip, rot, anchor);
    }

    public void drawImgEx(Frame f, int x, int y, int w, int h, Gbm img, int sx, int sy, int flip, int rot, int alpha, int effect,
                          int anchor) {
        Fx.drawImgEx(f, x, y, w, h, img, sx, sy, flip, rot, alpha, effect, anchor);
    }

    public void drawImgRotate(Frame f, int x, int y, int w, int h, Gbm img, int sx, int sy, int flip, int rot, int scaleX, int scaleY,
                              int anchor) {
        Fx.drawImgRotate(f, x, y, w, h, img, sx, sy, flip, rot, scaleX, scaleY, anchor);
    }

    public void drawImgRotateEx(Frame f, int x, int y, int w, int h, Gbm img, int sx, int sy, int flip, int rot, int scaleX, int scaleY,
                                int effect, int alpha, int anchor) {
        Fx.drawImgRotateEx(f, x, y, w, h, img, sx, sy, flip, rot, scaleX, scaleY, effect, alpha, anchor);
    }

    public int particle() {
        return particleOpt;
    }
}
