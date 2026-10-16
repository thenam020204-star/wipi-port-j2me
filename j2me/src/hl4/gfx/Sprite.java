package hl4.gfx;

/**
 * SPR_drawSpriteNPC (@0xc00a0): composes the parts of a CIF frame into DM_* calls.  Port of tools/sprite.py, verified at the call
 * boundary against the original (14,400 random-CIF cases covering the whole function + 55,107 draws over the 148 real CIFs).
 * See docs/phaseC_sprite.md for the part encoding and every quirk (kept).
 *
 * Image sets: sets[i] is the image array of set i (null = no set); an image index (s8) outside the array draws nothing
 * (the original reads neighbouring memory - undefined; no shipped CIF does this: max index 52, sets 0..7).
 */
public final class Sprite {
    public Cif cif;
    /** attack / hit rectangles (spr+0..0x24, +0x54, +0x58, +0x5c): loaded by SPR_loadExtra / SPR_loadAttackProperty */
    public SprProps props;
    public int nSets;                       // spr+0x38
    public Gbm[][] sets = new Gbm[32][];    // spr+0x28 (current)
    public Gbm[][] orig = new Gbm[32][];    // spr+0x2c
    public int c60, c62, c64, c67, c66;     // control palette parameters (s16 s16 s16 u8 u8)
    public int f68;                         // control-palette flag
    public int f6d;                         // 'set-2 image offset' flag
    public int f6e;                         // blend enable
    public int f6f;                         // blend alpha

    public Sprite() {
    }

    /** SPR_set @0xd1784: the zero state of an SPR struct */
    public void set() {
        cif = null;
        props = null;
        nSets = 0;
        for (int i = 0; i < sets.length; i++) {
            sets[i] = null;
            orig[i] = null;
        }
        c60 = 0;
        c62 = 0;
        c64 = 0;
        c67 = 0;
        c66 = 0;
        f68 = 0;
        f6d = 0;
        f6e = 0;
        f6f = 0;
    }

    /** SPR_copyRes(src, this) @0xcfe9c: shares the loaded data, copies the control-palette fields, clears +0x6c..0x6f */
    public void copyFrom(Sprite o) {
        cif = o.cif;
        props = o.props;
        nSets = o.nSets;
        for (int i = 0; i < sets.length; i++) {
            sets[i] = o.sets[i];
            orig[i] = o.orig[i];
        }
        c60 = o.c60;
        c62 = o.c62;
        c64 = o.c64;
        c67 = o.c67;
        c66 = o.c66;
        f68 = o.f68;
        f6d = 0;
        f6e = 0;
        f6f = 0;
    }

    /** SPR_loadSprite(spr, path, mode): index parsed from the CIF file; nSets = first byte (spr+0x38). */
    public Sprite(byte[] cifData, int mode) {
        cif = new Cif(cifData, mode);
        nSets = cifData[0];
    }

    /** SPR_freeSpriteImage: drops every image set (the sprite structure stays loaded) */
    public void freeImages() {
        for (int i = 0; i < sets.length; i++) {
            sets[i] = null;
            orig[i] = null;
        }
    }

    /** loadSprImages(spr, setIdx, ...): both the current and the original set array point at the loaded images. */
    public void setImages(int setIdx, Gbm[] imgs) {
        sets[setIdx] = imgs;
        orig[setIdx] = imgs;
    }

    /**
     * SPR_drawSprite(frame, spr, anim, dir, &frameNo, x, y, p8, p9) @0xd11bc = SPR_drawSpriteNPC(frame, spr, anim, 2, 3, dir, &frameNo,
     * x, y, p8, p9, -1) (the control-palette pre-step for _lcdEff[10] == 2 is not ported yet: that class never occurs in the title flow).
     */
    public int drawSprite(SpriteOut o, Frame fr, int anim, int dir, int frameNo, int x, int y, int p8, int p9) {
        return drawNpc(o, fr, anim, 2, 3, dir, frameNo, x, y, p8, p9, -1);
    }

