package wipi.host;

/**
 * The game's default text encoding. WIPI handsets decode new String(byte[])
 * as KSC5601/CP949; J2ME handsets use ISO-8859-1 or UTF-8 and have no Korean
 * codec. The port redirects the game's String(byte[]) constructors here.
 *
 * Table /enc/map.bin (built from the game's text files): u16 n, then n pairs
 * (u16 code, u16 char) sorted by code. Double-byte codes follow CP949
 * (lead 0x81-0xFE). Localised text uses the KS X 1001 user rows the same way.
 */
public final class Enc {
    private static char[] codes;
    private static char[] chars;

    private static synchronized void load() {
        if (codes != null) {
            return;
        }
        byte[] b = Res.readAll("/enc/map.bin");
        int n = b == null ? 0 : ((b[0] & 0xff) << 8) | (b[1] & 0xff);
        codes = new char[n];
        chars = new char[n];
        for (int i = 0; i < n; i++) {
            int o = 2 + i * 4;
            codes[i] = (char) (((b[o] & 0xff) << 8) | (b[o + 1] & 0xff));
            chars[i] = (char) (((b[o + 2] & 0xff) << 8) | (b[o + 3] & 0xff));
        }
    }

    private static char lookup(int code) {
        int lo = 0, hi = codes.length - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            int c = codes[mid];
            if (c < code) {
                lo = mid + 1;
            } else if (c > code) {
                hi = mid - 1;
            } else {
                return chars[mid];
            }
        }
        return '?';
    }

    private static boolean trail(int t) {
        return t >= 0x41 && t <= 0x5a || t >= 0x61 && t <= 0x7a || t >= 0x81 && t <= 0xfe;
    }

    public static String str(byte[] b) {
        return str(b, 0, b.length);
    }

    public static String str(byte[] b, int off, int len) {
        if (off < 0 || len < 0 || off + len > b.length) {
            throw new IndexOutOfBoundsException();
        }
        if (codes == null) {
            load();
        }
        char[] out = new char[len];
        int n = 0, end = off + len;
        for (int i = off; i < end;) {
            int c = b[i] & 0xff;
            if (c < 0x80) {
                out[n++] = (char) c;
                i++;
            } else if (c >= 0x81 && c <= 0xfe && i + 1 < end && trail(b[i + 1] & 0xff)) {
                out[n++] = lookup((c << 8) | (b[i + 1] & 0xff));
                i += 2;
            } else {
                out[n++] = '?';
                i++;
            }
        }
        return new String(out, 0, n);
    }

    /** String.getBytes() replacement (inverse mapping; '?' for unknown chars). */
    public static byte[] bytes(String s) {
        if (codes == null) {
            load();
        }
        int len = s.length();
        byte[] t = new byte[len * 2];
        int n = 0;
        for (int i = 0; i < len; i++) {
            char ch = s.charAt(i);
            if (ch < 0x80) {
                t[n++] = (byte) ch;
                continue;
            }
            int code = 0;
            for (int k = 0; k < chars.length; k++) {
                if (chars[k] == ch) {
                    code = codes[k];
                    break;
                }
            }
            if (code == 0) {
                t[n++] = (byte) '?';
            } else {
                t[n++] = (byte) (code >> 8);
                t[n++] = (byte) code;
            }
        }
        byte[] o = new byte[n];
        System.arraycopy(t, 0, o, 0, n);
        return o;
    }
}
