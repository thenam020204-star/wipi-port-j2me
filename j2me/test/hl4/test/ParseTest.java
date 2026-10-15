package hl4.test;

import hl4.data.AiData;
import hl4.data.Exd;
import hl4.data.MapData;
import hl4.data.Pal;
import hl4.data.Scn;
import hl4.data.Text;
import hl4.sys.Des;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

/** args: <res dir> <parse.txt>.  Each Java parser is dumped to the same flat int sequence as tools/j2me_golden.py and hashed (FNV-1a over LE ints). */
public class ParseTest {
    static int[] v = new int[1 << 16];
    static int n;

    static void add(int x) {
        if (n == v.length) {
            int[] nv = new int[n * 2];
            System.arraycopy(v, 0, nv, 0, n);
            v = nv;
        }
        v[n++] = x;
    }

    static void bl(byte[] b) {
        add(b.length);
        for (int i = 0; i < b.length; i++) {
            add(b[i] & 0xff);
        }
    }

    static void ints(int[] a) {
        for (int i = 0; i < a.length; i++) {
            add(a[i]);
        }
    }

    static void ubl(int[] a) {
        add(a.length);
        ints(a);
    }

    static int hash() {
        int h = 0x811c9dc5;
        for (int i = 0; i < n; i++) {
            for (int s = 0; s < 32; s += 8) {
                h ^= (v[i] >> s) & 0xff;
                h *= 0x01000193;
            }
        }
        return h;
    }

    static void dumpMap(byte[] b) {
        MapData d = MapData.parse(b);
        add(d.tilesetA.length);
        for (int i = 0; i < d.tilesetA.length; i++) {
            add(d.tilesetA[i]);
            add(d.tilesetB[i]);
        }
        bl(d.name0);
        bl(d.name1);
        add(d.unk0);
        add(d.w);
        add(d.h);
        ubl(d.objImg);
        ubl(d.sprIds);
        for (int i = 0; i < d.layerA.length; i++) {
            add(d.layerA[i] & 0xff);
        }
        for (int i = 0; i < d.layerB.length; i++) {
            add(d.layerB[i] & 0xff);
        }
        for (int l = 0; l < 4; l++) {
            add(d.objs[l].length / 6);
            ints(d.objs[l]);
        }
        ubl(d.summon);
        add(d.mons.length / 3);
        ints(d.mons);
        add(d.end);
        int[] sp = d.monsterSpawns(b);
        add(sp.length / 3);
        ints(sp);
    }

    static void dumpExd(byte[] b) {
        Exd m = Exd.parse(b);
        ints(m.hdr);
        for (int i = 0; i < 400; i++) {
            add(m.idx[i] & 0xff);
        }
        add(m.n);
        add(m.end);
        for (int s = 0; s < m.slots.length; s++) {
            Exd.Entry[] e = m.slots[s];
            if (e == null) {
                add(-1);
                continue;
            }
            add(e.length);
            for (int k = 0; k < e.length; k++) {
                add(e[k].id);
                add(e[k].nB);
                add(e[k].g.length);
                for (int c = 0; c < e[k].g.length; c++) {
                    add(e[k].type[c]);
                    ints(e[k].g[c]);
                }
            }
        }
    }

    static void dumpAi(byte[] b) {
        AiData m = AiData.parse(b);
        add(m.kind);
        add(m.trigCnt.length);
        ints(m.trigCnt);
        ints(m.trigSize);
        ints(m.trigPos);
        bl(m.trigScript);
        add(m.actCnt.length);
        ints(m.actCnt);
        ints(m.actSize);
        ints(m.actPos);
        bl(m.actScript);
        add(m.strings.length);
        for (int i = 0; i < m.strings.length; i++) {
            bl(m.strings[i]);
        }
        add(m.end);
    }

    static void sect(byte[][] s) {
        add(s.length);
        for (int i = 0; i < s.length; i++) {
            bl(s[i]);
        }
    }

    static void dumpScn(byte[] b) {
        Scn m = Scn.parse(b);
        add(m.w1);
        add(m.w2);
        add(m.w3);
        add(m.b3);
        add(m.b4);
        add(m.b5);
        add(m.mon0);
        add(m.mon1);
        add(m.mon2);
        add(m.bgm);
        add(m.tod);
        add(m.fgi);
        sect(m.events);
        sect(m.second);
        sect(m.strings);
        add(m.end);
    }

    static void dumpPal(byte[] b) {
        int[] p = Pal.parse(b);
        add(p.length / 2);
        ints(p);
    }

    static void dumpMrg(byte[] b) {
        sect(Text.parseMrg(b));
    }

    static void dumpLinked(byte[] b) {
        int cnt = Text.linkedCount(b);
        add((b[0] & 0xff) | ((b[1] & 0xff) << 8));
        add(cnt);
        for (int k = 0; k < cnt; k++) {
            add(Text.linkedOffset(b, k));
        }
        for (int k = 0; k < cnt; k++) {
            bl(Text.linked(b, k));
        }
    }

    public static void main(String[] a) throws Exception {
        BufferedReader r = new BufferedReader(new FileReader(a[1]));
        String line;
        int total = 0, bad = 0;
        while ((line = r.readLine()) != null) {
            String[] p = line.split(" ");
            byte[] b = DesTest.readFileBytes(new File(a[0], p[1]));
            n = 0;
            String k = p[0];
            if (k.equals("map")) {
                dumpMap(b);
            } else if (k.equals("exd")) {
                dumpExd(b);
            } else if (k.equals("ai")) {
                dumpAi(b);
            } else if (k.equals("scn")) {
                Des.decrypt(b, b.length);
                dumpScn(b);
            } else if (k.equals("pal")) {
                dumpPal(b);
            } else if (k.equals("mrg")) {
                dumpMrg(b);
            } else {
                dumpLinked(b);
            }
            total++;
            if (hash() != (int) Long.parseLong(p[2], 16)) {
                bad++;
                if (bad <= 10) {
                    System.out.println("MISMATCH " + line + " got " + Integer.toHexString(hash()));
                }
            }
        }
        System.out.println("ParseTest files " + total + " mismatches " + bad);
        System.exit(bad == 0 ? 0 : 1);
    }
}
