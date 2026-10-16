package hl4.game;

import hl4.gfx.Gbm;
import hl4.gfx.Sprite;

/** Event dialog face images (4 script slots): the face sprite and its image set. */
public final class Faces {
    public static final Sprite[] spr = new Sprite[4];
    public static final Gbm[][] img = new Gbm[4][];

    private Faces() {
    }

    /** Event_Release_Faceimage's resource part: SPR_destroy + UM_Free of slot k */
    public static void release(int k) {
        spr[k] = null;
        img[k] = null;
    }
}
