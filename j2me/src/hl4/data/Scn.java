package hl4.data;

/**
 * MAP/SC/{e,n}####_scn scene container (docs/phaseC_tables.md section 6; verified against IT_Initialize, 345/345).
 * The file must already be DES-decoded (Res.loadDes).  Script bodies are the IT_* VM byte stream (Phase E).
 */
public final class Scn {
    public int w1;
    public int w2;
    public int w3;
    public int b3;
    public int b4;
    public int b5;
    public int mon0;
    public int mon1;
    public int mon2;
    public int bgm;        // s8, -1 = none
    public int tod;        // time-of-day class
    public int fgi;        // s8
    public byte[][] events;
    public byte[][] second;
    public byte[][] strings;
    public int end;

    private int p;

    public static Scn parse(byte[] b) {
        Scn s = new Scn();
        s.w1 = b[0] & 0xff;
        s.w2 = b[1] & 0xff;
        s.w3 = b[2] & 0xff;
        s.b3 = b[3] & 0xff;
        s.b4 = b[4] & 0xff;
        s.b5 = b[5] & 0xff;
        s.mon0 = b[6] & 0xff;
        s.mon1 = b[7] & 0xff;
        s.mon2 = b[8] & 0xff;
        s.bgm = b[9];
        s.tod = b[10] & 0xff;
        s.fgi = b[11];
        s.p = 12;
        s.events = s.section(b, s.w1);
        s.second = s.section(b, s.w2);
        s.strings = s.section(b, s.w3);
        s.end = s.p;
        return s;
    }

    private byte[][] section(byte[] b, int w) {
        int n = b[p++] & 0xff;
        int[] lens = new int[n];
        for (int i = 0; i < n; i++) {
            int l = b[p++] & 0xff;
            if (w > 1) {
                l += (b[p++] & 0xff) << 8;
            }
            lens[i] = l;
        }
        byte[][] out = new byte[n][];
        for (int i = 0; i < n; i++) {
            out[i] = new byte[lens[i]];
            System.arraycopy(b, p, out[i], 0, lens[i]);
            p += lens[i];
        }
        return out;
    }
}
