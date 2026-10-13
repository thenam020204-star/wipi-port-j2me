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

    public void netAttempt() {
        rt.log("net attempt: state=" + (rt.mem.read8(0x15081c4) & 255) + " request=" + (rt.mem.read8(0x1508330) & 255)
            + " sub=" + rt.mem.read32(0x1502d14 + 4));
    }

    /**
     * Cash purchases (request types 3 and 30, handler 0x3f9fa) are free offline: the game has already moved
     * the items into the bag before it sends the request and only commits (success) or rolls back (failure)
     * when the reply arrives. The reply of type 30 is 12 bytes: [7] must be 0 and [8] (purchase result:
     * 0 = monthly limit exceeded, 2/3 = other refusals) must be 1; type 3 is 1 byte '0'. Every other request keeps failing like a phone without service.
     */
    public boolean netAllow() {
        int type = rt.mem.read8(0x1508330) & 255;
        return type == 3 || type == 30;
    }

    public int netReply(int buf, int len) {
        int type = rt.mem.read8(0x1508330) & 255;
        for (int i = 0; i < len; i++) rt.mem.write8(buf + i, type <= 0x19 ? '0' : i == 8 ? 1 : 0);
        return len;
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
