package wipi.host;

/**
 * Plain-text strings. Every text of the game lives in UTF-8 files under
 * /text/ inside the jar ("key=text" lines), read at run time, so the text can
 * be edited in the jar without rebuilding:
 *
 *  - /text/ui.txt: the string literals of the game classes. The build replaces
 *    each `ldc "literal"` by `ldc "literal"; sipush id; invokestatic lit`, so a
 *    missing or deleted line falls back to the original literal.
 *  - /text/*.txt + /text/index.bin: the strings the game builds from its data
 *    files (dialogue, names, help, quests...). See Enc.txt and tools/textx.py.
 *
 * Escapes in a text: \n new line, \\ backslash. Lines starting with '#' are
 * comments. Memory: ui.txt stays loaded (it replaces the constant pool
 * strings); data files are cached two at a time.
 */
public final class Str {
    private static String[] ui;

    /** Literal replacement (call site rewritten by the build). */
    public static String lit(String orig, int id) {
        String[] u = ui;
        if (u == null) {
            u = loadUi();
        }
        String s = id < u.length ? u[id] : null;
        return s != null ? s : orig;
    }

    private static synchronized String[] loadUi() {
        if (ui == null) {
            File f = parse(Res.readAll("/text/ui.txt"));
            int max = -1;
            for (int i = 0; i < f.n; i++) {
                if ((f.keys[i] & 15) == 0 && (f.keys[i] >> 4) > max) {
                    max = f.keys[i] >> 4;
                }
            }
            String[] u = new String[max + 1];
            for (int i = 0; i < f.n; i++) {
                if ((f.keys[i] & 15) == 0) {
                    u[f.keys[i] >> 4] = f.vals[i];
                }
            }
            ui = u;
        }
        return ui;
    }

    // ------------------------------------------------------------ data strings

    private static byte[] idx; // index.bin
    private static String[] names;
    private static int entOff, nEnt;
    private static int[][] lay; // per layout: start,len,width triples
    private static final int SLOTS = 2;
    private static final int[] cacheNo = { -1, -1 };
    private static final File[] cache = new File[SLOTS];
    private static int victim;

    static synchronized boolean ready() {
        if (idx != null) {
            return names != null;
        }
        idx = Res.readAll("/text/index.bin");
        if (idx == null) {
            idx = new byte[0];
            return false;
        }
        int p = 0;
        int nf = u16(p);
        p += 2;
        names = new String[nf];
        for (int i = 0; i < nf; i++) {
            int n = idx[p++] & 0xff;
            char[] c = new char[n];
            for (int k = 0; k < n; k++) {
                c[k] = (char) idx[p + k];
            }
            names[i] = new String(c);
            p += n;
        }
        nEnt = u16(p);
        entOff = p + 2;
        p = entOff + nEnt * 9;
        int nl = u16(p);
        p += 2;
        lay = new int[nl][];
        for (int i = 0; i < nl; i++) {
            int nf2 = idx[p++] & 0xff;
            int[] l = new int[nf2 * 3];
            for (int k = 0; k < nf2; k++) {
                l[k * 3] = u16(p);
                l[k * 3 + 1] = u16(p + 2);
                l[k * 3 + 2] = idx[p + 4] & 0xff;
                p += 5;
            }
            lay[i] = l;
        }
        return true;
    }

    private static int u16(int p) {
        return ((idx[p] & 0xff) << 8) | (idx[p + 1] & 0xff);
    }

