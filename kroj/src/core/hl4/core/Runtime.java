package hl4.core;

import hl4.cpu.Cpu;
import hl4.cpu.CpuException;
import hl4.cpu.Memory;
import hl4.cpu.SvcHandler;
import java.util.Hashtable;
import java.util.Vector;

/**
 * Platform independent part of the WIPI-C emulation: memory layout, host-call stubs,
 * timers, event loop and the WIPI-C kernel services shared by the LGT and KTF front ends.
 */
public abstract class Runtime implements SvcHandler {
    // ---- memory layout ----
    public static final int HEAP_BASE = 0x02000000;
    public static final int HEAP_SIZE = 0x04000000; // 64MB virtual, pages are allocated lazily
    public static final int STACK_TOP = 0x08000000;
    public static final int STACK_SIZE = 0x00100000;
    public static final int STUB_BASE = 0x7F000000;
    public static final int STUB_SIZE = 0x00010000;

    // ---- stub categories (id = category << 16 | index) ----
    public static final int CAT_INTERNAL = 0x7F00;
    public static final int CAT_HLE = 0x7E00;       // host replacements of guest functions

    /** Vietnamese text engine (null for the original build). */
    public TextEngine text;

    /** Host drawn replacement windows (null for the original build). */
    public UiOverlay ui;

    /** Write pending game storage to the host (app pause / exit). */
    public void flushStorage() {
    }

    public final Memory mem = new Memory();
    public final Cpu cpu = new Cpu(mem);
    public final Heap heap = new Heap(mem, HEAP_BASE, HEAP_SIZE);
    public final Host host;
    public String aid = "";
    public String pid = "";
    public int screenWidth = 240, screenHeight = 320;

    private int stubNext = STUB_BASE + 16;
    private final Hashtable stubs = new Hashtable();

    public boolean debug;
    public boolean exited;

    // clet functions
    public int fnStartClet, fnPauseClet, fnResumeClet, fnDestroyClet, fnPaintClet, fnHandleCletEvent;

    protected Runtime(Host host) {
        this.host = host;
        mem.map(STACK_TOP - STACK_SIZE, STACK_SIZE);
        // Low memory is readable on the handsets (NULL dereferences do not fault there).
        mem.map(0, 0x1000);
        mem.map(STUB_BASE, STUB_SIZE);
        cpu.r[13] = STACK_TOP - 16;
        cpu.handler = this;
        int ret = cpu.makeStub(STUB_BASE, Cpu.SVC_RETURN);
        cpu.setReturnStub(ret);
    }

    /** Returns (Thumb) address of the host-call stub for id, creating it on first use. */
    public int stub(int id) {
        Integer key = new Integer(id);
        Integer a = (Integer) stubs.get(key);
        if (a != null) return a.intValue();
        int addr = stubNext;
        stubNext += 8;
        if (stubNext > STUB_BASE + STUB_SIZE) throw new CpuException("out of stub space");
        int s = cpu.makeStub(addr, id);
        stubs.put(key, new Integer(s));
        return s;
    }

    public void log(String s) {
        host.log(s);
    }

    /** Replace the Thumb function at addr by host function idx (see {@link #hle}). */
    public void hookThumb(int addr, int idx) {
        cpu.makeStub(addr & ~1, (CAT_HLE << 16) | idx);
        cpu.aotBlock(addr & ~1, 8);
    }

    /** Host call id for HLE function idx (for patching pointers/literals). */
    public int hleStub(int idx) {
        return stub((CAT_HLE << 16) | idx);
    }

    /** Dispatch of HLE host functions. */
    protected void hle(int idx) {
        switch (idx) {
            case 0: text.measure(); return;
            case 1: text.fit(); return;
            case 2: text.drawNoColor(); return;
            case 3: text.drawColor(); return;
            default:
                throw new CpuException("unknown HLE function " + idx);
        }
    }

    /** Patch a Thumb BL instruction pair at site to call target. */
    public void patchBL(int site, int target) {
        int off = target - (site + 4);
        mem.write16(site, 0xF000 | ((off >> 12) & 0x7FF));
        mem.write16(site + 2, 0xF800 | ((off >> 1) & 0x7FF));
        cpu.aotBlock(site, 4);
    }

