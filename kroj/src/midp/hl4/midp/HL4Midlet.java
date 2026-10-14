package hl4.midp;

import javax.microedition.lcdui.Display;
import javax.microedition.midlet.MIDlet;

/** MIDlet entry point: runs the original WIPI-C binary of Heroes Lore 4 inside a Java ARM core. */
public class HL4Midlet extends MIDlet {
    private GameCanvas canvas;

    protected void startApp() {
        if (canvas == null) {
            canvas = new GameCanvas(this);
            Display.getDisplay(this).setCurrent(canvas);
            canvas.start();
        } else {
            Display.getDisplay(this).setCurrent(canvas);
            canvas.resumeGame();
        }
    }

    protected void pauseApp() {
        if (canvas != null) canvas.pauseGame();
    }

    protected void destroyApp(boolean unconditional) {
        if (canvas != null) canvas.stopGame();
    }

    void quit() {
        destroyApp(true);
        notifyDestroyed();
    }
}
