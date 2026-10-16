package hl4.game;

/** _menuOptions (10 bytes): +0 BGM volume, +1 effect volume, +2 speed, +3.. flags, +4 orientation (forced 0 = portrait), +7 particles. OptionInit @0xbd624 defaults. */
public final class Opt {
    public static final byte[] v = new byte[10];
    /** fps[] @0xe1b10: per-frame delay in ms for the speed option (the timer is re-armed with this after each paint) */
    public static final int[] FPS = {90, 80, 70, 60, 50, 40, 30, 20, 10, 1};

    private Opt() {
    }

    /** OptionInit; +4 is 0 here (the AND2011 default 1 selects the 320x240 landscape layout; see tools/ref_game.py IMPL['orientation']). */
    public static void init() {
        v[9] = 0;
        v[2] = 6;
        v[8] = 1;
        v[0] = 1;
        v[1] = 1;
        v[3] = 1;
        v[4] = 0;
        v[5] = 1;
        v[6] = 1;
        v[7] = 1;
        sync();
    }

    /** mirror of MenuOptions_particle for the sprite code (hl4.gfx must not depend on hl4.game) */
    public static void sync() {
        hl4.gfx.GfxOut.particleOpt = v[7] == 1 ? 1 : 0;
    }

    /** LoadOption: the saved options file (./Hero4OptSave) is applied by the save system later; a fresh install keeps the defaults. */
    public static void load() {
        init();
    }

    /** Opt_getVolumeLv */
    public static int volume(int which) {
        return which == 1 ? v[1] : (which == 0 ? v[0] : 0);
    }

    /** MenuOptions_particle */
    public static boolean particle() {
        return v[7] == 1;
    }
}
