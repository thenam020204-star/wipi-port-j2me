package wipi.host;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.microedition.io.StreamConnection;

/**
 * In-process stand-in for the GXG billing/ranking gateway used by the game
 * (gxg.GXGNet + NetWork). Packet = 25-byte header ("GXG.01.00.03", 2-char
 * type, 0x4A, ASCII data length) + data + parity byte 23.
 *
 * Cash (backend port 15135):  1006 -> C8 ; C9(price) -> CA(tx) ; CB -> CC (done)
 * Rank (backend port 18007):  type 1 (my score) -> 6 (top list) + 10 (my rank)
 * The local player is the only entry of the offline ranking board.
 */
final class FakeGxg implements StreamConnection {
    private static final byte PARITY = 23;

    private byte[] in = new byte[0];
    private int inPos;
    private final byte[] out = new byte[4096];
    private int outLen;
    private boolean cash;

    // ------------------------------------------------------------------ streams

    private final InputStream is = new InputStream() {
        public int read() {
            synchronized (FakeGxg.this) {
                return inPos < in.length ? in[inPos++] & 0xff : -1;
            }
        }

        public int read(byte[] b, int off, int n) {
            synchronized (FakeGxg.this) {
                int a = in.length - inPos;
                if (a <= 0) {
                    return -1;
                }
                if (n > a) {
                    n = a;
                }
                System.arraycopy(in, inPos, b, off, n);
                inPos += n;
                return n;
            }
        }

        public int available() {
            synchronized (FakeGxg.this) {
                return in.length - inPos;
            }
        }
    };

    private final OutputStream os = new OutputStream() {
        public void write(int b) throws IOException {
            write(new byte[] { (byte) b }, 0, 1);
        }

        public void write(byte[] b, int off, int n) throws IOException {
            synchronized (FakeGxg.this) {
                if (outLen + n > out.length) {
                    throw new IOException("packet too big");
                }
                System.arraycopy(b, off, out, outLen, n);
                outLen += n;
                pump();
            }
        }
    };

    public InputStream openInputStream() {
        return is;
    }

    public DataInputStream openDataInputStream() {
        return new DataInputStream(is);
    }

    public OutputStream openOutputStream() {
        return os;
    }

    public DataOutputStream openDataOutputStream() {
        return new DataOutputStream(os);
    }

    public void close() {
    }

    // ------------------------------------------------------------------ protocol

    private static int asciiInt(byte[] b, int off, int n) {
        int v = 0;
        for (int i = 0; i < n; i++) {
            int c = b[off + i];
            if (c >= '0' && c <= '9') {
                v = v * 10 + (c - '0');
            }
        }
        return v;
    }

    /** Handles every complete packet in the output buffer. */
    private void pump() {
        while (outLen >= 25) {
            int dlen = asciiInt(out, 15, 10);
            if (outLen < 25 + dlen) {
                return;
            }
            byte[] data = new byte[dlen > 0 ? dlen - 1 : 0];
            System.arraycopy(out, 25, data, 0, data.length);
            char t0 = (char) out[12], t1 = (char) out[13];
            System.arraycopy(out, 25 + dlen, out, 0, outLen - 25 - dlen);
            outLen -= 25 + dlen;
            handle(t0, t1, data);
        }
    }

    private void handle(char t0, char t1, byte[] d) {
        if (t0 == 'I' && t1 == 'P') {
            // MCID 10, MIN 21, ip len, ip, port 7 ...
            int ipl = d.length > 31 ? d[31] : 0;
            int port = d.length >= 32 + ipl + 7 ? asciiInt(d, 32 + ipl, 7) : 0;
            cash = port == 15135;
            byte[] r = new byte[1 + 1 + 1 + 7 + 1 + 1 + 100];
            r[0] = 0; // GE_SUCCESS
            send("IP", r);
        } else if (t0 == 'D' && t1 == 'P') {
            if (cash) {
                cashPacket(d);
            } else {
                rankPacket(d);
            }
        }
        // other requests are not used by this game
    }

    private static int s16(byte[] b, int o) {
        return ((b[o] & 0xff) << 8) | (b[o + 1] & 0xff);
    }

    private static void put16(byte[] b, int o, int v) {
        b[o] = (byte) (v >> 8);
        b[o + 1] = (byte) v;
    }

    private static void put32(byte[] b, int o, int v) {
        b[o] = (byte) (v >>> 24);
        b[o + 1] = (byte) (v >>> 16);
        b[o + 2] = (byte) (v >>> 8);
        b[o + 3] = (byte) v;
    }

    private void cashPacket(byte[] d) {
        if (d.length < 5) {
            return;
        }
        int type = d[3];
        if (type == 0 && d.length >= 7 && s16(d, 5) == 1006) {
            byte[] r = new byte[5];
            put16(r, 0, r.length);
            r[3] = (byte) 0xC8; // ask for the price
            send("DP", r);
        } else if (type == (byte) 0xC9) {
            byte[] r = new byte[17];
            put16(r, 0, r.length);
            r[3] = (byte) 0xCA; // transaction id + 8-char code
            put32(r, 5, (int) (System.currentTimeMillis() & 0x7fffffff));
            byte[] code = "OFFLINE0".getBytes();
            System.arraycopy(code, 0, r, 9, 8);
            send("DP", r);
        } else if (type == (byte) 0xCB) {
            byte[] r = new byte[5];
            put16(r, 0, r.length);
            r[3] = (byte) 0xCC; // purchase complete
            send("DP", r);
        }
    }

    private void rankPacket(byte[] d) {
        if (d.length < 15 || d[2] != 1) {
            return;
        }
        int pl = d[13] & 0xff;
        if (14 + pl > d.length) {
            return;
        }
        byte[] phone = new byte[pl];
        System.arraycopy(d, 14, phone, 0, pl);
        int point = 0;
        if (d.length >= 4) {
            int o = d.length - 4;
            point = ((d[o] & 0xff) << 24) | ((d[o + 1] & 0xff) << 16) | ((d[o + 2] & 0xff) << 8) | (d[o + 3] & 0xff);
        }
        byte[] r = new byte[5 + 5 + pl + 4];
        put16(r, 0, r.length);
        r[2] = 6;
        r[4] = 1;
        r[9] = (byte) pl;
        System.arraycopy(phone, 0, r, 10, pl);
        put32(r, 10 + pl, point);
        send("DP", r);
        byte[] m = new byte[11];
        put16(m, 0, m.length);
        m[2] = 10;
        put32(m, 3, 1);
        put32(m, 7, point);
        send("DP", m);
    }

    private void send(String type, byte[] data) {
        int dlen = data.length + 1;
        byte[] p = new byte[25 + dlen];
        byte[] v = "GXG.01.00.03".getBytes();
        System.arraycopy(v, 0, p, 0, 12);
        p[12] = (byte) type.charAt(0);
        p[13] = (byte) type.charAt(1);
        p[14] = 74;
        byte[] l = String.valueOf(dlen).getBytes();
        System.arraycopy(l, 0, p, 15, l.length);
        System.arraycopy(data, 0, p, 25, data.length);
        p[25 + data.length] = PARITY;
        byte[] n = new byte[in.length - inPos + p.length];
        System.arraycopy(in, inPos, n, 0, in.length - inPos);
        System.arraycopy(p, 0, n, in.length - inPos, p.length);
        in = n;
        inPos = 0;
    }
}