    public void debug(String s) {
        if (debug) host.log(s);
    }

    public static String hex(int x) {
        return "0x" + Integer.toHexString(x);
    }

    // ------------------------------------------------------------------
    // Guest memory helpers

    public int alloc(int size) {
        int a = heap.alloc(size);
        if (a == 0) throw new CpuException("guest heap exhausted (" + size + ")");
        return a;
    }

    public int allocStruct(int[] words) {
        int a = alloc(words.length * 4);
        for (int i = 0; i < words.length; i++) mem.write32(a + i * 4, words[i]);
        return a;
    }

    public String readString(int addr) {
        if (addr == 0) return null;
        byte[] b = mem.readCString(addr);
        char[] c = new char[b.length];
        for (int i = 0; i < b.length; i++) c[i] = (char) (b[i] & 0xFF);
        return new String(c);
    }

    public static byte[] ascii(String s) {
        byte[] b = new byte[s.length()];
        for (int i = 0; i < b.length; i++) b[i] = (byte) s.charAt(i);
        return b;
    }

    public void ret(int v) {
        cpu.r[0] = v;
    }

    public void ret64(long v) {
        cpu.r[0] = (int) v;
        cpu.r[1] = (int) (v >>> 32);
    }

    // ------------------------------------------------------------------
    // Timers & events

    static final class Timer {
        int ptr, cb, param;
        int[] args; // host scheduled call (ptr is a unique negative id)
        Runnable run; // host scheduled Java task
        long due;
        long seq;
    }

    private int nextHostTimerId = -1;

    /** Schedule a call to guest function fn(args) after delay ms. */
    public void scheduleCall(int fn, int[] args, long delay) {
        synchronized (lock) {
            Timer t = new Timer();
            t.ptr = nextHostTimerId--;
            t.cb = fn;
            t.args = args;
            t.due = host.currentTimeMillis() + delay;
            t.seq = timerSeq++;
            timers.addElement(t);
            lock.notifyAll();
        }
    }

    /** Schedule a Java task on the game thread after delay ms. */
    public void scheduleHost(Runnable r, long delay) {
        synchronized (lock) {
            Timer t = new Timer();
            t.ptr = nextHostTimerId--;
            t.run = r;
            t.due = host.currentTimeMillis() + delay;
            t.seq = timerSeq++;
            timers.addElement(t);
            lock.notifyAll();
        }
    }

    private final Vector timers = new Vector();
    private long timerSeq;
    private final Vector events = new Vector(); // int[3]
    public volatile boolean repaintRequested;
    private final Object lock = new Object();

    // ---- frame limiter ----
    // The game re-arms its main loop timer with a 1 ms timeout and advances its logic once per
    // callback, so it runs as fast as the CPU allows (and its speed follows the frame rate).
    // A guest timer re-armed with a timeout shorter than the frame interval is delayed so that
    // consecutive callbacks start at least frameUs apart (fixed schedule, no catch-up bursts).
    private long frameUs;                           // 0 = no limit
    private final Hashtable slots = new Hashtable(); // timer ptr -> long[]{last callback start (us)}

    /** Limit guest timer driven frames to fps callbacks per second (0 = unlimited). */
    public void setFrameLimit(int fps) {
        frameUs = fps > 0 ? 1000000L / fps : 0;
    }

    public void setTimer(int ptr, long timeout, int param) {
        int cb = mem.read32(ptr);
        synchronized (lock) {
            removeTimer(ptr);
            Timer t = new Timer();
            t.ptr = ptr;
            t.cb = cb;
            t.param = param;
            long now = host.currentTimeMillis();
            long due = now + Math.max(0, timeout);
            if (frameUs > 0 && timeout * 1000 < frameUs) {
                long[] sl = (long[]) slots.get(new Integer(ptr));
                if (sl != null) {
                    long next = (sl[0] + frameUs + 999) / 1000;
                    if (next > due) due = next;
                }
            }
            t.due = due;
            t.seq = timerSeq++;
            timers.addElement(t);
            lock.notifyAll();
        }
    }

