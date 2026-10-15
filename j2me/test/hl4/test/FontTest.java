package hl4.test;

import hl4.gfx.HnfFont;
import hl4.sys.Res;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;

/** args: <res dir> <golden dir>.  Sets the resource root to the directory (test-only classloader trick) and hashes all glyphs. */
public class FontTest {
    public static void main(String[] a) throws Exception {
        BufferedReader r = new BufferedReader(new FileReader(new File(a[1], "font.txt")));
        String[] p = r.readLine().split(" ");
        HnfFont f = new HnfFont();
        int[] v = new int[1 << 17];
        int n = 0;
        int[] rows = new int[16];
        for (int lead = 0xb0; lead <= 0xc8; lead++) {
            for (int trail = 0xa1; trail <= 0xfe; trail++) {
                int code = f.johab(lead, trail);
                v[n++] = code;
                f.compose(code, rows);
                for (int i = 0; i < f.hangulHeight(); i++) {
                    v[n++] = rows[i];
                }
            }
        }
        for (int font = 0; font < 2; font++) {
            for (int ch = 32; ch < 127; ch++) {
                f.latin(font, ch, rows);
                for (int i = 0; i < f.latinHeight(font); i++) {
                    v[n++] = rows[i];
                }
                v[n++] = HnfFont.latinWidth(font, ch);
            }
        }
        int h = FxTest.h32(v, n);
        boolean ok = h == (int) Long.parseLong(p[0], 16) && n == Integer.parseInt(p[1]);
        System.out.println("FontTest ints " + n + " " + (ok ? "mismatches 0" : "MISMATCH got " + Integer.toHexString(h)));
        System.exit(ok ? 0 : 1);
    }
}
