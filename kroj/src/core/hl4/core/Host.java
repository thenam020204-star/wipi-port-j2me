package hl4.core;

/**
 * Services the emulated WIPI-C platform needs from the host (MIDP or Java SE test harness).
 */
public interface Host {
    /** Resource from the application archive, or null when missing. */
    byte[] loadResource(String name);

    long currentTimeMillis();

    /** Present a full frame (ARGB, width*height). Called from the game thread. */
    void present(int[] argb, int width, int height);

    void log(String s);

    /**
     * Decode an encoded image (PNG) to ARGB pixels; size receives {width, height}.
     * Returns null when the data cannot be decoded.
     */
    int[] decodeImage(byte[] data, int off, int len, int[] size);

    void exit();

    void vibrate(int ms, int level);

    /** Persistent storage (save data). Returns null if missing. */
    byte[] storageRead(String name);

    void storageWrite(String name, byte[] data);

    boolean storageDelete(String name);

    /** Audio: data is the raw clip (usually SMAF/MMF). Returns an opaque handle or null. */
    Object soundCreate(byte[] data, int type);

    void soundPlay(Object sound, boolean loop);

    void soundStop(Object sound);

    void soundFree(Object sound);

    void soundVolume(Object sound, int volume);

    boolean soundIsPlaying(Object sound);
}
