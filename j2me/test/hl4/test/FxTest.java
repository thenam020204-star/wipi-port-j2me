package hl4.test;

import hl4.gfx.Fx;
import hl4.gfx.Frame;
import hl4.gfx.Gbm;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.zip.CRC32;

/** args: <golden dir>.  Replays tools/j2me_golden.py fx.txt (blend table, HSB, palette operations, DrawImgEx, scaled draws). */
public class FxTest {
    static int h32(int[] v, int n) {
        int h = 0x811c9dc5;
        for (int i = 0; i < n; i++) {
            for (int s = 0; s < 32; s += 8) {
                h ^= (v[i] >> s) & 0xff;
                h *= 0x01000193;
            }
        }
        return h;
    }

    static byte[] hex(String s) {
        byte[] b = new byte[s.length() / 2];
        for (int i = 0; i < b.length; i++) {
            b[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        }
        return b;
    }

    static Gbm palImage(int[] pal) {
        byte[] body = new byte[9 + 2 * pal.length + 1];
        body[0] = 0x0c;
        body[1] = 1;
        body[3] = 1;
        body[5] = 1;
        body[7] = (byte) (pal.length & 0xff);
        body[8] = (byte) (pal.length >> 8);
        for (int i = 0; i < pal.length; i++) {
            body[9 + 2 * i] = (byte) pal[i];
            body[10 + 2 * i] = (byte) (pal[i] >> 8);
        }
        return new Gbm(body, 0, body.length);
    }

    static int palHash(short[] pal, int flag) {
        int[] v = new int[pal.length + 1];
        for (int i = 0; i < pal.length; i++) {
            v[i] = pal[i] & 0xffff;
        }
        v[pal.length] = flag;
        return h32(v, v.length);
    }

    public static void main(String[] a) throws Exception {
        BufferedReader r = new BufferedReader(new FileReader(new File(a[0], "fx.txt")));
        String line;
        int total = 0, bad = 0;
        Frame f = new Frame(240, 400);
        byte[] bytes = new byte[240 * 400 * 2];
        CRC32 crc = new CRC32();
        while ((line = r.readLine()) != null) {
            String[] p = line.split(" ");
            String k = p[0];
            int got = 0;
            int exp = (int) Long.parseLong(p[p.length - 1], 16);
            if (k.equals("table")) {
                got = Fnv.bytes(Fx.TABLE, 0, Fx.TABLE.length);
            } else if (k.equals("hsb1") || k.equals("hsb2")) {
                int[] v = new int[1 << 18];
                int n = 0;
                int[] o = new int[3];
                int lcg = 12345;
                if (k.equals("hsb1")) {
                    for (int rr = 0; rr < 256; rr += 17) {
                        for (int g = 0; g < 256; g += 17) {
                            for (int b = 0; b < 256; b += 17) {
                                Fx.rgbToHsb(rr, g, b, o);
                                v[n++] = o[0];
                                v[n++] = o[1];
                                v[n++] = o[2];
                            }
                        }
                    }
                    for (int i = 0; i < 20000; i++) {
                        int[] t = new int[3];
                        for (int j = 0; j < 3; j++) {
                            lcg = (lcg * 1103515245 + 12345) & 0x7fffffff;
                            t[j] = (lcg >> 8) % 256;
                        }
                        Fx.rgbToHsb(t[0], t[1], t[2], o);
                        v[n++] = o[0];
                        v[n++] = o[1];
                        v[n++] = o[2];
                    }
                } else {
                    // the Python generator advanced the LCG through the 20000 hsb1 samples first
                    for (int i = 0; i < 60000; i++) {
                        lcg = (lcg * 1103515245 + 12345) & 0x7fffffff;
                    }
                    for (int H = 0; H <= 360; H += 7) {
                        for (int S = 0; S <= 100; S += 5) {
                            for (int B = 0; B <= 100; B += 5) {
                                Fx.hsbToRgb(H, S, B, o);
                                v[n++] = o[0];
                                v[n++] = o[1];
                                v[n++] = o[2];
                            }
                        }
                    }
                    int[] m = {361, 101, 101};
                    for (int i = 0; i < 20000; i++) {
                        int[] t = new int[3];
                        for (int j = 0; j < 3; j++) {
                            lcg = (lcg * 1103515245 + 12345) & 0x7fffffff;
                            t[j] = (lcg >> 8) % m[j];
                        }
                        Fx.hsbToRgb(t[0], t[1], t[2], o);
                        v[n++] = o[0];
                        v[n++] = o[1];
                        v[n++] = o[2];
                    }
                }
                got = h32(v, n);
            } else if (k.equals("setpal") || k.equals("loadblend") || k.equals("mergeblend")) {
                int q = 1;
                int flag = 0, p2 = 0, p3 = 0, p4 = 0, mode = 0, alpha = 0, slot = 0;
                if (k.equals("setpal")) {
                    flag = Integer.parseInt(p[q++]);
                    p2 = Integer.parseInt(p[q++]);
                    p3 = Integer.parseInt(p[q++]);
                    p4 = Integer.parseInt(p[q++]);
                    mode = Integer.parseInt(p[q++]);
                    alpha = Integer.parseInt(p[q++]);
                } else {
                    slot = Integer.parseInt(p[q++]);
                }
                int n = Integer.parseInt(p[q++]);
                int[] pal = new int[n];
                for (int i = 0; i < n; i++) {
                    pal[i] = Integer.parseInt(p[q++]);
                }
                Gbm g = palImage(pal);
                if (k.equals("setpal")) {
                    g.palFlag = flag;
                    Fx.setPalBlendEx(g, p2, p3, p4, mode, alpha);
                    got = palHash(g.pal, g.palFlag);
                } else {
                    if (k.equals("loadblend")) {
                        Fx.loadBlend(g, slot);
                    } else {
                        Fx.mergeBlend(g, slot);
                    }
                    // golden hashes the palette only (no flag)
                    int[] v = new int[g.pal.length];
                    for (int i = 0; i < v.length; i++) {
                        v[i] = g.pal[i] & 0xffff;
                    }
                    got = h32(v, v.length);
                }
            } else {
                int q = 1;
                byte[] body = hex(p[q++]);
                int[] c = new int[18];
                for (int i = 0; i < 18; i++) {
                    c[i] = Integer.parseInt(p[q++]);
                }
                // order: clipL clipT clipR clipB flip rot alpha effect anchor x y sx sy w h scx scy exflag
                Gbm g = new Gbm(body, 0, body.length);
                for (int i = 0; i < f.px.length; i++) {
                    f.px[i] = (short) ((i * 40503 + 12345) & 0xffff);
                }
                f.setClip(c[0], c[1], c[2], c[3]);
                if (k.equals("drawex")) {
                    Fx.drawImgEx(f, c[9], c[10], c[13], c[14], g, c[11], c[12], c[4], c[5], c[6], c[7], c[8]);
                } else if (c[17] != 0) {
                    Fx.drawImgRotateEx(f, c[9], c[10], c[13], c[14], g, c[11], c[12], c[4], c[5], c[15], c[16], c[7], c[6], c[8]);
                } else {
                    Fx.drawImgRotate(f, c[9], c[10], c[13], c[14], g, c[11], c[12], c[4], c[5], c[15], c[16], c[8]);
                }
                for (int i = 0; i < f.px.length; i++) {
                    bytes[2 * i] = (byte) f.px[i];
                    bytes[2 * i + 1] = (byte) (f.px[i] >> 8);
                }
                crc.reset();
                crc.update(bytes, 0, bytes.length);
                got = (int) crc.getValue();
            }
            total++;
            if (got != exp) {
                bad++;
                if (bad <= 8) {
                    System.out.println("MISMATCH " + (line.length() > 200 ? line.substring(0, 200) : line));
                }
            }
        }
        System.out.println("FxTest lines " + total + " mismatches " + bad);
        System.exit(bad == 0 ? 0 : 1);
    }
}