    public void unsetTimer(int ptr) {
        synchronized (lock) {
            removeTimer(ptr);
        }
    }

    private void removeTimer(int ptr) {
        for (int i = timers.size() - 1; i >= 0; i--) {
            if (((Timer) timers.elementAt(i)).ptr == ptr) timers.removeElementAt(i);
        }
    }

    /** Queue a clet event (thread safe). */
    public void postEvent(int type, int p1, int p2) {
        synchronized (lock) {
            events.addElement(new int[]{type, p1, p2});
            lock.notifyAll();
        }
    }

    public void requestRepaint() {
        synchronized (lock) {
            repaintRequested = true;
            lock.notifyAll();
        }
    }

    /**
     * Run pending work: queued events, due timers, repaint. Returns the number of
     * milliseconds until the next timer (or -1 when idle).
     */
    public long pump() {
        int[] ev = null;
        Timer due = null;
        boolean repaint = false;
        long now = host.currentTimeMillis();
        long wait = -1;
        synchronized (lock) {
            if (!events.isEmpty()) {
                ev = (int[]) events.elementAt(0);
                events.removeElementAt(0);
            } else {
                Timer best = null;
                for (int i = 0; i < timers.size(); i++) {
                    Timer t = (Timer) timers.elementAt(i);
                    if (best == null || t.due < best.due || (t.due == best.due && t.seq < best.seq)) best = t;
                }
                if (best != null && best.due <= now) {
                    timers.removeElement(best);
                    due = best;
                } else if (repaintRequested) {
                    repaintRequested = false;
                    repaint = true;
                } else if (best != null) {
                    wait = best.due - now;
                }
            }
        }
        if (ev != null) {
            dispatchEvent(ev[0], ev[1], ev[2]);
            return 0;
        }
        if (due != null) {
            if (frameUs > 0 && due.args == null && due.run == null) {
                Integer key = new Integer(due.ptr);
                long[] sl = (long[]) slots.get(key);
                if (sl == null) slots.put(key, sl = new long[1]);
                long nowUs = now * 1000, planned = sl[0] + frameUs;
                // on (or nearly on) schedule: keep the fractional grid so the average rate is exact
                // despite ms timer granularity; clearly late (slow frame): restart the grid from now
                sl[0] = nowUs - planned >= 0 && nowUs - planned < frameUs / 2 ? planned : nowUs;
            }
            if (due.run != null) due.run.run();
            else if (due.args != null) cpu.call(due.cb, due.args);
            else cpu.call(due.cb, due.ptr, due.param);
            return 0;
        }
        if (repaint) {
            paint();
            return 0;
        }
        return wait;
    }

    /** Block until there is work or timeout (ms, -1 = forever). */
    public void waitWork(long ms) {
        synchronized (lock) {
            if (!events.isEmpty() || repaintRequested) return;
            try {
                if (ms < 0) lock.wait(100);
                else if (ms > 0) lock.wait(ms);
            } catch (InterruptedException e) {
            }
        }
    }

    public boolean hasPendingInput() {
        synchronized (lock) {
            return !events.isEmpty();
        }
    }

    public static final int CTRL_PAUSE = -1000, CTRL_RESUME = -1001, CTRL_DESTROY = -1002;

    protected void dispatchEvent(int type, int p1, int p2) {
        if (type == CTRL_PAUSE) {
            pauseApp();
            return;
        }
        if (type == CTRL_RESUME) {
            resumeApp();
            return;
        }
        if (type == CTRL_DESTROY) {
            destroyApp();
            return;
        }
        if (fnHandleCletEvent != 0) cpu.call(fnHandleCletEvent, new int[]{type, p1, p2});
    }

    // WIPI-C event types (MV_KEY_PRESS_EVENT etc.)
    public static final int EV_KEY_PRESS = 502, EV_KEY_RELEASE = 503, EV_KEY_REPEAT = 504;

    protected void paint() {
        if (fnPaintClet != 0) cpu.call(fnPaintClet, new int[]{0, 0, screenWidth, screenHeight});
    }

