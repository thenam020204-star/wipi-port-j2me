package hl4.test;

import hl4.game.Um;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

/** args: <golden dir>.  Scalar functions vs goldens taken from the original (tools/e_fn.py): one switch arm per ported function. */
public class FnTest {
    static int[] ints(String s) {
        String[] p = s.trim().split(" ");
        int[] v = new int[p.length];
        for (int i = 0; i < p.length; i++) {
            v[i] = Integer.parseInt(p[i]);
        }
        return v;
    }

    public static void main(String[] a) throws Exception {
        BufferedReader r = new BufferedReader(new FileReader(new File(a[0], "fn.txt")));
        String line;
        int n = 0;
        int bad = 0;
        java.util.Hashtable cnt = new java.util.Hashtable();
        while ((line = r.readLine()) != null) {
            int sp = line.indexOf(' ');
            String name = line.substring(0, sp);
            int eq = line.indexOf(" = ");
            int[] x = ints(line.substring(sp + 1, eq));
            String rest = line.substring(eq + 3);
            int bar = rest.indexOf(" | ");
            int want = Integer.parseInt(bar < 0 ? rest : rest.substring(0, bar));
            int got;
            int[] o = {-1431655766, -1431655766, -1431655766, -1431655766};
            String extra = "";
            if (name.equals("UM_sin")) {
                got = Um.sin(x[0]);
            } else if (name.equals("UM_cos")) {
                got = Um.cos(x[0]);
            } else if (name.equals("UM_getAngleY")) {
                got = Um.angleY(x[0], x[1]);
            } else if (name.equals("UM_getAngleX")) {
                got = Um.angleX(x[0], x[1]);
            } else if (name.equals("UM_vecX")) {
                got = Um.vecX(x[0]);
            } else if (name.equals("UM_vecY")) {
                got = Um.vecY(x[0]);
            } else if (name.equals("UM_turnOppositeDirection")) {
                got = Um.turnOpposite(x[0]);
            } else if (name.equals("UM_getAngleFor8Dir")) {
                got = Um.angleFor8Dir(x[0]);
            } else if (name.equals("UM_get8DirForAngle")) {
                got = Um.dirForAngle(x[0]);
            } else if (name.equals("UM_getDir24Anglendex")) {
                got = Um.dir24(x[0]);
            } else if (name.equals("UM_turnDirection")) {
                got = Um.turnDirection(x[0], x[1], x[2], x[3]);
            } else if (name.equals("UM_direction8")) {
                got = Um.direction8(x[0], x[1], x[2], x[3]) & 0xff;
            } else if (name.equals("UM_Collision_Two_Rect")) {
                got = Um.collision(x[0], x[1], x[2], x[3], x[4], x[5], x[6], x[7], o);
                extra = o[0] + " " + o[1] + " " + o[2] + " " + o[3];
            } else {
                System.out.println("no Java port for " + name);
                bad++;
                continue;
            }
            n++;
            Integer c = (Integer) cnt.get(name);
            cnt.put(name, new Integer(c == null ? 1 : c.intValue() + 1));
            boolean ok = got == want;
            if (ok && bar >= 0) {
                ok = extra.equals(rest.substring(bar + 3));
            }
            if (!ok) {
                if (bad < 12) {
                    System.out.println("MISMATCH " + line + "  got " + got + " " + extra);
                }
                bad++;
            }
        }
        java.util.Enumeration e = cnt.keys();
        while (e.hasMoreElements()) {
            Object k = e.nextElement();
            System.out.println("  " + k + ": " + cnt.get(k) + " cases");
        }
        System.out.println("FnTest " + (bad == 0 ? "ok" : "FAILED (" + bad + ")") + " cases " + n);
        System.exit(bad == 0 ? 0 : 1);
    }
}