    // global render state read by the function
    public static final int[] lcdEff = new int[12];     // _lcdEff bytes
    /** DM_initLCDEffect @0x5d39c */
    public static void lcdEffInit() {
        lcdEff[9] = 0;
        lcdEff[4] = 0;
        lcdEff[5] = 0;
        lcdEff[6] = 0;
        lcdEff[0] = 0;
        lcdEff[1] = 0;
        lcdEff[2] = 0;
        lcdEff[3] = 0;
        lcdEff[7] = 0;
        lcdEff[8] = 0;
    }

    /** DM_SetLCDEffect(r, g, b, p4, p5, p6) @0x5d3d8 */
    public static void lcdEffSet(int p1, int p2, int p3, int p4, int p5, int p6) {
        lcdEff[7] = p4 & 0xff;
        lcdEff[0] = p1 & 0xff;
        lcdEff[1] = (p1 >> 8) & 0xff;
        lcdEff[9] = p6 & 0xff;
        lcdEff[2] = p2 & 0xff;
        lcdEff[3] = (p2 >> 8) & 0xff;
        lcdEff[4] = p3 & 0xff;
        lcdEff[5] = (p3 >> 8) & 0xff;
        lcdEff[6] = p5 & 0xff;
        lcdEff[8] = 1;
    }

    public static Gbm[] extraEffect;                    // _imgSKextraEffect (an image set)

    private static int s8(int v) {
        return (byte) v;
    }

    private static int s16(int v) {
        return (short) v;
    }

    private static int cls(int c) {
        return c == 0 ? 0x10 : (c == 1 ? 8 : 4);
    }

    /** SPR_getMaxFrame @0xbff54 -> s8: number of frames of direction slot d, minus one. */
    public int maxFrame(int anim, int d) {
        d = s8(d);
        int k;
        if (d == 3) {
            k = 2;
        } else if (d > 4) {
            k = d == 6 ? 0 : d - 5;
        } else {
            k = d;
        }
        int[][] dirs = cif.frameStart[anim];
        if (dirs.length <= 2) {
            k = 0;
        }
        int nf = (k >= 0 && k < dirs.length) ? dirs[k].length : 0;
        return s8(nf - 1);
    }

    /** SPR_isEndFrame(spr, anim, dir) @0xd0014 for the frame number 'frame' (s8) */
    public boolean isEndFrame(int anim, int d, int frame) {
        return maxFrame(anim, d) <= (byte) frame;
    }

    private static Gbm image(Gbm[] set, int idx) {
        if (set == null || idx < 0 || idx >= set.length) {
            return null;
        }
        return set[idx];
    }

    private void palShort(SpriteOut o, Gbm img, int setIdx) {
        if (lcdEff[9] == 0) {
            o.restorePalette(img);
        } else {
            if (img.palFlag == 1) {
                o.restorePalette(img);
            }
            if (setIdx != 5 && setIdx != 7) {
                o.setPalBlendEx(img, s16(lcdEff[0] | lcdEff[1] << 8), s16(lcdEff[2] | lcdEff[3] << 8), s16(lcdEff[4] | lcdEff[5] << 8),
                        lcdEff[7], lcdEff[6]);
            }
        }
    }

