package hl4;

import hl4.gfx.Blit;
import hl4.gfx.Frame;
import hl4.gfx.Gbm;
import hl4.sys.Res;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Graphics;

/**
 * Display + loop shell.  The game renders into a 240x320 RGB565 Frame (the original's internal resolution); this class only
 * presents it (RGB565 -> ARGB in bands, centred on the device screen) and forwards key edges.  The frame content comes from
 * hl4.Game (Phase E); until then a resource smoke screen is drawn.
 */
public class GameCanvas extends Canvas implements Runnable {
    public static final int W = 240;
    public static final int H = 320;
    private static final int BAND = 32;

    private final Frame frame = new Frame(W, H);
    private final int[] rgb = new int[W * BAND];
    private Thread thread;
    private volatile boolean running;
    private volatile boolean paused;
    private int tickCount;
    private Gbm demo;

    public GameCanvas() {
        setFullScreenMode(true);
        byte[] f = Res.load("/tdf/TITLE_BM");
        if (f != null) {
            demo = Gbm.loadMerged(f)[0];
        }
    }

    void resume() {
        paused = false;
        if (thread == null) {
            running = true;
            thread = new Thread(this);
            thread.start();
        }
    }

    void pause() {
        paused = true;
    }

    void stop() {
        running = false;
    }

    public void run() {
        while (running) {
            if (!paused) {
                tick();
                repaint();
                serviceRepaints();
            }
            try {
                Thread.sleep(40);
            } catch (InterruptedException e) {
                // ignore
            }
        }
    }

    private void tick() {
        tickCount++;
        short[] px = frame.px;
        for (int i = 0; i < px.length; i++) {
            px[i] = 0;
        }
        if (demo != null) {
            Blit.drawImg(frame, 0, 0, demo.w, demo.h, demo, 0, 0, 0, 0, 0);
        }
    }

    protected void paint(Graphics g) {
        int sw = getWidth();
        int sh = getHeight();
        g.setColor(0);
        g.fillRect(0, 0, sw, sh);
        int ox = (sw - W) / 2;
        int oy = (sh - H) / 2;
        short[] px = frame.px;
        // clip to the device screen ourselves (some MIDP stacks do not clip drawRGB); a screen smaller than 240x320 shows the top-left part
        int sx0 = ox < 0 ? -ox : 0;
        int dx0 = ox < 0 ? 0 : ox;
        int cw = W - sx0;
        if (cw > sw - dx0) {
            cw = sw - dx0;
        }
        for (int y0 = 0; y0 < H; y0 += BAND) {
            int top = oy + y0;
            int skip = top < 0 ? -top : 0;
            int ch = BAND - skip;
            if (ch > sh - (top + skip)) {
                ch = sh - (top + skip);
            }
            if (cw <= 0 || ch <= 0) {
                continue;
            }
            int base = y0 * W;
            for (int i = 0; i < W * BAND; i++) {
                int c = px[base + i] & 0xffff;
                int r = (c >> 11) & 31;
                int gg = (c >> 5) & 63;
                int b = c & 31;
                rgb[i] = 0xff000000 | (((r << 3) | (r >> 2)) << 16) | (((gg << 2) | (gg >> 4)) << 8) | ((b << 3) | (b >> 2));
            }
            g.drawRGB(rgb, skip * W + sx0, W, dx0, top + skip, cw, ch, false);
        }
    }

    protected void keyPressed(int keyCode) {
        // Phase E: key mapping (docs/phaseB_flows.md section 4)
    }

    protected void keyReleased(int keyCode) {
    }
}
