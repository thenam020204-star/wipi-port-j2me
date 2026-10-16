package hl4.game;

/** Boss monsters (type >= 200; Boss_* @0xcf2fc..0xd0xxx).  Only the predicates used by the common monster code so far. */
public final class Boss {
    private Boss() {
    }

    /** Boss_isShield @0xcf374: a boss of kind 0x34 / 0x35 (+0x30c) */
    public static boolean isShield(Char o) {
        if (!Monsters.isBoss(o)) {
            return false;
        }
        int v = (o.m[0x30c] & 0xff) - 0x34;
        return v >= 0 && v <= 1;
    }
}
