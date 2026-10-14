import java.io.BufferedReader;
import java.io.FileReader;

/**
 * Build check of the plain-text layer: runs the port's own compiled
 * wipi.host.Enc (CLDC classes, resources from the jar tree on the classpath)
 * on every string the game data can produce (tools/textx.py vectors: bytes
 * [TAB expected text]). With the .txt files as extracted, Enc.txt(bytes) must
 * equal the expected text (CP949 decoding by textx) for every text string, and
 * equal Enc.str(bytes) for the non-text strings of the same call sites
 * (scripts): proves hash, index search, field layouts, digit rebuilding,
 * UTF-8 parsing and escapes. Exit code 1 on any difference.
 *
 * usage: java -cp <cls>:<resdir>:<tools> TextCheck <vectors.txt>
 */
public class TextCheck {
    public static void main(String[] a) throws Exception {
        Class<?> enc = Class.forName("wipi.host.Enc");
        java.lang.reflect.Method str = enc.getMethod("str", byte[].class);
        java.lang.reflect.Method txt = enc.getMethod("txt", byte[].class);
        BufferedReader r = new BufferedReader(new FileReader(a[0]));
        int n = 0, bad = 0;
        for (String l; (l = r.readLine()) != null;) {
            String[] f = l.split("\t", -1);
            byte[] b = hex(f[0]);
            Object s = f.length > 1 ? new String(hex(f[1]), "UTF-8") : str.invoke(null, b), t = txt.invoke(null, b);
            n++;
            if (!s.equals(t)) {
                bad++;
                if (bad < 10) {
                    System.out.println("MISMATCH\n  orig: " + cps((String) s) + "\n  txt : " + cps((String) t));
                }
            }
        }
        System.out.println("textcheck: " + n + " strings, " + bad + " mismatches -> " + (bad == 0 ? "PASS" : "FAIL"));
        System.exit(bad == 0 ? 0 : 1);
    }

    static byte[] hex(String h) {
        byte[] b = new byte[h.length() / 2];
        for (int i = 0; i < b.length; i++) {
            b[i] = (byte) Integer.parseInt(h.substring(2 * i, 2 * i + 2), 16);
        }
        return b;
    }

    static String cps(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            b.append(c < 0x80 ? String.valueOf(c) : String.format("\\u%04x", (int) c));
        }
        return b.toString();
    }
}
