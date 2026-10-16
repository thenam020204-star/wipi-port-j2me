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

    /** Map_setBgmPlay(1); Map_Set_BGM(10): menu music request (state in _map) */
    public static void menuBgm() {
        G.map[0x6b] = 1;
        G.map[0x6a] = G.map[0x69];
        G.map[0x69] = 10;
    }

    /** SM_PlayEffSound(id): sound effect request (Phase F) */
    public static void playEff(int id) {
    }

    /** loadBGMSound(id) / freeBGMSound (Phase F) */
    public static void loadBgm(int id) {
        if ((id & 0xff) < 0x15) {
            bgmId = id & 0xff;
            bgmLoaded = true;
        }
    }

    /** freeBGMSound @0xcc0e8: stops the music and asks for it to be (re)started */
    public static void freeBgm() {
        bgmLoaded = false;
        G.map[0x6b] = 1;
    }

    public static boolean bgmLoaded;
    public static int bgmId;

    /** SM_StopSound */
    public static void stop() {
    }
}
