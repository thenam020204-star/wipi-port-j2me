package hl4.game;

import hl4.gfx.Frame;
import hl4.gfx.Hf;
import hl4.gfx.Img;
import hl4.gfx.Gbm;

/** Title screen with the data-consent dialog (TitleFrame_* @0xd8b5c..0xd8af0). */
public final class TitleFrame implements FrameHandler {
    public static final TitleFrame I = new TitleFrame();

    public static boolean pressAnyKey;
    private static int frameCnt;               // _frameCnt (signed byte, wraps to 0 after 10)
    private static boolean smsShow;            // _bSmsShow

    private TitleFrame() {
    }

    /** TitleFrame_Initialize @0xd8b5c */
    public static void initialize() {
        smsShow = false;
        Opt.load();
        MapFx.setFGI(6);
        pressAnyKey = true;
        frameCnt = 0;
        MapFx.setScreenEffect(5, 100, 100);
        Glob.setUiChange(1);
    }

    public void process() {
    }

    public void render() {
        Frame back = Gfx.back;
        Frame scr = Gfx.scr;
        int w = Glob.gameW;
        int h = Glob.gameH;
        Gfx.fillRectRGB(back, 0, 0, w, h, 0, 0, 0);
        if (Imgs.titleBg != null) {
            Img.draw(back, w / 2, h / 2, Img.get(Imgs.titleBg, 0), 0x12);
        }
        Gbm[] g = w < 0xb1 ? Imgs.gradeS : Imgs.grade;
        Img.draw(back, w - 2, 2, Img.get(g, 0), 0x21);
        Hf.setColor(0x57, 0x57, 0x57);
        Hf.drawAnchorH(back, Glob.centerX, h - 0xe, Strs.EA, 0x40, 0, 1);
        Gfx.copyOffScreen(0, 0, w, h, 0, 0);
        if (!MapFx.screenEffectCheck(5) && pressAnyKey) {
            Hf.setColor(0xff, 0xff, 0xff);
            Hf.drawAnchorH(scr, w >> 1, (h >> 1) + 0x1e, Strs.VER, 100, 0, 1);
            if (frameCnt < 5) {
                Hf.setColor(0xff, 0xff, 0);
                Hf.drawAnchorH(scr, w >> 1, (h >> 1) + 0x2b, Strs.TOUCH_SCREEN, 100, 0, 1);
            }
            frameCnt++;
            if (frameCnt > 10) {
                frameCnt = 0;
            }
        }
        MapFx.screenEffect();
        MapFx.fallRandDraw(scr);
        if (!MapFx.screenEffectCheck(5) && Glob.smsAgree == -1 && !smsShow) {
            Touch.setFrame(0x3f);
            Gfx.setClip(0, 0, w, Touch.cy + 0x61);
            Touch.draw(scr);
            Gfx.setClip(0, 0, w, h);
            byte[] body = LogoFrame.text(2);
            int[] e = Touch.extra(0x3c99);
            Hf.drawAnchorH(scr, e[0] + Touch.cx, Touch.cy - 5 + e[1], Strs.TITLE_AGREE, Strs.len(Strs.TITLE_AGREE), 0, 1);
            e = Touch.extra(0x3c3b);
            Hf.drawAnchorH(scr, e[0] + Touch.cx, Touch.cy - 5 + e[1], Strs.TITLE_REFUSE, Strs.len(Strs.TITLE_REFUSE), 0, 1);
            MMenu.setTextBox(body, 200, 100);
            Hf.setColor(0xff, 0xff, 0xff);
            byte[] head = LogoFrame.text(1);
            Hf.drawAnchorH(scr, Glob.centerX, Glob.centerY - 0x5a, head, Strs.len(head), 0, 1);
            int sel = MMenu.sel;
            Hf.drawAnchorH2(scr, Glob.centerX - 100, Glob.centerY - 0x4a, 200, 0, body, MMenu.start[sel], 0, MMenu.start[sel] + MMenu.len[sel], 0);
            GMenu.drawPageScroll(scr, Glob.centerX + 0x6a, Glob.centerY - 0x4a, 99, MMenu.nLines, MMenu.sel);
        }
    }

    public void endProcess() {
        if (Engine.changeFrame == 0) {
            return;
        }
        LoadFrame.setState(5, 3);
        MapFx.fallOff();
        MapFx.deleteEffectSprites();
        LogoFrame.releaseText();
    }

    public void keyPressed(int key) {
        if (MapFx.screenEffectCheck(5)) {
            return;
        }
        if (Glob.smsAgree != -1 || smsShow) {
            Engine.requestChange(1);
            return;
        }
        if (key == -1) {
            if (MMenu.sel > 0) {
                MMenu.sel--;
            }
        } else if (key == -2) {
            if (MMenu.sel < MMenu.nLines - 1) {
                MMenu.sel++;
            }
        } else if (key == 0x31 || key == 0x32) {
            Glob.smsAgree = key == 0x31 ? 1 : 0;
            throw new IllegalStateException("consent answer -> NpcUi(5, 10) popup: later");
        }
    }

    public void keyReleased(int key) {
    }

    public void hideNotify() {
    }

    public void showNotify() {
    }
}
