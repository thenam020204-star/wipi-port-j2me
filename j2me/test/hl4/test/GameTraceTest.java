package hl4.test;

import hl4.game.Engine;
import hl4.game.GameStart;
import hl4.game.Gfx;
import hl4.sys.Clock;
import hl4.sys.Rng;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Hashtable;

/**
 * args: <trace.txt> <endTick> <startTick> [tick:key ...] [--ppm dir] [--state tick,...  stateOut].  The reference trace holds an RNG snapshot taken before tick <startTick>
 * (tools/e_trace.py with SNAP=<startTick>); the Java engine boots, restores the generator and the clock, and takes the game-start branch the story menu would take at the end
 * of that tick; ticks startTick+1.. are compared against the original (frame hash + RNG call count).
 */
public class GameTraceTest {
    static final int BACK_FROM = 522;
    static boolean backOnly;
    static boolean cont;
    static String rawTicks;
    static String rawDir;
    static int probeTick = -1;
    static String probeFile;

    public static void main(String[] a) throws Exception {
        String[] ref = new String[100000];
        int nref = 0;
        String snap = null;
        String[] seeds = new String[16];
        int nseeds = 0;
        BufferedReader r = new BufferedReader(new FileReader(a[0]));
        String line;
        while ((line = r.readLine()) != null) {
            if (line.startsWith("# seed ")) {
                seeds[nseeds++] = line;
            } else if (line.startsWith("# snap")) {
                snap = line;
            } else if (!line.startsWith("#")) {
                // indexed by tick number: a trace generated from a snapshot (tools/e_snap.py) starts at the snapshot tick
                int tk = Integer.parseInt(line.substring(0, line.indexOf(' ')));
                ref[tk] = line;
                nref = Math.max(nref, tk + 1);
            }
        }
        int end = Integer.parseInt(a[1]);
        int start = Integer.parseInt(a[2]);
        Hashtable script = new Hashtable();
        Hashtable pressOnly = new Hashtable();
        Hashtable releaseOnly = new Hashtable();
        Hashtable warps = new Hashtable();
        Hashtable heals = new Hashtable();
        String ppm = null;
        String stateTicks = null;
        String stateOut = null;
        for (int i = 3; i < a.length; i++) {
            if (a[i].equals("--ppm")) {
                ppm = a[++i];
                continue;
            }
            if (a[i].equals("--norender")) {
                hl4.game.MainGameFrame.testNoRender = true;
                continue;
            }
            if (a[i].equals("--raw")) {
                rawTicks = "," + a[++i] + ",";
                rawDir = a[++i];
                continue;
            }
            if (a[i].equals("--probe")) {
                probeTick = Integer.parseInt(a[++i]);    // after this tick: direct calls listed in the file (tools/e_calls.py PROBE), then exit
                probeFile = a[++i];
                continue;
            }
            if (a[i].equals("--cont")) {
                cont = true;
                continue;
            }
            if (a[i].equals("--backonly")) {
                backOnly = true;
                continue;
            }
            if (a[i].equals("--state")) {
                stateTicks = "," + a[++i] + ",";
                stateOut = a[++i];
                continue;
            }
            int c = a[i].indexOf(':');
            Integer tk = new Integer(Integer.parseInt(a[i].substring(0, c)));
            String kv = a[i].substring(c + 1);
            if (kv.equals("N")) {
                continue;                                   // fixture of the original's story menu (before the snapshot): nothing to do here
            }
            if (kv.equals("H")) {
                heals.put(tk, tk);                          // test fixture (tools/ref_game.py): hero hp := max hp before the tick
            } else if (kv.startsWith("w")) {
                warps.put(tk, kv.substring(1));
            } else if (kv.startsWith("p")) {
                pressOnly.put(tk, Integer.decode(kv.substring(1)));
            } else if (kv.startsWith("r")) {
                releaseOnly.put(tk, Integer.decode(kv.substring(1)));
            } else {
                script.put(tk, new Integer(Integer.decode(kv).intValue()));
            }
        }
        String[] p = snap.split(" ");
        int calls = Integer.parseInt(p[4]);
        int fi = Integer.parseInt(p[6]);
        int ri = Integer.parseInt(p[8]);
        int[] w = new int[31];
        for (int i = 0; i < 31; i++) {
            w[i] = (int) Long.parseLong(p[9 + i], 16);
        }
        Clock.setVirtual(1303268400000L);
        hl4.game.Glob.presetConsent = 1;
        Engine.startApp();
        hl4.game.Imgs.loadCommon(0);       // stand-in for the boot LoadFrame (target 3) the original ran before the snapshot
        Rng.restore(w, fi, ri, calls);
        Clock.setVirtual(1303268400000L + (long) (start + 1) * 50);
        // test seeding standing in for the title + story menu (E4): state the original holds at the snapshot tick that the game code reads
        for (int i = 0; i < nseeds; i++) {
            String[] q = seeds[i].split(" ");
            byte[] dst = q[2].equals("_gameMenu") ? hl4.game.G.gameMenu : (q[2].equals("_slotInfo") ? hl4.game.G.slotInfo : hl4.game.G.map);
            int off = Integer.parseInt(q[3]);
            for (int k = 0; k + 1 < q[4].length(); k += 2) {
                dst[off + k / 2] = (byte) Integer.parseInt(q[4].substring(k, k + 2), 16);
            }
        }
        // hero class: the class screen stored it in the slot record before the snapshot (MMenu_StoryModeKey: slot[0] = class, case 3 copies it to hero +0x16b)
        int cls = hl4.game.G.slotInfo[hl4.game.G.gameMenu[0x16] * 16];
        GameStart.newGame(cls);
        java.io.PrintStream so = stateOut == null ? null : new java.io.PrintStream(new java.io.FileOutputStream(stateOut));
        int bad = 0;
        for (int t = start + 1; t < end && t < nref; t++) {
            CallRec.tick = t;
            String wp = (String) warps.get(new Integer(t));
            if (wp != null) {
                java.util.StringTokenizer qt = new java.util.StringTokenizer(wp, "/");
                int[] q = new int[4];
                for (int qi = 0; qi < 4; qi++) {
                    q[qi] = Integer.parseInt(qt.nextToken());
                }
                hl4.game.Event.moveNextScene(q[0], q[1], q[2], q[3], -1);
            }
            if (heals.get(new Integer(t)) != null) {
                byte[] hm = hl4.game.Pool.hero.m;
                hl4.sys.Bytes.w32(hm, 0xa4, hl4.sys.Bytes.s32(hm, 0xa8));
            }
            Integer k = (Integer) script.get(new Integer(t));
            if (k != null) {
                Engine.onKey(true, k.intValue());
                Engine.onKey(false, k.intValue());
            }
            Integer kp = (Integer) pressOnly.get(new Integer(t));
            if (kp != null) {
                Engine.onKey(true, kp.intValue());
            }
            Integer kr = (Integer) releaseOnly.get(new Integer(t));
            if (kr != null) {
                Engine.onKey(false, kr.intValue());
            }
            Clock.setVirtual(Clock.now() + 50);
            long b0 = t >= BENCH_FROM ? System.nanoTime() : 0;
            try {
                Engine.tick();
            } catch (RuntimeException ex) {
                System.out.println("exception at tick " + t);
                throw ex;
            }
            if (t >= BENCH_FROM) {
                long d = System.nanoTime() - b0;
                benchSum += d;
                benchN++;
                if (d > benchMax) {
                    benchMax = d;
                }
            }
            int h = Fnv.shorts(Gfx.scr.px, Gfx.scr.px.length);
            String mine = t + " " + TraceTest.hex8(h) + " " + Rng.calls;
            String[] rp = ref[t].split(" ");
            if (rp.length > 3) {
                // the background buffer is compared from the first in-game render on (before that it still holds whatever the menus drew)
                mine += " " + (t >= BACK_FROM ? TraceTest.hex8(Fnv.shorts(Gfx.back.px, Gfx.back.px.length)) : rp[3]);
            }
            if (rawTicks != null && rawTicks.indexOf("," + t + ",") >= 0) {
                writeRaw(rawDir + "/js" + t + ".raw", Gfx.scr.px);
                writeRaw(rawDir + "/jb" + t + ".raw", Gfx.back.px);
            }
            if (so != null && stateTicks.indexOf("," + t + ",") >= 0) {
                StateDump.write(so, t);
            }
            if (t == probeTick) {
                probe(t + 1);
                System.out.println("GameTraceTest probes done after tick " + t);
                System.exit(0);
            }
            boolean same = mine.equals(ref[t]);
            if (hl4.game.MainGameFrame.testNoRender) {
                // no picture yet: only the RNG call count is compared
                String[] mp = mine.split(" ");
                same = mp[2].equals(rp[2]) || t >= 534;   // from 534 on the original draws the rain effect (RNG)
            }
            if (backOnly) {
                // picture work in progress: only the background buffer hash is compared
                String[] mp = mine.split(" ");
                same = mp.length > 3 && rp.length > 3 && mp[3].equals(rp[3]);
            }
            if (!same) {
                System.out.println("DIVERGE at tick " + t + ": java " + mine + " ref " + ref[t]);
                if (ppm != null) {
                    TraceTest.writePpm(ppm + "/java_" + t + ".ppm");
                }
                bad++;
                if (!cont) {
                    break;
                }
            }
        }
        if (so != null) {
            so.close();
        }
        if (benchN > 0) {
            System.out.println("bench ticks " + benchN + " avg " + (benchSum / benchN / 1000) + " us max " + (benchMax / 1000) + " us (Engine.tick only)");
        }
        System.out.println("GameTraceTest " + (bad == 0 ? "ok" : "DIVERGED") + " ticks " + (start + 1) + ".." + end);
        System.exit(bad == 0 ? 0 : 1);
    }

