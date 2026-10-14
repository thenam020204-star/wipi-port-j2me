import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;

/**
 * Platform-boundary remap of the original game classes. Only call sites into
 * platform APIs are redirected; every redirect consumes and produces exactly
 * the same operand stack, so no game logic, local, branch or descriptor of a
 * game member changes.
 *
 *  1. Class.getResourceAsStream(String)       -> wipi/host/Res.open(Class,String)
 *     (fully buffered stream: exact available(), complete read())
 *  2. System.getProperty(String)              -> wipi/host/Sys.getProperty(String)
 *     (SKT handset properties used to build the player id)
 *  3. javax.microedition.io.Connector.open(S) -> wipi/host/Net.open(String)
 *     (offline GXG gateway)
 *  3b. new String(byte[]) / new String(byte[],int,int) -> wipi/host/Enc.str(...)
 *     (WIPI decodes bytes as KSC5601/CP949; J2ME phones have no Korean codec).
 *     The NEW/DUP pair is removed and invokespecial <init> becomes invokestatic:
 *     same operand stack before and after.
 *  3c. at the call sites that read game TEXT (TEXT_SITES) the redirect goes to
 *     wipi/host/Enc.txt instead, which serves the string from /text/*.txt
 *     (tools/textx.py); scripts read by the same sites miss the index and
 *     decode as before.
 *  3d. with -Dui=<ui.txt>: every displayed string literal (non-ASCII, plus the
 *     ASCII ones in UI_ASCII, all checked to be display-only) becomes
 *     ldc "lit"; sipush id; invokestatic wipi/host/Str.lit(String,int) and is
 *     written to ui.txt as "id=text" (same text -> same id, so literal
 *     comparisons such as DownMent.equals("...") still hold after editing).
 *  4. class rename com/skt/m/* -> wipi/skt/* (emulators such as FreeJ2ME ship
 *     their own com.skt.m classes, which would shadow the port's audio layer)
 *  5. extra hooks from a hook list file (UI layer), see Hooks.txt format:
 *     owner.name.desc -> newOwner.newName.newDesc (static, receiver first)
 *  6. with -Dbody=<list>: drawing methods whose BODY is replaced by a call to
 *     the new UI layer (default-package Ui* classes, which read the same
 *     package-private game fields). Line: "Class.name(desc) -> UiClass.name";
 *     the new body is: aload this, load every argument, invokestatic
 *     UiClass.name(LClass;args)ret, return. Descriptor, access flags and
 *     callers of the game method are unchanged. Only methods whose logic was
 *     copied into the Ui method are listed (see tools/ui_body.txt).
 *
 * usage: Remap <inDir> <outDir> <report.txt> [hooks.txt]
 */
public class Remap {
    static class Rule {
        String owner, name, desc;
        int op;
        String nOwner, nName, nDesc;
        int count;
        String where; // optional: only inside this class.method

        Rule(int op, String o, String n, String d, String no, String nn, String nd) {
            this.op = op;
            owner = o;
            name = n;
            desc = d;
            nOwner = no;
            nName = nn;
            nDesc = nd;
        }
    }

    static List<Rule> rules = new ArrayList<Rule>();

    /** Methods whose new String(byte[]..) read game text (see tools/textx.py for the formats). */
    static final java.util.Set<String> TEXT_SITES = new java.util.HashSet<String>(java.util.Arrays.asList(
            "GameCanvas.LoadExplanDat()V", "MapDat.LoadCropData()V", "MapDat.LoadItemName(I)V",
            "MapDat.LoadMap(I)V", "MapDat.LoadPopup(III)V", "MapDat.LoadQuest(I)V", "MapDat.LoadSkill()V",
            "NPC.LoadAnimation(I)V", "NPC.LoadTalk(IILjava/lang/String;)V", "NPC.NPCSetup(I)V",
            "Pet.CheckVersion()V", "Pet.LoadSkillExplan()V"));

    /** ASCII literals that are only ever drawn (contexts reviewed: drawString/TalkSet/append-for-display). */
    static final java.util.Set<String> UI_ASCII = new java.util.HashSet<String>(java.util.Arrays.asList(
            "PM / Program", "Bhang mo-gun", "Main Design", "Lee mo-yang", "Sub / Interface Design", "Lee mo-gun",
            "Scenario", "GowArts", "JanSoRi", "GowArts, Sa Jang-nim", "Q/A", "Com2us Q/A Team", "Special Thanks",
            "You", "Press Any Key", "OK", "THE END", "Lv.", " Lv.", "???"));

