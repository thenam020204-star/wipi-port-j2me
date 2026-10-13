package hl4.midp;

import hl4.core.Host;
import hl4.core.Runtime;
import hl4.lgt.LgtRuntime;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.rms.RecordStore;

/**
 * Full screen canvas that hosts the WIPI-C runtime: presents frames, forwards keys,
 * provides resources/RMS storage/sound to the emulated platform.
 */
public class GameCanvas extends Canvas implements Host, Runnable {
    static final int W = 240, H = 320;

    private final HL4Midlet midlet;
    private Runtime rt;
    private Thread thread;
    private volatile boolean running;
    private final int[] frame = new int[W * H];
    private int[] scaled;
    private int scale = 1;
    private volatile boolean frameReady;
    private String error;
    private final Sound sound = new Sound();

    public GameCanvas(HL4Midlet midlet) {
        this.midlet = midlet;
        setFullScreenMode(true);
    }

    void start() {
        running = true;
        thread = new Thread(this);
        thread.start();
    }

    void pauseGame() {
        if (rt != null) rt.flushStorage();
        if (rt != null) rt.postEvent(Runtime.CTRL_PAUSE, 0, 0);
        sound.pauseAll();
    }

    void resumeGame() {
        if (rt != null) rt.postEvent(Runtime.CTRL_RESUME, 0, 0);
    }

    void stopGame() {
        if (rt != null) rt.flushStorage();
        running = false;
        sound.stopAll();
    }

    // ------------------------------------------------------------------
    // game thread

    static String[] splitLines(String t) {
        java.util.Vector v = new java.util.Vector();
        int p = 0;
        while (p < t.length()) {
            int q = t.indexOf('\n', p);
            if (q < 0) q = t.length();
            v.addElement(t.substring(p, q));
            p = q + 1;
        }
        String[] a = new String[v.size()];
        v.copyInto(a);
        return a;
    }

    public void run() {
        try {
            sound.preload();
            // LGT WIPI-C game: binary.mod + resources in this jar
            if ("LSK".equals(prop("HL5-LSK", "CLR").trim().toUpperCase())) lskCode = -6;
            LgtRuntime lgt = new LgtRuntime(this);
            rt = lgt;
            rt.setFrameLimit(fpsLimit());
            lgt.saveDebug = "1".equals(prop("HL4-SAVEDBG", "0").trim());
            lgt.aid = prop("HL4-AID", "00027BAA");
            lgt.pid = prop("HL4-PID", "PD116132");
            byte[] bin = readResource("/binary.mod");
            if (bin == null) throw new RuntimeException("binary.mod missing");
            lgt.load(bin);
            bin = null;
            // game specific hooks (Vietnamese text /vi/font.bin + /vi/dict.txt, offline patches)
            lgt.game = new hl4.lgt.KroGame(lgt, readResource("/vi/font.bin"), readResource("/vi/dict.txt"));
            try {
                // statically translated hot code (optional, bit-exact with the interpreter)
                Object aot = Class.forName("hl4.aot.LgtAot").newInstance();
                rt.cpu.setAot((hl4.cpu.Aot) aot);
            } catch (Throwable t) {
                System.out.println("AOT unavailable " + t);
            }
            lgt.start();
            int n = 0;
            while (running && !rt.exited) {
                long w = rt.pump();
                if (w != 0) rt.waitWork(w);
                if ((++n & 7) == 0) Thread.yield();
            }
        } catch (Throwable t) {
            error = t.toString();
            System.out.println("HL4 fatal: " + error);
            t.printStackTrace();
            repaint();
        }
        if (rt != null && rt.exited) midlet.quit();
    }

    /** Frame cap (manifest HL4-FPS, default 27, 0 = unlimited). */
    private int fpsLimit() {
        try {
            return Integer.parseInt(prop("HL4-FPS", "27").trim());
        } catch (Exception e) {
            return 27;
        }
    }

    private String prop(String k, String d) {
        String v = midlet.getAppProperty(k);
        return v == null ? d : v;
    }

    // ------------------------------------------------------------------
    // Host

    private String cacheName;
    private byte[] cacheData;

    public byte[] loadResource(String name) {
        synchronized (this) {
            if (name.equals(cacheName)) return cacheData;
        }
        byte[] d = readResource(name.startsWith("/") ? name : "/" + name);
        synchronized (this) {
            cacheName = name;
            cacheData = d;
        }
        return d;
    }

    static byte[] readResource(String path) {
        InputStream in = null;
        try {
            in = GameCanvas.class.getResourceAsStream(path);
            if (in == null) return null;
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int r;
            while ((r = in.read(buf)) > 0) bo.write(buf, 0, r);
            return bo.toByteArray();
        } catch (Exception e) {
            return null;
        } finally {
            try {
                if (in != null) in.close();
            } catch (Exception e) {
            }
        }
    }

    public long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    public void present(int[] argb, int w, int h) {
        synchronized (frame) {
            System.arraycopy(argb, 0, frame, 0, Math.min(frame.length, w * h));
            frameReady = true;
        }
        repaint();
    }

