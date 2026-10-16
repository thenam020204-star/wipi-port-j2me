package hl4.game;

import hl4.gfx.Frame;
import hl4.gfx.Fx;
import hl4.gfx.Gbm;
import hl4.gfx.Hf;

/** Shared menu drawing helpers of GMenu_* / drawGMenuBackFrame* (portrait layout; the nTouchRotate == 1 branches are not ported). */
public final class GMenu {
    private GMenu() {
    }

    /** drawGMenuBackFrame(title, left, center, right) -> drawGMenuBackFrame_2 @0x73378: title ribbon + bottom labels. */
    public static void backFrame(byte[] title, byte[] left, byte[] center, byte[] right) {
        Frame f = Gfx.scr;
        int y = Glob.menuY - 0x11;
        int x = Glob.menuX;
        int w = Touch.gw;
        if (title != null) {
            Gbm img = hl4.gfx.Img.get(Imgs.gmenuComm, 0);
            int tw = Hf.width(title, Strs.len(title), 0);
            Gfx.fillRectRGB(f, x, y, w, 0x11, 0xd3, 0xee, 0x3c);
            Gfx.fillRectRGB(f, x + 3, y + 2, w - 6, 0xd, 0x53, 0x5b, 0x2b);
            Gfx.drawRectRGB(f, x - 1, y, w + 2, 0x11, 0xb, 0xb, 0xb);
            Fx.drawImgRotate(f, Glob.centerX - (tw >> 1), y + 0xf, img.w, img.h, img, 0, 0, 0, 0, 100, 100, 0x24);
            Fx.drawImgRotate(f, (tw >> 1) + Glob.centerX, y + 2, img.w, img.h, img, 0, 0, 3, 0, 100, 100, 9);
            Hf.setColor(0xff, 0xff, 0xff);
            Hf.drawAnchorH(f, Glob.centerX, y + 3, title, Strs.len(title), 0, 1);
        }
        if (left != null) {
            Hf.drawAnchorH(f, 0xc, Touch.cy + 0x4a, left, Strs.len(left), 0, 0);
        }
        if (center != null) {
            Hf.drawAnchorH(f, Glob.centerX, Touch.cy + 0x4a, center, Strs.len(center), 0, 1);
        }
        if (right != null) {
            Hf.drawAnchorH(f, Gfx.scr.w - 0xc, Touch.cy + 0x4a, right, Strs.len(right), 0, 2);
        }
    }

    /**
     * drawGMenuBackFrame_main(p1, x, y, p4, w, h, r, g, b, p10) @0x6ac70: translucent panel (alpha ~p10) with a green line, two black outlines and the four
     * corner ornaments of the common menu image 0.  p1 != 0 shortens the box by 12.
     */
    public static void backFrameMain(Frame f, int p1, int x, int y, int p4, int w, int h, int r, int g, int b, int p10) {
        int top = p4 + y;
        int cut = p1 == 0 ? 0 : 0xc;
        int hh = (h - p4) - cut;
        Gfx.fillRectAlpha(f, x, top, w, hh, ~p10 & 0xff, r, g, b);
        Gfx.drawRectRGB(f, x, top, w, hh, 0x53, 0x5b, 0x2b);
        Gfx.drawRectRGB(f, x + 1, top + 1, w - 2, ((-2 - p4) + h) - cut, 0xb, 0xb, 0xb);
        int lx = x - 1;
        int ty = top - 1;
        Gfx.drawRectRGB(f, lx, ty, w + 2, (h - (p4 - 2)) - cut, 0xb, 0xb, 0xb);
        int rx = w + x + 1;
        Gbm img = hl4.gfx.Img.get(Imgs.gmenuComm, 0);
        Fx.drawImgRotate(f, lx, ty, img.w, img.h, img, 0, 0, 0, 0, 100, 100, 9);
        Fx.drawImgRotate(f, rx, ty, img.w, img.h, img, 0, 0, 1, 0, 100, 100, 0x21);
        int by = ((y + 1) + h) - cut;
        Fx.drawImgRotate(f, lx, by, img.w, img.h, img, 0, 0, 2, 0, 100, 100, 0xc);
        Fx.drawImgRotate(f, rx, by, img.w, img.h, img, 0, 0, 3, 0, 100, 100, 0x24);
    }

    /** GMenu_drawSubTitle(x, y, w, num1, num2, text, kind) @0x73090 (kind 7 = green ribbon). num1/num2 = -1 mean none. */
    public static void drawSubTitle(int x, int y, int w, int num1, int num2, byte[] text, int kind) {
        Frame f = Gfx.scr;
        Gbm img = hl4.gfx.Img.get(Imgs.gmenuComm, kind - 2);
        int half = w >> 1;
        if (kind == 7) {
            Gfx.fillRectRGB(f, x - half, y, w, 0xd, 0xd3, 0xee, 0x3c);
        }
        Fx.drawImgRotate(f, (x - img.aux) - half, y, img.w, img.h, img, 0, 0, 0, 0, 100, 100, 9);
        Fx.drawImgRotate(f, x + img.aux + half, y, img.w, img.h, img, 0, 0, 1, 0, 100, 100, 0x21);
        if (num1 != -1 && num1 + 1 >= 0) {
            throw new IllegalStateException("subtitle numbers: later");
        }
        if (num2 != -1 && num2 + 1 >= 0) {
            throw new IllegalStateException("subtitle numbers: later");
        }
        if (text != null) {
            Hf.setColor(0, 0, 0);
            Hf.drawAnchorH(f, x, y + 2, text, Strs.len(text), 0, 1);
        }
    }

    /** drawTwoColorRectRGB without the tiled-image part (image == null): outline then inner fill. */
    public static void twoColorRect(Frame f, int x, int y, int w, int h, int or, int og, int ob, int fr, int fg, int fb) {
        Gfx.drawRectRGB(f, x, y, w, h, or, og, ob);
        Gfx.fillRectRGB(f, x + 1, y + 1, w - 2, h - 2, fr, fg, fb);
    }

    /** GMenu_drawPageScroll(frame, x, y, h, nLines, sel) @0x7393c: slim scroll bar of the text box. */
    public static void drawPageScroll(Frame f, int x, int y, int h, int nLines, int sel) {
        if (nLines < 2) {
            return;
        }
        int cur = sel < nLines ? (sel < 0 ? 0 : sel) : nLines - 1;
        int knob = h / nLines;
        int knobH = knob < 6 ? 4 : knob - 2;
        if (knob < 6) {
            knob = 6;
        }
        twoColorRect(f, x, y, 3, h + 2, 0x25, 0x25, 0x25, 0, 0, 0);
        int off = ((h - knob) * cur) / (nLines - 1);
        twoColorRect(f, x, y + 1 + off, 3, knobH, 0x25, 0x25, 0x25, 0xd3, 0xee, 0x3c);
        x = x + 1;
        Gfx.setFg(0xff, 0xff, 0xff);
        Gfx.drawLine(f, x, y, x, y);
        Gfx.drawLine(f, x, h + y, x, h + y);
    }
}
