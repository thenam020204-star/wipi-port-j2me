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
            pools[TARGET_EFFECT][i] = new Obj(SZ_TEFFECT, TARGET_EFFECT, i);
            pools[SPROBJ][i] = new Obj(SZ_SPROBJ, SPROBJ, i);
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
        pools[TILE] = new Obj[20];
        for (int i = 0; i < 20; i++) {
            pools[TILE][i] = new Obj(SZ_TILE, TILE, i);
        }
    }

    /** MP_search / MP_getNew: first free slot of the type (its free flag is cleared); null = none (the original returns type 0x11) */
    public static Obj getNew(int type) {
        Obj[] a = pools[type];
        for (int i = 0; i < a.length; i++) {
            if (a[i].m[Obj.FREE] != 0) {
                a[i].m[Obj.FREE] = 0;
                return a[i];
            }
        }
        return null;
    }

    /** MP_return @0xadc4c: resets the object to the free state of its type */
    public static void ret(Obj o) {
        throw new IllegalStateException("MP_return type " + Obj.structType(o) + ": later");
    }
}
