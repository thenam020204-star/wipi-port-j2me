package hl4.core;

import hl4.cpu.Memory;
import java.util.Hashtable;

/**
 * Guest heap allocator (first fit, address ordered free list with coalescing).
 * Metadata lives on the host side so guest code can never corrupt it.
 */
public final class Heap {
    private final Memory m;
    public final int base;
    public final int limit;
    private int top;
    private int[] freeAddr = new int[256];
    private int[] freeSize = new int[256];
    private int freeCount;
    private final Hashtable sizes = new Hashtable();
    public int used;
    public int peak;

    public Heap(Memory m, int base, int size) {
        this.m = m;
        this.base = base;
        this.limit = base + size;
        this.top = base;
    }

    public int alloc(int size) {
        if (size <= 0) size = 1;
        size = (size + 7) & ~7;
        int addr = 0;
        for (int i = 0; i < freeCount; i++) {
            if (freeSize[i] >= size) {
                addr = freeAddr[i];
                if (freeSize[i] == size) {
                    removeFree(i);
                } else {
                    freeAddr[i] += size;
                    freeSize[i] -= size;
                }
                break;
            }
        }
        if (addr == 0) {
            if (top + size > limit || top + size < top) return 0;
            addr = top;
            top += size;
            m.map(addr, size);
        }
        sizes.put(new Integer(addr), new Integer(size));
        used += size;
        if (used > peak) peak = used;
        return addr;
    }

    public int calloc(int size) {
        int a = alloc(size);
        if (a != 0) m.fill(a, 0, (size + 7) & ~7);
        return a;
    }

    /** Returns the block size or -1 if addr is not an allocated block. */
    public int sizeOf(int addr) {
        Integer s = (Integer) sizes.get(new Integer(addr));
        return s == null ? -1 : s.intValue();
    }

    public boolean free(int addr) {
        Integer s = (Integer) sizes.remove(new Integer(addr));
        if (s == null) return false;
        int size = s.intValue();
        used -= size;
        insertFree(addr, size);
        return true;
    }

    private void removeFree(int i) {
        System.arraycopy(freeAddr, i + 1, freeAddr, i, freeCount - i - 1);
        System.arraycopy(freeSize, i + 1, freeSize, i, freeCount - i - 1);
        freeCount--;
    }

    private void insertFree(int addr, int size) {
        // binary search insertion point
        int lo = 0, hi = freeCount;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (freeAddr[mid] < addr) lo = mid + 1;
            else hi = mid;
        }
        int i = lo;
        // merge with previous
        if (i > 0 && freeAddr[i - 1] + freeSize[i - 1] == addr) {
            freeSize[i - 1] += size;
            // merge with next
            if (i < freeCount && freeAddr[i - 1] + freeSize[i - 1] == freeAddr[i]) {
                freeSize[i - 1] += freeSize[i];
                removeFree(i);
            }
            i = i - 1;
        } else if (i < freeCount && addr + size == freeAddr[i]) {
            freeAddr[i] = addr;
            freeSize[i] += size;
        } else {
            if (freeCount == freeAddr.length) {
                int[] na = new int[freeCount * 2];
                int[] ns = new int[freeCount * 2];
                System.arraycopy(freeAddr, 0, na, 0, freeCount);
                System.arraycopy(freeSize, 0, ns, 0, freeCount);
                freeAddr = na;
                freeSize = ns;
            }
            System.arraycopy(freeAddr, i, freeAddr, i + 1, freeCount - i);
            System.arraycopy(freeSize, i, freeSize, i + 1, freeCount - i);
            freeAddr[i] = addr;
            freeSize[i] = size;
            freeCount++;
        }
        // give back to top
        if (freeCount > 0 && freeAddr[freeCount - 1] + freeSize[freeCount - 1] == top) {
            top = freeAddr[freeCount - 1];
            freeCount--;
        }
    }

    public int freeBytes() {
        int f = limit - top;
        for (int i = 0; i < freeCount; i++) f += freeSize[i];
        return f;
    }
}
