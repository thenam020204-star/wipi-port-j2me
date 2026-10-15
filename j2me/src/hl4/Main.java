package hl4;

import javax.microedition.lcdui.Display;
import javax.microedition.midlet.MIDlet;

/** MIDlet entry (the original's Clet start/pause/resume/destroy, @CUSTOM_onStartClet and friends - Phase B flows). */
public class Main extends MIDlet {
    private GameCanvas canvas;

    protected void startApp() {
        if (canvas == null) {
            canvas = new GameCanvas();
            Display.getDisplay(this).setCurrent(canvas);
        }
        canvas.resume();
    }

    protected void pauseApp() {
        if (canvas != null) {
            canvas.pause();
        }
    }

    protected void destroyApp(boolean unconditional) {
        if (canvas != null) {
            canvas.stop();
        }
    }
}
