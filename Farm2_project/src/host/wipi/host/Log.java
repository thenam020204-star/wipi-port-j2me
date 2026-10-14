package wipi.host;

/** Minimal diagnostics: console + last error shown by Overlay when enabled. */
public final class Log {
    public static String last;
    public static int count;

    public static void e(String where, Throwable t) {
        count++;
        last = where + ": " + t;
        System.out.println("[farm] " + last);
        t.printStackTrace();
    }

    public static void i(String s) {
        System.out.println("[farm] " + s);
    }
}
