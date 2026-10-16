package hl4;

import hl4.game.Engine;
import hl4.game.Gfx;
import hl4.gfx.Frame;

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

    private Frame frame;
    private final int[] rgb = new int[W * BAND];
    private Thread thread;
    private volatile boolean running;
    private volatile boolean paused;
    private boolean started;
    private Main midlet;

    public GameCanvas(Main m) {
        midlet = m;
        setFullScreenMode(true);
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
            long t0 = System.currentTimeMillis();
            if (!paused) {
                tick();
                repaint();
                serviceRepaints();
            }
            // the original re-arms its timer 'frameDelay' ms after painting (fps[] table: 30 ms at the default speed); the processing time of
            // the reference device is unknown (UNKNOWN) - 15 ms is assumed, tuned in Phase F
            long wait = Engine.frameDelay + 15 - (System.currentTimeMillis() - t0);
            try {
                Thread.sleep(wait < 1 ? 1 : wait);
            } catch (InterruptedException e) {
                // ignore
            }
            if (Engine.exit) {
                running = false;
                midlet.quit();
            }
        }
    }

    private void tick() {
        if (!started) {
            Engine.startApp();
            frame = Gfx.scr;
            started = true;
        }
        Engine.tick();
    }

    protected void paint(Graphics g) {
        int sw = getWidth();
        int sh = getHeight();
        g.setColor(0);
        g.fillRect(0, 0, sw, sh);
        int ox = (sw - W) / 2;
        int oy = (sh - H) / 2;
        if (frame == null) {
            return;
        }
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

    /** MIDP key code -> the WIPI key code the game logic uses (docs/phaseB_flows.md section 4); 0 = unmapped. */
    private int map(int keyCode) {
        if (keyCode >= KEY_NUM0 && keyCode <= KEY_NUM9) {
            return '0' + (keyCode - KEY_NUM0);
        }
        if (keyCode == KEY_STAR) {
            return '*';
        }
        if (keyCode == KEY_POUND) {
            return '#';
        }
        int a = 0;
        try {
            a = getGameAction(keyCode);
        } catch (IllegalArgumentException e) {
            a = 0;
        }
        switch (a) {
            case UP:
                return -1;
            case DOWN:
                return -2;
            case LEFT:
                return -3;
            case RIGHT:
                return -4;
            case FIRE:
                return -5;
            default:
                break;
        }
        if (keyCode == -6) {
            return -6;
        }
        if (keyCode == -7) {
            return -7;
        }
        if (keyCode == -8) {
            return -16;
        }
        return 0;
    }

    protected void keyPressed(int keyCode) {
        int k = map(keyCode);
        if (k != 0 && started) {
            Engine.onKey(true, k);
        }
    }

    protected void keyReleased(int keyCode) {
        int k = map(keyCode);
        if (k != 0 && started) {
            Engine.onKey(false, k);
        }
    }
}