    /**
     * SPR_drawSpriteNPC(frame, spr, anim, p4, p5, p6, &frameNo, X, Y, p10, p11, p12).  p11/p12 = -1 mean "unset" (0xffffffff).
     * Returns the new frame number (the original updates *frameptr).
     */
    public int drawNpc(SpriteOut o, Frame fr, int anim, int p4, int p5, int p6, int frameNo, int X, int Y, int p10, int p11, int p12) {
        if ((p6 & 0x80) != 0) {
            return frameNo;
        }
        int ctrl = p12 != -1 ? s8(p12) : -1;
        int effDir = p6 == p5 ? (p4 & 0xff) : p6;
        // image-set array refresh for ctrl == 2 (SK skins) @0xc0124..0xc01b0
        int n = nSets;
        int i = 0;
        while (i < n) {
            Gbm[] og = orig[i];
            if (og == null || ctrl != 2) {
                sets[i] = og;
            } else if (i != 7) {
                sets[i] = og;
            } else {
                if (extraEffect != null) {
                    sets[7] = extraEffect;
                }
                i = 8;
                if (n < 9) {
                    break;
                }
                continue;
            }
            i++;
        }
        if (anim < 0 || anim >= cif.nAnim) {
            return frameNo;                     // the original reads garbage record memory (UNKNOWN)
        }
        int mf = maxFrame(anim, effDir);
        if (s8(frameNo) > mf || s8(frameNo) < 0) {
            frameNo = 0;
        }
        int[][][] fs = cif.frameStart;
        int[][] dirs = fs[anim];
        int d = s8(effDir);
        if (d >= dirs.length || d < 0) {
            d = 0;
        }
        if (dirs.length == 0) {
            return frameNo;
        }
        int[] frames = dirs[d];
        if (s8(frameNo) >= frames.length) {
            return frameNo;
        }
        int start = frames[s8(frameNo)];
        byte[] data = cif.data;
        int nparts = data[start];
        if (nparts <= 0) {
            return frameNo;
        }
        int fp = start + 1;
        int flip = 0;
        int rot = 0;                            // local_40 / local_44 persist across parts
        int shortSz = cif.mode == 2 ? 5 : 4;
        boolean D = p10 == 1 || p6 == p5;
        for (int pi = 0; pi < nparts; pi++) {
            int b0 = data[fp] & 0xff;
            if (b0 == 0x7f) {
                fp += shortSz;
                continue;
            }
            int cl = cls((b0 >> 5) & 3);
            int setIdx = b0 & 0x1f;
            int imgI = data[fp + 1] & 0xff;
            if (setIdx == 2 && f6d != 0) {
                imgI = (imgI + 2) & 0xff;
            }
            int x;
            int y;
            if (cif.mode == 2) {
                int b2 = data[fp + 2] & 0xff;
                int b3 = data[fp + 3] & 0xff;
                int b4 = data[fp + 4] & 0xff;
                x = s16(((b2 << 4) | (b3 >> 4) | ((b2 & 0x80) != 0 ? 0xf000 : 0)) & 0xffff);
                y = s16((b4 | ((b3 & 0xf) << 8) | ((b3 & 8) != 0 ? 0xf000 : 0)) & 0xffff);
            } else {
                x = data[fp + 2];
                y = data[fp + 3];
            }
            fp += shortSz;
            if ((b0 & 0x80) == 0) {
                Gbm img = image(sets[setIdx], s8(imgI));
                if (sets[setIdx] == null || img == null) {
                    continue;
                }
                if (!D) {
                    rot = 0;
                    flip = cl == 0x10 ? 0 : (cl == 8 ? 1 : 2);
                } else {
                    if (cl == 8) {
                        rot = 0;
                        flip = 0;
                    } else if (cl == 0x10) {
                        int aux = s16(img.aux);
                        flip = 1;
                        rot = 0;
                        if ((aux & 1) == 0) {
                            x = s16(x + 1);
                        }
                    } else {
                        rot = 0xb4;
                        flip = 0;
                    }
                    x = s16(-x);
                }
                boolean useEx = rot != 0;
                palShort(o, img, setIdx);
                if (f68 == 0) {
                    o.restorePalette(img);
                } else {
                    ctrlPal(o, img, setIdx);
                }
                if (f6e != 0) {
                    useEx = true;
                }
                if (!useEx) {
                    o.drawImg(fr, X + x, Y + y, s16(img.aux), s16(img.h), img, 0, 0, flip, 0, 0x12);
                } else {
                    o.drawImgEx(fr, X + x, Y + y, s16(img.aux), s16(img.h), img, 0, 0, flip, rot, f6f, 0, 0x12);
                }
            } else {
                int sx10 = data[fp] & 0xff;
                int sy10 = data[fp + 1] & 0xff;
                int ang = data[fp + 2] & 0xff;
                int b4 = data[fp + 4] & 0xff;
                int alpha;
                int eff;
                if (lcdEff[9] == 1 && f68 == 1) {
                    alpha = -1;
                    eff = 0;
                } else {
                    alpha = s8(data[fp + 3]);
                    eff = b4 >> 2;
                    if (eff != 0) {
                        int pt = o.particle();
                        if (pt == 0) {
                            alpha = -1;
                        }
                    }
                }
                fp += 5;
                int cls2 = cls(b4 & 3);
                if (ctrl == 0 && setIdx == 7) {
                    continue;
                }
                Gbm img = image(sets[setIdx], s8(imgI));
                if (sets[setIdx] == null || img == null) {
                    continue;
                }
                int ang2 = ang * 2;
                if (!D) {
                    flip = cls2 == 0x10 ? 0 : (cls2 == 8 ? 1 : 2);
                    rot = ang2;
                } else {
                    int aux = s16(img.aux);
                    if (cls2 == 8) {
                        flip = 0;
                    } else if (cls2 == 0x10) {
                        if (sx10 * 10 == 100 && (((aux ^ 1) & 1) != 0)) {
                            x = s16(x + 1);
                        }
                        flip = 1;
                    } else {
                        flip = 0;
                    }
                    if (ang == 0x87) {
                        rot = cls2 == 4 ? 0x10e : 0x5a;
                    } else if (ang == 0x2d) {
                        rot = cls2 == 4 ? 0x5a : 0x10e;
                    } else {
                        if (ang == 0x5a) {
                            rot = cls2 != 4 ? 0xb4 : 0;
                        } else if (ang == 0) {
                            rot = ang2;
                            if (cls2 == 4) {
                                rot = 0xb4;
                            }
                        }
                    }
                    x = s16(-x);
                }
                // palette handling
                if (lcdEff[9] != 0) {
                    if (img.palFlag == 1) {
                        o.restorePalette(img);
                    }
                    if (cif.mode == 2 && setIdx != 5 && setIdx != 7) {
                        o.setPalBlendEx(img, s16(lcdEff[0] | lcdEff[1] << 8), s16(lcdEff[2] | lcdEff[3] << 8),
                                s16(lcdEff[4] | lcdEff[5] << 8), lcdEff[7], lcdEff[6]);
                    }
                } else {
                    if (p11 == -1 || p11 == setIdx) {
                        o.restorePalette(img);
                    }
                }
                if (f68 == 0) {
                    o.restorePalette(img);
                } else {
                    if (img.palFlag == 1) {
                        o.restorePalette(img);
                    }
                    if (setIdx != 5 && setIdx != 7) {
                        o.setPalBlendEx(img, c60, c62, c64, c67, c66);
                    }
                }
                if (f6e != 0) {
                    alpha = s8(f6f);
                }
                int sx = sx10 * 10;
                int sy = sy10 * 10;
                if (sy != 100 || sx != 100) {
                    if (alpha != -1 || eff != 0) {
                        o.drawImgRotateEx(fr, X + x, Y + y, s16(img.aux), s16(img.h), img, 0, 0, flip, rot, sx, sy, eff, alpha & 0xff, 0x12);
                    } else {
                        o.drawImgRotate(fr, X + x, Y + y, s16(img.aux), s16(img.h), img, 0, 0, flip, rot, sx, sy, 0x12);
                    }
                } else {
                    if (alpha != -1 || eff != 0) {
                        o.drawImgEx(fr, X + x, Y + y, s16(img.aux), s16(img.h), img, 0, 0, flip, rot, alpha & 0xff, eff, 0x12);
                    } else {
                        o.drawImg(fr, X + x, Y + y, s16(img.aux), s16(img.h), img, 0, 0, flip, rot, 0x12);
                    }
                }
            }
        }
        return frameNo;
    }

    private void ctrlPal(SpriteOut o, Gbm img, int setIdx) {
        if (img.palFlag == 1) {
            o.restorePalette(img);
        }
        if (setIdx != 5 && setIdx != 7) {
            o.setPalBlendEx(img, c60, c62, c64, c67, c66);
        }
    }
}
