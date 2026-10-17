package hl4.game;

/**
 * The original's static object pool (_pool, MP_Initialize @0xade2c / MP_search @0xad444 / MP_return @0xadc4c): every dynamic object lives in a
 * fixed slot of its type and is handed out first-free-first.  Struct types: 1 NPC (20), 2 hero (1), 3 monster (25), 4 summon (1), 5 effect (100),
 * 6 target effect (100), 7 map object (900), 8 item (200), 9 dropped item (30), 10 tile (20), 12 arms/net item (70), 16 sprite object (100).
 */
public final class Pool {
    public static final int NPC = 1;
    public static final int HERO = 2;
    public static final int MONSTER = 3;
    public static final int SUMMON = 4;
    public static final int EFFECT = 5;
    public static final int TARGET_EFFECT = 6;
    public static final int MAPOBJ = 7;
    public static final int ITEM = 8;
    public static final int DROP = 9;
    public static final int TILE = 10;
    public static final int PICKUP = 14;
    public static final int ARMS_ITEM = 12;
    public static final int SPROBJ = 16;

    public static final int SZ_HERO = 0xd50;
    public static final int SZ_SUMMON = 0x63c;
    public static final int SZ_MONSTER = 0x430;
    public static final int SZ_NPC = 0x1cc;
    public static final int SZ_EFFECT = 0x44;
    public static final int SZ_TEFFECT = 0xb8;
    public static final int SZ_MAPOBJ = 0x3c;
    public static final int SZ_ITEM = 0x270;
    public static final int SZ_DROP = 0x60;
    public static final int SZ_TILE = 0x24;
    public static final int SZ_PICKUP = 0x48;
    public static final int SZ_SPROBJ = 0xa0;

    /** pools[type][idx] */
    public static final Obj[][] pools = new Obj[17][];

    public static Char hero;
    public static Char summon;

    private Pool() {
    }

    /** MP_Initialize: creates every slot (state reset to the 'free' state is done by each type's *_set function) */
    public static void initialize() {
        hero = new Char(SZ_HERO, HERO, 0);
        summon = new Char(SZ_SUMMON, SUMMON, 0);
        pools[HERO] = new Obj[] {hero};
        pools[SUMMON] = new Obj[] {summon};
        pools[MONSTER] = new Obj[25];
        for (int i = 0; i < 25; i++) {
            pools[MONSTER][i] = new Char(SZ_MONSTER, MONSTER, i);
        }
        pools[NPC] = new Obj[20];
        for (int i = 0; i < 20; i++) {
            pools[NPC][i] = new Char(SZ_NPC, NPC, i);
        }
        pools[EFFECT] = new Obj[100];
        pools[TARGET_EFFECT] = new Obj[100];
        pools[SPROBJ] = new Obj[100];
        for (int i = 0; i < 100; i++) {
            pools[EFFECT][i] = new Obj(SZ_EFFECT, EFFECT, i);
            pools[TARGET_EFFECT][i] = new TEff(SZ_TEFFECT, TARGET_EFFECT, i);
            pools[SPROBJ][i] = new SObj(SZ_SPROBJ, SPROBJ, i);
        }
        pools[MAPOBJ] = new Obj[900];
        for (int i = 0; i < 900; i++) {
            pools[MAPOBJ][i] = new Obj(SZ_MAPOBJ, MAPOBJ, i);
        }
        pools[ITEM] = new Obj[200];
        for (int i = 0; i < 200; i++) {
            pools[ITEM][i] = new Obj(SZ_ITEM, ITEM, i);
        }
        pools[ARMS_ITEM] = new Obj[70];
        for (int i = 0; i < 70; i++) {
            pools[ARMS_ITEM][i] = new Obj(SZ_ITEM, ARMS_ITEM, i);
        }
        pools[DROP] = new Obj[30];
        for (int i = 0; i < 30; i++) {
            pools[DROP][i] = new Obj(SZ_DROP, DROP, i);
        }
        pools[PICKUP] = new Obj[5];
        for (int i = 0; i < 5; i++) {
            pools[PICKUP][i] = new Obj(SZ_PICKUP, PICKUP, i);
        }
        pools[TILE] = new Obj[20];
        for (int i = 0; i < 20; i++) {
            pools[TILE][i] = new Obj(SZ_TILE, TILE, i);
        }
    }

