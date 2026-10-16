package hl4.game;

/** Audio (SM_* of the original). Phase F: the call sites are kept so the one-channel timing counters can be ported with the sound engine. */
public final class Sound {
    private Sound() {
    }

    /** SM_PlayResEx at the end of every tick */
    public static void tick() {
    }

    /** checkAndPlayBGM at the start of MainFrame_Process */
    public static void checkBgm() {
    }

    /** SM_StopSound */
    public static void stop() {
    }
}