    static java.util.LinkedHashMap<String, Integer> lits = new java.util.LinkedHashMap<String, Integer>();
    static java.util.Map<Integer, String> litWhere = new java.util.HashMap<Integer, String>();
    static int litSites, txtSites;

    public static void main(String[] a) throws Exception {
        rules.add(new Rule(Opcodes.INVOKEVIRTUAL, "java/lang/Class", "getResourceAsStream",
                "(Ljava/lang/String;)Ljava/io/InputStream;", "wipi/host/Res", "open",
                "(Ljava/lang/Class;Ljava/lang/String;)Ljava/io/InputStream;"));
        rules.add(new Rule(Opcodes.INVOKESTATIC, "java/lang/System", "getProperty",
                "(Ljava/lang/String;)Ljava/lang/String;", "wipi/host/Sys", "getProperty",
                "(Ljava/lang/String;)Ljava/lang/String;"));
        rules.add(new Rule(Opcodes.INVOKESTATIC, "javax/microedition/io/Connector", "open",
                "(Ljava/lang/String;)Ljavax/microedition/io/Connection;", "wipi/host/Net", "open",
                "(Ljava/lang/String;)Ljavax/microedition/io/Connection;"));
        if (a.length > 3) {
            loadHooks(new File(a[3]));
        }
        if (System.getProperty("body") != null) {
            for (String line : Files.readAllLines(new File(System.getProperty("body")).toPath())) {
                line = line.trim();
                if (line.length() > 0 && !line.startsWith("#")) {
                    String[] p = line.split("\\s*->\\s*");
                    bodies.put(p[0], p[1]);
                }
            }
        }
        File in = new File(a[0]), out = new File(a[1]);
        PrintStream rep = new PrintStream(new FileOutputStream(a[2]), true, "UTF-8");
        List<File> files = new ArrayList<File>();
        collect(in, files);
        for (File f : files) {
            String rel = in.toPath().relativize(f.toPath()).toString();
            byte[] b = Files.readAllBytes(f.toPath());
            ClassNode cn = new ClassNode();
            new ClassReader(b).accept(cn, 0);
            int changed = 0;
            for (MethodNode m : cn.methods) {
                changed += body(cn, m, rep);
            }
            for (MethodNode m : cn.methods) {
                changed += stringCtors(cn, m, rep);
            }
            if (System.getProperty("ui") != null && !cn.name.startsWith("gxg/")) {
                for (MethodNode m : cn.methods) {
                    changed += literals(cn, m);
                }
            }
            for (MethodNode m : cn.methods) {
                for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
                    if (!(i instanceof MethodInsnNode)) {
                        continue;
                    }
                    MethodInsnNode mi = (MethodInsnNode) i;
                    for (Rule r : rules) {
                        if (mi.getOpcode() == r.op && mi.owner.equals(r.owner) && mi.name.equals(r.name)
                                && mi.desc.equals(r.desc)
                                && (r.where == null || r.where.equals(cn.name + "." + m.name + m.desc))) {
                            rep.println(cn.name + "." + m.name + m.desc + ": " + r.owner + "." + r.name + " -> "
                                    + r.nOwner + "." + r.nName + r.nDesc);
                            mi.setOpcode(Opcodes.INVOKESTATIC);
                            mi.owner = r.nOwner;
                            mi.name = r.nName;
                            mi.desc = r.nDesc;
                            mi.itf = false;
                            r.count++;
                            changed++;
                            break;
                        }
                    }
                }
            }
            File o = new File(out, rel);
            o.getParentFile().mkdirs();
            boolean ren = new String(b, "ISO-8859-1").indexOf("com/skt/m/") >= 0;
            if (changed == 0 && !ren) {
                Files.write(o.toPath(), b); // untouched: byte-identical copy
            } else {
                ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS); // literal hooks need +1 stack; no frames for v45
                if (ren) {
                    java.util.Map<String, String> map = new java.util.HashMap<String, String>();
                    for (String k : new String[] { "AudioClip", "AudioSystem", "Device" }) {
                        map.put("com/skt/m/" + k, "wipi/skt/" + k);
                    }
                    cn.accept(new ClassRemapper(cw, new SimpleRemapper(map)));
                    rep.println(cn.name + ": com/skt/m/* -> wipi/skt/*");
                } else {
                    cn.accept(cw);
                }
                Files.write(o.toPath(), cw.toByteArray());
            }
        }
        for (Rule r : rules) {
            rep.println("rule " + r.owner + "." + r.name + (r.where != null ? " @" + r.where : "") + ": " + r.count);
            System.out.println("remap " + r.owner + "." + r.name + " -> " + r.nOwner + "." + r.nName + ": " + r.count);
        }
        for (String k : bodies.keySet()) {
            if (!bodyDone.contains(k)) {
                throw new RuntimeException("body replacement target not found: " + k);
            }
        }
        rep.println("rule body -> Ui: " + bodyDone.size());
        System.out.println("remap method bodies -> Ui layer: " + bodyDone.size());
        rep.println("rule new String(byte[]..) -> Enc.str: " + (strCount - txtSites) + ", -> Enc.txt: " + txtSites);
        System.out.println("remap new String(byte[]..) -> wipi/host/Enc.str: " + (strCount - txtSites)
                + ", -> wipi/host/Enc.txt (text sites): " + txtSites);
        if (System.getProperty("ui") != null) {
            writeUi(new File(System.getProperty("ui")));
            rep.println("rule literal -> Str.lit: " + litSites + " sites, " + lits.size() + " texts");
            System.out.println("remap literals -> wipi/host/Str.lit: " + litSites + " sites, " + lits.size()
                    + " texts -> " + System.getProperty("ui"));
        }
        rep.close();
    }

    static int strCount;
    static java.util.Map<String, String> bodies = new java.util.LinkedHashMap<String, String>();
    static java.util.Set<String> bodyDone = new java.util.HashSet<String>();

    static int body(ClassNode cn, MethodNode m, PrintStream rep) {
        String key = cn.name + "." + m.name + m.desc;
        String to = bodies.get(key);
        if (to == null) {
            return 0;
        }
        if ((m.access & (Opcodes.ACC_STATIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) {
            throw new RuntimeException("body replacement needs an instance method with code: " + key);
        }
        int dot = to.lastIndexOf('.');
        String owner = to.substring(0, dot), name = to.substring(dot + 1);
        org.objectweb.asm.Type mt = org.objectweb.asm.Type.getMethodType(m.desc);
        String desc = "(L" + cn.name + ";" + m.desc.substring(1);
        org.objectweb.asm.tree.InsnList l = new org.objectweb.asm.tree.InsnList();
        l.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
        int slot = 1;
        for (org.objectweb.asm.Type t : mt.getArgumentTypes()) {
            l.add(new org.objectweb.asm.tree.VarInsnNode(t.getOpcode(Opcodes.ILOAD), slot));
            slot += t.getSize();
        }
        l.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner, name, desc, false));
        l.add(new org.objectweb.asm.tree.InsnNode(mt.getReturnType().getOpcode(Opcodes.IRETURN)));
        int old = m.instructions.size();
        m.instructions.clear();
        m.instructions.add(l);
        m.tryCatchBlocks.clear();
        m.localVariables = null;
        m.maxLocals = slot;
        bodyDone.add(key);
        rep.println(key + ": body (" + old + " insns) -> " + owner + "." + name + desc);
        return 1;
    }

    /** Rewrites byte[] String constructors; NEW/DUP matched LIFO (javac emits NEW;DUP adjacent). */
    static int stringCtors(ClassNode cn, MethodNode m, PrintStream rep) {
        java.util.ArrayList<AbstractInsnNode> news = new java.util.ArrayList<AbstractInsnNode>();
        int n = 0;
        for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
            if (i.getOpcode() == Opcodes.NEW && ((org.objectweb.asm.tree.TypeInsnNode) i).desc.equals("java/lang/String")) {
                news.add(i);
            } else if (i.getOpcode() == Opcodes.INVOKESPECIAL && ((MethodInsnNode) i).owner.equals("java/lang/String")
                    && ((MethodInsnNode) i).name.equals("<init>")) {
                if (news.isEmpty()) {
                    throw new RuntimeException("unmatched String.<init> in " + cn.name + "." + m.name);
                }
                AbstractInsnNode nw = news.remove(news.size() - 1);
                MethodInsnNode mi = (MethodInsnNode) i;
                String nd = mi.desc.equals("([B)V") ? "([B)Ljava/lang/String;"
                        : mi.desc.equals("([BII)V") ? "([BII)Ljava/lang/String;" : null;
                if (nd == null) {
                    continue;
                }
                AbstractInsnNode dup = nw.getNext();
                if (dup.getOpcode() != Opcodes.DUP) {
                    throw new RuntimeException("NEW String not followed by DUP in " + cn.name + "." + m.name);
                }
                m.instructions.remove(nw);
                m.instructions.remove(dup);
                mi.setOpcode(Opcodes.INVOKESTATIC);
                mi.owner = "wipi/host/Enc";
                boolean text = TEXT_SITES.contains(cn.name + "." + m.name + m.desc);
                mi.name = text ? "txt" : "str";
                if (text) {
                    txtSites++;
                }
                mi.desc = nd;
                rep.println(cn.name + "." + m.name + m.desc + ": new String" + nd.substring(0, nd.indexOf(')') + 1)
                        + " -> wipi/host/Enc." + mi.name);
                n++;
                strCount++;
            }
        }
        if (!news.isEmpty()) {
            throw new RuntimeException("unmatched NEW String in " + cn.name + "." + m.name);
        }
        return n;
    }

    static boolean display(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) > 0x7f) {
                return true;
            }
        }
        return UI_ASCII.contains(s);
    }

    static int literals(ClassNode cn, MethodNode m) {
        int n = 0;
        for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
            if (!(i instanceof org.objectweb.asm.tree.LdcInsnNode)) {
                continue;
            }
            Object c = ((org.objectweb.asm.tree.LdcInsnNode) i).cst;
            if (!(c instanceof String) || !display((String) c)) {
                continue;
            }
            Integer id = lits.get(c);
            if (id == null) {
                id = lits.size();
                lits.put((String) c, id);
                litWhere.put(id, cn.name + "." + m.name);
            }
            if (id > 32767) {
                throw new RuntimeException("too many literals");
            }
            org.objectweb.asm.tree.InsnList l = new org.objectweb.asm.tree.InsnList();
            l.add(new org.objectweb.asm.tree.IntInsnNode(Opcodes.SIPUSH, id));
            l.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "wipi/host/Str", "lit",
                    "(Ljava/lang/String;I)Ljava/lang/String;", false));
            AbstractInsnNode next = i.getNext();
            m.instructions.insert(i, l);
            i = next.getPrevious();
            n++;
            litSites++;
        }
        return n;
    }

    static void writeUi(File f) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("# UI strings of the game code (string literals), one per line: id=text\n");
        sb.append("# \\n = new line; /C0../C4 colour codes must stay. A deleted line shows the original.\n");
        String last = null;
        for (java.util.Map.Entry<String, Integer> e : lits.entrySet()) {
            String w = litWhere.get(e.getValue());
            if (!w.equals(last)) {
                sb.append("# ").append(w).append('\n');
                last = w;
            }
            sb.append(e.getValue()).append('=').append(e.getKey().replace("\\", "\\\\").replace("\n", "\\n")
                    .replace("\r", "\\r")).append('\n');
        }
        f.getParentFile().mkdirs();
        Files.write(f.toPath(), sb.toString().getBytes("UTF-8"));
    }

    static void collect(File d, List<File> out) {
        File[] fs = d.listFiles();
        java.util.Arrays.sort(fs);
        for (File f : fs) {
            if (f.isDirectory()) {
                collect(f, out);
            } else if (f.getName().endsWith(".class")) {
                out.add(f);
            }
        }
    }

    /**
     * Hook line: [@Class.method(desc)] op owner.name(desc) -> newOwner.newName(newDesc)
     * op = virtual|static|special ; '#' starts a comment.
     */
    static void loadHooks(File f) throws Exception {
        for (String line : Files.readAllLines(f.toPath())) {
            line = line.trim();
            if (line.length() == 0 || line.startsWith("#")) {
                continue;
            }
            String where = null;
            if (line.startsWith("@")) {
                int sp = line.indexOf(' ');
                where = line.substring(1, sp); // Class.method(desc), internal names
                line = line.substring(sp + 1).trim();
            }
            String[] p = line.split("\\s+");
            int op = p[0].equals("static") ? Opcodes.INVOKESTATIC
                    : p[0].equals("special") ? Opcodes.INVOKESPECIAL : Opcodes.INVOKEVIRTUAL;
            String[] src = split(p[1]), dst = split(p[3]);
            Rule r = new Rule(op, src[0], src[1], src[2], dst[0], dst[1], dst[2]);
            r.where = where;
            rules.add(r);
        }
    }

    static String[] split(String s) {
        int par = s.indexOf('(');
        int dot = s.lastIndexOf('.', par);
        return new String[] { s.substring(0, dot), s.substring(dot + 1, par), s.substring(par) };
    }
}
