import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.StringItem;
import javax.microedition.midlet.MIDlet;

/**
 * S60 capability probe for the static-recompilation port (CLDC 1.0/1.1 + MIDP 2.0 only, no floating point).
 * Measures: platform properties, heap, largest array, small-object capacity, integer/array/call speed and whether
 * the VM can load classes whose single method is 4..60 KB of bytecode (generated classes Big4 ... Big60).
 */
public class Probe extends MIDlet implements CommandListener, Runnable {
    private final Form form = new Form("S60 probe");
    private final Command exit = new Command("Exit", Command.EXIT, 1);
    private boolean started;

    private static int sink;

    public Probe() {
        form.addCommand(exit);
        form.setCommandListener(this);
    }

    public void startApp() {
        Display.getDisplay(this).setCurrent(form);
        if (!started) {
            started = true;
            new Thread(this).start();
        }
    }

    public void pauseApp() {
    }

    public void destroyApp(boolean unconditional) {
    }

    public void commandAction(Command c, Displayable d) {
        if (c == exit) {
            destroyApp(true);
            notifyDestroyed();
        }
    }

    private void line(String s) {
        form.append(new StringItem(null, s + "\n"));
    }

    private static String prop(String k) {
        String v = System.getProperty(k);
        return v == null ? "-" : v;
    }

    public void run() {
        line("conf " + prop("microedition.configuration"));
        line("prof " + prop("microedition.profiles"));
        line("plat " + prop("microedition.platform"));
        Runtime rt = Runtime.getRuntime();
        System.gc();
        line("heap total " + rt.totalMemory() + " free " + rt.freeMemory());
        line("max int[] " + maxIntArray() + " elems");
        line("max byte[] " + maxByteArray() + " bytes");
        line("small objs " + objectCapacity());
        speed();
        bigClasses();
        System.gc();
        line("heap free after " + rt.freeMemory());
        line("DONE");
    }

    private static int maxIntArray() {
        int lo = 0, hi = 1;
        try {
            while (hi < (1 << 24)) {
                int[] a = new int[hi];
                sink += a.length;
                a = null;
                lo = hi;
                hi <<= 1;
            }
        } catch (Throwable t) {
        }
        System.gc();
        while (hi - lo > 1024) {
            int mid = (lo + hi) >>> 1;
            try {
                int[] a = new int[mid];
                sink += a.length;
                a = null;
                lo = mid;
            } catch (Throwable t) {
                hi = mid;
            }
        }
        System.gc();
        return lo;
    }

    private static int maxByteArray() {
        int lo = 0, hi = 1;
        try {
            while (hi < (1 << 26)) {
                byte[] a = new byte[hi];
                sink += a.length;
                a = null;
                lo = hi;
                hi <<= 1;
            }
        } catch (Throwable t) {
        }
        System.gc();
        while (hi - lo > 4096) {
            int mid = (lo + hi) >>> 1;
            try {
                byte[] a = new byte[mid];
                sink += a.length;
                a = null;
                lo = mid;
            } catch (Throwable t) {
                hi = mid;
            }
        }
        System.gc();
        return lo;
    }

    private static final class Node {
        Node next;
        int v;
    }

    private static int objectCapacity() {
        Node head = null;
        int n = 0;
        try {
            while (n < 1000000) {
                Node x = new Node();
                x.next = head;
                head = x;
                n++;
            }
        } catch (Throwable t) {
        }
        head = null;
        System.gc();
        return n;
    }

    private static int mix(int a, int b) {
        return (a * 31) ^ (b + (a >>> 3));
    }

    private void speed() {
        long t0 = System.currentTimeMillis();
        int s = 1;
        for (int i = 0; i < 2000000; i++) {
            s = (s * 3) ^ (i + (s >>> 5));
        }
        sink += s;
        line("int loop 2M: " + (System.currentTimeMillis() - t0) + " ms");
        int[] arr = new int[4096];
        t0 = System.currentTimeMillis();
        for (int k = 0; k < 500; k++) {
            for (int i = 0; i < arr.length; i++) {
                arr[i] += i ^ k;
            }
        }
        sink += arr[7];
        line("int[] 2M acc: " + (System.currentTimeMillis() - t0) + " ms");
        t0 = System.currentTimeMillis();
        s = 0;
        for (int i = 0; i < 1000000; i++) {
            s = mix(s, i);
        }
        sink += s;
        line("static call 1M: " + (System.currentTimeMillis() - t0) + " ms");
        t0 = System.currentTimeMillis();
        Object[] objs = new Object[256];
        for (int i = 0; i < 300000; i++) {
            objs[i & 255] = new int[4];
        }
        line("alloc 300k int[4]: " + (System.currentTimeMillis() - t0) + " ms");
    }

    private void bigClasses() {
        int[] kb = {4, 16, 32, 48, 60};
        for (int i = 0; i < kb.length; i++) {
            String name = "Big" + kb[i];
            long t0 = System.currentTimeMillis();
            try {
                Runnable r = (Runnable) Class.forName(name).newInstance();
                r.run();
                line(name + " ok " + (System.currentTimeMillis() - t0) + " ms");
            } catch (Throwable t) {
                line(name + " FAIL " + t);
            }
        }
    }
}
