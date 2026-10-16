package hl4.game;

import hl4.gfx.Frame;
import hl4.gfx.GfxOut;
import hl4.gfx.Img;
import hl4.gfx.Gbm;
import hl4.gfx.Sprite;
import hl4.sys.Bytes;
import hl4.sys.Rng;

/**
 * Screen fades (Map_screenEffect, state _map+0x348/+0x34a/+0x34c) and the falling-sprite scene effect (Map_fallRandInit / Map_fallRandDraw,
 * _map+0x351 / +0x352, five 16-byte objects at _map+0x2e8) - shared by the title screen and the map.
 */
public final class MapFx {
    // All state lives in _map at the original offsets (G.map):
    //   screen effect: +0x348 type (0xff = none), s16 +0x34a current, s16 +0x34c max
    //   fall effect:   +0x351 on, +0x352 FGI id (0xff = none), +0x353 effect id, +0x354 sprite count, +0x338 next object (s32), +0x33d rain index,
    //                  five 16-byte objects at +0x2e8: +0 active, +4 x (s32), +8 y (s32), +0xd frame, +0xf type
    private static final int OBJ = 0x2e8;

    private static int scrType() {
        return G.map[0x348] & 0xff;
    }

    private static int scrCur() {
        return Bytes.s16(G.map, 0x34a);
    }

    private static int scrMax() {
        return Bytes.s16(G.map, 0x34c);
    }

    private static void setScrCur(int v) {
        Bytes.w16(G.map, 0x34a, v);
    }

    /** the title screen's end: the effect is switched off without touching its sprites */
    public static void fallOff() {
        G.map[0x351] = 0;
        G.map[0x352] = (byte) 0xff;
    }

    private MapFx() {
    }

    /** Map_setScreenEffect(type, cur, max) */
    public static void setScreenEffect(int type, int cur, int max) {
        Bytes.w16(G.map, 0x34c, max);
        Bytes.w16(G.map, 0x34a, cur);
        G.map[0x348] = (byte) type;
    }

    /** Map_screenEffectCheck(type): true while the effect of that type is still running (5: fade-out of white). */
    public static boolean screenEffectCheck(int type) {
        switch (type) {
            case 2:
            case 4:
                return scrMax() <= scrCur();
            case 3:
                return scrCur() < 1;
            case 5:
                return 0 < scrCur();
            default:
                return true;
        }
    }

    /** Map_screenEffect @0xa4360 (cases used so far: 2..6; 0 and 1 = Map_screenLoseEye are not ported yet). */
    public static void screenEffect() {
        Frame f = Gfx.scr;
        int w = Glob.gameW;
        int h = Glob.gameH;
        switch (scrType()) {
            case 0:
            case 1:
                throw new IllegalStateException("Map_screenLoseEye: later");
            case 2:
                if (scrCur() < scrMax()) {
                    Gfx.fillRectAlpha(f, 0, 0, w, h, scrCur() * 0xff / scrMax(), 0xb, 0xb, 0xb);
                    setScrCur(scrCur() + 10);
                }
                break;
            case 3:
                if (scrCur() > 0) {
                    Gfx.fillRectAlpha(f, 0, 0, w, h, scrCur() * 0xff / scrMax(), 0xb, 0xb, 0xb);
                    setScrCur(scrCur() - 10);
                }
                break;
            case 4:
                if (scrCur() >= 0) {
                    Gfx.fillRectAlpha(f, 0, 0, w, h, (scrCur() * 0xff / 100) & 0xff, 0xb, 0xb, 0xb);
                }
                if (scrCur() < scrMax()) {
                    setScrCur(scrCur() + 5);
                }
                break;
            case 5:
                if (scrCur() > 0) {
                    Gfx.fillRectAlpha(f, 0, 0, w, h, scrCur() * 0xff / scrMax(), 0xff, 0xff, 0xff);
                    setScrCur(scrCur() - 5);
                }
                break;
            case 6:
                if (scrCur() > 0) {
                    Gfx.fillRectAlpha(f, 0, 0, w, h, 0xff, 0xb, 0xb, 0xb);
                    setScrCur(scrCur() - 1);
                }
                break;
            default:
                break;
        }
    }

