package hl4.game;

import hl4.sys.Bytes;

/**
 * A pooled game object: the original's fixed-size struct (the bytes are kept in 'm' with the ORIGINAL offsets and widths, so every
 * byte/short/int field keeps its exact truncation and sign behaviour) plus the pointer fields as Java references.
 * Common header (OBJ_* @0xc922c..): +0 prev, +4 next (here: references), +8 x, +0xc y, +0x10 self pointer (here: this),
 * +0x14 struct type, +0x15 free flag (1 = in the pool), +0x16 pool index, +0x18 visible, +0x19 sort-Y offset (s8), +0x1a release type,
 * +0x1b event index (s8), +0x1c create-map index (s16).
 */
public class Obj {
    public static final int X = 0x08;
    public static final int Y = 0x0c;
    public static final int TYPE = 0x14;
    public static final int FREE = 0x15;
    public static final int IDX = 0x16;
    public static final int VISIBLE = 0x18;
    public static final int SORTY = 0x19;
    public static final int RELEASE = 0x1a;
    public static final int EVENT = 0x1b;
    public static final int MAPIDX = 0x1c;

    public final byte[] m;
    public Obj prev;
    public Obj next;
    /** event record index (the original keeps a pointer to _event+10+10k at +0x20 of map objects / +0x1b0 of NPCs), -1 = none */
    public int evRef = -1;

    public Obj(int size, int type, int idx) {
        m = new byte[size];
        Bytes.w8(m, TYPE, type);
        Bytes.w8(m, FREE, 1);
        Bytes.w16(m, IDX, idx);
    }

    public final int x() {
        return Bytes.s32(m, X);
    }

    public final int y() {
        return Bytes.s32(m, Y);
    }

    /** OBJ_getStructType (a null object has type 0x11) */
    public static int structType(Obj o) {
        return o == null ? 0x11 : m8(o.m, TYPE);
    }

    private static int m8(byte[] b, int o) {
        return b[o];
    }

    /** OBJ_getSortY @0xc927c */
    public final int sortY() {
        return m[SORTY] + Bytes.s32(m, Y);
    }

    /**
     * OBJ_set @0xc9318 (also OBJ_destroy / OBJ_return): header reset, free flag = 'avail'.
     * x = y = 0, visible = 1, sort-Y offset 0, event -1, release type 0, create-map index 0xffff.
     */
    public final void objSet(int avail) {
        prev = null;
        next = null;
        Bytes.w8(m, FREE, avail);
        Bytes.w32(m, X, 0);
        Bytes.w32(m, Y, 0);
        Bytes.w32(m, VISIBLE, 1);                 // +0x18..0x1b
        Bytes.w32(m, SORTY, 0);                   // +0x19..0x1c
        Bytes.w8(m, EVENT, 0xff);
        Bytes.w8(m, RELEASE, 0);
        Bytes.w32(m, MAPIDX, 0xffff);
    }
}