    public void pauseApp() {
        if (fnPauseClet != 0) cpu.call(fnPauseClet);
    }

    public void resumeApp() {
        if (fnResumeClet != 0) cpu.call(fnResumeClet);
    }

    public void destroyApp() {
        if (fnDestroyClet != 0) cpu.call(fnDestroyClet);
    }

    // ------------------------------------------------------------------
    // printf family (byte oriented so EUC-KR text passes through untouched)

    public byte[] sprintf(int fmtAddr, int firstArg) {
        byte[] fmt = mem.readCString(fmtAddr);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int argi = firstArg;
        int i = 0;
        while (i < fmt.length) {
            int ch = fmt[i++] & 0xFF;
            if (ch != '%') {
                out.write(ch);
                continue;
            }
            if (i >= fmt.length) {
                out.write('%');
                break;
            }
            boolean left = false, zero = false, plus = false, space = false, alt = false;
            int width = -1, prec = -1, lng = 0;
            // flags
            while (i < fmt.length) {
                int f = fmt[i];
                if (f == '-') left = true;
                else if (f == '0') zero = true;
                else if (f == '+') plus = true;
                else if (f == ' ') space = true;
                else if (f == '#') alt = true;
                else break;
                i++;
            }
            if (i < fmt.length && fmt[i] == '*') {
                width = cpu.arg(argi++);
                i++;
            } else {
                while (i < fmt.length && fmt[i] >= '0' && fmt[i] <= '9') {
                    width = (width < 0 ? 0 : width * 10) + (fmt[i] - '0');
                    i++;
                }
            }
            if (i < fmt.length && fmt[i] == '.') {
                i++;
                prec = 0;
                if (i < fmt.length && fmt[i] == '*') {
                    prec = cpu.arg(argi++);
                    i++;
                } else {
                    while (i < fmt.length && fmt[i] >= '0' && fmt[i] <= '9') {
                        prec = prec * 10 + (fmt[i] - '0');
                        i++;
                    }
                }
            }
            while (i < fmt.length && (fmt[i] == 'l' || fmt[i] == 'h')) {
                if (fmt[i] == 'l') lng++;
                i++;
            }
            if (i >= fmt.length) break;
            int conv = fmt[i++] & 0xFF;
            String body;
            switch (conv) {
                case 'd':
                case 'i': {
                    long val;
                    if (lng >= 2) {
                        int lo = cpu.arg(argi++), hi = cpu.arg(argi++);
                        val = ((long) hi << 32) | (lo & 0xFFFFFFFFL);
                    } else val = cpu.arg(argi++);
                    boolean neg = val < 0;
                    String digits = Long.toString(neg ? -val : val);
                    if (prec >= 0) {
                        while (digits.length() < prec) digits = "0" + digits;
                        if (prec == 0 && val == 0) digits = "";
                    }
                    String sign = neg ? "-" : (plus ? "+" : (space ? " " : ""));
                    body = pad(sign, digits, width, left, zero && prec < 0);
                    break;
                }
                case 'u':
                case 'x':
                case 'X':
                case 'o': {
                    long val;
                    if (lng >= 2) {
                        int lo = cpu.arg(argi++), hi = cpu.arg(argi++);
                        val = ((long) hi << 32) | (lo & 0xFFFFFFFFL);
                    } else val = cpu.arg(argi++) & 0xFFFFFFFFL;
                    String digits;
                    if (conv == 'u') digits = unsignedToString(val, 10);
                    else if (conv == 'o') digits = unsignedToString(val, 8);
                    else digits = unsignedToString(val, 16);
                    if (conv == 'X') digits = digits.toUpperCase();
                    if (prec >= 0) {
                        while (digits.length() < prec) digits = "0" + digits;
                        if (prec == 0 && val == 0) digits = "";
                    }
                    String prefix = "";
                    if (alt && val != 0) prefix = conv == 'o' ? "0" : (conv == 'x' ? "0x" : (conv == 'X' ? "0X" : ""));
                    body = pad(prefix, digits, width, left, zero && prec < 0);
                    break;
                }
                case 'p': {
                    body = pad("0x", Integer.toHexString(cpu.arg(argi++)), width, left, false);
                    break;
                }
                case 'c': {
                    int c = cpu.arg(argi++) & 0xFF;
                    byte[] one = new byte[]{(byte) c};
                    writePadded(out, one, width, left);
                    continue;
                }
                case 's': {
                    int p = cpu.arg(argi++);
                    byte[] s = p == 0 ? ascii("(null)") : mem.readCString(p);
                    if (prec >= 0 && s.length > prec) {
                        byte[] t = new byte[prec];
                        System.arraycopy(s, 0, t, 0, prec);
                        s = t;
                    }
                    writePadded(out, s, width, left);
                    continue;
                }
                case '%':
                    out.write('%');
                    continue;
                default:
                    out.write('%');
                    out.write(conv);
                    continue;
            }
            byte[] bb = ascii(body);
            out.write(bb, 0, bb.length);
        }
        return out.toByteArray();
    }

