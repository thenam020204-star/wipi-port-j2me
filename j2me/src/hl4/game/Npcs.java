package hl4.game;

import hl4.sys.Bytes;

/** Non-player characters (the pooled 0x1cc-byte structs of type 1): NPC_* @0xb8990..0xba354.  +0x160 data id, +0x1a8 walk count, +0x1aa/+0x1ac move delay, +0x1b0 data record. */
public final class Npcs {
    public static int heartFrame;
    public static int cannonFrame;

    private Npcs() {
    }

    /** NPC_setMoveDelay @0xb8990 */
    public static void setMoveDelay(Obj n) {
        Bytes.w16(n.m, 0x1aa, Bytes.s16(n.m, 0x1ac));
    }

    /** NPC_set(n) @0xba354 / NPC_return's reset: pool-slot state (no data record attached) */
    public static void set(Char n) {
        Chars.set(n);
        Chars.init(n, 0, 0, 0, 0, 0, ((Bytes.s16(n.m, 0x160) - 0x29) & 0xffff) > 1 ? 1 : 0, 4);
        n.m[0x1a8] = 4;
        setMoveDelay(n);
        n.m[0x1b4] = 0;
        n.m[0x1c3] = 0;
        Bytes.w32(n.m, 0x1b8, 0);
        Bytes.w32(n.m, 0x1bc, 0);
        n.m[0x1c0] = 0;
        n.m[0x1c1] = 0;
        n.m[0x1c2] = 0;
        n.m[0x1c5] = 0;
        heartFrame = 0;
        cannonFrame = 0;
        n.m[0x171] = (byte) 0xff;
        n.m[0x1c8] = 0;
    }
}
