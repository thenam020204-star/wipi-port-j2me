package hl4.sys;

/** MC_knlCurrentTime (milliseconds). The product reads the system clock; tests install a virtual clock (tools/ref_game.py advances 50 ms per tick). */
public final class Clock {
    private static long virtual;
    private static boolean useVirtual;

    private Clock() {
    }

    public static void setVirtual(long ms) {
        virtual = ms;
        useVirtual = true;
    }

    public static long now() {
        return useVirtual ? virtual : System.currentTimeMillis();
    }
}
