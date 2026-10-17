package hl4.sys;

/**
 * UM_Random / UM_Rand / UM_RandRatio (docs/phaseB_flows.md section 6): BSD random() TYPE_3 additive feedback generator,
 * 31 words, fptr/rptr 3 apart, state at boot = the BSD table for seed 1 (UM_RandSeed is dead code, so the sequence is the
 * same on every start).  Verified against the original in the reference runner (tools/ref_game.py counts the calls).
 */
public final class Rng {
    private static final int[] TBL = {
        (int) 0x9a319039L, (int) 0x32d9c024L, (int) 0x9b663182L, (int) 0x5da1f342L, (int) 0xde3b81e0L, (int) 0xdf0a6fb5L,
        (int) 0xf103bc02L, 0x48f340fb, 0x7449e56b, (int) 0xbeb1dbb0L, (int) 0xab5c5918L, (int) 0x946554fdL, (int) 0x8c2e680fL,
        (int) 0xeb3d799fL, (int) 0xb11ee0b7L, 0x2d436b86, (int) 0xda672e2aL, 0x1588ca88, (int) 0xe369735dL, (int) 0x904f35f7L,
        (int) 0xd7158fd6L, 0x6fa6f051, 0x616e6b96, (int) 0xac94efdcL, 0x36413f93, (int) 0xc622c298L, (int) 0xf5a42ab8L,
        (int) 0x8a88d77bL, (int) 0xf5ad9d0eL, (int) 0x8999220bL, 0x27fb47b9
    };
    private static final int[] st = new int[31];
    private static int f;
    private static int r;
    /** number of UM_Random calls since start (trace aid: a count mismatch locates a logic divergence before pixels differ) */
    public static int calls;

    private Rng() {
    }

    public static void reset() {
        System.arraycopy(TBL, 0, st, 0, 31);
        f = 3;
        r = 0;
        calls = 0;
    }

    static {
        reset();
    }

    /** test aid: restore a snapshot of the generator taken from the reference (tools/e_trace.py SNAP) */
    public static void restore(int[] words, int fi, int ri, int callCount) {
        System.arraycopy(words, 0, st, 0, 31);
        f = fi;
        r = ri;
        calls = callCount;
    }

    /** UM_Random @0xc8f2c: returns a 31-bit value. */
    public static int random() {
        calls++;
        int v = st[f] + st[r];
        st[f] = v;
        f++;
        if (f < 31) {
            r++;
            if (r >= 31) {
                r = 0;
            }
        } else {
            f = 0;
            r++;
        }
        return v >>> 1;
    }

    /** UM_Rand(a, b) @0xc8fc0: a + random() % (b - a + 1) with an UNSIGNED modulo (__aeabi_uidivmod); b - a + 1 == 0 divides by zero (the original does not guard it either) */
    public static int rand(int a, int b) {
        int d = b - a + 1;
        int v = random();
        if (d > 0) {
            return a + v % d;
        }
        return a + (int) ((v & 0xffffffffL) % (d & 0xffffffffL));
    }

    /** UM_RandRatio(p) @0xc8fec: UM_Rand(0, 100) <= p (signed compare), returns 1/0 */
    public static boolean ratio(int p) {
        return rand(0, 100) <= p;
    }
}
