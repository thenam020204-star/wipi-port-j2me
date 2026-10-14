package wipi.host;

/** System.getProperty replacement: supplies the SKT handset properties the game reads. */
public final class Sys {
    public static String getProperty(String k) {
        if (k == null) {
            return null;
        }
        if (k.equals("m.CARRIER")) {
            return "SKT";
        }
        if (k.equals("m.VENDER") || k.equals("m.VENDOR")) {
            return "SKT";
        }
        if (k.equals("m.MODEL")) {
            return "J2ME";
        }
        if (k.equals("m.MIN") || k.equals("MIN")) {
            return "1234567";
        }
        return System.getProperty(k);
    }
}
