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
        byte[] b = hl4.sys.Store.load("./Hero4Sms");
        if (b == null) {
            smsFlag2 = 0;
            smsAgree = -1;
            return;
        }
        smsAgree = b[0];
        smsFlag2 = b[1];
    }
}
