package hl4.lgt;

/**
 * 크로이센 (Kroisen, Softmaru, LGT AID 0002CCCB): game specific hooks.
 */
public final class KroGame extends LgtGame {
    final LgtRuntime rt;

    /** font/dict: Vietnamese overlay (/vi/font.bin, /vi/dict.txt), null for the original text. */
    public KroGame(LgtRuntime rt, byte[] font, byte[] dict) {
        this.rt = rt;
    }

    public boolean hle(int idx) {
        return false;
    }
}
