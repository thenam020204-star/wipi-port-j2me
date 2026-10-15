package hl4.test;

import hl4.sys.Des;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;

/** args: <res dir> <golden file>.  golden lines: "<relative path> <fnv1a32 of the decrypted bytes hex>" */
public class DesTest {
    public static void main(String[] a) throws Exception {
        String res = a[0];
        BufferedReader r = new BufferedReader(new FileReader(a[1]));
        String line;
        int n = 0, bad = 0;
        while ((line = r.readLine()) != null) {
            String[] p = line.split(" ");
            File f = new File(res, p[0]);
            byte[] raw = new byte[(int) f.length()];
            FileInputStream in = new FileInputStream(f);
            int got = 0;
            while (got < raw.length) {
                got += in.read(raw, got, raw.length - got);
            }
            in.close();
            byte[] d = (byte[]) raw.clone();
            Des.decrypt(d, d.length);
            int h = Fnv.bytes(d, 0, d.length);
            byte[] e = (byte[]) d.clone();
            Des.encrypt(e, e.length);
            boolean same = true;
            for (int i = 0; i < raw.length; i++) {
                if (e[i] != raw[i]) {
                    same = false;
                    break;
                }
            }
            n++;
            if (h != (int) Long.parseLong(p[1], 16) || !same) {
                bad++;
                System.out.println("MISMATCH " + p[0] + " hash " + Integer.toHexString(h) + " expected " + p[1] + " roundtrip " + same);
            }
        }
        System.out.println("DesTest files " + n + " mismatches " + bad);
        System.exit(bad == 0 ? 0 : 1);
    }
}
