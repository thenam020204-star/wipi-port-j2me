package org.kwis.msp.io;

import java.io.IOException;

import wipi.host.Store;

/**
 * WIPI file handle. Contents live in memory; writes reach RMS in one block on
 * close() so a crash mid-save never leaves a half-written file.
 */
public class File {
    public static final int READ_ONLY = 1;
    public static final int WRITE = 2;
    public static final int READ_WRITE = 3;
    public static final int WRITE_TRUNC = 4;

    private final String name;
    private final int mode;
    private byte[] buf;
    private int len;
    private int pos;
    private boolean dirty;
    private boolean open = true;

    public File(String name, int mode) throws IOException {
        this.name = name;
        this.mode = mode;
        byte[] b = Store.read(name);
        if (b == null) {
            if (mode == READ_ONLY) {
                throw new IOException("no file " + name);
            }
            buf = new byte[64];
            len = 0;
            dirty = true; // creating a file makes it exist even if nothing is written
        } else {
            buf = b;
            len = b.length;
            if (mode == WRITE_TRUNC) {
                len = 0;
                dirty = true;
            }
        }
    }

    public File(String name, int mode, int unused) throws IOException {
        this(name, mode);
    }

    private void check() throws IOException {
        if (!open) {
            throw new IOException("closed");
        }
    }

    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    public int read(byte[] b, int off, int n) throws IOException {
        check();
        if (pos >= len) {
            return -1;
        }
        if (n > len - pos) {
            n = len - pos;
        }
        System.arraycopy(buf, pos, b, off, n);
        pos += n;
        return n;
    }

    public int write(byte[] b) throws IOException {
        return write(b, 0, b.length);
    }

    public int write(byte[] b, int off, int n) throws IOException {
        check();
        if (mode == READ_ONLY) {
            throw new IOException("read only");
        }
        if (pos + n > buf.length) {
            int cap = buf.length * 2;
            if (cap < pos + n) {
                cap = pos + n;
            }
            byte[] nb = new byte[cap];
            System.arraycopy(buf, 0, nb, 0, len);
            buf = nb;
        }
        System.arraycopy(b, off, buf, pos, n);
        pos += n;
        if (pos > len) {
            len = pos;
        }
        dirty = true;
        return n;
    }

    public int seek(int p) throws IOException {
        check();
        if (p < 0) {
            p = 0;
        }
        pos = p;
        return pos;
    }

    public int sizeOf() throws IOException {
        check();
        return len;
    }

    public void close() throws IOException {
        if (!open) {
            return;
        }
        open = false;
        if (dirty) {
            dirty = false;
            Store.write(name, buf, len);
        }
        buf = null;
    }
}
