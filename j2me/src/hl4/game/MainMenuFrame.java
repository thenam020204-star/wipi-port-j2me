package hl4.game;

import hl4.data.Text;
import hl4.gfx.Frame;
import hl4.gfx.Gbm;
import hl4.gfx.Hf;
import hl4.gfx.Img;
import hl4.sys.Res;

/**
 * The main menu (MainMenuFrame_* @0xa0a5c.., MMenu_* ). Sub-mode _mainMenu[0]: 0 menu bar, 1 story mode, 2 network, 3 options, 4 help,
 * 5 support, 6 exit.  Only the menu bar (mode 0) is ported so far.
 */
public final class MainMenuFrame implements FrameHandler {
    public static final MainMenuFrame I = new MainMenuFrame();

    /** _mainMenu[0] */
    public static int mode;
    /** _mainMenu[2]: selected item of the bar (0..5) */
    public static int sel;
    private static byte[][] menuTxt;       // /tdf/_tdf_MENU_TXT
    private static byte[][] helpTxt;       // /tdf/_tdf_HELP

    private MainMenuFrame() {
    }

    /** MainMenuFrame_Initialize @0xa0a5c */
    public static void initialize() {
        menuTxt = Text.linkedAll(Res.load("/tdf/_tdf_MENU_TXT"));
        helpTxt = Text.linkedAll(Res.load("/tdf/_tdf_HELP"));
        MapFx.setScreenEffect(-1, 0, 0);
        TitleFrame.pressAnyKey = false;
        Engine.frameDelay = Opt.FPS[Opt.v[2]];
        mode = 0;
        sel = 0;
        G.gameMenu[0x16] = 0;
        G.map[0x68] = (byte) 0xff;
        Touch.setFrame(Touch.NONE);
    }

    /** MainMenuFrame_Finalize: text resources released (the title background and rating images stay until MainFrame_Finalize in the original: only the title bg is freed) */
    public static void finalizeFrame() {
        Touch.setFrame(Touch.NONE);
        menuTxt = null;
        helpTxt = null;
        Imgs.titleBg = null;
        Imgs.grade = null;
    }

    public void process() {
    }

    public void render() {
        if (mode != 3) {
            Gfx.copyOffScreen(0, 0, Glob.gameW, Touch.cy - 0x68, 0, 0);
        }
        if (mode != 0) {
            throw new IllegalStateException("main menu sub-mode " + mode + ": later");
        }
        paintMain();
    }

    /** MMenu_PaintMain @0x9f70c */
    private static void paintMain() {
        Frame scr = Gfx.scr;
        Frame back = Gfx.back;
        int w = Glob.gameW;
        int h = Glob.gameH;
        Gbm arrow = Img.get(Imgs.mmenu, 0x1d);
        byte[] text = menuTxt[sel];
        int tw = Hf.width(text, Strs.len(text), 0);
        Gfx.fillRectRGB(scr, 0, 0, w, h, 0, 0, 0);
        Gfx.fillRectRGB(back, 0, 0, w, h, 0, 0, 0);
        if (Imgs.titleBg != null) {
            Img.draw(back, w / 2, h / 2, Img.get(Imgs.titleBg, 0), 0x12);
        }
        Gfx.fillRectAlpha(back, 0, (h >> 1) + 0x28, w, 0x10, 0x50, 0xb, 0xb, 0xb);
        Gfx.copyOffScreen(0, 0, w, h, 0, 0);
        Hf.setColor(0xff, 0xff, 0xff);
        Hf.drawAnchorH(scr, Glob.centerX, (h >> 1) + 0x2b, text, 0x40, 0, 1);
        if (arrow != null) {
            Img.drawPlain(scr, (Glob.centerX - 0xf) - (tw >> 1), (h >> 1) + 0x2c, arrow, 0x11);
            Img.drawFlip3(scr, Glob.centerX + 0xf + (tw >> 1), (h >> 1) + 0x2c, arrow, 0x11);
        }
        Hf.setColor(0x57, 0x57, 0x57);
        Hf.drawAnchorH(scr, Glob.centerX, h - 0xe, Strs.EA, 0x40, 0, 1);
    }

    public void endProcess() {
    }

    public void keyPressed(int key) {
        if (mode != 0) {
            throw new IllegalStateException("main menu sub-mode " + mode + ": later");
        }
        switch (key) {
            case -4:
            case 0x36:
                sel = (sel + 1) % 6;
                break;
            case -3:
            case 0x34:
                sel = (sel + 5) % 6;
                break;
            case -5:
            case 0x35:
                if (sel == 0) {
                    // TEST BUILD shortcut (behaviour change, docs/phaseE.md section 4): the story-mode screens (slot / force / class / confirm,
                    // phase E4) are not ported yet, so item 0 starts a new game in slot 0 with class 0 - the same state the trace tests start from
                    GameStart.newGame(0);
                    return;
                }
                throw new IllegalStateException("main menu item " + sel + ": later");
            default:
                break;
        }
    }

    public void keyReleased(int key) {
    }

    public void hideNotify() {
    }

    public void showNotify() {
    }
}
