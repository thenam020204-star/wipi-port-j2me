package hl4.cpu;

/**
 * Flat 32-bit little-endian address space backed by lazily allocated 64KB pages.
 * Each page is an int[16384] so aligned word accesses are a single array load.
 * CLDC 1.1 compatible.
 */
public final class Memory {
    public static final int PAGE_SHIFT = 16;
    public static final int PAGE_SIZE = 1 << PAGE_SHIFT;
    public static final int PAGE_MASK = PAGE_SIZE - 1;
    public static final int PAGE_WORDS = PAGE_SIZE >> 2;

    public final int[][] pages = new int[1 << (32 - PAGE_SHIFT)][];

    /** Map [addr, addr+size) with zero filled pages (existing pages are kept). */
    public void map(int addr, int size) {
        if (size <= 0) return;
        long start = (addr & 0xFFFFFFFFL) >>> PAGE_SHIFT;
        long end = ((addr & 0xFFFFFFFFL) + size - 1) >>> PAGE_SHIFT;
        for (long p = start; p <= end; p++) {
            if (pages[(int) p] == null && (watched == null || watched[(int) p] == null)) pages[(int) p] = new int[PAGE_WORDS];
        }
    }

    public boolean isMapped(int addr) {
        return pages[addr >>> PAGE_SHIFT] != null || (watched != null && watched[addr >>> PAGE_SHIFT] != null);
    }

    /** Debug watchpoints: a watched page is removed from {@link #pages} so accesses fault into the hook. */
    public interface Watch {
        void hit(int addr, boolean write);
    }

    public Watch watch;
    private int[][] watched;

    /** Watch every access to the pages covering [addr, addr+size). */
    public void watchRange(int addr, int size) {
        if (watched == null) watched = new int[pages.length][];
        for (int a = addr & ~PAGE_MASK; a - addr < size; a += PAGE_SIZE) {
            int i = a >>> PAGE_SHIFT;
            if (pages[i] != null) {
                watched[i] = pages[i];
                pages[i] = null;
            }
        }
    }

    public void unwatchAll() {
        if (watched == null) return;
        for (int i = 0; i < watched.length; i++) {
            if (watched[i] != null) {
                pages[i] = watched[i];
                watched[i] = null;
            }
        }
    }

    private int[] fault(int addr, boolean write) {
        if (watched != null) {
            int[] p = watched[addr >>> PAGE_SHIFT];
            if (p != null) {
                if (watch != null) watch.hit(addr, write);
                return p;
            }
        }
        throw new CpuException("Unmapped " + (write ? "write" : "read") + " at 0x" + Integer.toHexString(addr));
    }

    private int[] page(int addr) {
        int[] p = pages[addr >>> PAGE_SHIFT];
        if (p == null) p = fault(addr, false);
        return p;
    }

    /** Aligned or unaligned raw little-endian 32-bit read (no ARM rotation). */
    public int read32(int addr) {
        if ((addr & 3) == 0) {
            int[] p = pages[addr >>> PAGE_SHIFT];
            if (p == null) p = fault(addr, false);
            return p[(addr & PAGE_MASK) >> 2];
        }
        return read8(addr) | (read8(addr + 1) << 8) | (read8(addr + 2) << 16) | (read8(addr + 3) << 24);
    }

    public int read16(int addr) {
        if ((addr & 1) == 0) {
            int[] p = pages[addr >>> PAGE_SHIFT];
            if (p == null) p = fault(addr, false);
            return (p[(addr & PAGE_MASK) >> 2] >>> ((addr & 2) << 3)) & 0xFFFF;
        }
        return read8(addr) | (read8(addr + 1) << 8);
    }

    public int read8(int addr) {
        int[] p = pages[addr >>> PAGE_SHIFT];
        if (p == null) p = fault(addr, false);
        return (p[(addr & PAGE_MASK) >> 2] >>> ((addr & 3) << 3)) & 0xFF;
    }

    public void write32(int addr, int value) {
        if ((addr & 3) == 0) {
            int[] p = pages[addr >>> PAGE_SHIFT];
            if (p == null) p = fault(addr, true);
            p[(addr & PAGE_MASK) >> 2] = value;
            return;
        }
        write8(addr, value);
        write8(addr + 1, value >>> 8);
        write8(addr + 2, value >>> 16);
        write8(addr + 3, value >>> 24);
    }

    public void write16(int addr, int value) {
        if ((addr & 1) == 0) {
            int[] p = pages[addr >>> PAGE_SHIFT];
            if (p == null) p = fault(addr, true);
            int i = (addr & PAGE_MASK) >> 2;
            int sh = (addr & 2) << 3;
            p[i] = (p[i] & ~(0xFFFF << sh)) | ((value & 0xFFFF) << sh);
            return;
        }
        write8(addr, value);
        write8(addr + 1, value >>> 8);
    }

