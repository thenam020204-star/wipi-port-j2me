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
     * OBJ_set(o, pos) @0xb9318 (Ghidra 0xc9318; with pos = {self, type, 'avail', idx}; also the head of OBJ_destroy / OBJ_return):
     * header reset, free flag = 'avail'.  x = y = 0, visible = 1, sort-Y offset 0, event -1 (OBJ_setEventIdx), release type 0 and
     * create-map index -1 (OBJ_initReleaseTypeCreateMapIdx: a halfword store, bytes +0x1e/+0x1f are left alone).
     */
    public final void objSet(int avail) {
        prev = null;
        next = null;
        Bytes.w8(m, FREE, avail);
        Bytes.w32(m, X, 0);
        Bytes.w32(m, Y, 0);
        Bytes.w8(m, VISIBLE, 1);
        Bytes.w8(m, SORTY, 0);
        Bytes.w8(m, EVENT, 0xff);
        Bytes.w8(m, RELEASE, 0);
        Bytes.w16(m, MAPIDX, 0xffff);
    }

    /** OBJ_getPtr(o) @0xb9258: the object's self pointer at +0x10 (here the object itself; NULL stays NULL) */
    public static Obj getPtr(Obj o) {
        return o;
    }

    /** OBJ_next(o) @0xb9264: the next node of the list the object is linked in (NULL stays NULL) */
    public static Obj next(Obj o) {
        return o == null ? null : o.next;
    }

    /** OBJ_setPosition(o, x, y) @0xb923c */
    public static void setPosition(Obj o, int x, int y) {
        Bytes.w32(o.m, Y, y);
        Bytes.w32(o.m, X, x);
    }

    /** OBJ_getVisible(o) @0xb9248 (ldrb) */
    public static int getVisible(Obj o) {
        return o.m[VISIBLE] & 0xff;
    }

    /** OBJ_setVisible(o, v) @0xb9250 (strb) */
    public static void setVisible(Obj o, int v) {
        o.m[VISIBLE] = (byte) v;
    }

    /** OBJ_setReleaseType(o, t) @0xb928c (strb) */
    public static void setReleaseType(Obj o, int t) {
        o.m[RELEASE] = (byte) t;
    }

    /** OBJ_setCreateMapIdx(o, idx) @0xb929c (strh) */
    public static void setCreateMapIdx(Obj o, int idx) {
        Bytes.w16(o.m, MAPIDX, idx);
    }

    /** OBJ_initReleaseTypeCreateMapIdx(o) @0xb92a4: release type 0, create-map index -1 */
    public static void initReleaseTypeCreateMapIdx(Obj o) {
        setReleaseType(o, 0);
        setCreateMapIdx(o, -1);
    }

    /** OBJ_getCreateMapIdx(o) @0xb92c4 (ldrsh) */
    public static int getCreateMapIdx(Obj o) {
        return Bytes.s16(o.m, MAPIDX);
    }

    /** OBJ_setEventIdx(o, idx) @0xb9310 (strb) */
    public static void setEventIdx(Obj o, int idx) {
        o.m[EVENT] = (byte) idx;
    }

    /** OBJ_getEventIdx(o) @0xb9408 (ldrsb) */
    public static int getEventIdx(Obj o) {
        return o.m[EVENT];
    }

    /** OBJ_destroy(o) @0xb9368: OBJ_set(o, {self, type, 1, idx}) (= objSet(1)), OBJ_setEventIdx(o, -1), OBJ_initReleaseTypeCreateMapIdx(o) */
    public static void destroy(Obj o) {
        o.objSet(1);
        setEventIdx(o, -1);
        initReleaseTypeCreateMapIdx(o);
    }

    /** OBJ_return(o) @0xb93b8: the same body as OBJ_destroy */
    public static void ret(Obj o) {
        o.objSet(1);
        setEventIdx(o, -1);
        initReleaseTypeCreateMapIdx(o);
    }
}