    private static String unsignedToString(long v, int radix) {
        if (v >= 0) return Long.toString(v, radix);
        // 64-bit unsigned (only for %llu with top bit set)
        long q = (v >>> 1) / radix * 2;
        long r = v - q * radix;
        while (r >= radix) {
            q++;
            r -= radix;
        }
        return Long.toString(q, radix) + Long.toString(r, radix);
    }

    private static String pad(String prefix, String digits, int width, boolean left, boolean zero) {
        int len = prefix.length() + digits.length();
        if (width <= len) return prefix + digits;
        StringBuffer sb = new StringBuffer();
        if (left) {
            sb.append(prefix).append(digits);
            while (sb.length() < width) sb.append(' ');
        } else if (zero) {
            sb.append(prefix);
            for (int k = len; k < width; k++) sb.append('0');
            sb.append(digits);
        } else {
            for (int k = len; k < width; k++) sb.append(' ');
            sb.append(prefix).append(digits);
        }
        return sb.toString();
    }

    private static void writePadded(java.io.ByteArrayOutputStream out, byte[] s, int width, boolean left) {
        int padn = width - s.length;
        if (!left) for (int k = 0; k < padn; k++) out.write(' ');
        out.write(s, 0, s.length);
        if (left) for (int k = 0; k < padn; k++) out.write(' ');
    }

    public static String latin1(byte[] b) {
        char[] c = new char[b.length];
        for (int i = 0; i < b.length; i++) c[i] = (char) (b[i] & 0xFF);
        return new String(c);
    }

    // ------------------------------------------------------------------
    // WIPI-C kernel services (arguments read from CPU registers/stack)

    public void knlPrintk() {
        byte[] s = sprintf(cpu.arg(0), 1);
        host.log("[printk] " + latin1(s));
    }

    public void knlSprintk() {
        int dst = cpu.arg(0);
        byte[] s = sprintf(cpu.arg(1), 2);
        mem.writeCString(dst, s);
        ret(s.length);
    }

    public void knlExit() {
        log("MC_knlExit(" + cpu.arg(0) + ")");
        exited = true;
        host.exit();
    }

    public void knlGetProgramName() {
        int buf = cpu.arg(0), size = cpu.arg(1);
        byte[] b = ascii(aid);
        if (size < b.length + 1) {
            ret(-18);
            return;
        }
        mem.writeCString(buf, b);
        ret(0);
    }

    public void knlAlloc() {
        int size = cpu.arg(0);
        ret(size == 0 ? 0 : heap.alloc(size));
    }

    public void knlCalloc() {
        int size = cpu.arg(0);
        ret(size == 0 ? 0 : heap.calloc(size));
    }

    public void knlFree() {
        int p = cpu.arg(0);
        if (p != 0 && !heap.free(p)) log("MC_knlFree: bad pointer " + hex(p));
        ret(p);
    }

    public int reportedTotalMemory = 0x100000;
    public int reportedFreeMemory = 0x100000;

    public void knlGetTotalMemory() {
        ret(reportedTotalMemory);
    }