    /** Map_setEffect(id) @0xa0b6c: toggles (an already set effect is cleared). */
    public static void setEffect(int id) {
        byte[] m = G.map;
        boolean was = m[0x353] != -1;
        m[0x353] = (byte) id;
        if (was) {
            m[0x353] = (byte) 0xff;
        }
    }

    /** Map_fallRandInit @0xa33b0: places the next object at a random spot. */
    public static void fallRandInit() {
        byte[] m = G.map;
        int c = Bytes.s32(m, 0x338);
        int o = OBJ + c * 0x10;
        Bytes.w32(m, o + 4, Rng.rand(0x1e, Glob.gameW - 0x1e));
        Bytes.w32(m, o + 8, Rng.rand(0x14, Glob.gameH - 0x32));
        m[o + 0xf] = 1;
        m[o + 0xd] = 0;
        m[o] = 1;
        Bytes.w32(m, 0x338, Bytes.s32(m, 0x338) + 1);
    }

    /** Map_SetRain(slot) @0xa3438 */
    private static void setRain(int i) {
        byte[] m = G.map;
        int o = OBJ + i * 0x10;
        Bytes.w32(m, o + 4, Rng.rand(0x1e, Glob.gameW - 10));
        m[o + 0xf] = 1;
        Bytes.w32(m, o + 8, Rng.rand(0x14, Glob.gameH - 10));
        m[o + 0xd] = 0;
        m[o] = 1;
    }

    /** Event_setFGI(id) @0x6dc24 for the scene-effect ids (< 8); -1 clears; >= 8 = ending art (not ported). */
    public static void setFGI(int id) {
        final int[] cif = {0, 2, 4, 5, 255, 6, 8, 9};
        final int[] cnt = {2, 2, 1, 1, 0, 2, 1, 1};
        byte[] m = G.map;
        if (id == -1) {
            fallOff();
            deleteEffectSprites();
        } else if (id < 8) {
            if (id != 7 && id != 4 && (m[0x352] & 0xff) != (id & 0xff)) {
                deleteEffectSprites();
                m[0x351] = 1;
                m[0x352] = (byte) id;
                setEffect(id);
                fallRandInit();
                if (m[0x351] != 0) {
                    m[0x354] = (byte) cnt[id];
                    for (int k = 0; k < (m[0x354] & 0xff); k++) {
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
        fallOff();
        Imgs.deleteEvtEffects();                 // the five object records keep their state (deleteEvtEffectCif @0xccf6c only clears +0x351/+0x352 and the sprites)
    }

    /** Map_fallRandDraw(frame) @0xa34b0 (types < 8, not 4/7: five falling sprites; 4/7 draw nothing). */
    public static void fallRandDraw(Frame f) {
        byte[] m = G.map;
        if (m[0x351] == 0) {
            return;
        }
        int type = m[0x352] & 0xff;
        if (type == 7 || type == 4) {
            return;
        }
        if (type < 8) {
            int cur = Bytes.s32(m, 0x338) + 1;
            Bytes.w32(m, 0x338, cur);
            if (cur == 6) {
                Bytes.w32(m, 0x338, 0);
                int ri = m[0x33d] & 0xff;
                if (m[OBJ + ri * 0x10] == 0) {
                    setRain(ri);
                    ri++;
                    m[0x33d] = (byte) ri;
                    if (ri > 4) {
                        m[0x33d] = 0;
                    }
                }
            }
            Sprite s = Imgs.evtEffect[0];
            for (int i = 0; i < 5; i++) {
                int o = OBJ + i * 0x10;
                if (m[o] != 0 && s != null && (m[o + 0xd] & 0xff) < s.maxFrame(0, 0) - 1) {
                    m[o + 0xd] = (byte) s.drawSprite(GfxOut.I, f, 0, 0, m[o + 0xd] & 0xff, Bytes.s32(m, o + 4), Bytes.s32(m, o + 8), 0, 0);
                }
            }
            for (int i = 0; i < 5; i++) {
                int o = OBJ + i * 0x10;
                if (m[o] != 0) {
                    m[o + 0xd] = (byte) (m[o + 0xd] + 1);
                    if (s != null && (m[o + 0xd] & 0xff) >= s.maxFrame(0, 0)) {
                        m[o] = 0;
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
