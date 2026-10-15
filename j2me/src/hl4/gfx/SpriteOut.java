package hl4.gfx;

/** The DM_* calls SPR_drawSpriteNPC makes (verified at this boundary: same calls, same order, same arguments). */
public interface SpriteOut {
    void restorePalette(Gbm img);

    void setPalBlendEx(Gbm img, int p1, int p2, int p3, int p4, int p5);

    void drawImg(Frame f, int x, int y, int w, int h, Gbm img, int sx, int sy, int flip, int rot, int anchor);

    void drawImgEx(Frame f, int x, int y, int w, int h, Gbm img, int sx, int sy, int flip, int rot, int alpha, int effect, int anchor);

    void drawImgRotate(Frame f, int x, int y, int w, int h, Gbm img, int sx, int sy, int flip, int rot, int scaleX, int scaleY, int anchor);

    void drawImgRotateEx(Frame f, int x, int y, int w, int h, Gbm img, int sx, int sy, int flip, int rot, int scaleX, int scaleY,
                         int effect, int alpha, int anchor);

    /** MenuOptions_particle(): 0 when particle effects are disabled in the options. */
    int particle();
}
