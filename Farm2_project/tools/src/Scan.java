import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;

/**
 * J2ME compatibility scanner. Rejects (exit 1) a jar that would fail on a
 * real CLDC 1.1 / MIDP 2.0 (+JSR-135) KVM:
 *   - class file version > 48, StackMapTable present, missing CLDC StackMap
 *   - any class / method / field reference that does not resolve inside the
 *     jar or the CLDC/MIDP API stubs (descriptor-exact, through supers)
 *   - jsr/ret, invokedynamic, float/double ops are fine on CLDC 1.1 but
 *     jsr/ret must be gone after preverify
 *   - methods failing bytecode stack/type verification (ASM BasicVerifier)
 * Warns about: reflection, allocation in paint paths of port code, big
 * constant arrays, direct recursion, threads created in port code.
 *
 * usage: Scan <jar> <report.txt> <api.jar>...
 */
public class Scan {
    static Map<String, ClassNode> jar = new TreeMap<String, ClassNode>();
    /** "Class.method(desc)" -> "|attr|attr|" names of the Code attribute's sub-attributes (raw parse). */
    static Map<String, String> rawCodeAttrs = new HashMap<String, String>();
    static Map<String, ClassNode> api = new HashMap<String, ClassNode>();
    static List<String> errors = new ArrayList<String>();
    static List<String> warns = new ArrayList<String>();
    static Set<String> seenErr = new HashSet<String>();

    static class Raw extends Attribute {
        Raw(String t) {
            super(t);
        }
    }

