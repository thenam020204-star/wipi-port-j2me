package hl4.lgt;

/**
 * 크로이센 (Chroisen, Softmaru, LGT AID 0002CCCB v01.00.03): game specific hooks.
 */
public final class KroGame extends LgtGame {
    final LgtRuntime rt;
    KroText text;
    /** harness debug: compare the HLE text measure with the original ARM code before installing it */
    public static boolean MEASURECHECK;

    /** font/dict: Vietnamese overlay (/vi/font.bin, /vi/dict.txt), null for the original text. */
    public KroGame(LgtRuntime rt, byte[] font, byte[] dict) throws java.io.IOException {
        this.rt = rt;
        if (font != null && dict != null) {
            text = new KroText(rt, new hl4.core.VnFont(font), lines(new String(dict, "UTF-8")));
            text.install();
        }
    }

    /** dict.txt: one entry per line (\n), a trailing newline does not add an entry */
    static String[] lines(String t) {
        java.util.Vector v = new java.util.Vector();
        int p = 0;
        while (p < t.length()) {
            int q = t.indexOf('\n', p);
            if (q < 0) q = t.length();
            v.addElement(t.substring(p, q));
            p = q + 1;
        }
        String[] a = new String[v.size()];
        v.copyInto(a);
        return a;
    }

    /** harness: run the measure self check now (after the game has loaded its font) */
    public int measureCheck() {
        return text == null ? -1 : text.selfCheck(0x1000, 0x5efd7);
    }

    public boolean hle(int idx) {
        if (text != null) {
            if (idx == KroText.HLE_MEASURE) {
                text.measure();
                return true;
            }
            if (idx == KroText.HLE_DRAW) {
                text.draw();
                return true;
            }
            if (idx == KroText.HLE_ASCII) {
                text.ascii();
                return true;
            }
        }
        return false;
    }
}
