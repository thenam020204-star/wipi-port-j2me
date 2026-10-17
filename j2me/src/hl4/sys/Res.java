package hl4.sys;

import java.io.IOException;
import java.io.InputStream;

/**
 * Resource access (UM_LoadRes / UM_LoadResDes).  Paths use the original spelling ("/MAP/M/_MAP_M_001"); the JAR holds the
 * resources under the same names (no extension).  Returns null when the resource does not exist (UM_LoadRes returns 0).
 */
public final class Res {
    private Res() {
    }

    /**
     * UM_LoadRes(name, &len) @0xca200: CUSTOM_knlGetResourceID + MC_knlCalloc(size) + MC_knlGetResource: the raw bytes of the resource, or 0 / null when it does not
     * exist (or the read fails).  The out length *len is bytes.length here (0 in the original on failure).
     */
    public static byte[] load(String path) {
        InputStream in = null;
        try {
            in = Res.class.getResourceAsStream(path);
            if (in == null) {
                return null;
            }
            return readAll(in);
        } catch (IOException e) {
            return null;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    /** UM_LoadResDes(name, &len) @0xca16c: as UM_LoadRes, then MX_desDecrypt(buf, len) in place (the trailing len % 8 bytes stay as stored). */
    public static byte[] loadDes(String path) {
        byte[] b = load(path);
        if (b != null) {
            Des.decrypt(b, b.length);
        }
        return b;
    }

    /**
     * UM_getMakeResName(name, dst, a, b, c) @0xc9e40: builds "stem + a [+ b] [+ 0 c | c] + suffix" with MC_knlSprintk.  stem = the characters of name before its first
     * digit (the scan skips '/' and '_' and everything above '9', and stops at a digit or at the NUL); suffix = name from its LAST '_' to the end.  b < 0 leaves b out,
     * c < 0 leaves c out, 0 <= c <= 9 is printed with a leading 0 (c is a short).  Java returns the string instead of filling dst (and returning its length).
     * Not reproduced: a stem of 30+ bytes or a suffix of 5+ bytes overruns the stack buffers of the original ("later"); a name without '_' runs off the string.
     */
    public static String makeName(String name, int a, int b, int c) {
        int k = name.length() - 1;
        while (name.charAt(k) != '_') {
            k--;
        }
        String suffix = name.substring(k);
        if (suffix.length() > 4) {
            throw new IllegalStateException("UM_getMakeResName: suffix longer than 4 bytes: later");
        }
        int i = 0;
        for (;;) {
            char ch = i < name.length() ? name.charAt(i) : (char) 0;
            if (ch == '/' || ch == '_' || ch > '9') {
                i++;
            } else {
                break;
            }
        }
        if (i > 29) {
            throw new IllegalStateException("UM_getMakeResName: stem of 30+ bytes: later");
        }
        c = (short) c;
        StringBuffer sb = new StringBuffer();
        sb.append(name.substring(0, i));
        sb.append(a);
        if (b >= 0) {
            sb.append(b);
        }
        if (c >= 0) {
            if (c < 10) {
                sb.append('0');
            }
            sb.append(c);
        }
        sb.append(suffix);
        return sb.toString();
    }

    /**
     * UM_fileNameMode(fmt, dst, v) @0xc9e04: MC_knlSprintk(dst, fmt, v) after a dlib_memcpy(dst, NULL, strlen(dst)) (an original bug: it only works because the
     * destination is an empty string when called).  Only the single "%d" directive that the game's formats use is supported; the string is returned.
     */
    public static String fileNameMode(String fmt, int v) {
        int p = fmt.indexOf("%d");
        if (p < 0 || fmt.indexOf('%') != p || fmt.indexOf('%', p + 2) >= 0) {
            throw new IllegalStateException("UM_fileNameMode: format other than a single %d: later");
        }
        return fmt.substring(0, p) + v + fmt.substring(p + 2);
    }

    /** InputStream.available() is not reliable on all MIDP stacks: read until EOF into a growing buffer. */
    public static byte[] readAll(InputStream in) throws IOException {
        byte[] buf = new byte[in.available() > 0 ? in.available() : 1024];
        int n = 0;
        for (;;) {
            if (n == buf.length) {
                byte[] nb = new byte[buf.length * 2];
                System.arraycopy(buf, 0, nb, 0, n);
                buf = nb;
            }
            int r = in.read(buf, n, buf.length - n);
            if (r < 0) {
                break;
            }
            n += r;
        }
        if (n == buf.length) {
            return buf;
        }
        byte[] out = new byte[n];
        System.arraycopy(buf, 0, out, 0, n);
        return out;
    }
}
