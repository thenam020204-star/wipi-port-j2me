package hl4.test;

import hl4.gfx.Cif;
import hl4.gfx.Frame;
import hl4.gfx.Gbm;
import hl4.gfx.Sprite;
import hl4.gfx.SpriteOut;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

/** args: <res dir> <golden dir>.  Replays tools/j2me_golden.py sprite.txt (ENV / CALL lines) against hl4.gfx.Sprite. */
public class SpriteTest implements SpriteOut {
    static final int POOL = 12;
    static Gbm[][] pool = new Gbm[POOL][128];
    int particle;
    int[] ev = new int[1 << 12];
    int ne;

    static Gbm mk(int k, int idx) {
        int h = 1 + (k * 7 + idx * 13) % 60;
        int aux = 1 + (k * 11 + idx * 5) % 60;
        int flag = (k + idx) % 3 == 0 ? 1 : 0;
        byte[] body = new byte[9 + 2 + h];
        body[0] = 0x0c;
        body[1] = 1;
        body[3] = (byte) h;
        body[5] = (byte) aux;
        body[7] = 1;
        Gbm g = new Gbm(body, 0, body.length);
        g.palFlag = flag;
        return g;
    }

    void add(int v) {
        if (ne == ev.length) {
            int[] n = new int[ne * 2];
            System.arraycopy(ev, 0, n, 0, ne);
            ev = n;
        }
        ev[ne++] = v;
    }

    int id(Gbm g) {
        for (int k = 0; k < POOL; k++) {
            for (int i = 0; i < 128; i++) {
                if (pool[k][i] == g) {
                    return k * 256 + i;
                }
            }
        }
        throw new IllegalStateException("unknown image");
    }

    public void restorePalette(Gbm img) {
        add(1);
        add(id(img));
    }

    public void setPalBlendEx(Gbm img, int a, int b, int c, int d, int e) {
        add(2);
        add(id(img));
        add(a);
        add(b);
        add(c);
        add(d);
        add(e);
    }

    void head(int code, int x, int y, int w, int h, Gbm img, int sx, int sy, int flip, int rot) {
        add(code);
        add(x);
        add(y);
        add(w);
        add(h);
        add(id(img));
        add(sx);
        add(sy);
        add(flip);
        add(rot);
    }

    public void drawImg(Frame f, int x, int y, int w, int h, Gbm img, int sx, int sy, int flip, int rot, int anchor) {
        head(3, x, y, w, h, img, sx, sy, flip, rot);
        add(anchor);
    }

    public void drawImgEx(Frame f, int x, int y, int w, int h, Gbm img, int sx, int sy, int flip, int rot, int alpha, int effect, int anchor) {
        head(4, x, y, w, h, img, sx, sy, flip, rot);
        add(alpha);
        add(effect);
        add(anchor);
    }

    public void drawImgRotate(Frame f, int x, int y, int w, int h, Gbm img, int sx, int sy, int flip, int rot, int scx, int scy, int anchor) {
        head(5, x, y, w, h, img, sx, sy, flip, rot);
        add(scx);
        add(scy);
        add(anchor);
    }

    public void drawImgRotateEx(Frame f, int x, int y, int w, int h, Gbm img, int sx, int sy, int flip, int rot, int scx, int scy, int effect,
                                int alpha, int anchor) {
        head(6, x, y, w, h, img, sx, sy, flip, rot);
        add(scx);
        add(scy);
        add(effect);
        add(alpha);
        add(anchor);
    }

    public int particle() {
        add(7);
        return particle;
    }

    static int hash(int[] v, int n) {
        int h = 0x811c9dc5;
        for (int i = 0; i < n; i++) {
            for (int s = 0; s < 32; s += 8) {
                h ^= (v[i] >> s) & 0xff;
                h *= 0x01000193;
            }
        }
        return h;
    }

    public static void main(String[] a) throws Exception {
        for (int k = 0; k < POOL; k++) {
            for (int i = 0; i < 128; i++) {
                pool[k][i] = mk(k, i);
            }
        }
        File res = new File(a[0]);
        File gold = new File(a[1]);
        BufferedReader r = new BufferedReader(new FileReader(new File(gold, "sprite.txt")));
        String line;
        SpriteTest t = new SpriteTest();
        Sprite sp = null;
        Frame frame = new Frame(240, 320);
        int calls = 0, bad = 0;
        while ((line = r.readLine()) != null) {
            String[] p = line.split(" ");
            if (p[0].equals("ENV")) {
                byte[] data = p[1].startsWith("@syn/") ? DesTest.readFileBytes(new File(gold, "syncif/" + p[1].substring(5)))
                        : DesTest.readFileBytes(new File(res, p[1]));
                int mode = Integer.parseInt(p[2]);
                sp = new Sprite();
                sp.cif = new Cif(data, mode);
                sp.nSets = Integer.parseInt(p[3]);
                int q = 4;
                for (int i = 0; i < 32; i++) {
                    int id = Integer.parseInt(p[q++]);
                    sp.sets[i] = id < 0 ? null : pool[id];
                }
                for (int i = 0; i < 32; i++) {
                    int id = Integer.parseInt(p[q++]);
                    sp.orig[i] = id < 0 ? null : pool[id];
                }
                sp.c60 = Integer.parseInt(p[q++]);
                sp.c62 = Integer.parseInt(p[q++]);
                sp.c64 = Integer.parseInt(p[q++]);
                sp.c67 = Integer.parseInt(p[q++]);
                sp.c66 = Integer.parseInt(p[q++]);
                sp.f68 = Integer.parseInt(p[q++]);
                sp.f6d = Integer.parseInt(p[q++]);
                sp.f6e = Integer.parseInt(p[q++]);
                sp.f6f = Integer.parseInt(p[q++]);
                for (int i = 0; i < 12; i++) {
                    Sprite.lcdEff[i] = Integer.parseInt(p[q++]);
                }
                int ex = Integer.parseInt(p[q++]);
                Sprite.extraEffect = ex < 0 ? null : pool[ex];
                t.particle = Integer.parseInt(p[q++]);
            } else {
                int[] c = new int[10];
                for (int i = 0; i < 10; i++) {
                    c[i] = Integer.parseInt(p[1 + i]);
                }
                t.ne = 0;
                int nf = sp.drawNpc(t, frame, c[0], c[1], c[2], c[3], c[4], c[5], c[6], c[7], c[8], c[9]);
                t.add(nf);
                calls++;
                if (hash(t.ev, t.ne) != (int) Long.parseLong(p[11], 16)) {
                    bad++;
                    if (bad <= 8) {
                        System.out.println("MISMATCH " + line);
                    }
                }
            }
        }
        System.out.println("SpriteTest calls " + calls + " mismatches " + bad);
        System.exit(bad == 0 ? 0 : 1);
    }
}
