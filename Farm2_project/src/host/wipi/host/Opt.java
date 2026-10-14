package wipi.host;

/** Port options (manifest attributes). */
public final class Opt {
    public static boolean vibrate = true;
    public static boolean debug;

    static void load() {
        vibrate = !"0".equals(Host.appProperty("Farm-Vibrate"));
        debug = "1".equals(Host.appProperty("Farm-Debug"));
    }
}