    /** the rest of MP_Initialize: every slot gets its type's *_set state (hero, summon, monsters, map objects, tiles, sprite objects, items, NPCs, effects, drops) */
    public static void initSlots() {
        Hero.set(hero);
        Summon.set(summon);
        for (int i = 0; i < 25; i++) {
            Char c = (Char) pools[MONSTER][i];
            Chars.set(c);
            Chars.init(c, 0xff, 0xff, 0, 0, 0, 0, 0);      // MONSTER_set
        }
        for (int i = 0; i < 900; i++) {
            Obj o = pools[MAPOBJ][i];
            MapObjDraw.set(o);
        }
        for (int i = 0; i < 20; i++) {
            Obj o = pools[TILE][i];
            o.objSet(1);
            o.m[0x20] = 0;
            hl4.sys.Bytes.w32(o.m, 8, 0);
            hl4.sys.Bytes.w32(o.m, 0xc, 0);
        }
        for (int i = 0; i < 100; i++) {
            Obj o = pools[SPROBJ][i];
            o.objSet(1);
            Maps.sprObjInit(o, 0, 0, 0xff, 0xff);
        }
        for (int i = 0; i < 200; i++) {
            Obj o = pools[ITEM][i];
            o.objSet(1);
            Items.init(o);
        }
        for (int i = 0; i < 70; i++) {
            Obj o = pools[ARMS_ITEM][i];
            o.objSet(1);
            Items.init(o);
        }
        for (int i = 0; i < 20; i++) {
            Npcs.set((Char) pools[NPC][i]);
        }
        for (int i = 0; i < 100; i++) {
            Obj o = pools[EFFECT][i];
            o.objSet(1);
            Effects.initEffect(o, 0xff, 0xff, 0);
            o.m[0x19] = 100;
            Obj t = pools[TARGET_EFFECT][i];
            t.objSet(1);
            Effects.initEffect(t, 0xff, 0xff, 0);
            t.m[0x19] = 100;
            Effects.targetEffectSet(t);
        }
        PickUp.init();
        for (int i = 0; i < 30; i++) {
            Obj o = pools[DROP][i];
            DropItems.set(o);
        }
    }

    /**
     * MP_getNew(type, out) @0x9dab0 (a tail call of MP_search @0xad444): first free slot of the type (its free flag is cleared); null = none
     * (the original fills the out position with type 0x11, idx -1; also for the types MP_search has no case for: 11, 13, 15, out of range)
     */
    public static Obj getNew(int type) {
        if (type < 0 || type >= pools.length || pools[type] == null) {
            return null;
        }
        Obj[] a = pools[type];
        for (int i = 0; i < a.length; i++) {
            if (a[i].m[Obj.FREE] != 0) {
                a[i].m[Obj.FREE] = 0;
                return a[i];
            }
        }
        return null;
    }

    /**
     * MP_POSITION_init(pos, type, idx, ptr, flag) @0x9d1cc: fills the position {ptr +0, type +4 (byte), flag +5 (byte), idx +6 (halfword)};
     * here the position is the object header at +0x10 of 'o' (self pointer = o: 'ptr' is not stored).
     */
    public static void positionInit(Obj o, int type, int idx, Obj ptr, int flag) {
        o.m[Obj.TYPE] = (byte) type;
        o.m[Obj.FREE] = (byte) flag;
        hl4.sys.Bytes.w16(o.m, Obj.IDX, idx);
    }

    /** MP_InitNetItem(idx) @0x9d1e4: position of the arms/net item slot idx = {item, 12, 1, idx} (the slot's own header; no network access) */
    public static void initNetItem(int idx) {
        Obj o = pools[ARMS_ITEM][(short) idx];
        positionInit(o, ARMS_ITEM, idx, o, 1);
    }

    /**
     * MP_getObj(pos) @0x9d250: the pool object a position names (type +4 signed byte, idx +6); null for types without a pool here
     * (0, 11, 13..15, > 16).  The hero and the summon ignore the index.  An index outside the pool gives null (the original computes a wild address).
     */
    public static Obj getObj(Obj pos) {
        int t = pos.m[Obj.TYPE];
        int i = hl4.sys.Bytes.s16(pos.m, Obj.IDX);
        switch (t) {
            case NPC:
            case MONSTER:
            case EFFECT:
            case TARGET_EFFECT:
            case MAPOBJ:
            case ITEM:
            case DROP:
            case TILE:
            case ARMS_ITEM:
            case SPROBJ:
                return i >= 0 && i < pools[t].length ? pools[t][i] : null;
            case HERO:
                return hero;
            case SUMMON:
                return summon;
            default:
                return null;
        }
    }