    public static void main(String[] a) throws Exception {
        load(new File(a[0]), jar);
        for (int i = 2; i < a.length; i++) {
            load(new File(a[i]), api);
        }
        PrintStream out = new PrintStream(new FileOutputStream(a[1]), true, "UTF-8");
        int methods = 0, refs = 0;
        for (ClassNode c : jar.values()) {
            int major = c.version & 0xffff;
            if (major > 48) {
                err(c.name + ": class version " + major + " > 48");
            }
            checkType(c.name, c.superName);
            for (String i : c.interfaces) {
                checkType(c.name, i);
            }
            for (FieldNode f : c.fields) {
                checkDesc(c.name, f.desc);
            }
            for (MethodNode m : c.methods) {
                methods++;
                String where = c.name + "." + m.name + m.desc;
                checkDesc(where, m.desc);
                if (m.instructions.size() == 0) {
                    continue;
                }
                boolean branches = !m.tryCatchBlocks.isEmpty();
                String codeAttrs = rawCodeAttrs.get(c.name + "." + m.name + m.desc);
                if (codeAttrs == null) {
                    codeAttrs = "";
                }
                boolean stackMap = codeAttrs.indexOf("|StackMap|") >= 0;
                boolean smt = codeAttrs.indexOf("|StackMapTable|") >= 0;
                if (smt) {
                    err(where + ": StackMapTable (Java 6 frames) not allowed on KVM");
                }
                int news = 0;
                for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
                    int op = i.getOpcode();
                    if (i instanceof JumpInsnNode || i instanceof TableSwitchInsnNode || i instanceof LookupSwitchInsnNode) {
                        branches = true;
                    }
                    if (op == Opcodes.JSR || op == Opcodes.RET) {
                        err(where + ": jsr/ret left after preverify");
                    }
                    if (op == Opcodes.INVOKEDYNAMIC) {
                        err(where + ": invokedynamic");
                    }
                    if (i instanceof MethodInsnNode) {
                        MethodInsnNode mi = (MethodInsnNode) i;
                        refs++;
                        checkMethod(where, mi.owner, mi.name, mi.desc);
                        checkKind(where, mi);
                        if (mi.owner.equals("java/lang/Class") && (mi.name.equals("forName") || mi.name.equals("newInstance"))) {
                            warn(where + ": reflection " + mi.name + " (CLDC 1.1 supports it; class must not be obfuscated)");
                        }
                        if (mi.owner.equals("java/lang/Thread") && mi.name.equals("<init>") && !isGame(c.name)) {
                            warn(where + ": creates a thread");
                        }
                    } else if (i instanceof FieldInsnNode) {
                        FieldInsnNode fi = (FieldInsnNode) i;
                        refs++;
                        checkField(where, fi.owner, fi.name, fi.desc);
                        FieldNode fn = fieldNode(fi.owner, fi.name, fi.desc, new HashSet<String>());
                        if (fn != null) {
                            boolean st = (fn.access & Opcodes.ACC_STATIC) != 0;
                            boolean wantSt = op == Opcodes.GETSTATIC || op == Opcodes.PUTSTATIC;
                            if (st != wantSt) {
                                err(where + ": " + (wantSt ? "static" : "instance") + " access to "
                                        + (st ? "static" : "instance") + " field " + fi.owner + "." + fi.name);
                            }
                        }
                    } else if (i instanceof TypeInsnNode) {
                        checkType(where, ((TypeInsnNode) i).desc);
                        if (op == Opcodes.NEW) {
                            news++;
                            ClassNode nc = cls(((TypeInsnNode) i).desc);
                            if (nc != null && (nc.access & (Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT)) != 0) {
                                err(where + ": new of abstract/interface " + nc.name);
                            }
                        }
                    } else if (i instanceof MultiANewArrayInsnNode) {
                        checkDesc(where, ((MultiANewArrayInsnNode) i).desc);
                    } else if (i instanceof LdcInsnNode) {
                        Object v = ((LdcInsnNode) i).cst;
                        if (v instanceof Type) {
                            err(where + ": ldc class literal (not CLDC)");
                        }
                    } else if (i instanceof IntInsnNode && op == Opcodes.NEWARRAY) {
                        news++;
                        AbstractInsnNode p = i.getPrevious();
                        if (p instanceof IntInsnNode && ((IntInsnNode) p).operand > 65536) {
                            warn(where + ": large array " + ((IntInsnNode) p).operand);
                        }
                    }
                    if (op == Opcodes.ANEWARRAY) {
                        news++;
                    }
                }
                for (TryCatchBlockNode t : m.tryCatchBlocks) {
                    if (t.type != null) {
                        checkType(where, t.type);
                    }
                }
                if (branches && !stackMap) {
                    err(where + ": branches but no CLDC StackMap (not preverified)");
                }
                if (!isGame(c.name) && news > 0 && (m.name.equals("paint") || m.name.startsWith("present")
                        || m.name.startsWith("draw"))) {
                    warn(where + ": " + news + " allocation(s) in a paint path");
                }
                if (!isGame(c.name) && selfCall(c.name, m)) {
                    warn(where + ": direct recursion");
                }
                try {
                    new Analyzer<org.objectweb.asm.tree.analysis.BasicValue>(new BasicVerifier()).analyze(c.name, m);
                } catch (Exception e) {
                    err(where + ": bytecode verify failed: " + e.getMessage());
                }
            }
        }
        out.println("classes " + jar.size() + ", methods " + methods + ", member refs " + refs);
        for (String e : errors) {
            out.println("ERROR " + e);
        }
        for (String w : warns) {
            out.println("WARN  " + w);
        }
        out.println(errors.isEmpty() ? "RESULT: PASS" : "RESULT: REJECT (" + errors.size() + " errors)");
        out.close();
        System.out.println("scan: classes " + jar.size() + ", refs " + refs + ", errors " + errors.size() + ", warnings "
                + warns.size() + (errors.isEmpty() ? " -> PASS" : " -> REJECT"));
        for (int i = 0; i < errors.size() && i < 30; i++) {
            System.out.println("  ERROR " + errors.get(i));
        }
        System.exit(errors.isEmpty() ? 0 : 1);
    }

    static boolean isGame(String n) {
        return n.indexOf('/') < 0 || n.startsWith("gxg/");
    }

    static boolean selfCall(String owner, MethodNode m) {
        for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
            if (i instanceof MethodInsnNode) {
                MethodInsnNode mi = (MethodInsnNode) i;
                if (mi.owner.equals(owner) && mi.name.equals(m.name) && mi.desc.equals(m.desc)) {
                    return true;
                }
            }
        }
        return false;
    }

    static void load(File f, Map<String, ClassNode> into) throws Exception {
        ZipFile z = new ZipFile(f);
        Enumeration<? extends ZipEntry> en = z.entries();
        while (en.hasMoreElements()) {
            ZipEntry e = en.nextElement();
            if (!e.getName().endsWith(".class")) {
                continue;
            }
            InputStream in = z.getInputStream(e);
            byte[] bytes = in.readAllBytes();
            in.close();
            ClassNode cn = new ClassNode();
            new ClassReader(bytes).accept(cn, 0);
            if (into == jar) {
                rawAttrs(bytes);
            }
            if (!into.containsKey(cn.name)) {
                into.put(cn.name, cn);
            }
        }
        z.close();
    }

    /** Minimal class-file walk collecting Code sub-attribute names per method. */
    static void rawAttrs(byte[] b) {
        java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(b);
        bb.getInt();
        bb.getShort();
        bb.getShort();
        int n = bb.getShort() & 0xffff;
        String[] utf = new String[n];
        int[] cls = new int[n];
        for (int i = 1; i < n; i++) {
            int tag = bb.get() & 0xff;
            switch (tag) {
            case 1: {
                int l = bb.getShort() & 0xffff;
                byte[] s = new byte[l];
                bb.get(s);
                utf[i] = new String(s, java.nio.charset.StandardCharsets.UTF_8);
                break;
            }
            case 7:
                cls[i] = bb.getShort() & 0xffff;
                break;
            case 8: case 16: case 19: case 20:
                bb.getShort();
                break;
            case 15:
                bb.get();
                bb.getShort();
                break;
            case 3: case 4: case 9: case 10: case 11: case 12: case 17: case 18:
                bb.getInt();
                break;
            case 5: case 6:
                bb.getLong();
                i++;
                break;
            default:
                throw new RuntimeException("bad cp tag " + tag);
            }
        }
        bb.getShort();
        String name = utf[cls[bb.getShort() & 0xffff]];
        bb.getShort();
        int ni = bb.getShort() & 0xffff;
        for (int i = 0; i < ni; i++) {
            bb.getShort();
        }
        for (int pass = 0; pass < 2; pass++) {
            int cnt = bb.getShort() & 0xffff;
            for (int i = 0; i < cnt; i++) {
                bb.getShort();
                String mn = utf[bb.getShort() & 0xffff];
                String md = utf[bb.getShort() & 0xffff];
                int ac = bb.getShort() & 0xffff;
                StringBuilder names = new StringBuilder("|");
                for (int k = 0; k < ac; k++) {
                    String an = utf[bb.getShort() & 0xffff];
                    int len = bb.getInt();
                    int end = bb.position() + len;
                    if (pass == 1 && an.equals("Code")) {
                        bb.getShort();
                        bb.getShort();
                        int cl = bb.getInt();
                        bb.position(bb.position() + cl);
                        int et = bb.getShort() & 0xffff;
                        bb.position(bb.position() + et * 8);
                        int sc = bb.getShort() & 0xffff;
                        for (int q = 0; q < sc; q++) {
                            names.append(utf[bb.getShort() & 0xffff]).append('|');
                            int sl = bb.getInt();
                            bb.position(bb.position() + sl);
                        }
                    }
                    bb.position(end);
                }
                if (pass == 1) {
                    rawCodeAttrs.put(name + "." + mn + md, names.toString());
                }
            }
        }
    }

    static void err(String s) {
        if (seenErr.add(s)) {
            errors.add(s);
        }
    }

    static void warn(String s) {
        warns.add(s);
    }

    static ClassNode cls(String n) {
        ClassNode c = jar.get(n);
        return c != null ? c : api.get(n);
    }

    static void checkType(String where, String t) {
        if (t == null) {
            return;
        }
        if (t.startsWith("[")) {
            checkDesc(where, t);
            return;
        }
        if (cls(t) == null) {
            err(where + ": missing class " + t);
        }
    }

    static void checkDesc(String where, String d) {
        Type t = Type.getType(d);
        if (t.getSort() == Type.METHOD) {
            checkDesc(where, t.getReturnType().getDescriptor());
            for (Type p : t.getArgumentTypes()) {
                checkDesc(where, p.getDescriptor());
            }
            return;
        }
        while (t.getSort() == Type.ARRAY) {
            t = t.getElementType();
        }
        if (t.getSort() == Type.OBJECT) {
            checkType(where, t.getInternalName());
        }
        if (t.getSort() == Type.FLOAT || t.getSort() == Type.DOUBLE) {
            // fine on CLDC 1.1
        }
    }

    static void checkMethod(String where, String owner, String name, String desc) {
        checkDesc(where, desc);
        if (owner.startsWith("[")) {
            return; // array clone etc.: only Object methods
        }
        if (cls(owner) == null) {
            err(where + ": missing class " + owner);
            return;
        }
        if (!findMethod(owner, name, desc, new HashSet<String>())) {
            err(where + ": missing method " + owner + "." + name + desc);
        }
    }

    /** invokeinterface must target an interface, other invokes a class; static-ness must match. */
    static void checkKind(String where, MethodInsnNode mi) {
        if (mi.owner.startsWith("[")) {
            return;
        }
        ClassNode c = cls(mi.owner);
        if (c == null) {
            return;
        }
        boolean itf = (c.access & Opcodes.ACC_INTERFACE) != 0;
        int op = mi.getOpcode();
        if (op == Opcodes.INVOKEINTERFACE && !itf) {
            err(where + ": invokeinterface on class " + mi.owner + " (IncompatibleClassChangeError)");
        }
        if ((op == Opcodes.INVOKEVIRTUAL || op == Opcodes.INVOKESPECIAL) && itf) {
            err(where + ": invokevirtual/special on interface " + mi.owner + " (IncompatibleClassChangeError)");
        }
        MethodNode m = methodNode(mi.owner, mi.name, mi.desc, new HashSet<String>());
        if (m != null) {
            boolean st = (m.access & Opcodes.ACC_STATIC) != 0;
            if (st != (op == Opcodes.INVOKESTATIC)) {
                err(where + ": static mismatch calling " + mi.owner + "." + mi.name + mi.desc);
            }
        }
    }

    static MethodNode methodNode(String owner, String name, String desc, Set<String> seen) {
        if (owner == null || !seen.add(owner)) {
            return null;
        }
        ClassNode c = cls(owner);
        if (c == null) {
            return null;
        }
        for (MethodNode m : c.methods) {
            if (m.name.equals(name) && m.desc.equals(desc)) {
                return m;
            }
        }
        MethodNode r = methodNode(c.superName, name, desc, seen);
        if (r != null) {
            return r;
        }
        for (String i : c.interfaces) {
            r = methodNode(i, name, desc, seen);
            if (r != null) {
                return r;
            }
        }
        return null;
    }

    static FieldNode fieldNode(String owner, String name, String desc, Set<String> seen) {
        if (owner == null || !seen.add(owner)) {
            return null;
        }
        ClassNode c = cls(owner);
        if (c == null) {
            return null;
        }
        for (FieldNode f : c.fields) {
            if (f.name.equals(name) && f.desc.equals(desc)) {
                return f;
            }
        }
        FieldNode r = fieldNode(c.superName, name, desc, seen);
        if (r != null) {
            return r;
        }
        for (String i : c.interfaces) {
            r = fieldNode(i, name, desc, seen);
            if (r != null) {
                return r;
            }
        }
        return null;
    }

    static boolean findMethod(String owner, String name, String desc, Set<String> seen) {
        if (owner == null || !seen.add(owner)) {
            return false;
        }
        ClassNode c = cls(owner);
        if (c == null) {
            return false;
        }
        for (MethodNode m : c.methods) {
            if (m.name.equals(name) && m.desc.equals(desc)) {
                return true;
            }
        }
        if (findMethod(c.superName, name, desc, seen)) {
            return true;
        }
        for (String i : c.interfaces) {
            if (findMethod(i, name, desc, seen)) {
                return true;
            }
        }
        return false;
    }

    static void checkField(String where, String owner, String name, String desc) {
        checkDesc(where, desc);
        if (cls(owner) == null) {
            err(where + ": missing class " + owner);
            return;
        }
        if (!findField(owner, name, desc, new HashSet<String>())) {
            err(where + ": missing field " + owner + "." + name + ":" + desc);
        }
    }

    static boolean findField(String owner, String name, String desc, Set<String> seen) {
        if (owner == null || !seen.add(owner)) {
            return false;
        }
        ClassNode c = cls(owner);
        if (c == null) {
            return false;
        }
        for (FieldNode f : c.fields) {
            if (f.name.equals(name) && f.desc.equals(desc)) {
                return true;
            }
        }
        if (findField(c.superName, name, desc, seen)) {
            return true;
        }
        for (String i : c.interfaces) {
            if (findField(i, name, desc, seen)) {
                return true;
            }
        }
        return false;
    }
}
