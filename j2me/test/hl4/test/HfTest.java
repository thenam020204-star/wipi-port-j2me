package hl4.test;

import hl4.gfx.Frame;
import hl4.gfx.Hf;
import hl4.gfx.Prim;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

/** args: <golden dir>.  HF_DrawTextAnchorH2Color port vs the original (cases from tools/e_golden.py hf). */
public class HfTest {
    static byte[] unhex(String h) {
        if (h.equals("-")) {
            return new byte[256];
        }
        byte[] b = new byte[h.length() / 2 + 256];
        for (int i = 0; i < h.length() / 2; i++) {
            b[i] = (byte) Integer.parseInt(h.substring(2 * i, 2 * i + 2), 16);
        }
        return b;
    }

    public static void main(String[] a) throws Exception {
        BufferedReader r = new BufferedReader(new FileReader(new File(a[0], "hf.txt")));
        Frame f = new Frame(240, 400);
        short[] bg = new short[240 * 400];
        for (int y = 0; y < 400; y++) {
            for (int x = 0; x < 240; x++) {
                bg[y * 240 + x] = (short) (((x * 31 + y * 17) ^ ((x >> 3) * 257)) & 0xffff);
            }
        }
        int n = 0;
        int bad = 0;
        int nw = 0;
        int badw = 0;
        String line;
        while ((line = r.readLine()) != null) {
            String[] p = line.split(" ");
            if (p[0].equals("W")) {
                Hf.semicolon = Integer.parseInt(p[5]);
                int got = Hf.width2(unhex(p[6]), Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]),
                    Integer.parseInt(p[4]));
                int want = Integer.parseInt(p[7]);
                if (got != want) {
                    if (badw < 8) {
                        System.out.println("MISMATCH width " + line + " got " + got);
                    }
                    badw++;
                }
                nw++;
                continue;
            }
            int x = Integer.parseInt(p[0]);
            int y = Integer.parseInt(p[1]);
            int maxw = Integer.parseInt(p[2]);
            int ft = Integer.parseInt(p[3]);
            int start = Integer.parseInt(p[4]);
            int skip = Integer.parseInt(p[5]);
            int end = Integer.parseInt(p[6]);
            int anchor = Integer.parseInt(p[7]);
            int color = Integer.parseInt(p[8]);
            int sem = Integer.parseInt(p[9]);
            int fc0 = Integer.parseInt(p[12], 16);
            int fg0 = Integer.parseInt(p[13], 16);
            byte[] s = unhex(p[14]);
            int crc = (int) Long.parseLong(p[15], 16);
            int fc1 = (int) Long.parseLong(p[16], 16);
            int fg1 = Integer.parseInt(p[17], 16);
            System.arraycopy(bg, 0, f.px, 0, bg.length);
            Hf.semicolon = sem;
            Hf.fontColor = fc0;
            Prim.fg = fg0;
            Hf.drawColor(f, x, y, maxw, ft, s, start, skip, end, anchor, color);
            int h = Fnv.shorts(f.px, f.px.length);
            if (a.length > 1 && Integer.parseInt(a[1]) == n) {
                StringBuffer sb = new StringBuffer();
                for (int yy = 0; yy < 400; yy++) {
                    for (int xx = 0; xx < 240; xx++) {
                        if (f.px[yy * 240 + xx] != bg[yy * 240 + xx]) {
                            sb.append(xx).append(',').append(yy).append('\n');
                        }
                    }
                }
                System.out.print(sb);
            }
            if (h != crc || Hf.fontColor != fc1 || Prim.fg != fg1) {
                if (bad < 8) {
                    System.out.println("MISMATCH case " + n + ": " + line.substring(0, Math.min(120, line.length())) + " got crc "
                        + Integer.toHexString(h) + " fc " + Integer.toHexString(Hf.fontColor) + " fg " + Integer.toHexString(Prim.fg));
                }
                bad++;
            }
            n++;
        }
        System.out.println("HfTest draw cases " + n + " mismatches " + bad + "; width cases " + nw + " mismatches " + badw);
        System.exit(bad + badw == 0 ? 0 : 1);
    }
}
