package hl4.data;

import hl4.sys.Bytes;

/** E/AI### enemy-AI script files (docs/phaseC_tables.md section 3; verified against EnemyAIParsing_LoadData, 43/43). Script bytes are kept verbatim. */
public final class AiData {
    public int kind;
    public int[] trigCnt;
    public int[] trigSize;
    public int[] trigPos;
    public byte[] trigScript;
    public int[] actCnt;
    public int[] actSize;
    public int[] actPos;
    public byte[] actScript;
    public byte[][] strings;
    public int end;

    private static int[] ubytes(byte[] b, int p, int n) {
        int[] r = new int[n];
        for (int i = 0; i < n; i++) {
            r[i] = b[p + i] & 0xff;
        }
        return r;
    }

    private static byte[] sub(byte[] b, int p, int n) {
        byte[] r = new byte[n];
        System.arraycopy(b, p, r, 0, n);
        return r;
    }

    public static AiData parse(byte[] b) {
        AiData a = new AiData();
        a.kind = b[0] & 0xff;
        int p = 1;
        int ln = Bytes.s16(b, p);
        int n = b[p + 2] & 0xff;
        int q = p + 3;
        a.trigCnt = ubytes(b, q, n);
        q += n;
        a.trigSize = ubytes(b, q, n);
        q += n;
        a.trigPos = new int[n];
        if (n > 0) {
            a.trigPos[0] = 1;
        }
        for (int i = 1; i < n; i++) {
            a.trigPos[i] = (a.trigPos[i - 1] + 2 + a.trigSize[i - 1]) & 0xff;
        }
        a.trigScript = sub(b, q, ln);
        q += ln;
        ln = Bytes.s16(b, q);
        n = b[q + 2] & 0xff;
        q += 3;
        a.actCnt = ubytes(b, q, n);
        q += n;
        a.actSize = ubytes(b, q, n);
        q += n;
        a.actPos = ubytes(b, q, n);
        q += n;
        a.actScript = sub(b, q, ln);
        q += ln;
        int ns = b[q++] & 0xff;
        int[] lens = ubytes(b, q, ns);
        q += ns;
        a.strings = new byte[ns][];
        for (int i = 0; i < ns; i++) {
            a.strings[i] = sub(b, q, lens[i]);
            q += lens[i];
        }
        a.end = q;
        return a;
    }
}
