package hl4.gfx;

import hl4.sys.Res;

/**
 * Image sets and whole-image draws (loadAllImages / getImage / DM_DrawImage of the original).
 * An image set is a Gbm[] parsed from a GBM container resource ("/CM/_CM_IMGCM", "/l/_LOGO", ...); the pixels stay in the
 * resource array.  blend = the DM_LoadResGbmMergeBlending tint slot (-1 = none, 0..5 time-of-day tints, 6 = red alert tint).
 */
public final class Img {
    private Img() {
    }

    /** loadAllImages(set, path, blend): null when the resource is missing (openGbmMerger fails). */
    public static Gbm[] loadAll(String path, int blend) {
        byte[] b = Res.load(path);
        if (b == null) {
            return null;
        }
        Gbm[] a = Gbm.loadMerged(b);
        if (blend != -1) {
            for (int i = 0; i < a.length; i++) {
                Fx.mergeBlend(a[i], blend);
            }
        }
        return a;
    }

    /** getImage(set, idx). */
    public static Gbm get(Gbm[] set, int idx) {
        return set == null || idx < 0 || idx >= set.length ? null : set[idx];
    }

    /**
     * DM_DrawImage(frame, x, y, imageStruct, anchor) @0x4fea4: DM_DrawImg(frame, x, y, w = s16 aux, h = s16 h, img, 0, 0, 0, 0, anchor).
     * (The width passed is the struct's +4 field 'aux', not +0 'w' - CONFIRMED in the disassembly.)
     */
    public static void draw(Frame f, int x, int y, Gbm img, int anchor) {
        if (img != null) {
            Blit.drawImg(f, x, y, img.aux, img.h, img, 0, 0, 0, 0, anchor);
        }
    }

    /** DM_DrawImageMirror(frame, x, y, imageStruct, 0x10, anchor): the same plain draw (mirror code 0x10 = none). */
    public static void drawPlain(Frame f, int x, int y, Gbm img, int anchor) {
        draw(f, x, y, img, anchor);
    }

    /**
     * DM_DrawEffectClip(frame, x, y, w, h, img, 0, 0, mirror 0xc, rot 0, 100, 100, effect 0, alpha 0xff, anchor) -> DM_DrawImageClipMirror:
     * a plain DM_DrawImg of the struct's +0 width/height with flip 3 (the alpha byte 0xff is -1 = 'no alpha').
     */
    public static void drawFlip3(Frame f, int x, int y, Gbm img, int anchor) {
        if (img != null) {
            Blit.drawImg(f, x, y, img.w, img.h, img, 0, 0, 3, 0, anchor);
        }
    }
}
