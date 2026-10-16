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
    public static void main(String[] a) throws Exception {
        String[] ref = new String[100000];
        int nref = 0;
        String snap = null;
        BufferedReader r = new BufferedReader(new FileReader(a[0]));
        String line;
        while ((line = r.readLine()) != null) {
            if (line.startsWith("# snap")) {
                snap = line;
            } else if (!line.startsWith("#")) {
                ref[nref++] = line;
            }
        }
        int end = Integer.parseInt(a[1]);
        int start = Integer.parseInt(a[2]);
        Hashtable script = new Hashtable();
        String ppm = null;
        String stateTicks = null;
        String stateOut = null;
        for (int i = 3; i < a.length; i++) {
            if (a[i].equals("--ppm")) {
                ppm = a[++i];
                continue;
            }
            if (a[i].equals("--state")) {
                stateTicks = "," + a[++i] + ",";
                stateOut = a[++i];
                continue;
            }
            int c = a[i].indexOf(':');
            script.put(new Integer(Integer.parseInt(a[i].substring(0, c))), new Integer(Integer.decode(a[i].substring(c + 1)).intValue()));
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
        Rng.restore(w, fi, ri, calls);
        Clock.setVirtual(1303268400000L + (long) (start + 1) * 50);
        GameStart.newGame(0);
        java.io.PrintStream so = stateOut == null ? null : new java.io.PrintStream(new java.io.FileOutputStream(stateOut));
        int bad = 0;
        for (int t = start + 1; t < end && t < nref; t++) {
            Integer k = (Integer) script.get(new Integer(t));
            if (k != null) {
                Engine.onKey(true, k.intValue());
                Engine.onKey(false, k.intValue());
            }
            Clock.setVirtual(Clock.now() + 50);
            Engine.tick();
            int h = Fnv.shorts(Gfx.scr.px, Gfx.scr.px.length);
            String mine = t + " " + TraceTest.hex8(h) + " " + Rng.calls;
            if (so != null && stateTicks.indexOf("," + t + ",") >= 0) {
                StateDump.write(so, t);
            }
            if (!mine.equals(ref[t])) {
                System.out.println("DIVERGE at tick " + t + ": java " + mine + " ref " + ref[t]);
                if (ppm != null) {
                    TraceTest.writePpm(ppm + "/java_" + t + ".ppm");
                }
                bad++;
                break;
            }
        }
        if (so != null) {
            so.close();
        }
        System.out.println("GameTraceTest " + (bad == 0 ? "ok" : "DIVERGED") + " ticks " + (start + 1) + ".." + end);
        System.exit(bad == 0 ? 0 : 1);
    }
}
