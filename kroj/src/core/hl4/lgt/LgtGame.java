package hl4.lgt;

/**
 * Per-game extension of the LGT runtime: HLE hooks installed on the game's own code
 * (Vietnamese text, offline patches). One instance per run, attached to {@link LgtRuntime#game}.
 */
public abstract class LgtGame {
    /** Handle HLE stub idx; return false when the index is not this game's. */
    public abstract boolean hle(int idx);

    /** Called when the game tries to use the network (always refused in the offline port). */
    public void netAttempt() {
    }
}
