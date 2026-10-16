package hl4.game;

import hl4.data.MapData;
import hl4.gfx.Frame;
import hl4.gfx.Fx;
import hl4.gfx.Gbm;
import hl4.sys.Bytes;
import hl4.sys.Res;

/**
 * The map module (Map_*, MAPOBJ_*, SprObject_*, DropItem_*, Tile_*) over the _map byte struct (896 bytes).  Known _map offsets:
 * +0x08/0x0c/0x10 monster bodies, +0x14.. 10 sprite-object bodies, +0x44 map index (s16), +0x48/4a/4c monster types, +0x68 time class, +0x69 bgm,
 * +0x6c/0x70/0x74 background rgb, +0x78 time slot, +0x79 battle type, +0x7a/0x7b map size in tiles, +0x7e/0x7f tile size, +0x84/0x88 map size in px,
 * +0x94/0x95 draw flags, +0x96/0x97 scrolling allowed, +0x9c/0xa0 scroll, +0xa4/0xa8 previous scroll, +0xac/0xb0 centring offset, +0xbc/0xc0 scroll delta,
 * +0xc4..0xd8 camera follow, +0x270 tileset count, +0x271.. tileset ids, +0x2d4 scene number, +0x2d8.. , +0x364.. monster bookkeeping.
 */
public final class Maps {
    public static final byte[] m = G.map;

    public static MapData data;
    public static byte[] layerA;
    public static byte[] layerB;
    public static Gbm[] tiles = new Gbm[3];
    /** loaded object images, one per entry of the object-image id list (Map_objIdxCreate order) */
    public static Gbm[] objImgs;
    public static int[] objIds = new int[0];
    public static int nObj;
    public static Frame hStrip;
    public static Frame vStrip;

    private Maps() {
    }

    // ---- small helpers ---------------------------------------------------------------------------------------------------------------------
    public static int s32(int off) {
        return Bytes.s32(m, off);
    }

    public static void w32(int off, int v) {
        Bytes.w32(m, off, v);
    }

    public static int timeReturn(int v) {
        return v;                       // Map_Time_Return is an empty function (returns its argument)
    }

    /** Map_initDrawFlag @0xa0fd0 */
    public static void initDrawFlag() {
        m[0x94] = 9;
        m[0x95] = 9;
    }

    /** Map_BG_Return @0xa1374 */
    public static void bgReturn(int idx) {
        w32(0x74, 0xf);
        w32(0x70, 0xd);
        w32(0x6c, 0xd);
    }

    /** Map_variableInit @0xac040 */
    public static void variableInit() {
        m[0x7f] = 0x10;
        m[0x7e] = 0x10;
        m[0x7a] = 0;
        m[0x7b] = 0;
        w32(0x84, 0);
        w32(0x88, 0);
        w32(0xac, 0);
        w32(0xb0, 0);
        m[0x96] = 0;
        m[0x97] = 0;
        initDrawFlag();
        Bytes.w16(m, 0x2e2, 0);
        Bytes.w16(m, 0x2e0, 0);
        m[0x2db] = 0;
        m[0x2dd] = (byte) 0xff;
        m[0x2de] = 0;
        m[0x2dc] = 0;
        m[0x2df] = 0;
        Bytes.fill(m, 0x641, 0, 0x1b);
        layerB = null;
        layerA = null;
        tiles[0] = null;
        tiles[1] = null;
        tiles[2] = null;
        m[0x288] = 0;
        Event.subtitleOneDraw(1);
        PickUp.init();
        m[0x34e] = (byte) 0xff;
        m[0x358] = 0;
    }

