package hl4.test;

import hl4.gfx.SprProps;
import hl4.sys.Res;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

/** args: <golden dir>. SprProps parser vs tools/exd.py (verified against the original loader) over every H4/EXD file. */
public class SprPropsTest {
    public static void main(String[] a) throws Exception {
        BufferedReader r = new BufferedReader(new FileReader(new File(a[0], "sprprops.txt")));
        String line;
        int n = 0;
        int bad = 0;
        while ((line = r.readLine()) != null) {
            String[] p = line.split("\\|", -1);
            byte[] b = Res.load("/H4/EXD/" + p[0]);
            SprProps s = SprProps.parse(b);
            StringBuffer sb = new StringBuffer();
            for (int i = 0; i < 10; i++) {
                sb.append(i == 0 ? "" : " ").append(s.hdr[i]);
            }
            boolean ok = sb.toString().equals(p[1]) && Integer.parseInt(p[2]) == s.n;
            StringBuffer ix = new StringBuffer();
            for (int i = 0; i < 400; i++) {
                if (s.idx[i] != 0) {
                    ix.append(ix.length() == 0 ? "" : ",").append(i).append(':').append(s.idx[i] & 0xff);
                }
            }
            ok = ok && ix.toString().equals(p[3]);
            StringBuffer cs = new StringBuffer();
            for (int k = 0; k < s.slots.length; k++) {
                if (s.slots[k] == null) {
                    cs.append(cs.length() == 0 ? "" : " ").append('H');
                    continue;
                }
                for (int e = 0; e < s.slots[k].length; e++) {
                    SprProps.Entry en = s.slots[k][e];
                    for (int c = 0; c < en.cells.length; c++) {
                        cs.append(cs.length() == 0 ? "" : " ").append(k).append('.').append(en.id).append('.').append(en.nB).append(".t").append(en.cells[c].type).append(':');
                        for (int g = 0; g < 4; g++) {
                            for (int j = 0; j < 4; j++) {
                                cs.append(g + j == 0 ? "" : ",").append(en.cells[c].g[g][j]);
                            }
                        }
                    }
                }
            }
            ok = ok && cs.toString().equals(p.length > 4 ? p[4] : "");
            n++;
            if (!ok) {
                bad++;
                if (bad < 4) {
                    System.out.println("MISMATCH " + p[0]);
                }
            }
        }
        System.out.println("SprPropsTest " + (bad == 0 ? "ok" : "FAILED " + bad) + " files " + n);
        System.exit(bad == 0 ? 0 : 1);
    }
}
