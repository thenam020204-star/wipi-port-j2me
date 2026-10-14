import hl4.core.Host;
import hl4.core.Runtime;
import hl4.lgt.LgtRuntime;

import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import javax.imageio.ImageIO;

/**
 * Headless Java SE runner: loads an original archive (LGT zip), runs the game with a
 * virtual clock, injects scripted keys and dumps frames as PNG.
 *
 * usage: RunHeadless <archive.zip> <total_ms> <dump_every_ms> <outdir> [keys "ms:KEY,..."]
 */
public class RunHeadless implements Host {
    Map<String, byte[]> files = new HashMap<>();
    long vtime = System.getenv("HL4_VTIME") != null ? Long.parseLong(System.getenv("HL4_VTIME")) : 1262000000000L;
    long vbase;
    static long ipms = Long.parseLong(System.getProperty("ipms", "100000"));
    Runtime rt;
    int[] lastFrame;
    int fw, fh;
    long frameSeq, savedSeq;
    File storageDir;
    boolean verbose = System.getenv("HL4_VERBOSE") != null;

    public byte[] loadResource(String name) {
        return files.get(name);
    }

    public long currentTimeMillis() {
        return vtime + rt.cpu.icount / ipms;
    }

    public void present(int[] argb, int w, int h) {
        if (lastFrame == null || lastFrame.length != argb.length) lastFrame = new int[argb.length];
        System.arraycopy(argb, 0, lastFrame, 0, argb.length);
        fw = w;
        fh = h;
        frameSeq++;
        if (HEAPLOG && frameSeq % 500 == 0) System.err.println("[heap] t=" + (currentTimeMillis() - vbase) + " used=" + rt.heap.used + " peak=" + rt.heap.peak);
        if (FPS) {
            long now = currentTimeMillis() - vbase;
            fpsFrames++;
            if (now - fpsT >= 1000) {
                long ic = rt.cpu.icount;
                System.err.println("[fps] t=" + now + " frames=" + fpsFrames + " instr/frame=" + ((ic - fpsIc) / fpsFrames) + " busy=" + (int) (100 * (ic - fpsIc) / ipms / Math.max(1, now - fpsT)) + "%" + " realms=" + (System.nanoTime() - fpsReal) / 1000000);
                fpsT = now; fpsIc = ic; fpsFrames = 0; fpsReal = System.nanoTime();
            }
        }
    }
    static final boolean FPS = System.getenv("HL4_FPS") != null;
    static final boolean HEAPLOG = System.getenv("HL4_HEAPLOG") != null;
    long fpsT, fpsIc, fpsReal; int fpsFrames;

    public void log(String s) {
        System.err.println("[" + (currentTimeMillis() - vbase) + "] " + s);
    }

    public int[] decodeImage(byte[] data, int off, int len, int[] size) {
        try {
            BufferedImage im = ImageIO.read(new ByteArrayInputStream(data, off, len));
            if (im == null) return null;
            size[0] = im.getWidth();
            size[1] = im.getHeight();
            return im.getRGB(0, 0, size[0], size[1], null, 0, size[0]);
        } catch (IOException e) {
            return null;
        }
    }

    public void exit() {
        log("EXIT");
    }

    public void vibrate(int ms, int level) {
    }

    public byte[] storageRead(String name) {
        File f = new File(storageDir, sanitize(name));
        if (!f.exists()) return null;
        try {
            return java.nio.file.Files.readAllBytes(f.toPath());
        } catch (IOException e) {
            return null;
        }
    }

