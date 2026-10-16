package hl4.game;

import hl4.gfx.Frame;
import hl4.gfx.Gbm;
import hl4.gfx.Hf;
import hl4.gfx.Img;
import hl4.sys.Clock;
import hl4.sys.Res;

/** Logo / rating / notice screens (LogoFrame_* @0x87fe4..0x889a8). States: 0 age-rating icons (3.5 s), 1 notice pages, 2 game logo, 3 authentication error. */
public final class LogoFrame implements FrameHandler {
    public static final LogoFrame I = new LogoFrame();

    private static int state;
    private static long time;
    private static int frame;
    private static int select;                 // _logoLgtUseInfoSelect: 0 first notice page, 1 second
    private static byte[][] useInfo;           // _txtOnlyLgt (/tdf/_tdf_UseInfo_TXT): linked text strings

    private LogoFrame() {
    }

    /** LogoFrame_Initialize @0x87fe4 */
    public static void initialize() {
        frame = 0;
        state = Engine.authError ? 3 : 0;
        time = Clock.now() + 0xdac;
        select = 0;
    }

    /** LogoFrame_UseInfoTxt: load the notice text once */
    private static void loadUseInfo() {
        if (useInfo == null) {
            byte[] b = Res.load("/tdf/_tdf_UseInfo_TXT");
            int n = hl4.data.Text.linkedCount(b);
            useInfo = new byte[n][];
            for (int i = 0; i < n; i++) {
                byte[] s = hl4.data.Text.linked(b, i);
                byte[] p = new byte[s.length + 8];
                System.arraycopy(s, 0, p, 0, s.length);
                useInfo[i] = p;
            }
        }
    }

    /** the notice text is kept by TitleFrame (consent dialog) until it is released there */
    public static byte[] text(int k) {
        return useInfo[k];
    }

    public static void releaseText() {
        useInfo = null;
    }

    public void process() {
    }

    public void render() {
        Frame f = Gfx.scr;
        int w = Glob.gameW;
        int h = Glob.gameH;
        if (state == 3) {
            Gfx.fillRectRGB(f, 0, 0, w, h, 0xff, 0xff, 0xff);
            Hf.setColor(0, 0, 0);
            Hf.drawText(f, 1, h / 2, Strs.LOGO_AUTH_ERR, 0);
        } else if (state == 0) {
            Gfx.fillRectRGB(f, 0, 0, w, h, 0xff, 0xff, 0xff);
            if (time < Clock.now()) {
                time = 0;
                state = 1;
                loadUseInfo();
                return;
            }
            Gbm[] g0;
            Gbm[] g1;
            Gbm[] g2;
            int x0;
            int x1;
            if (w < 0xb1) {
                g0 = Imgs.gradeS;
                g1 = Imgs.gradeV1S;
                g2 = Imgs.gradeV2S;
                x0 = w - 0x60;
                x1 = w - 0x31;
            } else {
                g0 = Imgs.grade;
                g1 = Imgs.gradeV1;
                g2 = Imgs.gradeV2;
                x0 = w - 0x7c;
                x1 = w - 0x3f;
            }
            Img.draw(f, x0, 2, Img.get(g0, 0), 0x21);
            Img.draw(f, x1, 2, Img.get(g1, 0), 0x21);
            Img.draw(f, w - 2, 2, Img.get(g2, 0), 0x21);
        } else if (state == 1) {
            Gfx.fillRectRGB(f, 0, 0, w, h, 0, 0, 0);
            Touch.setFrame(0x19);
            Gfx.setClip(0, 0, w, Touch.cy + 0x61);
            Touch.draw(f);
            Gfx.setClip(0, 0, w, h);
            byte[] t = useInfo[select == 0 ? 0 : 3];
            int menuY = Glob.menuY;
            GMenu.backFrame(null, null, Strs.LOGO_OK, null);
            MMenu.setTextBox(t, select == 1 ? 0xaa : 0x9a, 100);
            GMenu.drawSubTitle(Glob.centerX, Glob.centerY - 0x5e, 0x55, -1, -1, Strs.LOGO_TITLE, 7);
            Hf.setColor(0xff, 0xff, 0xff);
            int sel = MMenu.sel;
            if (select == 1) {
                Hf.setColor(0xff, 0, 0);
                for (int i = 0; i < 2; i++) {
                    Hf.drawAnchorH(f, Glob.centerX, Glob.centerY - 0x37 + i, Strs.LOGO_CALL, Strs.len(Strs.LOGO_CALL), 0, 1);
                    byte[] ph = Strs.PHONE;
                    Hf.drawAnchorH(f, Glob.centerX, Glob.centerY - 0xb + i, ph, Strs.len(ph), 0, 1);
                }
                Hf.setColor(0xff, 0xff, 0xff);
                Hf.drawAnchorH2(f, Glob.centerX, Glob.centerY - 0x32, 0xaa, 0, t, MMenu.start[sel], 0, MMenu.start[sel] + MMenu.len[sel], 1);
            } else {
                Hf.drawAnchorH2(f, Glob.centerX, Glob.centerY - 0x28, 0x9a, 0, t, MMenu.start[sel], 0, MMenu.start[sel] + MMenu.len[sel], 1);
            }
            GMenu.drawPageScroll(f, Glob.centerX + 0x50, menuY + 0x34, 99, MMenu.nLines, MMenu.sel);
        } else if (state == 2) {
            Gfx.fillRectRGB(f, 0, 0, w, h, 0xf7, 0xfc, 0xf8);
            Gbm img = Img.get(Imgs.logo, 4);
            int q = img.h * 3;
            if (q < 0) {
                q += 3;
            }
            Img.draw(f, w / 2, h / 2 - (q >> 2), img, 0x11);
        }
    }

    public void endProcess() {
        if (state == 0) {
            return;
        }
        if (state == 2) {
            frame++;
            if (frame > 0x32) {
                time = 0;
                LoadFrame.setState(4, 2);
            }
        }
    }

    public void keyPressed(int key) {
        if (state == 3) {
            Engine.exit = true;
            return;
        }
        if (state != 0) {
            if (state == 1) {
                if (key == 0x35 || key == 0x3b49 || key == -5) {
                    Touch.setFrame(0x65);
                    if (select == 0) {
                        select = 1;
                    } else {
                        state = 2;
                    }
                }
            } else if (state == 2) {
                frame = 0x32;
            }
            return;
        }
        state = 1;
        time = 0;
        loadUseInfo();
    }

    public void keyReleased(int key) {
    }

    public void hideNotify() {
    }

    public void showNotify() {
    }
}
