package hl4.game;

import hl4.gfx.Gbm;
import hl4.gfx.Sprite;

/** Event dialog face images (4 script slots): the face sprite and its image set. */
public final class Faces {
    public static final Sprite[] spr = new Sprite[4];
    public static final Gbm[][] img = new Gbm[4][];

    private Faces() {
    }

    /** C.49.19617 @0xd790c: per face id (cif/bitmap resource number, animation index) */
    private static final byte[] TABLE = {
        0, 0, 0, 1, 0, 2, 0, 3, 1, 0, 2, 0, 2, 1, 2, 2, 2, 3, 2, 4, 3, 0, 3, 1, 4, 0, 4, 1, 4, 2, 5, 0, 6, 0, 6, 1, 6, 2, 7, 0, 8, 0, 8, 1,
        9, 0, 10, 0, 11, 0, 11, 1, 12, 0, 13, 0, 14, 0, 15, 0
    };

    /** animation index (the original's _event[slot * 0xbc + 0x22f]) of a face id */
    public static int motion(int faceId) {
        return TABLE[faceId * 2 + 1];
    }

    /** Event_Load_Faceimage(k, faceId) @0x5bc7c: "NPC/FACE_<n>_CIF" + "NPC/FACE_<n>_BM" through loadSimpleSprite (images tinted by the time class _map[0x68]) */
    public static void load(int k, int faceId) {
        int n = TABLE[faceId * 2];
        Sprite s = new Sprite(hl4.sys.Res.load("/NPC/FACE_" + n + "_CIF"), 0x11);
        s.setImages(0, hl4.gfx.Img.loadAll("/NPC/FACE_" + n + "_BM", G.map[0x68]));
        spr[k] = s;
    }

    /** Event_Release_Faceimage's resource part: SPR_destroy + UM_Free of slot k */
    public static void release(int k) {
        spr[k] = null;
        img[k] = null;
    }
}
