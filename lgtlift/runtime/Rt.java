/** Runtime helpers referenced by lifted code (CLDC 1.1 only; no reflection, no Java SE APIs). */
public final class Rt {
    private Rt() {}

    public static Throwable npe() { return new NullPointerException(); }

    public static Throwable arith() { return new ArithmeticException(); }

    public static Throwable aioobe(int i) { return new ArrayIndexOutOfBoundsException(); }

    public static Throwable cce() { return new ClassCastException(); }

    /** Body of a function the lifter could not translate: fails loudly instead of misbehaving silently. */
    public static Throwable unlifted(int addr) { return new RuntimeException(new StringBuffer().append("unlifted fn ").append(Integer.toHexString(addr)).toString()); }

    public static String str(int idx) { return Strs.get(idx); }
}
