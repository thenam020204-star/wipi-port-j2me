package hl4.game;

import hl4.gfx.Frame;
import hl4.gfx.GfxOut;
import hl4.gfx.Img;
import hl4.gfx.Gbm;
import hl4.gfx.Sprite;
import hl4.sys.Rng;

/**
 * Screen fades (Map_screenEffect, state _map+0x348/+0x34a/+0x34c) and the falling-sprite scene effect (Map_fallRandInit / Map_fallRandDraw,
 * _map+0x351 / +0x352, five 16-byte objects at _map+0x2e8) - shared by the title screen and the map.
 */
public final class MapFx {
    // screen effect
    public static int scrType = -1;      // _map[0x348] (0xff = none)
    public static int scrCur;            // s16 _map+0x34a
    public static int scrMax;            // s16 _map+0x34c
    // fall effect
    public static boolean fallOn;        // _map[0x351]
    public static int fallType = -1;     // _map[0x352] (event FGI id, 0xff = none)
    public static int effectId = -1;     // _map[0x353]
    public static int fallCount;         // _map[0x354]: number of effect sprites loaded
    public static int cursor;            // _map+0x338
    public static int rainIdx;           // _map[0x33d]
    private static final boolean[] active = new boolean[5];
    private static final int[] ox = new int[5];
    private static final int[] oy = new int[5];
    private static final int[] of = new int[5];
    private static final int[] ot = new int[5];

    private MapFx() {
    }

    /** Map_setScreenEffect(type, cur, max) */
    public static void setScreenEffect(int type, int cur, int max) {
        scrMax = (short) max;
        scrCur = (short) cur;
        scrType = type & 0xff;
    }

    /** Map_screenEffectCheck(type): true while the effect of that type is still running (5: fade-out of white). */
    public static boolean screenEffectCheck(int type) {
        switch (type) {
            case 2:
            case 4:
                return scrMax <= scrCur;
            case 3:
                return scrCur < 1;
            case 5:
                return 0 < scrCur;
            default:
                return true;
        }
    }

    /** Map_screenEffect @0xa4360 (cases used so far: 2..6; 0 and 1 = Map_screenLoseEye are not ported yet). */
    public static void screenEffect() {
        Frame f = Gfx.scr;
        int w = Glob.gameW;
        int h = Glob.gameH;
        switch (scrType) {
            case 0:
            case 1:
                throw new IllegalStateException("Map_screenLoseEye: later");
            case 2:
                if (scrCur < scrMax) {
                    Gfx.fillRectAlpha(f, 0, 0, w, h, scrCur * 0xff / scrMax, 0xb, 0xb, 0xb);
                    scrCur += 10;
                }
                break;
            case 3:
                if (scrCur > 0) {
                    Gfx.fillRectAlpha(f, 0, 0, w, h, scrCur * 0xff / scrMax, 0xb, 0xb, 0xb);
                    scrCur -= 10;
                }
                break;
            case 4:
                if (scrCur >= 0) {
                    Gfx.fillRectAlpha(f, 0, 0, w, h, (scrCur * 0xff / 100) & 0xff, 0xb, 0xb, 0xb);
                }
                if (scrCur < scrMax) {
                    scrCur += 5;
                }
                break;
            case 5:
                if (scrCur > 0) {
                    Gfx.fillRectAlpha(f, 0, 0, w, h, scrCur * 0xff / scrMax, 0xff, 0xff, 0xff);
                    scrCur -= 5;
                }
                break;
            case 6:
                if (scrCur > 0) {
                    Gfx.fillRectAlpha(f, 0, 0, w, h, 0xff, 0xb, 0xb, 0xb);
                    scrCur -= 1;
                }
                break;
            default:
                break;
        }
    }

    /** Map_setEffect(id) @0xa0b6c: toggles (an already set effect is cleared). */
    public static void setEffect(int id) {
        boolean was = effectId != -1;
        effectId = id & 0xff;
        if (was) {
            effectId = -1;
        }
    }

    /** Map_fallRandInit @0xa33b0: places the next object at a random spot. */
    public static void fallRandInit() {
        int c = cursor;
        ox[c] = Rng.rand(0x1e, Glob.gameW - 0x1e);
        oy[c] = Rng.rand(0x14, Glob.gameH - 0x32);
        ot[c] = 1;
        of[c] = 0;
        active[c] = true;
        cursor = c + 1;
    }

    /** Map_SetRain(slot) @0xa3438 */
    private static void setRain(int i) {
        ox[i] = Rng.rand(0x1e, Glob.gameW - 10);
        oy[i] = Rng.rand(0x14, Glob.gameH - 10);
        ot[i] = 1;
        of[i] = 0;
        active[i] = true;
    }

    /** Event_setFGI(id) @0x6dc24 for the scene-effect ids (< 8); -1 clears; >= 8 = ending art (not ported). */
    public static void setFGI(int id) {
        final int[] cif = {0, 2, 4, 5, 255, 6, 8, 9};
        final int[] cnt = {2, 2, 1, 1, 0, 2, 1, 1};
        if (id == -1) {
            fallOn = false;
            fallType = 0xff;
            deleteEffectSprites();
        } else if (id < 8) {
            if (id != 7 && id != 4 && fallType != id) {
                deleteEffectSprites();
                fallOn = true;
                fallType = id;
                setEffect(id);
                fallRandInit();
                if (fallOn) {
                    fallCount = cnt[id];
                    for (int k = 0; k < fallCount; k++) {
                        Imgs.loadEvtEffect(cif[id], k);
                    }
                }
            }
        } else {
            throw new IllegalStateException("ending art: later");
        }
    }

    /** deleteEvtEffectCif */
    public static void deleteEffectSprites() {
        fallOn = false;
        fallType = 0xff;
        Imgs.deleteEvtEffects();
        for (int i = 0; i < 5; i++) {
            active[i] = false;
        }
    }

    /** Map_fallRandDraw(frame) @0xa34b0 (types < 8, not 4/7: five falling sprites; 4/7 draw nothing). */
    public static void fallRandDraw(Frame f) {
        if (!fallOn) {
            return;
        }
        if (fallType == 7 || fallType == 4) {
            return;
        }
        if (fallType < 8) {
            cursor++;
            if (cursor == 6) {
                cursor = 0;
                if (!active[rainIdx]) {
                    setRain(rainIdx);
                    rainIdx++;
                    if (rainIdx > 4) {
                        rainIdx = 0;
                    }
                }
            }
            Sprite s = Imgs.evtEffect[0];
            for (int i = 0; i < 5; i++) {
                if (active[i] && s != null && of[i] < s.maxFrame(0, 0) - 1) {
                    of[i] = s.drawSprite(GfxOut.I, f, 0, 0, of[i], ox[i], oy[i], 0, 0);
                }
            }
            for (int i = 0; i < 5; i++) {
                if (active[i]) {
                    of[i] = (of[i] + 1) & 0xff;
                    if (s != null && of[i] >= s.maxFrame(0, 0)) {
                        active[i] = false;
                    }
                }
            }
            return;
        }
        Gbm[] end = Imgs.ending;
        Gfx.fillRectRGB(f, 0, 0, Glob.gameW, Glob.gameH, 0xb, 0xb, 0xb);
        Img.draw(f, Glob.gameW >> 1, Glob.gameH >> 1, Img.get(end, 0), 0x12);
    }
}
