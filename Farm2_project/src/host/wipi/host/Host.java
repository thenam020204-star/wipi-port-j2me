package wipi.host;

import java.util.Vector;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.midlet.MIDlet;

import org.kwis.msp.lcdui.Card;
import org.kwis.msp.lcdui.Jlet;

/**
 * The single MIDP canvas that hosts WIPI Cards.
 *
 * Multi-screen: the game lays itself out from Card.getWidth/getHeight, but its
 * world map is fixed at 20x20 tiles of 16px, so the logical screen must stay in
 * [MIN_W..MAX_W] x [MIN_H..MAX_H]. The logical size is picked once at start
 * from the real screen; the logical frame is then shown 1:1, integer-scaled
 * or fit-scaled (nearest neighbour) and centred.
 */
public final class Host extends Canvas implements Runnable {
    public static final int MIN_W = 176, MAX_W = 320, MIN_H = 208, MAX_H = 320;

    /** Logical (game) size. */
    public static int lw = 240, lh = 320;
    /** Real canvas size and placement of the scaled frame. */
    static int sw, sh, ox, oy, dw, dh;
    /** 0 = 1:1, k>1 = integer scale, -1 = fractional fit. */
    static int scale;

    static Host canvas;
    static MIDlet midlet;
    static Display display;
    static Jlet jlet;
    static String jletClass;
    static final Vector cards = new Vector();

    /** Persistent logical back buffer (WIPI screens keep their content). */
    static Image back;
    static org.kwis.msp.lcdui.Image backImg;
    static org.kwis.msp.lcdui.Graphics backG;

    /** Dirty rectangle requested by Card.repaint (logical coords). */
    static int dx1, dy1, dx2, dy2;
    static boolean dirty;
    static final Object lock = new Object();

    static boolean started;
    static boolean booted;

    // fractional scaling tables / buffers
    private static int[] xmap, ymap;
    private static int[] srcBuf = new int[0], dstBuf = new int[0];

    private Host() {
        setFullScreenMode(true);
    }

    // ================================================================ lifecycle

    static void start(MIDlet m, String jletName) {
        if (started) {
            if (jlet != null) {
                jlet.hostResume();
            }
            return;
        }
        started = true;
        midlet = m;
        jletClass = jletName;
        display = Display.getDisplay(m);
        canvas = new Host();
        display.setCurrent(canvas);
        new Thread(canvas).start();
    }

    static void pause() {
        if (jlet != null) {
            jlet.hostPause();
        }
        Store.flush();
    }

    static void destroy() {
        try {
            if (jlet != null) {
                jlet.hostDestroy(true);
            }
        } catch (Throwable t) {
            Log.e("destroy", t);
        }
        Snd.shutdown();
        Store.flush();
    }

    public static void exit() {
        Snd.shutdown();
        Store.flush();
        if (midlet != null) {
            midlet.notifyDestroyed();
        }
    }

    public static String appProperty(String k) {
        return midlet == null ? null : midlet.getAppProperty(k);
    }

