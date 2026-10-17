package hl4.test;

import hl4.game.G;
import hl4.game.Obj;
import hl4.game.Pool;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.PrintStream;
import java.util.Hashtable;
import java.util.zip.CRC32;

/**
 * Java side of the call recorder (T0.1, docs/PLAN_NEXT.md): same line format as tools/e_calls.py.  Test only: the calls into this class
 * exist only in the instrumented copy of the sources that tools/jcr.sh compiles (tools/callrec_inst.py wraps the methods whose javadoc
 * names the chosen original functions); the release build never references it.
 * Enabled by -Dcallrec.out=<file> -Dcallrec.ref=<reference calls file> (masks and object families are read from its header).
 */
public final class CallRec {
    private CallRec() {
    }

    /** current game tick (set by GameTraceTest before each tick); calls are recorded from callrec.from on */
    public static int tick;
    static int from = Integer.parseInt(System.getProperty("callrec.from", "0"));
    static PrintStream out;
    static final Hashtable masks = new Hashtable();
    static final Hashtable family = new Hashtable();
    static final Object[][] args = new Object[64][];
    static final String[] names = new String[64];
    static final int[] ticks = new int[64];
    static int depth;
    /** keys whose masked bytes are written after each call ('D key hex' lines, see tools/e_calls.py CALLDUMP) */
    static final String dump = "," + System.getProperty("callrec.dump", "") + ",";

    static {
        String o = System.getProperty("callrec.out");
        if (o != null) {
            try {
                out = new PrintStream(new java.io.FileOutputStream(o));
                BufferedReader r = new BufferedReader(new FileReader(System.getProperty("callrec.ref")));
                String ln;
                while ((ln = r.readLine()) != null && ln.startsWith("#")) {
                    String[] p = ln.split(" ");
                    if (p[1].equals("mask")) {
                        masks.put(p[2], p[3]);
                    } else if (p[1].equals("family")) {
                        family.put(new Integer(Integer.parseInt(p[2])), p[3]);
                    }
                    if (!p[1].equals("scenario")) {
                        out.println(ln);
                    }
                }
                r.close();
            } catch (Exception ex) {
                throw new RuntimeException("CallRec: " + ex);
            }
        }
    }

    public static String v(int x) {
        return Integer.toString(x);
    }

    public static String v(boolean x) {
        return x ? "1" : "0";
    }

    public static String v(long x) {
        return Long.toString(x);
    }

    public static String v(Object x) {
        if (x == null) {
            return "0";
        }
        if (x instanceof Obj) {
            Obj o = (Obj) x;
            return "o" + (o.m[Obj.TYPE] & 0xff) + "." + hl4.sys.Bytes.u16(o.m, Obj.IDX);
        }
        return "ptr";
    }

    /** a recorded call starts: args = the Java values in original argument order (pool objects as Obj) */
    public static void enter(String name, Object[] a) {
        if (out == null) {
            return;
        }
        names[depth] = name;
        args[depth] = a;
        ticks[depth] = tick;
        depth++;
    }

    public static void exit(int r) {
        done(Integer.toString(r));
    }

    public static void exit(boolean r) {
        done(r ? "1" : "0");
    }

    public static void exit(long r) {
        done(Long.toString(r));
    }

    public static void exit(Object r) {
        done(v(r));
    }

    public static void exitVoid() {
        done("-");
    }

    /** the call ended with an exception: the line is written with ret EXC so callcmp shows where */
    public static void fail(Throwable ex) {
        done("EXC");
    }

    static void done(String ret) {
        if (out == null) {
            return;
        }
        depth--;
        if (ticks[depth] < from) {
            return;
        }
        Object[] a = args[depth];
        StringBuffer sb = new StringBuffer();
        sb.append("C ").append(ticks[depth]).append(' ').append(depth).append(' ').append(names[depth]).append(' ');
        for (int i = 0; i < a.length; i++) {
            sb.append(i == 0 ? "" : ",").append(a[i] instanceof String ? (String) a[i] : v(a[i]));
        }
        if (a.length == 0) {
            sb.append('-');
        }
        sb.append(' ').append(ret);
        sb.append(" g=").append(hash(G.game, "_game")).append(" e=").append(hash(G.event, "_event"));
        sb.append(" h=").append(hash(Pool.hero.m, "hero"));
        Obj[] mon = Pool.pools[Pool.MONSTER];
        CRC32 c = new CRC32();
        for (int i = 0; i < mon.length; i++) {
            String hx = hash(mon[i].m, "monster");
            for (int k = 0; k < 8; k += 2) {
                c.update(Integer.parseInt(hx.substring(k, k + 2), 16));
            }
        }
        sb.append(" m=").append(hex8((int) c.getValue()));
        String sep = " o=";
        for (int i = 0; i < a.length; i++) {
            if (a[i] instanceof Obj) {
                Obj o = (Obj) a[i];
                String f = (String) family.get(new Integer(o.m[Obj.TYPE] & 0xff));
                if (f == null) {
                    continue;                 // the reference hashes only the pool families it knows
                }
                sb.append(sep).append(v(o)).append(':').append(hash(o.m, f));
                sep = ",";
            }
        }
        out.println(sb.toString());
        if (dump.indexOf(",h,") >= 0) {
            out.println("D h " + hexAll(masked(Pool.hero.m, "hero")));
        }
        if (dump.indexOf(",g,") >= 0) {
            out.println("D g " + hexAll(masked(G.game, "_game")));
        }
        if (dump.indexOf(",e,") >= 0) {
            out.println("D e " + hexAll(masked(G.event, "_event")));
        }
        if (dump.indexOf(",o,") >= 0) {
            for (int i = 0; i < a.length; i++) {
                if (a[i] instanceof Obj && family.get(new Integer(((Obj) a[i]).m[Obj.TYPE] & 0xff)) != null) {
                    Obj o = (Obj) a[i];
                    out.println("D o " + hexAll(masked(o.m, (String) family.get(new Integer(o.m[Obj.TYPE] & 0xff)))));
                    break;
                }
            }
        }
    }

    static String hexAll(byte[] b) {
        StringBuffer sb = new StringBuffer();
        String hx = "0123456789abcdef";
        for (int i = 0; i < b.length; i++) {
            sb.append(hx.charAt((b[i] >> 4) & 15)).append(hx.charAt(b[i] & 15));
        }
        return sb.toString();
    }

    static String hash(byte[] b, String name) {
        byte[] t = masked(b, name);
        CRC32 c = new CRC32();
        c.update(t, 0, t.length);
        return hex8((int) c.getValue());
    }

    static byte[] masked(byte[] b, String name) {
        byte[] t = new byte[b.length];
        System.arraycopy(b, 0, t, 0, b.length);
        String m = name == null ? null : (String) masks.get(name);
        if (m == null) {
            m = "0-8,16-20";              // common object header pointers (prev, next, self)
        }
        if (!m.equals("-")) {
            String[] rs = m.split(",");
            for (int i = 0; i < rs.length; i++) {
                int d = rs[i].indexOf('-');
                int lo = Integer.parseInt(rs[i].substring(0, d));
                int hi = Math.min(Integer.parseInt(rs[i].substring(d + 1)), t.length);
                for (int k = lo; k < hi; k++) {
                    t[k] = 0;
                }
            }
        }
        return t;
    }

    static String hex8(int v) {
        String s = Integer.toHexString(v);
        return "00000000".substring(s.length()) + s;
    }
}