    public void storageWrite(String name, byte[] data) {
        try {
            java.nio.file.Files.write(new File(storageDir, sanitize(name)).toPath(), data);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean storageDelete(String name) {
        return new File(storageDir, sanitize(name)).delete();
    }

    static String sanitize(String n) {
        return n.replace('/', '_').replace('\\', '_');
    }

    static final boolean SNDLOG = System.getenv("HL4_SNDLOG") != null;

    public Object soundCreate(byte[] data, int type) {
        String n;
        if (data.length >= 8 && data[0] == 'H' && data[1] == 'L' && data[2] == '4' && data[6] == 0) n = new String(data, 7, data.length - 7).trim();
        else {
            int h = java.util.Arrays.hashCode(data);
            n = "raw" + data.length + "#" + Integer.toHexString(h) + (data.length >= 4 ? "(" + new String(data, 0, 4) + ")" : "");
        }
        if (SNDLOG) log("snd create " + n);
        return new String(n);
    }

    public void soundPlay(Object s, boolean loop) {
        if (SNDLOG) log("snd play " + s + (loop ? " loop" : ""));
    }

    public void soundStop(Object s) {
        if (SNDLOG) log("snd stop " + s);
    }

    public void soundFree(Object s) {
        if (SNDLOG) log("snd free " + s);
    }

    public void soundVolume(Object s, int v) {
    }

    public boolean soundIsPlaying(Object s) {
        return false;
    }

    static Map<String, byte[]> unzip(byte[] data) throws IOException {
        Map<String, byte[]> m = new HashMap<>();
        ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(data));
        ZipEntry e;
        while ((e = zis.getNextEntry()) != null) {
            if (e.isDirectory()) continue;
            m.put(e.getName(), zis.readAllBytes());
        }
        return m;
    }

    void savePng(String path) throws IOException {
        BufferedImage img = new BufferedImage(fw, fh, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, fw, fh, lastFrame, 0, fw);
        ImageIO.write(img, "png", new File(path));
    }

    static int keyCode(String k) {
        switch (k) {
            case "UP": return -1;
            case "DOWN": return -2;
            case "LEFT": return -3;
            case "RIGHT": return -4;
            case "OK": return -5;
            case "LSK": return -6;
            case "RSK": return -7;
            case "CLR": return -16;
            case "#": return 35;
            case "*": return 42;
            default:
                if (k.length() > 1 && k.charAt(0) == 'k') return Integer.parseInt(k.substring(1));
                return k.charAt(0);
        }
    }

    static java.io.PrintWriter callLog;

    /** Overlay file of the Vietnamese build (HL4_VI dir), or null. */
    static byte[] viFile(String name) throws IOException {
        if (System.getenv("HL4_VI") == null) return null;
        File f = new File(System.getenv("HL4_VI"), name);
        return f.exists() ? java.nio.file.Files.readAllBytes(f.toPath()) : null;
    }
    static java.util.Set<Integer> callFilter = System.getenv("HL4_CALLS_FILTER") == null ? null : java.util.Arrays.stream(System.getenv("HL4_CALLS_FILTER").split(",")).map(x -> Integer.parseInt(x, 16)).collect(java.util.stream.Collectors.toSet());
    Runnable tick;
    static int callStk = Integer.parseInt(System.getenv().getOrDefault("HL4_CALLS_STK", "0"));
    static int veneerLo = Integer.parseInt(System.getenv().getOrDefault("HL4_VENEER", "759d4"), 16);

    public static void main(String[] args) throws Exception {
        RunHeadless h = new RunHeadless();
        byte[] zip = java.nio.file.Files.readAllBytes(new File(args[0]).toPath());
        long total = Long.parseLong(args[1]);
        long dumpEvery = Long.parseLong(args[2]);
        String out = args[3];
        new File(out).mkdirs();
        h.storageDir = new File(System.getProperty("storage", out + "/storage"));
        h.storageDir.mkdirs();
        List<long[]> keys = new ArrayList<>();
        List<long[]> seq = new ArrayList<>();
        Map<Integer, String> seqNames = new HashMap<>();
        if (args.length > 4 && args[4].startsWith("monkey:")) {
            java.util.Random rnd = new java.util.Random(Long.parseLong(args[4].substring(7)));
            String[] pool = {"UP","DOWN","LEFT","RIGHT","OK","OK","OK","UP","DOWN","LEFT","RIGHT","1","2","3","4","5","6","7","8","9","0","#","*","LSK","RSK","CLR","OK","5","5"};
            long t = 3000;
            while (t < total) {
                keys.add(new long[]{t, keyCode(pool[rnd.nextInt(pool.length)]), 120});
                t += 150 + rnd.nextInt(600);
            }
        } else if (args.length > 4 && args[4].startsWith("seq:")) {
            for (String st : args[4].substring(4).split(";")) {
                st = st.trim();
                if (st.isEmpty()) continue;
                if (st.startsWith("u:")) { // u:KEY.period.x.y.rrggbb[.tol[.timeout]]  press KEY every period until pixel matches
                    String[] q = st.substring(2).split("\\.");
                    seq.add(new long[]{4, keyCode(q[0]), Long.parseLong(q[1]), Long.parseLong(q[2]), Long.parseLong(q[3]), Long.parseLong(q[4], 16), q.length > 5 ? Long.parseLong(q[5]) : 40, q.length > 6 ? Long.parseLong(q[6]) : 120000, 0});
                } else if (st.startsWith("w:")) { // w:x.y.rrggbb[.tol[.timeout]]
                    String[] q = st.substring(2).split("\\.");
                    seq.add(new long[]{1, Long.parseLong(q[0]), Long.parseLong(q[1]), Long.parseLong(q[2], 16), q.length > 3 ? Long.parseLong(q[3]) : 40, q.length > 4 ? Long.parseLong(q[4]) : 60000});
                } else if (st.startsWith("s:")) {
                    seqNames.put(seq.size(), st.substring(2));
                    seq.add(new long[]{2});
                } else if (st.indexOf(':') < 0) {
                    seq.add(new long[]{3, Long.parseLong(st)});
                } else {
                    String[] q = st.split(":");
                    String k = q[1];
                    int rep = 1;
                    if (k.indexOf('*') > 0) { rep = Integer.parseInt(k.substring(k.indexOf('*') + 1)); k = k.substring(0, k.indexOf('*')); }
                    for (int r = 0; r < rep; r++) seq.add(new long[]{0, Long.parseLong(q[0]), keyCode(k), q.length > 2 ? Long.parseLong(q[2]) : 120});
                }
            }
        } else if (args.length > 4 && !args[4].isEmpty()) {
            for (String s : args[4].split(",")) {
                String[] p = s.split(":");
                keys.add(new long[]{Long.parseLong(p[0]), keyCode(p[1]), p.length > 2 ? Long.parseLong(p[2]) : 120});
            }
        }
        Map<String, byte[]> outer = unzip(zip);
        String kind = outer.containsKey("app_info") ? "LGT" : "KTF";
        for (Map.Entry<String, byte[]> e : outer.entrySet()) {
            if (e.getKey().endsWith(".jar")) h.files.putAll(unzip(e.getValue()));
        }
        if (System.getenv("HL4_RES") != null && !System.getenv("HL4_RES").isEmpty()) {
            java.nio.file.Path base = new File(System.getenv("HL4_RES")).toPath();
            java.nio.file.Files.walk(base).filter(java.nio.file.Files::isRegularFile).forEach(f -> {
                try { h.files.put(base.relativize(f).toString(), java.nio.file.Files.readAllBytes(f)); } catch (IOException e) { throw new UncheckedIOException(e); }
            });
        }
        Runtime rt0 = new LgtRuntime(h);
        h.rt = rt0;
        final Runtime rt = rt0;
        rt.debug = System.getenv("HL4_DEBUG") != null;
        ((LgtRuntime) rt).saveDebug = System.getenv("HL4_SAVEDBG") != null;
        hl4.core.TextEngine.DEBUG = System.getenv("HL4_TEDEBUG") != null;
        boolean[] traceSvc = null;
        if (System.getenv("HL4_TRACE") != null) {
            traceSvc = new boolean[0x800];
            for (String t : System.getenv("HL4_TRACE").split(",")) traceSvc[Integer.parseInt(t, 16)] = true;
        }
        final Map<String, int[]> watchHits = new java.util.TreeMap<>();
        if (System.getenv("HL4_RESLOG") != null) {
            final String[] last = {""};
            rt.resHook = (name, buf, len) -> { if (!name.equals(last[0])) { last[0] = name; h.log("res " + name + " @" + Integer.toHexString(buf) + " " + len); } };
        }
        if (System.getenv("HL4_WATCH") != null) {
            final java.util.Set<String> names = new java.util.HashSet<>(java.util.Arrays.asList(System.getenv("HL4_WATCH").split(",")));
            final long wfrom = Long.parseLong(System.getenv().getOrDefault("HL4_WATCH_FROM", "0"));
            final java.util.List<int[]> ranges = new ArrayList<>();
            final java.util.List<String> rnames = new ArrayList<>();
            rt.resHook = (name, buf, len) -> {
                for (int i = ranges.size() - 1; i >= 0; i--) {
                    int[] r = ranges.get(i);
                    if (buf < r[0] + r[1] && r[0] < buf + len) { ranges.remove(i); rnames.remove(i); }
                }
                if (!names.contains(name) && !names.contains("*")) return;
                h.log("watch " + name + " @0x" + Integer.toHexString(buf) + " len " + len);
                ranges.add(new int[]{buf, len});
                rnames.add(name);
                rt.mem.watchRange(buf, len);
            };
            rt.mem.watch = (addr, write) -> {
                if (h.currentTimeMillis() - h.vbase < wfrom) return;
                for (int i = 0; i < ranges.size(); i++) {
                    int[] r = ranges.get(i);
                    if (addr - r[0] >= 0 && addr - r[0] < r[1]) {
                        String k = rnames.get(i) + (write ? " W" : " R") + " pc=" + Integer.toHexString(rt.cpu.curPc) + " lr=" + Integer.toHexString(rt.cpu.r[14]);
                        int[] c = watchHits.get(k);
                        if (c == null) watchHits.put(k, c = new int[]{0, addr - r[0], (int) (h.currentTimeMillis() - h.vbase)});
                        c[0]++;
                    }
                }
            };
        }
        if (System.getenv("HL4_WATCHADDR") != null) {
            String[] w = System.getenv("HL4_WATCHADDR").split(",");
            final int wa = (int) Long.parseLong(w[0], 16), wl = Integer.parseInt(w[1], 16);
            final long wt = Long.parseLong(w[2]);
            final boolean[] armed = {false};
            h.tick = () -> {
                if (!armed[0] && h.currentTimeMillis() - h.vbase >= wt) { armed[0] = true; rt.mem.map(wa, wl); rt.mem.watchRange(wa, wl); h.log("watchaddr armed"); }
            };
            rt.mem.watch = (addr, write) -> {
                if (addr - wa >= 0 && addr - wa < wl) {
                    String k = "ADDR" + (write ? " W" : " R") + " pc=" + Integer.toHexString(rt.cpu.curPc) + " lr=" + Integer.toHexString(rt.cpu.r[14]);
                    int[] c = watchHits.get(k);
                    if (c == null) watchHits.put(k, c = new int[]{0, addr - wa, (int) (h.currentTimeMillis() - h.vbase)});
                    c[0]++;
                }
            };
        }
        if (System.getenv("HL4_SVCLOG") != null) {
            final hl4.cpu.SvcHandler orig = rt.cpu.handler;
            final java.io.PrintWriter sw = new java.io.PrintWriter(new java.io.FileWriter(out + "/svclog.txt"));
            final long skip = System.getenv("HL4_SVCLOG").isEmpty() ? 0 : Long.parseLong(System.getenv("HL4_SVCLOG"));
            final long[] n = {0};
            final long[] hash = {0};
            rt.cpu.handler = (cpu, id) -> {
                long k = n[0]++;
                hash[0] = hash[0] * 31 + id * 7 + cpu.r[0] * 13L + cpu.r[1] * 17L + cpu.r[14];
                if (k % 100000 == 0) sw.println("H " + k + " " + cpu.icount + " " + Long.toHexString(hash[0]));
                if (k >= skip && k < skip + 200000) { sw.println(k + " " + cpu.icount + " " + Integer.toHexString(id) + " " + Integer.toHexString(cpu.r[0]) + " " + Integer.toHexString(cpu.r[1]) + " " + Integer.toHexString(cpu.r[2]) + " lr=" + Integer.toHexString(cpu.r[14])); }
                orig.svc(cpu, id);
            };
            java.lang.Runtime.getRuntime().addShutdownHook(new Thread(sw::flush));
        }
        if (System.getenv("HL4_CALLS") != null) {
            String[] w = System.getenv("HL4_CALLS").split(",");
            final long cfrom = Long.parseLong(w[0]), cto = Long.parseLong(w[1]);
            final java.io.PrintWriter cw = new java.io.PrintWriter(new java.io.FileWriter(out + "/calls.txt"));
            callLog = cw;
            rt.cpu.trace = true;
            rt.cpu.traceSink = (cpu, addr, op, thumb) -> {
                if (!thumb || (op >>> 11) != 31) return;
                long now = h.currentTimeMillis() - h.vbase;
                if (now < cfrom || now > cto) return;
                int target = (cpu.r[14] + ((op & 0x7FF) << 1)) & ~1;
                if (veneerLo != 0 && target >= veneerLo && target <= veneerLo + 0x38 && ((target - veneerLo) & 3) == 0) target = cpu.r[(target - veneerLo) >> 2] & ~1;
                if (callFilter != null && !callFilter.contains(target)) return;
                StringBuilder sb = new StringBuilder();
                sb.append(now).append(' ').append(Integer.toHexString(addr)).append(" -> ").append(Integer.toHexString(target));
                for (int i = 0; i < 4; i++) sb.append(' ').append(Integer.toHexString(cpu.r[i]));
                sb.append(" sp=").append(Integer.toHexString(cpu.r[13]));
                int nst = callStk;
                for (int i = 0; i < nst; i++) sb.append(" [").append(i).append("]=").append(Integer.toHexString(rt.mem.read32(cpu.r[13] + 4 * i)));
                for (int i = 0; i < 4 + nst; i++) {
                    int v = i < 4 ? cpu.r[i] : rt.mem.read32(cpu.r[13] + 4 * (i - 4));
                    if ((v >= 0x1400000 && v < 0x1600000) || (v >= 0x2000000 && v < 0x6000000) || (v >= 0x7000000 && v < 0x8000000)) {
                        if (!rt.mem.isMapped(v)) continue;
                        byte[] b = new byte[24];
                        int k = 0;
                        for (; k < 24; k++) { b[k] = (byte) rt.mem.read8(v + k); if (b[k] == 0) break; }
                        int hi = 0;
                        for (int j = 0; j < k; j++) if ((b[j] & 0xFF) >= 0xA1) hi++;
                        if (k >= 2 && hi >= 2) {
                            try { sb.append(" s").append(i).append("=\"").append(new String(b, 0, k, "EUC-KR").replace('\n', '|')).append('"'); } catch (Exception e) {}
                        }
                    }
                }
                cw.println(sb);
            };
        }
        if (System.getenv("HL4_AOT") != null) {
            rt.cpu.setAot((hl4.cpu.Aot) Class.forName("hl4.aot.LgtAot").newInstance());
        }
        if (System.getenv("HL4_AOTSTAT") != null) hl4.cpu.Cpu.aotStat = new int[0x100000];
        if (System.getenv("HL4_AOTCHECK") != null) { String[] w = System.getenv("HL4_AOTCHECK").split(","); hl4.cpu.Cpu.dbgFrom = Long.parseLong(w[0]); hl4.cpu.Cpu.dbgTo = Long.parseLong(w[1]); }
        if (System.getenv("HL4_FPSCAP") != null) rt.setFrameLimit(Integer.parseInt(System.getenv("HL4_FPSCAP")));
        h.vbase = h.vtime;
        final int[] prof = System.getenv("HL4_PROFILE") != null ? new int[0x100000] : null;
        final long profStart = Long.parseLong(System.getenv().getOrDefault("HL4_PROFILE", "0").isEmpty() ? "0" : System.getenv().getOrDefault("HL4_PROFILE", "0"));
        final long profEnd = System.getenv("HL4_PROFILE_END") != null ? Long.parseLong(System.getenv("HL4_PROFILE_END")) : Long.MAX_VALUE;
        final long profT = Long.parseLong(System.getenv().getOrDefault("HL4_PROFILE_T", "0"));
        if (System.getenv("HL4_WATCH") != null) { // HL4_WATCH=af60,aecc : log registers when these thumb addresses execute
            final java.util.HashSet<Integer> ws = new java.util.HashSet<Integer>();
            for (String a : System.getenv("HL4_WATCH").split(",")) ws.add(Integer.parseInt(a, 16));
            final String wm = System.getenv("HL4_WATCHMEM"); // "reg:off:len" e.g. 0:54:12 = 12 bytes at r0+0x54
            rt.cpu.traceSink = (cpu, addr, op, thumb) -> { if (ws.contains(addr)) { StringBuilder sb = new StringBuilder("WATCH " + Integer.toHexString(addr) + " t=" + (h.currentTimeMillis() - h.vbase) + " r0-3=" + Integer.toHexString(cpu.r[0]) + "," + Integer.toHexString(cpu.r[1]) + "," + Integer.toHexString(cpu.r[2]) + "," + Integer.toHexString(cpu.r[3]) + " sp=" + Integer.toHexString(cpu.r[13]) + " lr=" + Integer.toHexString(cpu.r[14]));
                if (wm != null) for (String w : wm.split(",")) { String[] q = w.split(":"); int b = cpu.r[Integer.parseInt(q[0])] + Integer.parseInt(q[1], 16); if (q.length > 3) b = rt.mem.read32(b); sb.append(" [" + w + "]"); for (int i = 0; i < Integer.parseInt(q[2]); i++) sb.append(" " + Integer.toHexString(rt.mem.read8(b + i))); }
                System.err.println(sb); } };
            rt.cpu.trace = true;
        }
        if (prof != null) {
            rt.cpu.traceSink = (cpu, addr, op, thumb) -> { int i = addr >> 1; if (i >= 0 && i < prof.length && cpu.icount > profStart && cpu.icount < profEnd && (profT == 0 || h.currentTimeMillis() - h.vbase > profT)) prof[i]++; };
            rt.cpu.trace = true;
        }
        long t0 = System.nanoTime();
        try {
            if (rt instanceof LgtRuntime) {
                rt.aid = "0002CCCB";
                rt.pid = "PD137477";
                if (outer.containsKey("app_info")) {
                    for (String line : new String(outer.get("app_info"), "EUC-KR").split("\n")) {
                        line = line.trim();
                        if (line.startsWith("AID:")) rt.aid = line.substring(4).trim();
                        if (line.startsWith("PID:")) rt.pid = line.substring(4).trim();
                    }
                }
                ((LgtRuntime) rt).traceSvc = traceSvc;
                ((LgtRuntime) rt).load(h.files.get("binary.mod"));
                hl4.lgt.KroGame.MEASURECHECK = System.getenv("HL4_MEASURECHECK") != null;
                hl4.lgt.KroGame.TEXTLOG = System.getenv("HL4_TEXTLOG") != null;
                ((LgtRuntime) rt).game = new hl4.lgt.KroGame((LgtRuntime) rt, viFile("font.bin"), viFile("dict.txt"));
                if (System.getenv("HL4_STUBOUT") != null) for (String a : System.getenv("HL4_STUBOUT").split(",")) ((LgtRuntime) rt).stubOut(Integer.parseInt(a, 16));
                ((LgtRuntime) rt).start();
                if (System.getenv("HL4_IMPORTS") != null) {
                    for (int a = 0x1400000; a < 0x1401000; a += 4) {
                        int v = rt.mem.read32(a);
                        if (v >= Runtime.STUB_BASE && v < Runtime.STUB_BASE + Runtime.STUB_SIZE) {
                            int id = rt.mem.read32((v & ~1) + 4);
                            System.err.println("IMPORT " + Integer.toHexString(a) + " " + Integer.toHexString(id));
                        }
                    }
                }
            }
            if (System.getenv("HL4_TEXTLOG") != null && rt.text != null) {
                final long tl = Long.parseLong(System.getenv("HL4_TEXTLOG"));
                rt.text.sink = (g, x, y, mw, f, al, col, str) -> { if (h.currentTimeMillis() - h.vbase >= tl && h.currentTimeMillis() - h.vbase < tl + 60) System.err.println("TEXT g=" + Integer.toHexString(g) + " " + x + "," + y + " w=" + mw + " f=" + f + " al=" + al + " c=" + Integer.toHexString(col) + " '" + str + "'"); };
            }
            int ki = 0, si = 0;
            long seqBase = 0;
            long nextDump = dumpEvery;
            List<long[]> ups = new ArrayList<>();
            while (h.currentTimeMillis() - h.vbase < total) {
                if (h.tick != null) h.tick.run();
                long now = h.currentTimeMillis() - h.vbase;
                while (si < seq.size()) {
                    long[] st = seq.get(si);
                    if (st[0] == 0) { // key after delay
                        if (now < seqBase + st[1]) break;
                        h.log("key press " + st[2]);
                        rt.postEvent(Runtime.EV_KEY_PRESS, (int) st[2], 0);
                        ups.add(new long[]{now + st[3], st[2]});
                        seqBase = now;
                        si++;
                    } else if (st[0] == 1) { // wait for pixel
                        boolean ok = false;
                        if (h.lastFrame != null) {
                            int px = h.lastFrame[(int) st[2] * h.fw + (int) st[1]];
                            int rr = (int) (st[3] >> 16) & 255, gg = (int) (st[3] >> 8) & 255, bb = (int) st[3] & 255;
                            ok = Math.abs(((px >> 16) & 255) - rr) <= st[4] && Math.abs(((px >> 8) & 255) - gg) <= st[4] && Math.abs((px & 255) - bb) <= st[4];
                        }
                        if (ok || now - seqBase > st[5]) {
                            h.log(ok ? "wait ok step " + si : "WAIT TIMEOUT step " + si);
                            seqBase = now;
                            si++;
                        } else break;
                    } else if (st[0] == 4) { // press until pixel
                        boolean ok = false;
                        if (h.lastFrame != null) {
                            int px = h.lastFrame[(int) st[4] * h.fw + (int) st[3]];
                            int rr = (int) (st[5] >> 16) & 255, gg = (int) (st[5] >> 8) & 255, bb = (int) st[5] & 255;
                            ok = Math.abs(((px >> 16) & 255) - rr) <= st[6] && Math.abs(((px >> 8) & 255) - gg) <= st[6] && Math.abs((px & 255) - bb) <= st[6];
                        }
                        if (ok || now - seqBase > st[7]) {
                            h.log(ok ? "until ok step " + si : "UNTIL TIMEOUT step " + si);
                            seqBase = now;
                            si++;
                        } else {
                            if (now >= st[8] + st[2]) {
                                h.log("key press " + st[1]);
                                rt.postEvent(Runtime.EV_KEY_PRESS, (int) st[1], 0);
                                ups.add(new long[]{now + 120, st[1]});
                                st[8] = now;
                            }
                            break;
                        }
                    } else if (st[0] == 2) {
                        if (h.lastFrame != null) h.savePng(out + "/" + seqNames.get(si) + ".png");
                        si++;
                    } else {
                        if (now < seqBase + st[1]) break;
                        seqBase = now;
                        si++;
                    }
                }
                while (ki < keys.size() && keys.get(ki)[0] <= now) {
                    int kc = (int) keys.get(ki)[1];
                    h.log("key press " + kc);
                    rt.postEvent(Runtime.EV_KEY_PRESS, kc, 0);
                    ups.add(new long[]{now + keys.get(ki)[2], kc});
                    ki++;
                }
                for (Iterator<long[]> it = ups.iterator(); it.hasNext(); ) {
                    long[] u = it.next();
                    if (u[0] <= now) {
                        rt.postEvent(Runtime.EV_KEY_RELEASE, (int) u[1], 0);
                        it.remove();
                    }
                }
                if (hl4.lgt.KroGame.MEASURECHECK && h.currentTimeMillis() - h.vbase >= 3000) {
                    hl4.lgt.KroGame.MEASURECHECK = false;
                    ((hl4.lgt.KroGame) ((LgtRuntime) rt).game).measureCheck();
                }
                if (callSpec != null && h.currentTimeMillis() - h.vbase >= callAt[0]) { // HL4_CALL=ms:fn,arg,... (arg: hex, @addr = word at addr, @addr+off)
                    String[] cs = callSpec[0].split(",");
                    int fn = (int) Long.parseLong(cs[0], 16);
                    int[] cargs = new int[cs.length - 1];
                    for (int ai = 1; ai < cs.length; ai++) {
                        String a = cs[ai];
                        if (a.startsWith("@")) { String[] q = a.substring(1).split("\\+"); cargs[ai - 1] = rt.mem.read32((int) Long.parseLong(q[0], 16)) + (q.length > 1 ? (int) Long.parseLong(q[1], 16) : 0); }
                        else cargs[ai - 1] = (int) Long.parseLong(a, 16);
                    }
                    System.err.println("HL4_CALL " + cs[0] + " " + Arrays.toString(cargs) + " -> " + Integer.toHexString(rt.cpu.call(fn | 1, cargs)));
                    callSpec = callQueue.isEmpty() ? null : callQueue.remove(0);
                    if (callSpec != null) callAt[0] = Long.parseLong(callSpec[1]);
                }

                if (pokeAt > 0 && h.currentTimeMillis() - h.vbase >= pokeAt) { // HL4_POKE=ms:addr=val[/w],...
                    pokeAt = 0;
                    for (String pv : System.getenv("HL4_POKE").split(":", 2)[1].split(",")) {
                        String[] q = pv.split("=");
                        int pa = (int) Long.parseLong(q[0], 16);
                        String[] vv = q[1].split("/");
                        int val = Integer.parseInt(vv[0]);
                        if (vv.length > 1 && vv[1].equals("2")) rt.mem.write16(pa, val); else rt.mem.write32(pa, val);
                    }
                    System.err.println("poked");
                }
                if (dumpAt > 0 && h.currentTimeMillis() - h.vbase >= dumpAt) {
                    dumpAt = 0;
                    try (java.io.DataOutputStream ds = new java.io.DataOutputStream(new java.io.BufferedOutputStream(new java.io.FileOutputStream(out + "/mem.bin")))) {
                        for (int pi = 0; pi < rt.mem.pages.length; pi++) {
                            int[] pg = rt.mem.pages[pi];
                            if (pg == null) continue;
                            ds.writeInt(pi);
                            for (int v : pg) ds.writeInt(Integer.reverseBytes(v));
                        }
                    }
                    if (h.lastFrame != null) h.savePng(out + "/mem.png");
                    System.err.println("memory dumped");
                }
                long w = rt.pump();
                if (w != 0) {
                    long step = w < 0 ? 10 : Math.min(w, 10);
                    h.vtime += step;
                }
                now = h.currentTimeMillis() - h.vbase;
                if (now >= nextDump) {
                    if (h.lastFrame != null && h.frameSeq != h.savedSeq) {
                        h.savedSeq = h.frameSeq;
                        h.savePng(String.format("%s/f%07d.png", out, nextDump));
                    }
                    nextDump += dumpEvery;
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
            if (h.lastFrame != null) h.savePng(out + "/err.png");
            System.err.println("r: " + Arrays.toString(Arrays.stream(rt.cpu.r).mapToObj(Integer::toHexString).toArray()));
        }
        double secs = (System.nanoTime() - t0) / 1e9;
        for (Map.Entry<String, int[]> e : watchHits.entrySet()) System.err.println("WATCH " + e.getKey() + " n=" + e.getValue()[0] + " firstoff=0x" + Integer.toHexString(e.getValue()[1]) + " t=" + e.getValue()[2]);
        if (callLog != null) callLog.close();
        System.err.println("aot entries " + rt.cpu.aotEntries + " aot instr " + rt.cpu.aotInstr);
        System.err.println("instructions " + rt.cpu.icount + " in " + secs + "s = " + (rt.cpu.icount / secs / 1e6) + " MIPS");
        int[] stats = ((LgtRuntime) rt).svcStats;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < stats.length; i++) if (stats[i] > 0) sb.append(Integer.toHexString(i)).append(':').append(stats[i]).append(' ');
        System.err.println("svc: " + sb);
        if (hl4.cpu.Cpu.aotStat != null) {
            try (java.io.PrintWriter pw = new java.io.PrintWriter(out + "/aotstat.txt")) {
                int base = rt.cpu.aot.base();
                for (int i = 0; i < hl4.cpu.Cpu.aotStat.length; i++) if (hl4.cpu.Cpu.aotStat[i] > 0) pw.println(Integer.toHexString(base + i * 2) + " " + hl4.cpu.Cpu.aotStat[i]);
            }
        }
        if (prof != null) {
            try (java.io.PrintWriter pw = new java.io.PrintWriter(out + "/profile.txt")) {
                for (int i = 0; i < prof.length; i++) if (prof[i] > 0) pw.println(Integer.toHexString(i * 2) + " " + prof[i]);
            }
        }
        int pages = 0;
        for (int[] pg : rt.mem.pages) if (pg != null) pages++;
        System.err.println("heap used " + rt.heap.used + " peak " + rt.heap.peak + " mapped pages " + pages + " (" + (pages * 64) + "KB)");
    }

    // HL4_CALL=ms:fn,arg,...|ms:fn,arg,...  call ARM functions at virtual times (debug)
    static final java.util.List<String[]> callQueue = new ArrayList<>();
    static { if (System.getenv("HL4_CALL") != null) for (String c : System.getenv("HL4_CALL").split("\\|")) { String[] q = c.split(":", 2); callQueue.add(new String[]{q[1], q[0]}); } }
    static String[] callSpec = callQueue.isEmpty() ? null : callQueue.remove(0);
    static final long[] callAt = {callSpec == null ? 0 : Long.parseLong(callSpec[1])};
    static long pokeAt = System.getenv("HL4_POKE") == null ? 0 : Long.parseLong(System.getenv("HL4_POKE").split(":")[0]);
    static long dumpAt = Long.parseLong(System.getenv().getOrDefault("HL4_DUMPMEM", "0"));
    static int keyPressType = 502, keyReleaseType = 503;
}