    public void knlGetFreeMemory() {
        ret(reportedFreeMemory);
    }

    public void knlDefTimer() {
        int ptr = cpu.arg(0), cb = cpu.arg(1);
        mem.write32(ptr, cb);
    }

    public void knlSetTimer() {
        int ptr = cpu.arg(0);
        long timeout = ((long) cpu.arg(2) << 32) | (cpu.arg(1) & 0xFFFFFFFFL);
        int param = cpu.arg(3);
        debug("MC_knlSetTimer(" + hex(ptr) + ", " + timeout + ", " + hex(param) + ")");
        setTimer(ptr, timeout, param);
    }

    public void knlUnsetTimer() {
        unsetTimer(cpu.arg(0));
    }

    public void knlCurrentTime() {
        ret64(host.currentTimeMillis());
    }

    public void knlGetSystemProperty() {
        String id = readString(cpu.arg(0));
        int out = cpu.arg(1), size = cpu.arg(2);
        String v = systemProperty(id);
        if (v == null) {
            log("unknown system property " + id);
            ret(-9);
            return;
        }
        byte[] b = ascii(v);
        if (b.length + 1 > size) {
            ret(-18);
            return;
        }
        mem.writeCString(out, b);
        ret(0);
    }

    protected String systemProperty(String id) {
        if (id.equals("RSSILEVEL")) return "30";
        if (id.equals("BATTERYLEVEL")) return "100";
        if (id.equals("PHONEMODEL")) return "Emulator";
        if (id.equals("PHONENUMBER")) return "";
        if (id.equals("MIN")) return "01000000000";
        if (id.equals("ANNUN_CALL")) return "0";
        if (id.equals("ANNUN_SMS")) return "0";
        if (id.equals("ANNUN_SILENT")) return "0";
        if (id.equals("ANNUN_ALARM")) return "0";
        if (id.equals("ANNUN_SECURITY")) return "0";
        if (id.equals("CURRENTCH")) return "0";
        if (id.equals("AIRPLANE_MODE")) return "0";
        if (id.equals("ROAMING_AREA")) return "0";
        if (id.equals("DS_LOCK")) return "0";
        if (id.equals("TIMEZONE")) return "GMT+09:00";
        return null;
    }

    public void knlSetSystemProperty() {
        debug("MC_knlSetSystemProperty(" + readString(cpu.arg(0)) + ", " + readString(cpu.arg(1)) + ")");
    }

    /** Map a WIPI resource name to the archive path. */
    protected String resourcePath(String name) {
        return name;
    }

    private final Hashtable resourceCache = new Hashtable();

    protected byte[] resource(String name) {
        byte[] d = host.loadResource(resourcePath(name));
        return d;
    }

    public void knlGetResourceId() {
        String name = readString(cpu.arg(0));
        int psize = cpu.arg(1);
        byte[] data = resource(name);
        debug("MC_knlGetResourceID(" + name + ") -> " + (data == null ? "missing" : "" + data.length));
        if (data == null) {
            if (psize != 0) mem.write32(psize, 0);
            ret(-12);
            return;
        }
        byte[] nb = ascii(name);
        int h = alloc(nb.length + 1);
        mem.writeCString(h, nb);
        if (psize != 0) mem.write32(psize, data.length);
        ret(h);
    }

    public void knlGetResource() {
        int id = cpu.arg(0), buf = cpu.arg(1), size = cpu.arg(2);
        if (id < 0 || id == 0) {
            ret(-9);
            return;
        }
        String name = readString(id);
        byte[] data = resource(name);
        if (data == null) {
            ret(-12);
            return;
        }
        if (data.length > size) {
            ret(-1);
            return;
        }
        mem.writeBytes(buf, data, 0, data.length);
        debug("MC_knlGetResource(" + name + ") -> 0x" + Integer.toHexString(buf) + " len " + data.length);
        if (resHook != null) resHook.loaded(name, buf, data.length);
        ret(0);
    }

    /** Debug hook notified when the application copies a resource into memory. */
    public interface ResHook {
        void loaded(String name, int buf, int len);
    }

    public ResHook resHook;
}
