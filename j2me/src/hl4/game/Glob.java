package hl4.game;

import hl4.gfx.Hf;
import hl4.gfx.Img;
import hl4.sys.Res;

/** Game-wide state that the original keeps in globals (_lcdG sizes, centerX/Y, menuX/Y, consent flags ...). */
public final class Glob {
    /** game area (_lcdG+0x54 / +0x58): 240 x 320 portrait */
    public static int gameW = 240;
    public static int gameH = 320;
    /** centerX / centerY / menuX / menuY (UM_SetMenuXY) */
    public static int centerX;
    public static int centerY;
    public static int menuX;
    public static int menuY;
    /** _gameMenu[0x21]: SMS/consent answer (-1 undecided, 0 refused, 1 agreed); _gameMenu[0x22] */
    public static int smsAgree = -1;
    public static int smsFlag2;
    /** -2 = real behaviour (read ./Hero4Sms); otherwise the answer the trace reference pretends to have stored */
    public static int presetConsent = -2;

    private Glob() {
    }

    /** UM_SetMenuXY @0xd9ff8 */
    public static void setMenuXY() {
        centerX = gameW >> 1;
        centerY = gameH >> 1;
        menuX = (gameW - 0xb0) >> 1;
        menuY = (gameH - 0xa0) >> 1;
        Touch.pushState(0x33);
        int[] e = Touch.extra(0x3b2c);
        menuX = Touch.cx - 5 + e[0];
        menuY = Touch.cy - 8 + e[1];
        Touch.popState();
    }

    /**
     * touch_setUiChange(1, 0) @0x45c14, portrait: game area = frame - virtual pad (240 x 320), centre/menu positions recomputed, screen
     * cleared and the title background redrawn when loaded.
     */
    public static void setUiChange(int mode) {
        if (mode == 0) {
            // game mode (portrait): same game area as mode 1; Touch layer on; without a loaded map the whole buffer is cleared
            Touch.standInit();
            setMenuXY();
            Gfx.setClip(0, 0, gameW, gameH);
            if (Maps.layerA == null || Maps.layerB == null) {
                Gfx.fillRectRGB(Gfx.scr, 0, 0, gameW, gameH, 0xb, 0xb, 0xb);
                return;
            }
            // a map is loaded: keep the hero inside the camera window and redraw the whole picture at once (the first frame of the load screen)
            byte[] m = G.map;
            Obj h = Pool.hero;
            // 0x36000..0x36044: camera = hero - half the game area, clamped at 0 (the stores are unconditional; the pseudo-C drops them)
            int cx = hl4.sys.Bytes.s32(h.m, 8) - gameW / 2;
            int cy = hl4.sys.Bytes.s32(h.m, 0xc) - gameH / 2;
            hl4.sys.Bytes.w32(m, 156, cx < 0 ? 0 : cx);
            hl4.sys.Bytes.w32(m, 160, cy < 0 ? 0 : cy);
            if (hl4.sys.Bytes.s32(m, 132) < gameW + hl4.sys.Bytes.s32(m, 156)) {
                hl4.sys.Bytes.w32(m, 156, hl4.sys.Bytes.s32(m, 132) - gameW);
            }
            if (hl4.sys.Bytes.s32(m, 136) < gameH + hl4.sys.Bytes.s32(m, 160)) {
                hl4.sys.Bytes.w32(m, 160, hl4.sys.Bytes.s32(m, 136) - gameH);
            }
            Maps.initDrawFlag();
            Gfx.setClip(0, 0, 400, 400);            // touch_setUiChange sets the clip to the square (_lcdG+104 = 400) here: the scaled draws (DM_DrawRotate565Ex) then wrap past the right edge into the next row
            MainGameFrame.drawGame();
            return;
        }
        if (mode != 1) {
            throw new IllegalStateException("touch_setUiChange(" + mode + "): later");
        }
        Touch.standInit();
        setMenuXY();
        Gfx.setClip(0, 0, gameW, gameH);
        Gfx.fillRectRGB(Gfx.scr, 0, 0, gameW, gameH, 0xb, 0xb, 0xb);
        if (Imgs.titleBg != null) {
            Img.draw(Gfx.scr, gameW / 2, gameH / 2, Img.get(Imgs.titleBg, 0), 0x12);
        }
        Gfx.setClip(0, 0, gameW, gameH);
    }

    /** Load_smsAgree @0xceda0: ./Hero4Sms holds the consent answer (absent: undecided). */
    public static void loadSmsAgree() {
        if (presetConsent != -2) {            // test hook (tools/ref_game.py consent stub)
            smsAgree = presetConsent;
            smsFlag2 = 0;
            return;
        }
        byte[] b = hl4.sys.Store.load("./Hero4Sms");
        if (b == null) {
            smsFlag2 = 0;
            smsAgree = -1;
            return;
        }
        smsAgree = b[0];                      // two s32 (stored as bytes): +0 answer, +4 flag
        smsFlag2 = b[4];
    }

    /** Save_smsAgree @0xceb2c: two s32 {answer, flag}, DES-encrypted, written to ./Hero4Sms (Store keeps the decrypted image, like LoadGameBase returns) */
    public static void saveSmsAgree() {
        byte[] b = new byte[8];
        hl4.sys.Bytes.w32(b, 0, (byte) smsAgree);
        hl4.sys.Bytes.w32(b, 4, (byte) smsFlag2);
        hl4.sys.Store.save("./Hero4Sms", b);
    }
}
