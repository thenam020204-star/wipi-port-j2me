package hl4.gfx;

/**
 * Sprite property tables (H4/EXD/_H_###_EXD, OBJ/EXD ... : SPR_loadAttackProperty @0xd17e8 / SPR_loadExtra @0xd1ee0; docs/phaseC_exd.md, verified byte for byte against the
 * original heap structures, 117 files).  hdr[0..9] = the ten ints at spr+0..0x24 (anchors and rectangles), idx[] = motion id -> record index (spr+0x58, 400 bytes),
 * slots[k] = the entries of record k (null for the header record): Entry {id, nB, cells[nB]}; a Cell holds the type of the last group read and 4 groups of 4 ints.
 */
public final class SprProps {
    public static final class Cell {
        public int type;
        public final int[][] g = new int[4][4];
    }

    public static final class Entry {
        public int id;
        public int nB;
        public Cell[] cells;
    }

    public final int[] hdr = new int[10];
    /** bit i set: hdr[i] is present in the file (the original leaves the other ints of the SPR struct untouched) */
    public int hdrMask;
    public final byte[] idx = new byte[400];
    public int n;
    public Entry[][] slots;

    /** the header ints land in the character's SPR struct (spr = char + 0x20): anchor at +0x20/+0x24, rectangle 1 (move rect) at +0x28.., rectangle 2 at +0x38.. */
    public void store(byte[] m, int sprBase) {
        for (int i = 0; i < 10; i++) {
            if ((hdrMask & (1 << i)) != 0) {
                hl4.sys.Bytes.w32(m, sprBase + 4 * i, hdr[i]);
            }
        }
    }

    private static int s16(byte[] b, int o) {
        return (short) ((b[o] & 0xff) | ((b[o + 1] & 0xff) << 8));
    }

    /** parse; the original reads past the buffer end in two shipped files (treated as 0 bytes) */
    public static SprProps parse(byte[] src) {
        byte[] b = new byte[src.length + 16];
        System.arraycopy(src, 0, b, 0, src.length);
        SprProps r = new SprProps();
        int n = b[0] & 0xff;
        r.n = n;
        r.slots = new Entry[n][];
        int p = 1;
        for (int m = 0; m < n; m++) {
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
                            r.hdr[0] = s16(b, q);
                            r.hdr[1] = s16(b, q + 2);
                            r.hdrMask |= 3;
                            q += 4;
                        } else if (t == 1) {
                            for (int k = 0; k < 4; k++) {
                                r.hdr[2 + k] = s16(b, q + 2 * k);
                                r.hdrMask |= 1 << (2 + k);
                            }
                            q += 8;
                        } else if (t == 2) {
                            for (int k = 0; k < 4; k++) {
                                r.hdr[6 + k] = s16(b, q + 2 * k);
                                r.hdrMask |= 1 << (6 + k);
                            }
                            q += 8;
                        }
                    }
                }
                r.slots[m] = null;
                p = q;
                continue;
            }
            r.idx[b0] = (byte) m;
            int ne = b1 > 0 ? b1 : 0;
            Entry[] entries = new Entry[ne];
            for (int e = 0; e < ne; e++) {
                Entry en = new Entry();
                en.id = b[q] & 0xff;
                int nA = b[q + 1];
                int nB = b[q + 2];
                q += 3;
                en.nB = nB;
                en.cells = new Cell[nB > 0 ? nB : 0];
                for (int k = 0; k < en.cells.length; k++) {
                    en.cells[k] = new Cell();
                }
                if (nA > 0) {
                    for (int g = 0; g < nA; g++) {
                        for (int k = 0; k < nB; k++) {
                            Cell c = en.cells[k];
                            int t = b[q++] & 0xff;
                            c.type = t;
                            int[] v = null;
                            if (t == 3 || t == 13) {
                                v = new int[] {s16(b, q), s16(b, q + 2), s16(b, q + 4), s16(b, q + 6)};
                                q += 8;
                            } else if (t == 4 || t == 5 || t == 6 || t == 9 || t == 10 || t == 12) {
                                v = new int[] {s16(b, q), s16(b, q + 2), -1, -1};
                                q += 4;
                            } else if (t == 7 || t == 8 || t == 11) {
                                v = new int[] {s16(b, q), -1, -1, -1};
                                q += 2;
                            }
                            if (v != null && g < 4) {
                                System.arraycopy(v, 0, c.g[g], 0, 4);
                            }
                            if (nA != 1) {
                                int sg = nA == 2 ? 1 : 2;
                                int dg = nA == 2 ? 2 : 3;
                                int[] s = c.g[sg];
                                int tt = c.type;
                                int[] d = c.g[dg];
                                if (tt == 13 || tt == 3 || tt == 5 || tt == 6) {
                                    int a0 = s[0] < 0 ? -s[0] : s[0];
                                    int x0 = a0 - s[2];
                                    int x1 = s[1];
                                    int x2 = s[2];
                                    int x3 = s[3];
                                    d[0] = x0;
                                    d[1] = x1;
                                    d[2] = x2;
                                    d[3] = x3;
                                } else if (tt == 4) {
                                    int x0 = -s[0];
                                    int x1 = s[1];
                                    d[0] = x0;
                                    d[1] = x1;
                                    d[2] = -1;
                                    d[3] = -1;
                                } else {
                                    int x0 = s[0];
                                    int x1 = s[1];
                                    int x2 = s[2];
                                    int x3 = s[3];
                                    d[0] = x0;
                                    d[1] = x1;
                                    d[2] = x2;
                                    d[3] = x3;
                                }
                            } else {
                                for (int dg = 1; dg < 4; dg++) {
                                    System.arraycopy(c.g[0], 0, c.g[dg], 0, 4);
                                }
                            }
                        }
                    }
                }
                entries[e] = en;
            }
            r.slots[m] = entries;
            p = q;
        }
        return r;
    }
}
