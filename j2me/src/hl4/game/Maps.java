package hl4.game;

import hl4.data.MapData;
import hl4.gfx.Frame;
import hl4.gfx.Fx;
import hl4.gfx.Gbm;
import hl4.gfx.Img;
import hl4.gfx.Sprite;
import hl4.sys.Rng;
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
        Bytes.fill(m, 0x289, 0, 0x1b);
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
        Bytes.fill(m, 0x271, 0xff, 3);
        Bytes.fill(m, 0x274, 0xff, 3);
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
        m[0x96] = (byte) (sw < pw ? 1 : 0);
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
            SObj b = sprObjectCreate(d.sprIds[i], timeReturn(m[0x68]));
            addSprBodyList(b);
        }
        layerA = d.layerA;
        layerB = d.layerB;
        if (layerB.length > 0) {
            m[0x350] = (byte) (layerB[0] & 8);
        }
        for (int layer = 0; layer < 4; layer++) {
            addMapObjects(d.objs[layer], layer);
        }
        m[0x367] = (byte) 0xff;
        m[0x365] = (byte) 0xff;
        m[0x366] = (byte) 0xff;
        for (int i = 0; i < d.summon.length; i++) {
            int idx = (byte) d.summon[i];
            if (idx < 3) {
                int type = Bytes.s16(m, idx * 2 + 0x48);
                m[0x364] = (byte) idx;
                if (getSummonBody() == null) {
                    Monsters.addBody(type, 1);
                }
            }
        }
        m[0x37c] = 0;
        m[0x377] = 0;
        m[0x358] = 0;
        m[0x376] = 0;
        spawnMonsters(d.mons);
    }

    /** Map_getSummonBody @0xa0dbc: always none */
    public static Char getSummonBody() {
        return null;
    }

    /** _map+8/0xc/0x10: the three monster template bodies of the map */
    public static final Char[] monBodies = new Char[3];

    /** Map_addMobBodyList(body) @0xa0d3c */
    public static void addMobBodyList(Char b) {
        int i;
        if (monBodies[0] == null) {
            i = 0;
        } else if (monBodies[1] == null) {
            i = 1;
        } else if (monBodies[2] == null) {
            i = 2;
        } else {
            return;
        }
        monBodies[i] = b;
        b.m[0x40d] = (byte) i;
        m[m[0x364] + 0x365] = (byte) i;
    }

    /** Map_mobBody(type, idx) @0xab2b0 */
    public static Char mobBody(int type, int idx) {
        for (int i = 0; i < 3; i++) {
            Char b = monBodies[i];
            if (b != null && Bytes.s16(b.m, 0x160) == type && b.m[0x40d] == idx) {
                return b;
            }
        }
        return null;
    }

    /** the monster placement walk of Map_SetMapData @0xab8a4 (entries are (slot, x, y) triples; a slot >= 3 consumes one byte only) */
    private static void spawnMonsters(int[] raw) {
        int n = raw.length / 3;
        if (n <= 0) {
            return;
        }
        int i = 0;
        int p = 0;
        while (i != n && p < raw.length) {
            int idx = (byte) raw[p];
            int q = p + 1;
            if (idx >= 3) {
                i++;
                p = q;
                continue;
            }
            Char body = mobBody(Bytes.s16(m, idx * 2 + 0x48), idx);
            if (body == null) {
                i++;
                p = p + 3;
                continue;
            }
            body.m[0x40d] = (byte) idx;
            int iv;
            boolean skip = false;
            if (!Monsters.isBoss(body) || (i & 0xffff) != 0) {
                iv = (short) i;
                if (m[0x358] != 0 && iv > 0) {
                    if (m[0x376] != 0 && body.m[0x30c] == ':') {
                        throw new IllegalStateException("Map_SetMapData: boss escort clones: later");
                    }
                    skip = true;
                }
            } else {
                m[0x358] = 1;
                iv = 0;
            }
            if (skip) {
                i++;
                p = q;
                continue;
            }
            int x = (byte) raw[p + 1] * 0x10 + 8;
            int y = ((byte) raw[p + 2] + 1) * 0x10;
            Monsters.clone(body, iv, x, y, 0);
            q = p + 3;
            if (body.m[0x30c] != '7') {
                i++;
                p = q;
                continue;
            }
            i++;
            m[0x376] = 1;
            p = q;
        }
    }

    // ---- sprite bodies, map objects -------------------------------------------------------------------------------------------------------
    /** _map+0x14..0x3b: the 10 sprite-object bodies (templates the placed sprite objects are copied from) */
    public static final SObj[] sprBodies = new SObj[10];

    /** Map_addSprBodyList @0xa0c5c */
    public static void addSprBodyList(SObj b) {
        for (int i = 0; i < 10; i++) {
            if (sprBodies[i] == null) {
                sprBodies[i] = b;
                return;
            }
        }
    }

    /** Map_getSprBody(id) @0xa0cac */
    public static SObj getSprBody(int id) {
        if (id == -1) {
            return null;
        }
        for (int i = 0; i < 10; i++) {
            SObj b = sprBodies[i];
            if (b != null && b.m[0x9d] == id) {
                return b;
            }
        }
        return null;
    }

    private static String n3(int v) {
        return v < 10 ? "00" + v : (v < 100 ? "0" + v : String.valueOf(v));
    }

    /** SprObject_create(id, slot) @0xcfbb4: takes a pool slot and loads the CIF and the image sets of OBJ/SPR id */
    public static SObj sprObjectCreate(int id, int slot) {
        SObj o = (SObj) Pool.getNew(Pool.SPROBJ);
        if (o == null) {
            return null;
        }
        o.m[0x9d] = (byte) id;
        if (id != -1) {
            o.spr = new Sprite(Res.load("/OBJ/SPR/_OBJ_SPR_" + n3(id) + "_CIF"), 0x10);
            for (int i = 0; i < o.spr.nSets; i++) {
                o.spr.setImages(i, Img.loadSprSet("/OBJ/SPR/_OBJ_SPR_" + n3(i) + "_BM", 0xffff, slot));
            }
        }
        return o;
    }

    /** SprObject_copy(body, flags, x, y, layer) @0xcfab4 */
    public static SObj sprObjectCopy(SObj body, int flags, int x, int y, int layer) {
        if (body == null) {
            return null;
        }
        SObj o = (SObj) Pool.getNew(Pool.SPROBJ);
        if (o == null) {
            return null;
        }
        o.spr.copyFrom(body.spr);
        sprObjInit(o, x, y, body.m[0x9d], layer);
        o.m[0x9e] = (byte) flags;
        Bytes.w32(o.m, 0x9f, Bytes.s32(body.m, 0x9f));
        if (((layer - 2) & 0xff) < 2) {
            int mf = o.spr.maxFrame(0, 0);
            Bytes.w32(o.m, 0x8c, Rng.rand(0, (byte) mf));
        }
        if (layer == 2) {
            G.objList.addObject(o);
        }
        return o;
    }

    /** MAPOBJ_createAndCopy @0xace8c: one placed map object of a layer (0 more-bottom, 1 bottom, 2 middle, 3 top) */
    public static void mapObjCreateAndCopy(int b0, int b1, int b2, int b3, int x, int y, int layer) {
        Obj o = Pool.getNew(Pool.MAPOBJ);
        if (o == null) {
            return;
        }
        mapObjInit(o, x, y, b3, b0, b1, b2);
        if (layer == 0) {
            G.moreBottomObjectList.addObject(o);
        } else if (layer == 1) {
            G.bottomObjectList.addObject(o);
        } else if (layer == 2) {
            G.objList.addObject(o);
        } else if (layer == 3) {
            G.topObjectList.addObject(o);
        }
        o.m[0x19] = (byte) 0xff;
        o.m[0x2a] = (byte) layer;
        o.m[0x20] = 0;
    }

    /** Map_addMapObject(count, pos, layer) @0xab2e8 over the parsed records (b0, b1, b2, b3, x, y) */
    public static void addMapObjects(int[] recs, int layer) {
        for (int i = 0; i + 5 < recs.length; i += 6) {
            int b0 = recs[i];
            int b1 = recs[i + 1];
            int b2 = recs[i + 2];
            int b3 = recs[i + 3];
            int x = recs[i + 4];
            int y = recs[i + 5];
            if ((b0 & 0x80) != 0) {
                SObj body = getSprBody(b3);
                if (body != null) {
                    sprObjectCopy(body, (byte) b0, x, y, layer);
                }
            } else {
                mapObjCreateAndCopy(b0, b1, b2, b3, x, y, layer);
            }
        }
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

    // ---- event objects -------------------------------------------------------------------------------------------------------------------

    /** Map_objectImgIdx(id) @0xa0ff8: position of the object-image id in the load list, -1 = not loaded */
    public static int objectImgIdx(int id) {
        for (int i = 0; i < nObj; i++) {
            if ((objIds[i] & 0xff) == (id & 0xff)) {
                return i;
            }
        }
        return -1;
    }

    /** MAPOBJ_getImgIdx(o) @0xac394: object-image id of a map object (its own +0x27 or its event record's +5) */
    public static int mapObjImgId(Obj o) {
        if (o.evRef < 0) {
            return o.m[0x27];
        }
        return Event.e[Event.rec(o.evRef) + 5];
    }

    /** Tile_create(rec, evIdx) @0xc829c */
    public static void tileCreate(int k, int evIdx) {
        Obj t = Pool.getNew(Pool.TILE);
        if (t == null) {
            return;
        }
        int r = Event.rec(k);
        t.evRef = -1;
        Bytes.w32(t.m, 8, Bytes.s16(Event.e, r));
        Bytes.w32(t.m, 0xc, Bytes.s16(Event.e, r + 2));
        G.tileList.addObject(t);
        t.m[Obj.EVENT] = (byte) evIdx;
    }

    /** MAPOBJ_create(rec, layer, evIdx) @0x9cfac: a map object placed by an event record */
    public static void mapObjCreate(int k, int layer, int evIdx) {
        Obj o = Pool.getNew(Pool.MAPOBJ);
        if (o == null) {
            return;
        }
        int r = Event.rec(k);
        mapObjInit(o, Bytes.s16(Event.e, r) + 8, Bytes.s16(Event.e, r + 2) + 0x10, Event.e[r + 5] & 0xff, 0, 0, 0);
        o.m[0x0a] = 8;
        o.m[0x29] = 0x10;
        o.m[0] = 0;
        o.m[1] = 0;
        if (layer == 0) {
            G.moreBottomObjectList.addObject(o);
        } else if (layer == 1) {
            G.bottomObjectList.addObject(o);
        } else if (layer == 2) {
            G.objList.addObject(o);
        } else if (layer == 3) {
            G.topObjectList.addObject(o);
        }
        int imgId = o.m[0x27] & 0xff;      // evRef is still unset here: MAPOBJ_getImgIdx reads +0x27
        if (objectImgIdx(imgId) < 0) {
            objIdxCreate(Event.e[r + 5] & 0xff);
            loadObjectImageBase(nObj - 1);
        }
        o.evRef = k;
        o.m[0x2a] = (byte) layer;
        o.m[Obj.EVENT] = (byte) evIdx;
    }

    /** Map_addEventObject(rec k, evIdx) @0x93cec */
    public static void addEventObject(int k, int evIdx) {
        byte[] e = Event.e;
        int r = Event.rec(k);
        if ((e[r + 5] & 0xff) == 0xff) {
            return;
        }
        int sw = e[r + 8] & 0xff;
        int type;
        if (sw == 0xff || e[sw + 0x624] == 0) {
            type = e[r + 4] & 0xff;
        } else {
            int t = e[r + 4] & 0xff;
            if (t != 5) {
                if (t > 5) {
                    Bytes.w16(e, r, -0x10);
                    Bytes.w16(e, r + 2, -0x10);
                }
                return;
            }
            e[r + 5] = 0x2a;
            type = 5;
        }
        if (type == 0) {
            tileCreate(k, evIdx);
        } else if (type >= 1 && type <= 4) {
            mapObjCreate(k, type - 1, evIdx);
        } else {
            Npcs.create(k, evIdx);
        }
    }

    /** Map_Draw_Create_Minimap_Tile @0xa6348 (HUD minimap image; built when the HUD is ported) */
    public static void createMinimapTile() {
        minimapPending = true;
    }

    public static boolean minimapPending;

    /** Map_changeEvtObjectImage(type, k, img) @0xaaba0 */
    public static void changeEvtObjectImage(int type, int k, int img) {
        throw new IllegalStateException("Map_changeEvtObjectImage: later");
    }

    // ---- map load / initialisation -------------------------------------------------------------------------------------------------------

    /** Map_Finalize: frees the map (the Java objects are dropped; pool slots are returned later with MP_return) */
    public static void finalizeMap() {
        throw new IllegalStateException("Map_Finalize: later");
    }

    /** LinkedList_initAll @0x9728c */
    public static void linkedListInitAll() {
        G.objList.init();
        G.netItemList.init();
        tempInitAll();
    }

    /** LinkedList_tempInitAll @0x97224: the per-map lists are emptied (objList is kept: it holds the hero) */
    public static void tempInitAll() {
        G.topObjectList.init();
        G.bottomObjectList.init();
        G.moreBottomObjectList.init();
        G.dropedItem.init();
        G.tileList.init();
        G.npcList.release();
        G.monsterList.release();
        G.targetEffectList.release();
    }

    /** Map_Initialize @0xac110: loads the scene's map, images and puts the hero on it */
    public static void initialize() {
        Char hero = Pool.hero;
        tempInitAll();
        variableInit();
        if (vStrip == null) {
            vStrip = new Frame(0x10, Glob.gameH);
            hStrip = new Frame(Glob.gameW, 0x10);
        }
        Gfx.fillRectRGB(vStrip, 0, 0, 0x10, Glob.gameH, 0xd, 0xd, 0xf);
        Gfx.fillRectRGB(hStrip, 0, 0, Glob.gameW, 0x10, 0xd, 0xd, 0xf);
        m[0x78] = (byte) timeReturn(m[0x68]);
        bgReturn(Bytes.s16(m, 0x44));
        Chars.setDirection(hero, m[0x2d8]);
        Bytes.w32(hero.m, 8, s32(0x268));
        Bytes.w32(hero.m, 0xc, s32(0x26c));
        loadMap();
        imageLoad();
        position();
        m[0x96] = (byte) (Glob.gameW < s32(0x84) ? 1 : 0);
        m[0x97] = (byte) (Glob.gameH < s32(0x88) ? 1 : 0);
        HeroCtl.moveStop(hero);
        HeroCtl.setMotion(hero, 0, hero.m[0x168]);
        hero.m[0x171] = (byte) 0xff;
        G.objList.addObject(hero);
        if (hero.m[0x189] == 1 && hero.m[0x1ec] >= 0) {
            G.objList.addObject(Pool.summon);
        }
        Sprite.lcdEffInit();
        if (m[0x68] == 2) {
            Sprite.lcdEffSet(0, 0, 0, 0x10, 0xff, 0);
        }
    }

    /** Map_objectPreProcess @0xa7f30: every object of the sorted list gets its per-type pre-process, then each node is re-sorted by its Y (the hero last, with its own pass) */
    public static void objectPreProcess() {
        LList l = G.objList;
        if (l.isEmpty()) {
            return;
        }
        Obj n = l.head;
        while (n != null) {
            Obj next = n.next;
            int t = Obj.structType(n);
            if (t == 1) {
                Npcs.preProcess((Char) n);
            } else if (t == 2) {
                HeroProc.process(Pool.hero);
            } else if (t == 3) {
                Monsters.preProcess((Char) n);
            } else if (t == 6) {
                throw new IllegalStateException("EFF_preProcessOneTargetEffect: later");
            } else if (t == 4) {
                throw new IllegalStateException("SUMMON_preProcess: later");
            }
            n = next;
        }
        for (Obj p = l.head; p != null; p = p.next) {
            l.sort(p);
        }
        l.sort(Pool.hero);
    }

    /** Map_objectEndProcess @0xa7e3c */
    public static void objectEndProcess() {
        LList l = G.objList;
        if (l.isEmpty()) {
            return;
        }
        Obj n = l.head;
        while (n != null) {
            Obj next = n.next;
            int t = Obj.structType(n);
            if (t == 1) {
                Npcs.endProcess((Char) n);
            } else if (t == 2) {
                HeroProc.endProcess(Pool.hero);
            } else if (t == 3) {
                Monsters.endProcess((Char) n);
            } else if (t == 6) {
                throw new IllegalStateException("EFF_endProcessOneTargetEffect: later");
            } else if (t == 4) {
                throw new IllegalStateException("SUMMON_endProcess: later");
            }
            n = next;
        }
    }

    /** Map_chageEvtObjDir(k) @0xa3e5c: the first listed object of event index k gets the 'flipped' bit (+0x24 bit 6) */
    public static void chageEvtObjDir(int k) {
        for (Obj o = G.objList.head; o != null; o = o.next) {
            if (o.m[Obj.EVENT] == k) {
                int v = o.m[0x24] & 0xff;
                o.m[0x24] = (byte) (v + ((v ^ 0xc0) & 0x40));
                return;
            }
        }
    }
}