    /** test-only timing of Engine.tick (-Dbench=<first tick>); not part of the product */
    static final int BENCH_FROM = Integer.parseInt(System.getProperty("bench", "2147483647"));
    static long benchSum, benchMax;
    static int benchN;

    /** probe calls: each line 'NAME arg..' is dispatched to the instrumented ProbeDispatch classes (tools/callrec_inst.py), recorded by CallRec */
    static void probe(int tick) throws Exception {
        CallRec.tick = tick;
        BufferedReader r = new BufferedReader(new FileReader(probeFile));
        String line;
        String[] pk = {"hl4.game", "hl4.gfx", "hl4.sys", "hl4.data"};
        while ((line = r.readLine()) != null) {
            int c = line.indexOf('#');
            String[] p = (c >= 0 ? line.substring(0, c) : line).trim().split("\\s+");
            if (p[0].length() == 0) {
                continue;
            }
            if (p[0].equals("!poke")) {
                poke(p[1], Integer.parseInt(p[2]), (int) Long.decode(p[3]).longValue());
                continue;
            }
            String[] args = new String[p.length - 1];
            System.arraycopy(p, 1, args, 0, args.length);
            boolean done = false;
            for (int i = 0; i < pk.length && !done; i++) {
                try {
                    Class k = Class.forName(pk[i] + ".ProbeDispatch");
                    done = ((Boolean) k.getMethod("run", new Class[] {String.class, String[].class}).invoke(null, new Object[] {p[0], args})).booleanValue();
                } catch (ClassNotFoundException ex) {
                    // no wrapped method in that package
                } catch (java.lang.reflect.InvocationTargetException ex) {
                    System.out.println("probe " + line + ": " + ex.getCause());
                    done = true;
                }
            }
            if (!done) {
                throw new RuntimeException("probe: " + p[0] + " is not in the instrumented spec");
            }
        }
        r.close();
    }

