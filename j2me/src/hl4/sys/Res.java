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

    /** UM_LoadRes: raw bytes. */
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

    /** UM_LoadResDes: bytes decrypted in place with MX_desDecrypt (the trailing len % 8 bytes stay as stored). */
    public static byte[] loadDes(String path) {
        byte[] b = load(path);
        if (b != null) {
            Des.decrypt(b, b.length);
        }
        return b;
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
