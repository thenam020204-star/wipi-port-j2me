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
                ref[nref++] = line;
            }
        }
        int end = Integer.parseInt(a[1]);
        int start = Integer.parseInt(a[2]);
        Hashtable script = new Hashtable();
        Hashtable pressOnly = new Hashtable();
        Hashtable releaseOnly = new Hashtable();
        Hashtable warps = new Hashtable();
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
            if (kv.startsWith("w")) {
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
        GameStart.newGame(0);
        java.io.PrintStream so = stateOut == null ? null : new java.io.PrintStream(new java.io.FileOutputStream(stateOut));
        int bad = 0;
        for (int t = start + 1; t < end && t < nref; t++) {
            String wp = (String) warps.get(new Integer(t));
            if (wp != null) {
                java.util.StringTokenizer qt = new java.util.StringTokenizer(wp, "/");
                int[] q = new int[4];
                for (int qi = 0; qi < 4; qi++) {
                    q[qi] = Integer.parseInt(qt.nextToken());
                }
                hl4.game.Event.moveNextScene(q[0], q[1], q[2], q[3], -1);
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
            try {
                Engine.tick();
            } catch (RuntimeException ex) {
                System.out.println("exception at tick " + t);
                throw ex;
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
        System.out.println("GameTraceTest " + (bad == 0 ? "ok" : "DIVERGED") + " ticks " + (start + 1) + ".." + end);
        System.exit(bad == 0 ? 0 : 1);
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