    /** '!poke target width value' of a probe file (same targets as tools/e_calls.py): a global struct or a pool object, little endian */
    static void poke(String tg, int w, int v) {
        int plus = tg.indexOf('+');
        String base = plus < 0 ? tg : tg.substring(0, plus);
        int off = plus < 0 ? 0 : Integer.decode(tg.substring(plus + 1)).intValue();
        byte[] m;
        if (base.startsWith("o")) {
            int d = base.indexOf('.');
            int t = Integer.parseInt(base.substring(1, d));
            int i = Integer.parseInt(base.substring(d + 1));
            m = t == hl4.game.Pool.HERO ? hl4.game.Pool.hero.m : t == hl4.game.Pool.SUMMON ? hl4.game.Pool.summon.m : hl4.game.Pool.pools[t][i].m;
        } else {
            String[] n = {"_game", "_event", "_map", "_gameMenu", "_slotInfo", "_npcUi", "_questMenu", "_guardian"};
            byte[][] a = {hl4.game.G.game, hl4.game.G.event, hl4.game.G.map, hl4.game.G.gameMenu, hl4.game.G.slotInfo, hl4.game.G.npcUi,
                    hl4.game.G.questMenu, hl4.game.G.guardian};
            m = null;
            for (int i = 0; i < n.length; i++) {
                if (n[i].equals(base)) {
                    m = a[i];
                }
            }
            if (m == null) {
                throw new RuntimeException("probe: !poke of an unknown global " + base);
            }
        }
        for (int k = 0; k < w; k++) {
            m[off + k] = (byte) (v >> (8 * k));
        }
    }

    static void writeRaw(String path, short[] px) throws Exception {
        java.io.FileOutputStream o = new java.io.FileOutputStream(path);
        byte[] b = new byte[px.length * 2];
        for (int i = 0; i < px.length; i++) {
            b[2 * i] = (byte) px[i];
            b[2 * i + 1] = (byte) (px[i] >> 8);
        }
        o.write(b);
        o.close();
    }
}