    public void log(String s) {
        System.out.println(s);
    }

    public void exit() {
        running = false;
    }

    /** PNG via the handset decoder (MIDP 2.0 Image.createImage + getRGB keeps the tRNS transparency). */
    public int[] decodeImage(byte[] data, int off, int len, int[] size) {
        try {
            Image im = Image.createImage(data, off, len);
            int w = im.getWidth(), h = im.getHeight();
            int[] argb = new int[w * h];
            im.getRGB(argb, 0, w, 0, 0, w, h);
            im = null;
            size[0] = w;
            size[1] = h;
            return argb;
        } catch (Throwable t) {
            System.out.println("decodeImage: " + t);
            return null;
        }
    }

    public void vibrate(int ms, int level) {
        try {
            javax.microedition.lcdui.Display.getDisplay(midlet).vibrate(ms);
        } catch (Throwable t) {
        }
    }

    static String storeName(String name) {
        StringBuffer sb = new StringBuffer("hl4");
        for (int i = 0; i < name.length() && sb.length() < 32; i++) {
            char c = name.charAt(i);
            if (c == '/' || c == '\\' || c == '.') c = '_';
            sb.append(c);
        }
        return sb.toString();
    }

    // Storage: one RecordStore per game file ("hl4<name>") holding one record, plus a backup copy
    // ("hl4<name>~"). A save deletes the old store completely and writes a fresh one (backup first).
    // Reads fall back to the backup when the
    // main store is missing or unreadable. Writes are verified by reading them back.

    public byte[] storageRead(String name) {
        byte[] d = rmsRead(storeName(name));
        if (d == null || d.length == 0) {
            byte[] b = rmsRead(backupName(name));
            if (b == null || b.length == 0) return d;
            d = b;
            if (d != null) {
                System.out.println("RMS: " + name + " restored from backup");
                if (rt != null && rt.ui != null) rt.ui.note("DOC BAN DU PHONG " + name);
            }
        }
        return d;
    }

    public void storageWrite(String name, byte[] data) {
        // never overwrite in place: the old save is deleted as a whole, then the new one is written
        // into a fresh store. The backup is replaced first, so one complete copy always exists.
        replace(backupName(name), data);
        if (!replace(storeName(name), data) && !replace(storeName(name), data)) {
            System.out.println("RMS write failed " + name);
            if (rt != null && rt.ui != null) rt.ui.note("LOI GHI RMS " + name);
        }
    }

    /** Delete the whole record store, create it again and write data as its only record. */
    private static boolean replace(String store, byte[] data) {
        try {
            RecordStore.deleteRecordStore(store);
        } catch (Exception e) {
            // did not exist
        }
        return rmsWrite(store, data);
    }

    static String backupName(String name) {
        String s = storeName(name);
        return (s.length() >= 32 ? s.substring(0, 31) : s) + "~";
    }

