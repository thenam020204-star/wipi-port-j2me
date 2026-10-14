package wipi.host;

import java.io.IOException;

import javax.microedition.lcdui.Image;

/**
 * Plain-PNG art. The build unpacks every image packed in the game containers
 * into one sprite sheet per container ("Dir/Name.png") plus its frame list
 * ("Dir/Name.txt": "frame x y w h" per line), and leaves in the container, at
 * the original offset and with the original length, a reference:
 * "@PNG" u8 n, n bytes path, u16 frame (big endian), zero padding.
 *
 * Both files are read at run time, so the art can be edited inside the jar
 * without rebuilding. One sheet is cached while a loader decodes a burst of
 * frames; the host drops it after the next paint.
 */
public final class Sheet {
    private static final Object lock = new Object();
    private static String path;
    private static Image sheet;
    private static int[] rects; // 4 ints per frame
    private static boolean used;

    public static boolean isRef(byte[] d, int off, int len) {
        return len >= 8 && d[off] == '@' && d[off + 1] == 'P' && d[off + 2] == 'N' && d[off + 3] == 'G';
    }

    public static Image frame(byte[] d, int off, int len) {
        int n = d[off + 4] & 0xff;
        if (5 + n + 2 > len) {
            throw new IllegalArgumentException("bad image reference");
        }
        int f = ((d[off + 5 + n] & 0xff) << 8) | (d[off + 6 + n] & 0xff);
        synchronized (lock) {
            if (path == null || !same(path, d, off + 5, n)) {
                sheet = null;
                rects = null;
                path = null;
                char[] c = new char[n];
                for (int i = 0; i < n; i++) {
                    c[i] = (char) (d[off + 5 + i] & 0xff);
                }
                String p = new String(c);
                try {
                    sheet = Image.createImage("/" + p);
                } catch (IOException e) {
                    throw new IllegalArgumentException("missing " + p);
                }
                rects = parse(Res.readAll(p.substring(0, p.length() - 4) + ".txt"));
                path = p;
            }
            used = true;
            int k = f * 4;
            if (rects == null || k + 3 >= rects.length || rects[k + 2] <= 0) {
                throw new IllegalArgumentException("frame " + f + " not in " + path);
            }
            int x = rects[k], y = rects[k + 1], w = rects[k + 2], h = rects[k + 3];
            if (x == 0 && y == 0 && w == sheet.getWidth() && h == sheet.getHeight()) {
                return sheet;
            }
            return Image.createImage(sheet, x, y, w, h, 0);
        }
    }

    /** Called by the host after a paint: frees the cached sheet once loading is over. */
    public static void release() {
        synchronized (lock) {
            if (!used) {
                sheet = null;
                rects = null;
                path = null;
            }
            used = false;
        }
    }

    private static boolean same(String p, byte[] d, int o, int n) {
        if (p.length() != n) {
            return false;
        }
        for (int i = 0; i < n; i++) {
            if (p.charAt(i) != (char) (d[o + i] & 0xff)) {
                return false;
            }
        }
        return true;
    }

    /** "frame x y w h" lines; '#' comments; returns 4 ints per frame index. */
    static int[] parse(byte[] t) {
        if (t == null) {
            return null;
        }
        int[] r = new int[64];
        int[] v = new int[5];
        int i = 0, n = t.length;
        while (i < n) {
            int k = 0;
            boolean comment = t[i] == '#';
            while (i < n && t[i] != '\n') {
                if (!comment && t[i] >= '0' && t[i] <= '9' && k < 5) {
                    int x = 0;
                    while (i < n && t[i] >= '0' && t[i] <= '9') {
                        x = x * 10 + (t[i] - '0');
                        i++;
                    }
                    v[k++] = x;
                } else {
                    i++;
                }
            }
            i++;
            if (k == 5) {
                int need = (v[0] + 1) * 4;
                if (need > r.length) {
                    int[] nr = new int[Math.max(need, r.length * 2)];
                    System.arraycopy(r, 0, nr, 0, r.length);
                    r = nr;
                }
                System.arraycopy(v, 1, r, v[0] * 4, 4);
            }
        }
        return r;
    }
}