    /** Boot thread: wait for a stable full-screen size, then start the game. */
    public void run() {
        int pw = -1, ph = -1, stable = 0;
        for (int i = 0; i < 40 && stable < 3; i++) {
            int w = getWidth(), h = getHeight();
            if (isShown() && w == pw && h == ph) {
                stable++;
            } else {
                stable = 0;
            }
            pw = w;
            ph = h;
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
            }
        }
        layout(getWidth(), getHeight(), true);
        Txt.init();
        Snd.init();
        back = Image.createImage(lw, lh);
        backImg = org.kwis.msp.lcdui.Image.hostWrap(back);
        backG = backImg.getGraphics();
        booted = true;
        try {
            jlet = (Jlet) Class.forName(jletClass).newInstance();
            jlet.hostStart(new String[0]);
        } catch (Throwable t) {
            Log.e("startApp", t);
        }
        requestRepaint(null, 0, 0, lw, lh);
    }

    // ================================================================ layout

    static int prop(String k, int def) {
        String v = appProperty(k);
        if (v == null) {
            return def;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (Exception e) {
            return def;
        }
    }

    /** Chooses the logical size (only when first) and the on-screen placement. */
    static void layout(int w, int h, boolean first) {
        if (w <= 0 || h <= 0) {
            w = 240;
            h = 320;
        }
        sw = w;
        sh = h;
        if (first) {
            String fixed = appProperty("Farm-Screen");
            int fw = 0, fh = 0;
            if (fixed != null && fixed.indexOf('x') > 0) {
                try {
                    fw = Integer.parseInt(fixed.substring(0, fixed.indexOf('x')).trim());
                    fh = Integer.parseInt(fixed.substring(fixed.indexOf('x') + 1).trim());
                } catch (Exception e) {
                    fw = 0;
                }
            }
            if (fw > 0) {
                lw = clamp(fw, MIN_W, MAX_W);
                lh = clamp(fh, MIN_H, MAX_H);
            } else {
                chooseLogical(w, h);
            }
        }
        place();
    }

    static int clamp(int v, int a, int b) {
        return v < a ? a : (v > b ? b : v);
    }

    /** Best logical size for a screen: native when it fits, else integer or fit scale. */
    static void chooseLogical(int w, int h) {
        if (w >= MIN_W && w <= MAX_W && h >= MIN_H && h <= MAX_H) {
            lw = w;
            lh = h;
            return;
        }
        // integer scales: keep the one covering most of the screen
        int bestK = 0, bestCov = 0, bw = 0, bh = 0;
        for (int k = 1; k <= 6; k++) {
            int cw = Math.min(w / k, MAX_W), ch = Math.min(h / k, MAX_H);
            if (cw < MIN_W || ch < MIN_H) {
                continue;
            }
            int cov = (int) ((long) cw * k * ch * k * 1000 / ((long) w * h));
            if (cov > bestCov) {
                bestCov = cov;
                bestK = k;
                bw = cw;
                bh = ch;
            }
        }
        if (bestK > 0 && bestCov >= 860) {
            lw = bw;
            lh = bh;
            return;
        }
        // fractional fit keeping the screen aspect ratio
        if (w > MAX_W || h > MAX_H) {
            // shrink factor f = max(w/MAX_W, h/MAX_H) (x1000)
            int f = Math.max(w * 1000 / MAX_W, h * 1000 / MAX_H);
            lw = clamp(w * 1000 / f, MIN_W, MAX_W);
            lh = clamp(h * 1000 / f, MIN_H, MAX_H);
        } else {
            int f = Math.max(MIN_W * 1000 / w, MIN_H * 1000 / h);
            lw = clamp(w * f / 1000, MIN_W, MAX_W);
            lh = clamp(h * f / 1000, MIN_H, MAX_H);
        }
    }

    /**
     * Placement policy: largest integer scale if it covers >= 86% of the
     * screen (crisp pixels), otherwise a fractional fit. Manifest Farm-Scale:
     * "none" = always 1:1, "int" = integer only, "fit" = always fit.
     */
    static void place() {
        String mode = appProperty("Farm-Scale");
        int k = Math.min(sw / lw, sh / lh);
        if (k >= 1) {
            int cov = (int) ((long) lw * k * lh * k * 1000 / ((long) sw * sh));
            if ("none".equals(mode)) {
                k = 1;
            } else if ("fit".equals(mode)) {
                k = -1;
            } else if (!"int".equals(mode) && cov < 860) {
                k = -1;
            }
        } else {
            k = -1;
        }
        if (k == 1) {
            scale = 0;
            dw = lw;
            dh = lh;
        } else if (k > 1) {
            scale = k;
            dw = lw * k;
            dh = lh * k;
        } else {
            fit();
        }
        ox = (sw - dw) / 2;
        oy = (sh - dh) / 2;
        if (scale < 0) {
            xmap = new int[dw];
            for (int i = 0; i < dw; i++) {
                xmap[i] = i * lw / dw;
            }
            ymap = new int[dh];
            for (int i = 0; i < dh; i++) {
                ymap[i] = i * lh / dh;
            }
        }
    }

    private static void fit() {
        scale = -1;
        if ((long) sw * lh <= (long) sh * lw) {
            dw = sw;
            dh = lh * sw / lw;
        } else {
            dh = sh;
            dw = lw * sh / lh;
        }
        if (dw == lw && dh == lh) {
            scale = 0;
        }
    }

    protected void sizeChanged(int w, int h) {
        if (booted && (w != sw || h != sh)) {
            synchronized (lock) {
                layout(w, h, false);
            }
            repaint();
        }
    }

    // ================================================================ cards

    public static void push(Card c) {
        cards.addElement(c);
        requestRepaint(c, 0, 0, lw, lh);
    }

    public static void pop() {
        if (cards.size() > 0) {
            cards.removeElementAt(cards.size() - 1);
        }
        requestRepaint(null, 0, 0, lw, lh);
    }

    public static void removeAll() {
        cards.removeAllElements();
    }

    public static Card top() {
        int n = cards.size();
        return n == 0 ? null : (Card) cards.elementAt(n - 1);
    }

    public static void requestRepaint(Card c, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) {
            return;
        }
        synchronized (lock) {
            if (!dirty) {
                dx1 = x;
                dy1 = y;
                dx2 = x + w;
                dy2 = y + h;
                dirty = true;
            } else {
                if (x < dx1) {
                    dx1 = x;
                }
                if (y < dy1) {
                    dy1 = y;
                }
                if (x + w > dx2) {
                    dx2 = x + w;
                }
                if (y + h > dy2) {
                    dy2 = y + h;
                }
            }
        }
        if (canvas != null) {
            canvas.repaint();
        }
    }

    public static void flushRepaints() {
        if (canvas != null) {
            canvas.serviceRepaints();
        }
    }

    // ================================================================ paint

    protected void paint(Graphics g) {
        if (!booted) {
            g.setColor(0);
            g.fillRect(0, 0, getWidth(), getHeight());
            return;
        }
        Card c = top();
        boolean d;
        int x1, y1, x2, y2;
        synchronized (lock) {
            d = dirty;
            x1 = dx1;
            y1 = dy1;
            x2 = dx2;
            y2 = dy2;
            dirty = false;
        }
        if (c != null && d) {
            backG.reset(x1, y1, x2 - x1, y2 - y1);
            try {
                c.paint(backG);
            } catch (Throwable t) {
                Log.e("paint", t);
            }
            Overlay.paint(backG);
        }
        present(g);
        Sheet.release();
    }

    private void present(Graphics g) {
        g.setClip(0, 0, sw, sh);
        g.setColor(0);
        if (oy > 0) {
            g.fillRect(0, 0, sw, oy);
            g.fillRect(0, oy + dh, sw, sh - oy - dh);
        }
        if (ox > 0) {
            g.fillRect(0, oy, ox, dh);
            g.fillRect(ox + dw, oy, sw - ox - dw, dh);
        }
        if (scale == 0) {
            g.drawImage(back, ox, oy, Graphics.TOP | Graphics.LEFT);
        } else if (scale > 0) {
            presentInt(g, scale);
        } else {
            presentFit(g);
        }
    }

    private static int[] buf(int[] b, int n) {
        return b.length < n ? new int[n] : b;
    }

    /** Integer nearest-neighbour upscale, 8 logical rows per drawRGB. */
    private void presentInt(Graphics g, int k) {
        final int band = 8;
        int dwid = lw * k;
        srcBuf = buf(srcBuf, lw * band);
        dstBuf = buf(dstBuf, dwid * band * k);
        int[] s = srcBuf, d = dstBuf;
        for (int y0 = 0; y0 < lh; y0 += band) {
            int rows = Math.min(band, lh - y0);
            back.getRGB(s, 0, lw, 0, y0, lw, rows);
            for (int r = 0; r < rows; r++) {
                int so = r * lw;
                int dofs = r * k * dwid;
                int p = dofs;
                for (int x = 0; x < lw; x++) {
                    int px = s[so + x];
                    for (int j = 0; j < k; j++) {
                        d[p++] = px;
                    }
                }
                for (int j = 1; j < k; j++) {
                    System.arraycopy(d, dofs, d, dofs + j * dwid, dwid);
                }
            }
            g.drawRGB(d, 0, dwid, ox, oy + y0 * k, dwid, rows * k, false);
        }
    }

    /** Fractional nearest-neighbour scale, 16 output rows per drawRGB. */
    private void presentFit(Graphics g) {
        final int band = 16;
        int[] xm = xmap, ym = ymap;
        dstBuf = buf(dstBuf, dw * band);
        int[] d = dstBuf;
        for (int y0 = 0; y0 < dh; y0 += band) {
            int rows = Math.min(band, dh - y0);
            int sy0 = ym[y0], sy1 = ym[y0 + rows - 1];
            int srows = sy1 - sy0 + 1;
            srcBuf = buf(srcBuf, lw * srows);
            int[] s = srcBuf;
            back.getRGB(s, 0, lw, 0, sy0, lw, srows);
            for (int r = 0; r < rows; r++) {
                int dofs = r * dw;
                if (r > 0 && ym[y0 + r] == ym[y0 + r - 1]) {
                    System.arraycopy(d, dofs - dw, d, dofs, dw);
                    continue;
                }
                int so = (ym[y0 + r] - sy0) * lw;
                for (int x = 0; x < dw; x++) {
                    d[dofs + x] = s[so + xm[x]];
                }
            }
            g.drawRGB(d, 0, dw, ox, oy + y0, dw, rows, false);
        }
    }

    // ================================================================ input

    private static final int[] held = new int[8];
    private static final int[] heldWipi = new int[8];

    protected void keyPressed(int k) {
        int w = Keys.toWipi(this, k);
        if (w == 0) {
            return;
        }
        for (int i = 0; i < held.length; i++) {
            if (held[i] == 0 || held[i] == k) {
                held[i] = k;
                heldWipi[i] = w;
                break;
            }
        }
        send(1, w);
    }

    protected void keyReleased(int k) {
        int w = 0;
        for (int i = 0; i < held.length; i++) {
            if (held[i] == k) {
                w = heldWipi[i];
                held[i] = 0;
                break;
            }
        }
        if (w == 0) {
            w = Keys.toWipi(this, k);
        }
        if (w != 0) {
            send(2, w);
        }
    }

    protected void keyRepeated(int k) {
        int w = Keys.toWipi(this, k);
        if (w != 0) {
            send(3, w);
        }
    }

    static void send(int type, int key) {
        Card c = top();
        if (c == null || !booted) {
            return;
        }
        try {
            c.hostKey(type, key);
        } catch (Throwable t) {
            Log.e("key", t);
        }
    }

    protected void pointerPressed(int x, int y) {
        Touch.pressed(this, x, y);
    }

    protected void pointerReleased(int x, int y) {
        Touch.released(this, x, y);
    }

    protected void pointerDragged(int x, int y) {
        Touch.dragged(this, x, y);
    }

    protected void showNotify() {
        Card c = top();
        if (c != null && booted) {
            try {
                c.hostShow(true);
            } catch (Throwable t) {
                Log.e("show", t);
            }
            requestRepaint(c, 0, 0, lw, lh);
        }
    }

    protected void hideNotify() {
        for (int i = 0; i < held.length; i++) {
            if (held[i] != 0) {
                held[i] = 0;
                send(2, heldWipi[i]);
            }
        }
        Card c = top();
        if (c != null && booted) {
            try {
                c.hostShow(false);
            } catch (Throwable t) {
                Log.e("hide", t);
            }
        }
    }

    // ================================================================ device

    public static void vibrate(int ms) {
        try {
            if (display != null && Opt.vibrate) {
                display.vibrate(ms);
            }
        } catch (Throwable t) {
        }
    }

    public static void backlight(boolean on) {
        try {
            if (display != null) {
                display.flashBacklight(on ? 0x7fffffff : 0);
            }
        } catch (Throwable t) {
        }
    }

    /** Converts a real-screen point to logical coordinates (or -1). */
    static int toLogicalX(int x) {
        if (x < ox || x >= ox + dw) {
            return -1;
        }
        return (x - ox) * lw / dw;
    }

    static int toLogicalY(int y) {
        if (y < oy || y >= oy + dh) {
            return -1;
        }
        return (y - oy) * lh / dh;
    }
}