    /** First record of the store (no RecordEnumeration: some emulators loop forever in it). */
    private static byte[] rmsRead(String store) {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(store, false);
            if (rs.getNumRecords() == 0) return new byte[0];
            int last = rs.getNextRecordID();
            for (int id = 1; id < last; id++) {
                try {
                    byte[] d = rs.getRecord(id);
                    return d == null ? new byte[0] : d;
                } catch (Exception e) {
                    // deleted id
                }
            }
            return new byte[0];
        } catch (Exception e) {
            return null;
        } finally {
            close(rs);
        }
    }

    /** Write data as the only record of a (freshly created) store, then read it back to verify. */
    private static boolean rmsWrite(String store, byte[] data) {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(store, true);
            int id = rs.addRecord(data, 0, data.length);
            if (data.length == 0) return true;
            byte[] back = rs.getRecord(id);
            int n = back == null ? 0 : back.length;
            if (n != data.length) return false;
            for (int i = 0; i < n; i++) if (back[i] != data[i]) return false;
            return true;
        } catch (Exception e) {
            System.out.println("RMS " + store + ": " + e);
            return false;
        } finally {
            close(rs);
        }
    }

    public boolean storageDelete(String name) {
        try {
            RecordStore.deleteRecordStore(storeName(name));
            try {
                RecordStore.deleteRecordStore(backupName(name));
            } catch (Exception e) {
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void close(RecordStore rs) {
        try {
            if (rs != null) rs.closeRecordStore();
        } catch (Exception e) {
        }
    }

    public Object soundCreate(byte[] data, int type) {
        return sound.create(data);
    }

    public void soundPlay(Object s, boolean loop) {
        sound.play(s, loop);
    }

    public void soundStop(Object s) {
        sound.stop(s);
    }

    public void soundFree(Object s) {
        sound.free(s);
    }

    public void soundVolume(Object s, int volume) {
        sound.volume(s, volume);
    }

    public boolean soundIsPlaying(Object s) {
        return sound.isPlaying(s);
    }

    // ------------------------------------------------------------------
    // UI

    protected void paint(Graphics g) {
        int cw = getWidth(), ch = getHeight();
        if (error != null) {
            g.setColor(0);
            g.fillRect(0, 0, cw, ch);
            g.setColor(0xFF4040);
            g.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL));
            drawWrapped(g, "Error: " + error, 2, 2, cw - 4);
            return;
        }
        int s = Math.max(1, Math.min(cw / W, ch / H));
        int ox = (cw - W * s) / 2, oy = (ch - H * s) / 2;
        if (ox > 0 || oy > 0) {
            g.setColor(0);
            g.fillRect(0, 0, cw, ch);
        }
        synchronized (frame) {
            if (s == 1) {
                g.drawRGB(frame, 0, W, ox, oy, W, H, false);
            } else {
                int sw = W * s;
                if (scaled == null || scale != s) {
                    scaled = new int[sw * s];
                    scale = s;
                }
                // draw row by row to keep memory low
                for (int y = 0; y < H; y++) {
                    int src = y * W;
                    for (int x = 0, d = 0; x < W; x++) {
                        int p = frame[src + x];
                        for (int k = 0; k < s; k++) scaled[d++] = p;
                    }
                    for (int k = 1; k < s; k++) System.arraycopy(scaled, 0, scaled, k * sw, sw);
                    g.drawRGB(scaled, 0, sw, ox, oy + y * s, sw, s, false);
                }
            }
        }
    }

    private static void drawWrapped(Graphics g, String s, int x, int y, int w) {
        Font f = g.getFont();
        int start = 0;
        while (start < s.length()) {
            int end = start + 1;
            while (end < s.length() && f.substringWidth(s, start, end - start + 1) < w) end++;
            g.drawSubstring(s, start, end - start, x, y, Graphics.TOP | Graphics.LEFT);
            y += f.getHeight();
            start = end;
        }
    }

    /** Left soft key: "CLR" (default: menu/back, like the HL4 port) or "LSK" (original map key), JAD HL5-LSK. */
    private int lskCode = -16;

    /** Map a MIDP key code to the WIPI key code (WIPI: LSK -6, RSK -7, SEND -10, CLR -16). */
    int wipiKey(int k) {
        if (k == -6 || k == -21) return lskCode;
        if (k >= '0' && k <= '9') return k;
        if (k == '*' || k == '#') return k;
        switch (k) {
            case -1: return -1;   // up
            case -2: return -2;   // down
            case -3: return -3;   // left
            case -4: return -4;   // right
            case -5: return -5;   // fire
            case -7: return -7;   // right soft
            case -8: return -16;  // clear
            case -22: return -7;
            case -20: return -5;
            case -10: return -10; // send (quick save in HL5)
            case -11: return -11; // end
            case 8: return -16;   // backspace (emulators)
            case 10: return -5;   // enter (emulators)
            case 27: return -16;  // escape (emulators)
        }
        int ga = 0;
        try {
            ga = getGameAction(k);
        } catch (Exception e) {
        }
        switch (ga) {
            case UP: return -1;
            case DOWN: return -2;
            case LEFT: return -3;
            case RIGHT: return -4;
            case FIRE: return -5;
        }
        return k;
    }

    protected void keyPressed(int k) {
        if (rt != null) rt.postEvent(Runtime.EV_KEY_PRESS, wipiKey(k), 0);
    }

    protected void keyReleased(int k) {
        if (rt != null) rt.postEvent(Runtime.EV_KEY_RELEASE, wipiKey(k), 0);
    }

    protected void keyRepeated(int k) {
        if (rt != null) rt.postEvent(Runtime.EV_KEY_REPEAT, wipiKey(k), 0);
    }

    // Touch (emulators with a touch screen): the soft key labels of the HUD are tappable.
    // Bottom-left "map" = original left soft key (LSK), bottom-right "shop" = right soft key (RSK),
    // top-left corner = CLR (menu / back). Coordinates are converted to the 240x320 game screen.
    private int touchKey;

    private int touchToKey(int px, int py) {
        int cw = getWidth(), ch = getHeight();
        int s = Math.max(1, Math.min(cw / W, ch / H));
        int ox = (cw - W * s) / 2, oy = (ch - H * s) / 2;
        int x = (px - ox) / s, y = (py - oy) / s;
        if (x < 0 || y < 0 || x >= W || y >= H) return 0;
        if (y >= 250 && y < 282 && x < 44) return -6;
        if (y >= 250 && y < 282 && x >= W - 44) return -7;
        if (y < 28 && x < 28) return -16;
        return 0;
    }

    protected void pointerPressed(int x, int y) {
        touchKey = touchToKey(x, y);
        if (rt != null && touchKey != 0) rt.postEvent(Runtime.EV_KEY_PRESS, touchKey, 0);
    }

    protected void pointerReleased(int x, int y) {
        if (rt != null && touchKey != 0) rt.postEvent(Runtime.EV_KEY_RELEASE, touchKey, 0);
        touchKey = 0;
    }

    protected void hideNotify() {
        if (rt != null) rt.flushStorage();
    }

    protected void showNotify() {
    }
}
