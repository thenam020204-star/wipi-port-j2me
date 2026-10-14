import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;

/**
 * Binary diff of the original WIPI jar against the port jar.
 * Classes: member signatures + per-method instruction listing (line numbers,
 * frames and StackMap ignored) -> identical / changed with the changed lines.
 * Resources: added / removed / changed (SHA-1).
 *
 * usage: BinDiff <orig.jar> <new.jar> <report.txt>
 */
public class BinDiff {
    public static void main(String[] a) throws Exception {
        Map<String, byte[]> o = read(a[0]), n = read(a[1]);
        PrintStream out = new PrintStream(new FileOutputStream(a[2]), true, "UTF-8");
        int same = 0, changed = 0, added = 0, removed = 0, rsame = 0, rchg = 0;
        List<String> summary = new ArrayList<String>();
        for (String k : o.keySet()) {
            if (k.startsWith("META-INF/")) {
                continue;
            }
            byte[] nb = n.get(k);
            if (nb == null) {
                out.println("REMOVED " + k);
                removed++;
                continue;
            }
            if (k.endsWith(".class")) {
                String d = diffClass(o.get(k), nb);
                if (d.length() == 0) {
                    same++;
                } else {
                    changed++;
                    out.println("CLASS " + k + "\n" + d);
                    summary.add(k);
                }
            } else if (sha(o.get(k)).equals(sha(nb))) {
                rsame++;
            } else {
                rchg++;
                out.println("RESOURCE CHANGED " + k + " " + o.get(k).length + " -> " + nb.length);
            }
        }
        for (String k : n.keySet()) {
            if (!o.containsKey(k) && !k.startsWith("META-INF/")) {
                out.println("ADDED " + k + " (" + n.get(k).length + " B)");
                added++;
            }
        }
        String s = "classes: " + same + " identical code, " + changed + " changed " + summary + "; resources: " + rsame
                + " identical, " + rchg + " changed; entries added " + added + ", removed " + removed;
        out.println("SUMMARY " + s);
        out.close();
        System.out.println("bindiff: " + s);
    }

    static Map<String, byte[]> read(String f) throws Exception {
        Map<String, byte[]> m = new TreeMap<String, byte[]>();
        ZipFile z = new ZipFile(f);
        Enumeration<? extends ZipEntry> en = z.entries();
        while (en.hasMoreElements()) {
            ZipEntry e = en.nextElement();
            if (e.isDirectory()) {
                continue;
            }
            InputStream in = z.getInputStream(e);
            m.put(e.getName(), in.readAllBytes());
            in.close();
        }
        z.close();
        return m;
    }

    static String sha(byte[] b) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-1").digest(b);
        StringBuilder s = new StringBuilder();
        for (byte x : d) {
            s.append(Integer.toHexString((x & 0xff) | 0x100).substring(1));
        }
        return s.toString();
    }

    static String listing(MethodNode m) {
        Textifier t = new Textifier();
        TraceMethodVisitor v = new TraceMethodVisitor(t);
        m.instructions.accept(v);
        for (int i = 0; i < m.tryCatchBlocks.size(); i++) {
            m.tryCatchBlocks.get(i).accept(v);
        }
        StringWriter w = new StringWriter();
        t.print(new PrintWriter(w));
        StringBuilder s = new StringBuilder();
        for (String line : w.toString().split("\n")) {
            String l = line.trim();
            if (l.startsWith("LINENUMBER") || l.startsWith("FRAME") || l.length() == 0) {
                continue;
            }
            s.append(l).append('\n');
        }
        return s.toString();
    }

    static String diffClass(byte[] ob, byte[] nb) {
        ClassNode o = new ClassNode(), n = new ClassNode();
        new ClassReader(ob).accept(o, 0);
        new ClassReader(nb).accept(n, 0);
        StringBuilder d = new StringBuilder();
        if (!eq(o.superName, n.superName) || !o.interfaces.equals(n.interfaces) || o.access != n.access) {
            d.append("  header changed\n");
        }
        Map<String, String> of = new TreeMap<String, String>(), nf = new TreeMap<String, String>();
        for (FieldNode f : o.fields) {
            of.put(f.name + ":" + f.desc, "" + f.access);
        }
        for (FieldNode f : n.fields) {
            nf.put(f.name + ":" + f.desc, "" + f.access);
        }
        if (!of.equals(nf)) {
            d.append("  fields changed\n");
        }
        Map<String, MethodNode> om = new TreeMap<String, MethodNode>(), nm = new TreeMap<String, MethodNode>();
        for (MethodNode m : o.methods) {
            om.put(m.name + m.desc, m);
        }
        for (MethodNode m : n.methods) {
            nm.put(m.name + m.desc, m);
        }
        for (String k : om.keySet()) {
            MethodNode b = nm.get(k);
            if (b == null) {
                d.append("  method removed " + k + "\n");
                continue;
            }
            if (om.get(k).access != b.access) {
                d.append("  access changed " + k + "\n");
            }
            String lo = listing(om.get(k)), ln = listing(b);
            if (!lo.equals(ln)) {
                d.append("  method " + k + ":\n");
                String[] x = lo.split("\n"), y = ln.split("\n");
                int shown = 0;
                if (x.length == y.length) {
                    for (int i = 0; i < x.length && shown < 12; i++) {
                        if (!x[i].equals(y[i])) {
                            d.append("    - " + x[i] + "\n    + " + y[i] + "\n");
                            shown++;
                        }
                    }
                } else {
                    d.append("    instructions " + x.length + " -> " + y.length
                            + (lo.contains("JSR") ? " (jsr/ret subroutines inlined by preverify)" : "") + "\n");
                }
            }
        }
        for (String k : nm.keySet()) {
            if (!om.containsKey(k)) {
                d.append("  method added " + k + "\n");
            }
        }
        return d.toString();
    }

    static boolean eq(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }
}
