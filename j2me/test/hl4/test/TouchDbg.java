package hl4.test;

import hl4.game.*;
import hl4.gfx.*;
import hl4.sys.Clock;

public class TouchDbg {
    public static void main(String[] a) throws Exception {
        Clock.setVirtual(1303268400000L);
        Engine.startApp();
        Touch.setFrame(0x19);
        Sprite s = Touch.spriteForDebug();
        s.drawSprite(new SpriteOut() {
            public void restorePalette(Gbm img) {}
            public void setPalBlendEx(Gbm img, int p1, int p2, int p3, int p4, int p5) { System.out.println("palblend " + p1+" "+p2+" "+p3+" "+p4+" "+p5); }
            public void drawImg(Frame f, int x, int y, int w, int h, Gbm img, int sx, int sy, int flip, int rot, int anchor) { System.out.println("drawImg x=" + x + " y=" + y + " w=" + w + " h=" + h + " flip=" + flip + " rot=" + rot + " anc=" + anchor + " img " + img.w + "x" + img.h); }
            public void drawImgEx(Frame f, int x, int y, int w, int h, Gbm img, int sx, int sy, int flip, int rot, int alpha, int effect, int anchor) { System.out.println("drawImgEx x=" + x + " y=" + y + " w=" + w + " h=" + h + " rot=" + rot + " alpha=" + alpha + " eff=" + effect + " img " + img.w + "x" + img.h); }
            public void drawImgRotate(Frame f, int x, int y, int w, int h, Gbm img, int sx, int sy, int flip, int rot, int sX, int sY, int anchor) { System.out.println("drawRot x=" + x + " y=" + y + " w=" + w + " h=" + h + " sx=" + sX + " sy=" + sY + " img " + img.w + "x" + img.h); }
            public void drawImgRotateEx(Frame f, int x, int y, int w, int h, Gbm img, int sx, int sy, int flip, int rot, int sX, int sY, int eff, int alpha, int anchor) { System.out.println("drawRotEx x=" + x + " y=" + y + " sx=" + sX + " sy=" + sY + " eff=" + eff + " alpha=" + alpha); }
            public int particle() { return 1; }
        }, Gfx.scr, Touch.animForDebug(), 0, 0, Touch.cx, Touch.cy, 0, -1);
    }
}
