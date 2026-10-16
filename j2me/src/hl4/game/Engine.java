package hl4.game;

import hl4.gfx.Hf;
import hl4.sys.Clock;
import hl4.sys.Rng;

/**
 * The frame loop of the original: timerCB @0xa7994 (one call = one game tick) and the key latch of handleCletEvent/onHandleEvent
 * (a press event stores the key in 'keyPress', a release in 'keyRelease'; -100 = none; the tick delivers each at most once and clears
 * them - several events inside one tick keep only the last, docs/phaseB_flows.md section 4; events are ignored while loading).
 * Rendering goes to Gfx.scr; presentation (the original's MC_grpFlushLcd) is done by the caller after tick().
 */
public final class Engine {
    public static FrameHandler cur;
    /** delay in ms the original waits after painting before the next tick (MyTimer: 1, loading 10, then fps[speed option]) */
    public static int frameDelay = 1;
    public static int keyPress = -100;      // mainFrame+0x30
    public static int keyRelease = -100;    // mainFrame+0x34
    public static int changeFrame;          // mainFrame+0x38
    public static boolean keyDown;          // mainFrame+0x14
    public static int lastKey;              // mainFrame+0x18
    public static int prevKey;              // mainFrame+0x1c
    /** MC_knlExit was requested */
    public static boolean exit;
    /** MainFrame_certification failed (mainFrame+0x4e): the offline port always passes */
    public static boolean authError;
    /** mainFrame+0: the frame counter word reset by SaveGameDataInit */
    public static int mainFrame0;

    private Engine() {
    }

    /** MainFrame_ElementAt */
    public static void setFrame(FrameHandler h) {
        cur = h;
    }

    /** onStartApp @0x436d8 (the parts that exist in this port so far) + MainFrame_Initialize. */
    public static void startApp() {
        Rng.reset();
        Glob.gameW = 240;
        Glob.gameH = 320;
        // _menuOptions stays all zero until TitleFrame_Initialize runs LoadOption (CONFIRMED by the reference run: opt bytes 00.. before the title)
        Fr.load();
        Items.dataPreLoad();
        Pool.initialize();
        Pool.initSlots();
        Gfx.init();
        Gfx.fillRectRGB(Gfx.scr, 0, 0, Glob.gameW, Glob.gameH, 0xd, 0xd, 0xf);
        Hf.init();
        Hf.semicolon = 0;
        Imgs.loadCommonMenu();
        Touch.init();
        Glob.setMenuXY();
        keyPress = -100;
        keyRelease = -100;
        frameDelay = 1;
        // MainFrame_Initialize: the first load (target 3 -> Logo)
        LoadFrame.curTarget = 0;
        LoadFrame.setState(3, 1);
    }

    /** handleCletEvent -> onHandleEvent for the keypad events 0x1f6 (press) / 0x1f7 (release). */
    public static void onKey(boolean press, int key) {
        if (LoadFrame.nowLoading) {
            return;
        }
        if (press) {
            keyPress = key;
        } else {
            keyRelease = key;
        }
    }

    /** timerCB: one game frame. */
    public static void tick() {
        if (keyPress != -100) {
            keyDown = true;
            lastKey = keyPress;
            cur.keyPressed(keyPress);
        }
        if (keyRelease != -100) {
            keyDown = false;
            lastKey = keyRelease;
            cur.keyReleased(keyRelease);
            keyRelease = -100;
        }
        Sound.checkBgm();
        cur.process();
        prevKey = lastKey;
        lastKey = 0;
        cur.render();
        cur.endProcess();
        changeFrame = 0;
        Sound.tick();
        keyPress = -100;
        keyRelease = -100;
    }

    /** MainFrame_changeFrame / MainFrame_getChangeFrame */
    public static void requestChange(int n) {
        changeFrame = n;
    }
}
