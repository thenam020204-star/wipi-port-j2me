package hl4.game;

/** Lens distortion table (make_Lenz_array @0x5dacc: 100x100 shorts built with soft-float).  The table is only read by the lens screen effect, which is not ported yet, so building it is deferred until that effect needs it (the parameter is recorded). */
public final class Lenz {
    public static int param;
    public static short[] filter;

    private Lenz() {
    }

    public static void make(int p) {
        param = p;
        filter = null;
    }
}