    /** Map_Position @0xa46c4: camera on the hero at map load */
    public static void position() {
        Obj h = Pool.hero;
        int sw = Glob.gameW;
        int sh = Glob.gameH;
        w32(0x9c, 0);
        w32(0xa0, 0);
        w32(0xa4, 0);
        w32(0xa8, 0);
        w32(0xbc, 0);
        w32(0xc0, 0);
        int hx = h.x();
        int hy = h.y();
        w32(0x164, 0);
        w32(0x160, 0);
        w32(0xc4, hx);
        w32(0xc8, hy);
        w32(0xcc, hx);
        w32(0xd0, hy);
        w32(0xd4, hx);
        w32(0xd8, hy);
        if (m[0x96] != 0) {
            int tw = m[0x7e];
            int v = hx - tw * ((sw / tw) >> 1);
            if (v < 0) {
                v = 0;
            }
            if (s32(0x84) < sw + v) {
                v = s32(0x84) - sw;
            }
            w32(0x9c, v);
        }
        if (m[0x97] != 0) {
            int th = m[0x7f];
            int v = hy - th * ((sh / th) >> 1);
            if (v < 0) {
                v = 0;
            }
            if (s32(0x88) < sh + v) {
                v = s32(0x88) - sh;
            }
            w32(0xa0, v);
        }
    }

    // ---- loading -----------------------------------------------------------------------------------------------------------------------

    /** Map_LoadMap @0xabc88 + Map_LoadMapData + Map_SetMapData */
    public static void loadMap() {
        int idx = Bytes.s16(m, 0x44);
        String n = idx < 10 ? "00" + idx : (idx < 100 ? "0" + idx : String.valueOf(idx));
        byte[] f = Res.load("/MAP/M/_MAP_M_" + n);
        if (f != null) {
            setMapData(f);
        }
    }

    /** Map_SetMapData @0xab434: the map file -> _map fields, tile/object lists and the placed objects */
    public static void setMapData(byte[] file) {
        MapData d = MapData.parse(file);
        data = d;
        m[0x270] = (byte) d.tilesetA.length;
        Bytes.fill(m, 0x629, 0xff, 3);
        Bytes.fill(m, 0x62c, 0xff, 3);
        for (int i = 0; i < d.tilesetA.length && i < 3; i++) {
            m[0x271 + i] = (byte) d.tilesetA[i];
            m[0x274 + i] = (byte) d.tilesetB[i];
        }
        Bytes.fill(m, 0x278, 0, 0x10);
        m[0x278] = 0;
        m[0x7a] = (byte) d.w;
        m[0x7b] = (byte) d.h;
        int sw = Glob.gameW;
        int sh = Glob.gameH;
        if (m[0x7b] > 0 || m[0x7a] > 0) {
            // (the original tests the sign of the previous count; maps always have W, H >= 1)
        }
        int pw = m[0x7a] * 16;
        int ph = m[0x7b] * 16;
        w32(0x88, ph);
        m[0x97] = (byte) (sh < ph ? 1 : 0);
        if (pw < sw) {
            w32(0xac, (sw - pw) / 2);
        }
        m[0x92] = (byte) (sw < pw ? 1 : 0);
        int fix = 0;
        if (ph < sh) {
            fix = (sh - ph) - ((sh - ph) >> 31);
            fix >>= 1;
        }
        w32(0x84, pw);
        m[0x7f] = 0x10;
        m[0x7e] = 0x10;
        if (ph < sh) {
            w32(0xb0, fix);
        }
        // object-image ids (Map_objIdxCreate): the list is kept in load order
        nObj = 0;
        objIds = new int[d.objImg.length + 10];
        for (int i = 0; i < d.objImg.length; i++) {
            objIdxCreate(d.objImg[i]);
        }
        for (int i = 0; i < d.sprIds.length; i++) {
            throw new IllegalStateException("SprObject_create: later");
        }
        layerA = d.layerA;
        layerB = d.layerB;
        if (layerB.length > 0) {
            m[0x350] = (byte) (layerB[0] & 8);
        }
        throw new IllegalStateException("Map_addMapObject / monsters: later");
    }

    /** Map_objIdxCreate: appends an object-image id (the count lives in _map+0x2b4 as a short) */
    public static void objIdxCreate(int id) {
        if (nObj >= objIds.length) {
            int[] n = new int[objIds.length + 10];
            System.arraycopy(objIds, 0, n, 0, objIds.length);
            objIds = n;
        }
        objIds[nObj++] = id;
        Bytes.w16(m, 0x2b4, nObj);
    }