    public void write8(int addr, int value) {
        int[] p = pages[addr >>> PAGE_SHIFT];
        if (p == null) p = fault(addr, true);
        int i = (addr & PAGE_MASK) >> 2;
        int sh = (addr & 3) << 3;
        p[i] = (p[i] & ~(0xFF << sh)) | ((value & 0xFF) << sh);
    }

    // ---- bulk helpers ----

    public void writeBytes(int addr, byte[] src, int off, int len) {
        int i = 0;
        // head
        while (i < len && ((addr + i) & 3) != 0) {
            write8(addr + i, src[off + i]);
            i++;
        }
        while (len - i >= 4) {
            int a = addr + i;
            int[] p = page(a);
            int o = off + i;
            p[(a & PAGE_MASK) >> 2] = (src[o] & 0xFF) | ((src[o + 1] & 0xFF) << 8) | ((src[o + 2] & 0xFF) << 16) | ((src[o + 3] & 0xFF) << 24);
            i += 4;
        }
        while (i < len) {
            write8(addr + i, src[off + i]);
            i++;
        }
    }

    public void readBytes(int addr, byte[] dst, int off, int len) {
        int i = 0;
        while (i < len && ((addr + i) & 3) != 0) {
            dst[off + i] = (byte) read8(addr + i);
            i++;
        }
        while (len - i >= 4) {
            int a = addr + i;
            int[] p = page(a);
            int w = p[(a & PAGE_MASK) >> 2];
            int o = off + i;
            dst[o] = (byte) w;
            dst[o + 1] = (byte) (w >>> 8);
            dst[o + 2] = (byte) (w >>> 16);
            dst[o + 3] = (byte) (w >>> 24);
            i += 4;
        }
        while (i < len) {
            dst[off + i] = (byte) read8(addr + i);
            i++;
        }
    }

    /** memmove(dst, src, len) inside guest memory. */
    public void move(int dst, int src, int len) {
        if (len <= 0 || dst == src) return;
        long d = dst & 0xFFFFFFFFL, s = src & 0xFFFFFFFFL;
        boolean overlap = d < s + len && s < d + len;
        if (!overlap && ((dst ^ src) & 3) == 0) {
            int i = 0;
            while (i < len && ((dst + i) & 3) != 0) {
                write8(dst + i, read8(src + i));
                i++;
            }
            while (len - i >= 4) {
                int a = dst + i, b = src + i;
                int[] pd = page(a), ps = page(b);
                int od = (a & PAGE_MASK) >> 2, os = (b & PAGE_MASK) >> 2;
                int n = Math.min((len - i) >> 2, Math.min(PAGE_WORDS - od, PAGE_WORDS - os));
                System.arraycopy(ps, os, pd, od, n);
                i += n << 2;
            }
            while (i < len) {
                write8(dst + i, read8(src + i));
                i++;
            }
            return;
        }
        boolean half = ((dst | src | len) & 1) == 0;
        if (!overlap || d < s) {
            if (half) for (int i = 0; i < len; i += 2) write16(dst + i, read16(src + i));
            else for (int i = 0; i < len; i++) write8(dst + i, read8(src + i));
        } else {
            if (half) for (int i = len - 2; i >= 0; i -= 2) write16(dst + i, read16(src + i));
            else for (int i = len - 1; i >= 0; i--) write8(dst + i, read8(src + i));
        }
    }

    public byte[] readBytes(int addr, int len) {
        byte[] b = new byte[len];
        readBytes(addr, b, 0, len);
        return b;
    }

    public void fill(int addr, int value, int len) {
        value &= 0xFF;
        int word = value | (value << 8) | (value << 16) | (value << 24);
        int i = 0;
        while (i < len && ((addr + i) & 3) != 0) {
            write8(addr + i, value);
            i++;
        }
        while (len - i >= 4) {
            int a = addr + i;
            page(a)[(a & PAGE_MASK) >> 2] = word;
            i += 4;
        }
        while (i < len) {
            write8(addr + i, value);
            i++;
        }
    }

    public void copy(int dst, int src, int len) {
        if (len <= 0 || dst == src) return;
        if (dst > src && dst < src + len) {
            for (int i = len - 1; i >= 0; i--) write8(dst + i, read8(src + i));
        } else {
            move(dst, src, len); // forward copy: page-wise arraycopy when co-aligned
        }
    }

    public int strlen(int addr) {
        int n = 0;
        while (read8(addr + n) != 0) n++;
        return n;
    }

    public byte[] readCString(int addr) {
        return readBytes(addr, strlen(addr));
    }

    public void writeCString(int addr, byte[] s) {
        writeBytes(addr, s, 0, s.length);
        write8(addr + s.length, 0);
    }
}
