package hl4.test;

import hl4.gfx.Blit;
import hl4.gfx.Frame;
import hl4.gfx.Gbm;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.zip.CRC32;

/** args: <res dir> <blit_images.txt> <blit.txt>.  Compares the Java DM_DrawImg port with the golden CRC-32 of the 240x400 frame. */
public class BlitTest {
    static byte[] readFile(File f) throws Exception {
        byte[] b = new byte[(int) f.length()];
        FileInputStream in = new FileInputStream(f);
        int g = 0;
        while (g < b.length) {
            g += in.read(b, g, b.length - g);
        }
        in.close();
        return b;
    }

    static Gbm synthetic(int w, int h, int t) {
        int n = t == 0x0c ? w * h : 16;
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(t);
        int[] hdr = {w, h, w, n};
        for (int i = 0; i < 4; i++) {
            o.write(hdr[i] & 0xff);
            o.write(hdr[i] >> 8);
        }
        for (int i = 0; i < n; i++) {
            int c = ((i + 1) * 0x101) & 0xffff;
            o.write(c & 0xff);
            o.write(c >> 8);
        }
        if (t == 0x0c) {
            for (int i = 0; i < w * h; i++) {
                o.write(i & 0xff);
            }
        } else {
            int stride = (w + 1) >> 1;
            byte[] raw = new byte[stride * h];
            for (int yy = 0; yy < h; yy++) {
                for (int xx = 0; xx < w; xx++) {
                    int i = (yy * w + xx) % 16;
                    raw[yy * stride + (xx >> 1)] |= (xx % 2 == 0) ? (i << 4) : i;
                }
            }
            o.write(raw, 0, raw.length);
        }
        byte[] body = o.toByteArray();
        return new Gbm(body, 0, body.length);
    }

    public static void main(String[] a) throws Exception {
        File res = new File(a[0]);
        ArrayList imgs = new ArrayList();
        BufferedReader r = new BufferedReader(new FileReader(a[1]));
        String line;
        while ((line = r.readLine()) != null) {
            String[] p = line.split(" ");
            if (p[0].equals("syn")) {
                imgs.add(synthetic(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3])));
            } else {
                imgs.add(Gbm.loadMerged(readFile(new File(res, p[1])))[Integer.parseInt(p[2])]);
            }
        }
        r.close();
        Frame f = new Frame(240, 400);
        short bg = 0x1234;
        r = new BufferedReader(new FileReader(a[2]));
        int n = 0, bad = 0;
        byte[] bytes = new byte[240 * 400 * 2];
        CRC32 crc = new CRC32();
        while ((line = r.readLine()) != null) {
            String[] p = line.split(" ");
            int[] v = new int[14];
            for (int i = 0; i < 14; i++) {
                v[i] = Integer.parseInt(p[i]);
            }
            long exp = Long.parseLong(p[14], 16);
            for (int i = 0; i < f.px.length; i++) {
                f.px[i] = bg;
            }
            f.setClip(v[5], v[6], v[7], v[8]);
            Blit.drawImg(f, v[12], v[13], v[3], v[4], (Gbm) imgs.get(v[0]), v[1], v[2], v[9], v[10], v[11]);
            for (int i = 0; i < f.px.length; i++) {
                bytes[2 * i] = (byte) f.px[i];
                bytes[2 * i + 1] = (byte) (f.px[i] >> 8);
            }
            crc.reset();
            crc.update(bytes, 0, bytes.length);
            n++;
            if (crc.getValue() != exp) {
                bad++;
                if (bad <= 10) {
                    System.out.println("MISMATCH " + line);
                }
            }
        }
        System.out.println("BlitTest cases " + n + " mismatches " + bad);
        System.exit(bad == 0 ? 0 : 1);
    }
}