    /** MP_getHero() @0x9dab4 */
    public static Char getHero() {
        return hero;
    }

    /** MP_getSummon() @0x9dad0 */
    public static Char getSummon() {
        return summon;
    }

    /** MP_getPickUpItem(i) @0x9db78 */
    public static Obj getPickUpItem(int i) {
        return pools[PICKUP][i];
    }

    /** MP_getBag() @0x827a8: _itemBag */
    public static LList getBag() {
        return G.itemBag;
    }

    /** MP_getArmsBag() @0x829bc: _armsBag */
    public static LList getArmsBag() {
        return G.armsBag;
    }

    /** MP_getEquip(i) @0x827c4: _equip[i] */
    public static Obj getEquip(int i) {
        return G.equip[i];
    }

    /** MP_getArmsEquip(i) @0x82a18: _armsEquip[i] */
    public static Obj getArmsEquip(int i) {
        return G.armsEquip[i];
    }

    /** MP_getX(pos) @0x9db34: x of the monster a position names; -1 when the position is not a monster */
    public static int getX(Obj pos) {
        if (pos.m[Obj.TYPE] != MONSTER) {
            return -1;
        }
        return hl4.sys.Bytes.s32(pools[MONSTER][hl4.sys.Bytes.s16(pos.m, Obj.IDX)].m, Obj.X);
    }

    /** MP_getY(pos) @0x9daf0: y of the monster a position names; -1 when the position is not a monster */
    public static int getY(Obj pos) {
        if (pos.m[Obj.TYPE] != MONSTER) {
            return -1;
        }
        return hl4.sys.Bytes.s32(pools[MONSTER][hl4.sys.Bytes.s16(pos.m, Obj.IDX)].m, Obj.Y);
    }

    /** MP_release() @0x9d24c: empty in the original (a bare return; the pool is static) */
    public static void release() {
    }

    /** MP_return @0xadc4c: resets the object to the free state of its type */
    public static void ret(Obj o) {
        switch (Obj.structType(o)) {
            case MONSTER: {
                Char c = (Char) o;
                Chars.deleteEffectInList(c);
                Chars.deleteEffectInObjList(c);
                c.effects.release();
                c.targetEffects.release();
                c.spr.set();
                hl4.sys.Bytes.fill(c.m, 0x20, 0, 0x74);
                o.objSet(1);
                o.m[Obj.EVENT] = (byte) 0xff;
                o.m[Obj.RELEASE] = 0;
                hl4.sys.Bytes.w16(o.m, Obj.MAPIDX, 0xffff);
                break;
            }
            case NPC:
                Npcs.ret((Char) o);
                break;
            case MAPOBJ:
                MapObjDraw.ret(o);
                break;
            case TILE:
                o.objSet(1);
                o.m[0x20] = 0;
                hl4.sys.Bytes.w32(o.m, 8, 0);
                hl4.sys.Bytes.w32(o.m, 0xc, 0);
                break;
            case DROP:
                DropItems.ret(o);
                break;
            case SPROBJ: {
                SObj so = (SObj) o;
                o.objSet(1);
                so.spr.set();
                hl4.sys.Bytes.fill(so.m, 0x20, 0, 0x74);
                Maps.sprObjInit(o, 0, 0, 0xff, 0xff);
                hl4.sys.Bytes.w32(o.m, 0x94, 0);
                hl4.sys.Bytes.w32(o.m, 0x98, 0);
                break;
            }
            case EFFECT:                     // Effect_return @0x587bc
                o.objSet(1);
                Effects.initEffect(o, 0xff, 0xff, 0);
                break;
            case TARGET_EFFECT: {            // TargetEffect_destroy @0x58794
                TEff t = (TEff) o;
                o.objSet(1);
                Effects.targetEffectSet(o);
                t.owner = null;
                t.spr = null;
                t.hitTable = null;
                t.hitCells = null;
                hl4.sys.Bytes.w16(o.m, Obj.MAPIDX, 0xffff);
                break;
            }
            case PICKUP:
                PickUp.initSlot(hl4.sys.Bytes.s16(o.m, Obj.IDX));
                break;
            case ITEM:
            case ARMS_ITEM:                  // ITEM_return @0x84f10 = OBJ_return + Item_init
                o.objSet(1);
                Items.init(o);
                break;
            case HERO:
            case SUMMON:
                break;                       // MP_return has no case for them: the node is only unlinked
            default:
                throw new IllegalStateException("MP_return type " + Obj.structType(o) + ": later");
        }
    }
}