    /** Text for a game data string (orig = its decoded bytes, h = FNV-1a of the bytes). */
    static String data(String orig, int h) {
        if (!ready()) {
            return orig;
        }
        int lo = 0, hi = nEnt - 1, e = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            int o = entOff + mid * 9;
            int v = ((idx[o] & 0xff) << 24) | ((idx[o + 1] & 0xff) << 16) | ((idx[o + 2] & 0xff) << 8) | (idx[o + 3] & 0xff);
            if (v == h) {
                e = o;
                break;
            }
            // unsigned compare
            if ((v ^ 0x80000000) < (h ^ 0x80000000)) {
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        if (e < 0) {
            for (int i = 0; i < orig.length(); i++) {
                if (orig.charAt(i) > 0x7f) {
                    Log.i("text not in /text/index.bin: " + orig);
                    break;
                }
            }
            return orig;
        }
        int fno = idx[e + 4] & 0xff;
        int key = u16(e + 5);
        int[] l = lay[u16(e + 7)];
        File f = file(fno);
        if (f == null) {
            return orig;
        }
        if (l.length == 0) {
            String s = f.get(key << 4);
            return s != null ? s : orig;
        }
        StringBuffer sb = new StringBuffer(orig.length() + 16);
        int pos = 0;
        for (int i = 0; i < l.length; i += 3) {
            int st = l[i], n = l[i + 1], w = l[i + 2];
            if (st + n > orig.length() || st - w < pos) {
                return orig; // layout does not fit: keep the original
            }
            String v = f.get((key << 4) | (i / 3 + 1));
            if (v == null) {
                v = orig.substring(st, st + n);
            }
            sb.append(orig.substring(pos, st - w));
            if (w > 0) {
                int max = w == 1 ? 9 : w == 2 ? 99 : 999;
                if (v.length() > max) {
                    Log.i("text too long for its field (" + max + "): " + v);
                    v = v.substring(0, max);
                }
                String d = Integer.toString(v.length());
                for (int k = d.length(); k < w; k++) {
                    sb.append('0');
                }
                sb.append(d);
            }
            sb.append(v);
            pos = st + n;
        }
        sb.append(orig.substring(pos));
        return sb.toString();
    }

    private static synchronized File file(int fno) {
        for (int i = 0; i < SLOTS; i++) {
            if (cacheNo[i] == fno) {
                return cache[i];
            }
        }
        byte[] b = Res.readAll("/text/" + names[fno]);
        if (b == null) {
            return null;
        }
        int v = victim;
        victim = (victim + 1) % SLOTS;
        cache[v] = null;
        cache[v] = parse(b);
        cacheNo[v] = fno;
        return cache[v];
    }

    // ------------------------------------------------------------ text files

    /** Parsed "key=text" file; key "N" -> N<<4, "N.f" -> N<<4 | (f+1). */
    static final class File {
        int n;
        int[] keys;
        String[] vals;

        String get(int k) {
            int lo = 0, hi = n - 1;
            while (lo <= hi) {
                int mid = (lo + hi) >>> 1;
                int v = keys[mid];
                if (v < k) {
                    lo = mid + 1;
                } else if (v > k) {
                    hi = mid - 1;
                } else {
                    return vals[mid];
                }
            }
            return null;
        }
    }

    static File parse(byte[] b) {
        File f = new File();
        if (b == null) {
            f.keys = new int[0];
            f.vals = new String[0];
            return f;
        }
        int lines = 1;
        for (int i = 0; i < b.length; i++) {
            if (b[i] == '\n') {
                lines++;
            }
        }
        int[] keys = new int[lines];
        String[] vals = new String[lines];
        char[] buf = new char[256];
        int n = 0, p = 0, len = b.length;
        if (len >= 3 && (b[0] & 0xff) == 0xef && (b[1] & 0xff) == 0xbb && (b[2] & 0xff) == 0xbf) {
            p = 3; // UTF-8 BOM written by some editors
        }
        boolean sorted = true;
        while (p < len) {
            int e = p;
            while (e < len && b[e] != '\n') {
                e++;
            }
            int end = e;
            if (end > p && b[end - 1] == '\r') {
                end--;
            }
            // key
            int q = p, k = 0, fld = 0, digits = 0;
            boolean ok = q < end && b[q] >= '0' && b[q] <= '9';
            while (ok && q < end && b[q] >= '0' && b[q] <= '9') {
                k = k * 10 + (b[q++] - '0');
                digits++;
            }
            if (ok && q < end && b[q] == '.') {
                q++;
                int d = 0;
                while (q < end && b[q] >= '0' && b[q] <= '9') {
                    fld = fld * 10 + (b[q++] - '0');
                    d++;
                }
                ok = d > 0 && fld < 15;
                fld++;
            }
            if (ok && digits > 0 && digits < 7 && q < end && b[q] == '=') {
                q++;
                // value: UTF-8 with \n \r \\ escapes
                if (buf.length < end - q) {
                    buf = new char[end - q];
                }
                int c = 0;
                while (q < end) {
                    int x = b[q] & 0xff;
                    int ch;
                    if (x < 0x80) {
                        ch = x;
                        q++;
                    } else if (x >= 0xc0 && x < 0xe0 && q + 1 < end) {
                        ch = ((x & 0x1f) << 6) | (b[q + 1] & 0x3f);
                        q += 2;
                    } else if (x >= 0xe0 && x < 0xf0 && q + 2 < end) {
                        ch = ((x & 0x0f) << 12) | ((b[q + 1] & 0x3f) << 6) | (b[q + 2] & 0x3f);
                        q += 3;
                    } else {
                        ch = '?';
                        q++;
                        while (q < end && (b[q] & 0xc0) == 0x80) {
                            q++;
                        }
                    }
                    if (ch == '\\' && q < end) {
                        int y = b[q];
                        if (y == 'n') {
                            ch = '\n';
                            q++;
                        } else if (y == 'r') {
                            ch = '\r';
                            q++;
                        } else if (y == '\\') {
                            q++;
                        }
                    }
                    buf[c++] = (char) ch;
                }
                int key = (k << 4) | fld;
                if (n > 0 && keys[n - 1] >= key) {
                    sorted = false;
                }
                keys[n] = key;
                vals[n] = new String(buf, 0, c);
                n++;
            }
            p = e + 1;
        }
        if (!sorted) { // insertion sort (files are normally written in key order)
            for (int i = 1; i < n; i++) {
                int k = keys[i];
                String v = vals[i];
                int j = i - 1;
                while (j >= 0 && keys[j] > k) {
                    keys[j + 1] = keys[j];
                    vals[j + 1] = vals[j];
                    j--;
                }
                keys[j + 1] = k;
                vals[j + 1] = v;
            }
        }
        f.n = n;
        f.keys = keys;
        f.vals = vals;
        return f;
    }
}
