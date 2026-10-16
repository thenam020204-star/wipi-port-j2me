package hl4.game;

/** Monsters (pooled type 3, 0x430-byte structs): MONSTER_* @0xae7c8..0xb1xxx. Only the state used by the load path so far. */
public final class Monsters {
    /** _questBoss (4 bytes) */
    public static final byte[] questBoss = new byte[4];

    private Monsters() {
    }

    /** MONSTER_questBossDel @0xae794 */
    public static void questBossDel() {
        questBoss[3] = 0;
        questBoss[2] = (byte) 0xff;
        questBoss[0] = 0;
        questBoss[1] = 0;
    }
}
