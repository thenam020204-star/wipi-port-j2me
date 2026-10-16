package hl4.game;

import hl4.data.Text;
import hl4.gfx.Frame;
import hl4.sys.Res;
import hl4.sys.Rng;

/**
 * The loading screen and the scene-transition state machine (LoadFrame_* @0x87xxx).  LoadFrame_setState(target, next) starts a load;
 * LoadFrame_Process does the work for _loadCnt = 0 (and 1..9 for the game load), LoadFrame_Finalize counts ticks and, from the 11th, switches
 * to the frame named by _nextFrame (1 Logo, 2 Title, 3 MainMenu, 4/6 MainGame, 5 stay, 7 NpcUi).
 */
public final class LoadFrame implements FrameHandler {
    public static final LoadFrame I = new LoadFrame();

    public static boolean nowLoading;
    public static int loadCnt;
    public static int curTarget;
    public static int prevTarget;
    public static int nextFrame;
    public static byte[] tip = new byte[256];

    private static byte[][] tipTexts;

    private LoadFrame() {
    }

    /** LoadFrame_setState(target, next) @0x87d68 (the title/logo/menu variants; the in-game variant for next == 4 is in a later phase). */
    public static void setState(int target, int next) {
        nowLoading = true;
        loadCnt = 0;
        for (int i = 0; i < tip.length; i++) {
            tip[i] = 0;
        }
        prevTarget = curTarget;
        curTarget = target;
        nextFrame = next;
        Engine.frameDelay = 10;                 // MyTimer_setFrameRateLoad
        pickTip();
        Engine.setFrame(I);
        if (next == 1) {
            Glob.setUiChange(1);
        } else if (next == 4) {
            throw new IllegalStateException("in-game load: later");
        } else {
            Glob.setUiChange(1);
        }
    }

    /** MrgText_load("/tdf/_tdf_TIP") + UM_Rand(0, n-1) + copy: consumes one RNG value. */
    private static void pickTip() {
        if (tipTexts == null) {
            tipTexts = Text.parseMrg(Res.load("/tdf/_tdf_TIP"));
        }
        int k = (byte) Rng.rand(0, tipTexts.length - 1);
        byte[] t = tipTexts[k];
        System.arraycopy(t, 0, tip, 0, t.length < 256 ? t.length : 256);
    }

    public void process() {
        if (curTarget < 2) {
            return;
        }
        if (loadCnt == 0) {
            delete();
        }
        switch (curTarget) {
            case 2:
            case 3:
                if (curTarget == 3) {
                    Imgs.loadLogo();
                }
                Imgs.loadCommon(0);
                loadCnt = 10;
                break;
            case 4:
                Imgs.loadTitle();
                Glob.loadSmsAgree();
                loadCnt = 10;
                Touch.setFrame(0x3a);
                break;
            default:
                throw new IllegalStateException("load target " + curTarget + ": later");
        }
    }

    /** LoadFrame_delete: releases what the previous target held */
    private static void delete() {
        switch (prevTarget) {
            case 3:
                Imgs.deleteLogo();
                break;
            default:
                break;
        }
    }

    public void render() {
        if (curTarget != 0 && loadCnt < 10) {
            Frame f = Gfx.scr;
            int w = Glob.gameW;
            int h = Glob.gameH;
            Gfx.fillRectRGB(f, 0, 0, w, h, 0xb, 0xb, 0xb);
            int cx = w >> 1;
            int cy = h >> 1;
            Gfx.setFg(0x46, 0x46, 0x46);
            // the eight squares of the spinner: (dx, dy) in drawing order
            final int[] ox = {0, 10, 14, 10, 0, -10, -14, -10};
            final int[] oy = {-14, -10, 0, 10, 14, 10, 0, -10};
            for (int i = 0; i < 8; i++) {
                Gfx.fillRect(f, cx + ox[i], cy + oy[i], 7, 7);
            }
            Gfx.setFg(0xff, 0xff, 0xff);
            int k = loadCnt % 8;
            Gfx.fillRect(f, cx + ox[k], cy + oy[k], 7, 7);
            Gfx.setFg(0x89, 0x89, 0x89);
            int prev = (k + 7) % 8;
            Gfx.fillRect(f, cx + ox[prev], cy + oy[prev], 7, 7);
        }
    }

    /** LoadFrame_Finalize (= LoadFrame_endProcess) @0x878fc */
    public void endProcess() {
        loadCnt++;
        if (loadCnt < 0xb) {
            return;
        }
        switch (nextFrame) {
            case 1:
                LogoFrame.initialize();
                Engine.setFrame(LogoFrame.I);
                break;
            case 2:
                TitleFrame.initialize();
                Engine.setFrame(TitleFrame.I);
                break;
            default:
                throw new IllegalStateException("next frame " + nextFrame + ": later");
        }
        nowLoading = false;
        Engine.frameDelay = Opt.FPS[Opt.v[2]];     // MenuOptions_setSpeed
    }

    public void keyPressed(int key) {
    }

    public void keyReleased(int key) {
    }

    public void hideNotify() {
    }

    public void showNotify() {
    }
}