    /** MAPOBJ_init(o, x, y, id, p5, p6, p7) @0xac35c */
    public static void mapObjInit(Obj o, int x, int y, int id, int p5, int p6, int p7) {
        byte[] mm = o.m;
        mm[0x29] = 0;
        Bytes.w32(mm, 8, x);
        Bytes.w32(mm, 0xc, y);
        mm[0x27] = (byte) id;
        mm[0x24] = (byte) p5;
        mm[0x25] = (byte) p6;
        mm[0x26] = (byte) p7;
        mm[0x28] = 0;
    }

    /** SprObject_init(o, x, y, a, b) @0xcf568 */
    public static void sprObjInit(Obj o, int x, int y, int a, int b) {
        byte[] mm = o.m;
        mm[0x9e] = 0;
        Bytes.w32(mm, 8, x);
        Bytes.w32(mm, 0xc, y);
        mm[0x9d] = (byte) a;
        mm[0x9c] = (byte) b;
    }

    /** DropItem_init(o, x, y, a, p5..p10) @0xe1490 */
    public static void dropItemInit(Obj o, int x, int y, int a, int p5, int p6, int p7, int p8, int p9, int p10) {
        byte[] mm = o.m;
        Bytes.w32(mm, 8, x);
        Bytes.w32(mm, 0xc, y - 8);
        mm[0x24] = (byte) a;
        Bytes.w16(mm, 0x22, p5);
        mm[0x25] = (byte) p6;
        mm[0x26] = (byte) p7;
        mm[0x27] = (byte) p8;
        mm[0x28] = (byte) p9;
        mm[0x29] = (byte) p10;
        mm[0x20] = 0;
        Bytes.fill(mm, 0x45, 0, 0x1b);
        Bytes.fill(mm, 0x2a, 0, 0x1b);
    }

    /** Map_Load_Tile_image(slot) @0xaa8b0: the tilesets of the map (single GBM images, tinted for the time slot) */
    public static void loadTileImage(int slot) {
        for (int i = 0; i < m[0x270]; i++) {
            int id = m[0x271 + i];
            if (id != -1) {
                String n = (id & 0xff) < 10 ? "00" + (id & 0xff) : ((id & 0xff) < 100 ? "0" + (id & 0xff) : String.valueOf(id & 0xff));
                byte[] b = Res.load("/TILE/_TILE_" + n);
                Gbm g = new Gbm(b, 0, b.length);
                Fx.loadBlend(g, slot);
                tiles[i] = g;
            }
        }
    }

    /** Map_ImageLoad @0xaaf90 */
    public static void imageLoad() {
        if (m[0x270] > 0) {
            loadTileImage(m[0x78]);
        }
        short cnt = Bytes.s16(m, 0x2b4) > 0 ? (short) Bytes.s16(m, 0x2b4) : 0;
        if (cnt > 0) {
            objImgs = new Gbm[cnt + 10];
            for (int i = 0; i < cnt; i++) {
                loadObjectImageBase(i);
            }
        }
    }

    /** Map_Load_Object_Image_Base(i) @0xaae70: "/OBJ/%03d/_OBJ_%03d" (id / 100, id) tinted for the time slot */
    public static void loadObjectImageBase(int i) {
        if (i < 0 || i >= nObj) {
            return;
        }
        int id = objIds[i];
        String a = id / 100 < 10 ? "00" + id / 100 : "0" + id / 100;
        String b = id < 10 ? "00" + id : (id < 100 ? "0" + id : String.valueOf(id));
        byte[] f = Res.load("/OBJ/" + a + "/_OBJ_" + b);
        if (f != null) {
            Gbm g = new Gbm(f, 0, f.length);
            Fx.loadBlend(g, m[0x78]);
            objImgs[i] = g;
        }
    }

    /** Map_Initialize @0xac110: loads the scene's map, images and puts the hero on it */
    public static void initialize() {
        Char hero = Pool.hero;
        throw new IllegalStateException("Map_Initialize: LinkedList_tempInitAll/Map_Initialize body (this class): later");
    }
}
