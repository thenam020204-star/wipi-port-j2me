package hl4.data;

import hl4.sys.Bytes;

/**
 * H4/EXD/_H_###_EXD sprite property (hit/attack box) files, docs/phaseC_exd.md; verified against SPR_loadAttackProperty
 * (117/117 files, 1321 cells).  Cells keep the original 0x44-byte layout as: type (byte +1) and 16 ints (4 groups of 4, +4..+0x43).
 * The original reads past the end of two shipped files: a zero-padded copy is parsed here.
 */
public final class Exd {
    public static final class Entry {
        public int id;
        public int nB;
        public int[] type;      // per cell
        public int[][] g;       // per cell: 16 ints
    }

    public int[] hdr = new int[10];
    public byte[] idx = new byte[400];   // motion id -> slot index
    public int n;
    public Entry[][] slots;              // null = header slot (no entries)
    public int end;


    public static Exd parse(byte[] raw) {
        byte[] b = new byte[raw.length + 16];
        System.arraycopy(raw, 0, b, 0, raw.length);
        Exd e = new Exd();
        e.n = b[0] & 0xff;
        e.slots = new Entry[e.n][];
        int p = 1;
        for (int m = 0; m < e.n; m++) {
            int b0 = b[p] & 0xff;
            int b1 = b[p + 1];
            int q = p + 2;
            if (b0 == 0 && b1 == 1) {
                int cnt = b[p + 4] & 0xff;
                q = p + 5;
                if (cnt != 0 && cnt < 0x80) {
                    for (int i = 0; i < cnt; i++) {
                        int t = b[q++] & 0xff;
                        if (t == 0) {
                            e.hdr[0] = Bytes.s16(b, q);
                            e.hdr[1] = Bytes.s16(b, q + 2);
                            q += 4;
                        } else if (t == 1) {
                            for (int k = 0; k < 4; k++) {
                                e.hdr[2 + k] = Bytes.s16(b, q + 2 * k);
                            }
                            q += 8;
                        } else if (t == 2) {
                            for (int k = 0; k < 4; k++) {
                                e.hdr[6 + k] = Bytes.s16(b, q + 2 * k);
                            }
                            q += 8;
                        }
                    }
                }
                e.slots[m] = null;
                p = q;
                continue;
            }
            e.idx[b0] = (byte) m;
            int ne = b1 > 0 ? b1 : 0;
            Entry[] entries = new Entry[ne];
            for (int en = 0; en < ne; en++) {
                Entry ent = new Entry();
                ent.id = b[q] & 0xff;
                int nA = b[q + 1];
                int nB = b[q + 2];
                q += 3;
                int cells = nB > 0 ? nB : 0;
                ent.nB = nB;
                ent.type = new int[cells];
                ent.g = new int[cells][16];
                if (nA > 0) {
                    for (int g = 0; g < nA; g++) {
                        for (int k = 0; k < nB; k++) {
                            int[] cell = ent.g[k];
                            int t = b[q++] & 0xff;
                            ent.type[k] = t;
                            int a0 = 0, a1 = 0, a2 = 0, a3 = 0;
                            boolean have = false;
                            if (t == 3 || t == 13) {
                                a0 = Bytes.s16(b, q);
                                a1 = Bytes.s16(b, q + 2);
                                a2 = Bytes.s16(b, q + 4);
                                a3 = Bytes.s16(b, q + 6);
                                q += 8;
                                have = true;
                            } else if (t == 4 || t == 5 || t == 6 || t == 9 || t == 10 || t == 12) {
                                a0 = Bytes.s16(b, q);
                                a1 = Bytes.s16(b, q + 2);
                                a2 = -1;
                                a3 = -1;
                                q += 4;
                                have = true;
                            } else if (t == 7 || t == 8 || t == 11) {
                                a0 = Bytes.s16(b, q);
                                a1 = -1;
                                a2 = -1;
                                a3 = -1;
                                q += 2;
                                have = true;
                            }
                            if (have) {
                                cell[4 * g] = a0;
                                cell[4 * g + 1] = a1;
                                cell[4 * g + 2] = a2;
                                cell[4 * g + 3] = a3;
                            }
                            if (nA != 1) {
                                int sg = nA == 2 ? 1 : 2;
                                int dg = nA == 2 ? 2 : 3;
                                int s0 = cell[4 * sg], s1 = cell[4 * sg + 1], s2 = cell[4 * sg + 2], s3 = cell[4 * sg + 3];
                                int tt = ent.type[k];
                                if (tt == 13 || tt == 3 || tt == 5 || tt == 6) {
                                    cell[4 * dg] = (s0 < 0 ? -s0 : s0) - s2;
                                    cell[4 * dg + 1] = s1;
                                    cell[4 * dg + 2] = s2;
                                    cell[4 * dg + 3] = s3;
                                } else if (tt == 4) {
                                    cell[4 * dg] = -s0;
                                    cell[4 * dg + 1] = s1;
                                    cell[4 * dg + 2] = -1;
                                    cell[4 * dg + 3] = -1;
                                } else {
                                    cell[4 * dg] = s0;
                                    cell[4 * dg + 1] = s1;
                                    cell[4 * dg + 2] = s2;
                                    cell[4 * dg + 3] = s3;
                                }
                            } else {
                                for (int dg = 1; dg < 4; dg++) {
                                    for (int z = 0; z < 4; z++) {
                                        cell[4 * dg + z] = cell[z];
                                    }
                                }
                            }
                        }
                    }
                }
                entries[en] = ent;
            }
            e.slots[m] = entries;
            p = q;
        }
        e.end = p;
        return e;
    }
}
