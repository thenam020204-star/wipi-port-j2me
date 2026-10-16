package hl4.game;

import hl4.sys.Bytes;

/** Picked-up item notices (PickUpItem_* / MP_initPickUpItem @0x9dba8): pool type 0xe, 5 slots of 0x48 bytes. */
public final class PickUp {
    private PickUp() {
    }

    /** MP_initPickUpItem(k): OBJ header reset, x = y = 0, state bytes +0x20/+0x21 = 0, text buffer +0x24..+0x43 cleared */
    public static void initSlot(int k) {
        Obj o = Pool.pools[Pool.PICKUP][k];
        o.objSet(1);
        Bytes.w32(o.m, Obj.X, 0);
        Bytes.w32(o.m, Obj.Y, 0);
        o.m[0x21] = 0;
        o.m[0x20] = 0;
        Bytes.fill(o.m, 0x24, 0, 0x20);
    }

    /** PickUpItem_init @0x99b90 */
    public static void init() {
        for (int k = 0; k < 5; k++) {
            initSlot(k);
        }
    }
}
