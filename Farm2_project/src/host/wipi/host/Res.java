package wipi.host;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Resource access. The game reads resources with available() + one read(),
 * which some KVMs do not honour for jar streams; the port replaces its
 * Class.getResourceAsStream calls with Res.open, which returns a fully
 * buffered stream (exact available(), complete read()).
 */
public final class Res {
    public static InputStream open(Class c, String name) {
        byte[] b = read(c, name);
        return b == null ? null : new ByteArrayInputStream(b);
    }

    public static byte[] readAll(String name) {
        return read(Res.class, name.startsWith("/") ? name : "/" + name);
    }

    static byte[] read(Class c, String name) {
        InputStream in = c.getResourceAsStream(name);
        if (in == null) {
            return null;
        }
        try {
            byte[] b = new byte[4096];
            int n = 0;
            while (true) {
                if (n == b.length) {
                    byte[] nb = new byte[b.length * 2];
                    System.arraycopy(b, 0, nb, 0, n);
                    b = nb;
                }
                int r = in.read(b, n, b.length - n);
                if (r < 0) {
                    break;
                }
                n += r;
            }
            if (n == b.length) {
                return b;
            }
            byte[] o = new byte[n];
            System.arraycopy(b, 0, o, 0, n);
            return o;
        } catch (IOException e) {
            return null;
        } finally {
            try {
                in.close();
            } catch (IOException e) {
            }
        }
    }
}
