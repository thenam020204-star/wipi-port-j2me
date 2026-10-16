package hl4.game;

import hl4.gfx.Blit;
import hl4.gfx.Frame;
import hl4.gfx.Gbm;
import hl4.gfx.Hf;
import hl4.gfx.Img;
import hl4.sys.Bytes;

/**
 * The in-game HUD (MainGameFrame_drawBottomBar @0x9c0f4, ..._drawGameBottomUI @0x9c0b0, ..._drawGameBottomUI_Touch @0x9b8bc, Touch_drawImageTouchSingle @0x48aac).
 * Portrait layout only (nTouchRotate == 0).  Branches of the original that need systems not yet ported stop with "later".
 */
public final class Hud {
    private Hud() {
    }

    /** _newPadTouch: the HUD panel images (TOUCH/CIMG/NS) */
    private static Gbm[] pad;

    private static final byte[] LV = {'L', 'V', 0};
    private static final byte[] LVNUM = new byte[8];

    private static Gbm[] pad() {
        if (pad == null) {
            pad = Img.loadAll("/TOUCH/CIMG/NS", -1);
        }
        return pad;
    }

    /** Touch_drawImageTouchSingle(set, idx, x, y, srcX, srcY, w, h, mirror, rot): a clipped sub-rectangle of image idx at (x, y), top-left anchor */
    public static void drawSingle(int idx, int x, int y, int sx, int sy, int w, int h, int mirror, int rot) {
        Gbm g = Img.get(pad(), idx);
        if (g == null || w < 1 || h < 1) {
            return;
        }
        Blit.drawImg(Gfx.scr, x, y, w, h, g, sx, sy, mirror, rot, 9);
    }

    /** MainGameFrame_drawBottomBar: nothing during events */
    public static void drawBottomBar() {
        if (Event.isEvent()) {
            return;
        }
        byte[] g = G.game;
        PickUp.drawItems();
        drawGameBottomUI();
        if (g[3] != 0) {
            throw new IllegalStateException("Map_Draw_Minimap: later");
        }
    }

    /** MainGameFrame_drawGameBottomUI */
    private static void drawGameBottomUI() {
        byte[] h = Pool.hero.m;
        drawPanel();
        if (Bytes.s16(h, 0x220) > 0 || Bytes.s16(h, 0x222) > 0) {
            throw new IllegalStateException("MainGameFrame_drawGameBottomUI: skill cool-down overlay (DM_DrawImgRotateEx, _uiGame 6/7): later");
        }
        for (int i = 0; i < 3; i++) {
            if (h[0xf4 + i * 8] == 1) {
                throw new IllegalStateException("MainGameFrame_drawSkillIcon (buff icons): later");
            }
        }
        if (h[0x189] != 0) {
            if (h[0x1ed] != 0) {
                throw new IllegalStateException("MainGameFrame_drawGuardianIcon: later");
            }
            return;
        }
        int q = Bytes.s32(h, 0x216);
        if (q >= 0x1c && q <= 0x27) {
            throw new IllegalStateException("MainGameFrame_drawGameBottomUI: quick-slot item icon (Item_drawIcon): later");
        }
    }

    /** MainGameFrame_drawGameBottomUI_Touch(3): the top-left status panel (frame, EXP / HP / SP bars, level) */
    private static void drawPanel() {
        Char hero = Pool.hero;
        byte[] h = hero.m;
        Frame f = Gfx.scr;
        Gfx.fillRectRGB(f, 0, 1, 0x7f, 0xf, 0, 0, 0);
        Gfx.fillRectRGB(f, 0, 0x10, 0x7e, 1, 0, 0, 0);
        Gfx.fillRectRGB(f, 0, 0x11, 0x65, 4, 0, 0, 0);
        Gfx.fillRectRGB(f, 0, 2, 0x7f, 1, 0x4a, 0x4d, 0x39);
        Gfx.fillRectRGB(f, 0, 0x11, 0x65, 1, 0x4a, 0x4d, 0x39);
        int bw = 100;
        int curExp = Bytes.s32(h, 0x178);
        int maxExp = Bytes.s32(h, 0x17c);
        Gfx.fillRectRGB(f, 0, 0x13, div(curExp * bw, maxExp), 1, 0xff, 0xf2, 0);
        int hp = div(Bytes.s32(h, 0xa4) * bw, Bytes.s32(h, 0xa8));
        Gfx.fillRectRGB(f, 0x19, 5, hp, 1, 0xee, 0x98, 0x1c);
        Gfx.fillRectRGB(f, 0x19, 6, hp, 3, 0xee, 0x1c, 0x24);
        int sp = div((Bytes.s32(h, 0x218) / 10) * bw, Bytes.s32(h, 0x21c) / 10);
        Gfx.fillRectRGB(f, 0x19, 0xb, sp, 1, 0xac, 0xe7, 0xfd);
        Gfx.fillRectRGB(f, 0x19, 0xc, sp, 3, 0, 0xae, 0xef);
        drawSingle(1, 0x7d, 1, 0, 0, 6, 5, 0, 0);
        drawSingle(1, 99, 0x10, 0, 0, 6, 5, 0, 0);
        drawSingle(0, 0, 0, 0, 0, 0xf, 9, 0, 0);
        int lv = h[0x94];
        Hf.setColor(0x96, 0x96, 0x96);
        int n = 0;
        if (lv <= 9) {
            LVNUM[n++] = '0';
        }
        n = putInt(LVNUM, n, lv);
        LVNUM[n] = 0;
        Hf.drawText(f, 2, 10, LV, 1);
        Hf.setColor(0xff, 0xff, 0xff);
        Hf.drawText(f, 0xc, 6, LVNUM, 0);
    }

    private static int div(int a, int b) {
        return b == 0 ? 0 : a / b;
    }

    private static int putInt(byte[] b, int n, int v) {
        if (v < 0) {
            b[n++] = '-';
            v = -v;
        }
        int d = 1;
        while (v / d >= 10) {
            d *= 10;
        }
        while (d > 0) {
            b[n++] = (byte) ('0' + v / d % 10);
            d /= 10;
        }
        return n;
    }
}
