package wipi.host;

import java.util.Hashtable;

import javax.microedition.rms.RecordStore;

/**
 * WIPI files on RMS. Each file is one record store "F<name>" holding a single
 * record, plus a backup store "B<name>" written first. Record layout:
 *   u8 format (0 raw, 1 zero-RLE) | u32 length | u32 checksum | payload
 * A file whose main store is missing or corrupt is read from the backup.
 * Store names are kept short (RMS limit 32 chars).
 */
public final class Store {
    private static boolean listed;
    private static Hashtable present; // name -> Boolean

    private static String main(String n) {
        return "F" + n;
    }

    private static String bak(String n) {
        return "B" + n;
    }

    private static synchronized void list() {
        if (listed) {
            return;
        }
        listed = true;
        present = new Hashtable();
        try {
            String[] s = RecordStore.listRecordStores();
            if (s != null) {
                for (int i = 0; i < s.length; i++) {
                    if (s[i].length() > 1 && (s[i].charAt(0) == 'F' || s[i].charAt(0) == 'B')) {
                        present.put(s[i].substring(1), Boolean.TRUE);
                    }
                }
            }
        } catch (Throwable t) {
            Log.e("rms list", t);
        }
    }

    public static synchronized boolean exists(String name) {
        list();
        return present.get(name) != null;
    }

    public static synchronized byte[] read(String name) {
        list();
        if (present.get(name) == null) {
            return null;
        }
        byte[] b = load(main(name));
        if (b == null) {
            b = load(bak(name));
            if (b != null) {
                Log.i("rms: restored " + name + " from backup");
            }
        }
        if (b == null) {
            present.remove(name);
            return null;
        }
        return b;
    }

    public static synchronized void write(String name, byte[] data, int len) {
        list();
        byte[] rec = encode(data, len);
        boolean ok = save(bak(name), rec);
        boolean ok2 = save(main(name), rec);
        if (ok2) {
            byte[] chk = load(main(name));
            if (chk == null || chk.length != len) {
                ok2 = save(main(name), rec);
            }
        }
        if (!ok && !ok2) {
            Log.i("rms: write failed " + name);
        }
        present.put(name, Boolean.TRUE);
    }

    public static synchronized void remove(String name) {
        list();
        present.remove(name);
        del(main(name));
        del(bak(name));
    }

    public static int available() {
        try {
            RecordStore rs = RecordStore.openRecordStore("Fsys", true);
            int n = rs.getSizeAvailable();
            rs.closeRecordStore();
            return n;
        } catch (Throwable t) {
            return 100000;
        }
    }

    static void flush() {
        // writes are synchronous; nothing buffered here
    }

    private static void del(String store) {
        try {
            RecordStore.deleteRecordStore(store);
        } catch (Throwable t) {
            // not there
        }
    }

    private static boolean save(String store, byte[] rec) {
        RecordStore rs = null;
        try {
            del(store);
            rs = RecordStore.openRecordStore(store, true);
            rs.addRecord(rec, 0, rec.length);
            return true;
        } catch (Throwable t) {
            Log.e("rms save " + store, t);
            return false;
        } finally {
            if (rs != null) {
                try {
                    rs.closeRecordStore();
                } catch (Throwable t) {
                }
            }
        }
    }

    /** Reads and validates a store; null if missing or corrupt. Never uses RecordEnumeration. */
    private static byte[] load(String store) {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(store, false);
            int next = rs.getNextRecordID();
            for (int id = next - 1; id >= 1; id--) {
                byte[] r;
                try {
                    r = rs.getRecord(id);
                } catch (Throwable t) {
                    continue;
                }
                byte[] d = decode(r);
                if (d != null) {
                    return d;
                }
            }
            return null;
        } catch (Throwable t) {
            return null;
        } finally {
            if (rs != null) {
                try {
                    rs.closeRecordStore();
                } catch (Throwable t) {
                }
            }
        }
    }

    // ------------------------------------------------------------------ format

    private static int sum(byte[] b, int off, int n) {
        int a = 1, c = 0;
        for (int i = 0; i < n; i++) {
            a = (a + (b[off + i] & 0xff)) % 65521;
            c = (c + a) % 65521;
        }
        return (c << 16) | a;
    }

    private static void put32(byte[] b, int o, int v) {
        b[o] = (byte) (v >>> 24);
        b[o + 1] = (byte) (v >>> 16);
        b[o + 2] = (byte) (v >>> 8);
        b[o + 3] = (byte) v;
    }

    private static int get32(byte[] b, int o) {
        return ((b[o] & 0xff) << 24) | ((b[o + 1] & 0xff) << 16) | ((b[o + 2] & 0xff) << 8) | (b[o + 3] & 0xff);
    }

    /** Zero-run encoding: 0x00 n (n+1 zeros, n<=255) ; other bytes literal. */
    static byte[] encode(byte[] d, int len) {
        byte[] t = new byte[9 + len + len / 128 + 4];
        int p = 9;
        int i = 0;
        while (i < len) {
            if (d[i] == 0) {
                int run = 1;
                while (i + run < len && d[i + run] == 0 && run < 256) {
                    run++;
                }
                t[p++] = 0;
                t[p++] = (byte) (run - 1);
                i += run;
            } else {
                t[p++] = d[i++];
            }
            if (p > 9 + len) {
                break; // incompressible: fall back to raw
            }
        }
        byte[] out;
        if (i < len || p >= 9 + len) {
            out = new byte[9 + len];
            System.arraycopy(d, 0, out, 9, len);
            out[0] = 0;
        } else {
            out = new byte[p];
            System.arraycopy(t, 9, out, 9, p - 9);
            out[0] = 1;
        }
        put32(out, 1, len);
        put32(out, 5, sum(d, 0, len));
        return out;
    }

    static byte[] decode(byte[] r) {
        if (r == null || r.length < 9) {
            return null;
        }
        int len = get32(r, 1);
        if (len < 0 || len > 1 << 20) {
            return null;
        }
        byte[] d = new byte[len];
        if (r[0] == 0) {
            if (r.length != 9 + len) {
                return null;
            }
            System.arraycopy(r, 9, d, 0, len);
        } else if (r[0] == 1) {
            int p = 9, o = 0;
            while (p < r.length) {
                byte b = r[p++];
                if (b == 0) {
                    if (p >= r.length) {
                        return null;
                    }
                    int run = (r[p++] & 0xff) + 1;
                    if (o + run > len) {
                        return null;
                    }
                    o += run; // already zero
                } else {
                    if (o >= len) {
                        return null;
                    }
                    d[o++] = b;
                }
            }
            if (o != len) {
                return null;
            }
        } else {
            return null;
        }
        if (sum(d, 0, len) != get32(r, 5)) {
            return null;
        }
        return d;
    }
}
