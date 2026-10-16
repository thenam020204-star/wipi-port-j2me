package hl4.test;

import hl4.game.Engine;
import hl4.game.Gfx;
import hl4.sys.Clock;
import hl4.sys.Rng;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileOutputStream;
import java.util.Hashtable;

/**
 * args: <trace.txt> <ticks> [tick:key ...] [--ppm dir].  Runs the Java engine with the same scenario and virtual clock as tools/e_trace.py and
 * compares the per-tick game-area hash and RNG call count; reports the first divergent tick (and writes its frame as PPM when asked).
 */
public class TraceTest {
    public static void main(String[] a) throws Exception {
        String[] ref = new String[100000];
        int nref = 0;
        BufferedReader r = new BufferedReader(new FileReader(a[0]));
        String line;
        while ((line = r.readLine()) != null) {
            if (!line.startsWith("#")) {
                ref[nref++] = line;
            }
        }
        int ticks = Integer.parseInt(a[1]);
        Hashtable script = new Hashtable();
        String ppm = null;
        for (int i = 2; i < a.length; i++) {
            if (a[i].equals("--ppm")) {
                ppm = a[++i];
                continue;
            }
            int c = a[i].indexOf(':');
            script.put(new Integer(Integer.parseInt(a[i].substring(0, c))), new Integer(Integer.decode(a[i].substring(c + 1)).intValue()));
        }
        Clock.setVirtual(1303268400000L);
        Engine.startApp();
        int bad = 0;
        for (int t = 0; t < ticks && t < nref; t++) {
            Integer k = (Integer) script.get(new Integer(t));
            if (k != null) {
                Engine.onKey(true, k.intValue());
                Engine.onKey(false, k.intValue());
            }
            Clock.setVirtual(Clock.now() + 50);
            Engine.tick();
            int h = Fnv.shorts(Gfx.scr.px, Gfx.scr.px.length);
            String mine = t + " " + hex8(h) + " " + Rng.calls;
            if (!mine.equals(ref[t])) {
                System.out.println("DIVERGE at tick " + t + ": java " + mine + " ref " + ref[t]);
                if (ppm != null) {
                    writePpm(ppm + "/java_" + t + ".ppm");
                }
                bad++;
                break;
            }
        }
        System.out.println("TraceTest " + (bad == 0 ? "ok" : "DIVERGED") + " ticks compared " + ticks);
        System.exit(bad == 0 ? 0 : 1);
    }

    static String hex8(int h) {
        String s = Integer.toHexString(h);
        while (s.length() < 8) {
            s = "0" + s;
        }
        return s;
    }

    static void writePpm(String path) throws Exception {
        FileOutputStream o = new FileOutputStream(path);
        o.write(("P6\n" + Gfx.scr.w + " " + Gfx.scr.h + "\n255\n").getBytes());
        for (int i = 0; i < Gfx.scr.px.length; i++) {
            int v = Gfx.scr.px[i] & 0xffff;
            o.write(((v >> 11) & 31) * 255 / 31);
            o.write(((v >> 5) & 63) * 255 / 63);
            o.write((v & 31) * 255 / 31);
        }
        o.close();
    }
}
