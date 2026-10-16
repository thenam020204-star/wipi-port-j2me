package hl4.game;

import hl4.gfx.Hf;

/** The paged text box of _mainMenu (MMenu_SetTextBox @0x9e79c): line starts/lengths for a wrapped text, and the selected page 'sel'. */
public final class MMenu {
    public static final int MAX = 20;
    /** _mainMenu+0xc: number of pages (lines of the box as counted by HF_GetByteLenPrintable) */
    public static int nLines;
    /** _mainMenu+0x10+4k: byte length of page k; +0x60+4k: start of page k */
    public static final int[] len = new int[MAX];
    public static final int[] start = new int[MAX + 1];
    /** _mainMenu+0xb0: current page */
    public static int sel;

    private MMenu() {
    }

    /** MMenu_SetTextBox(text, width, height): pages of floor(height / 12) lines. */
    public static void setTextBox(byte[] text, int width, int height) {
        int pos = 0;
        nLines = 0;
        for (int i = 0; i < MAX; i++) {
            len[i] = 0;
        }
        for (int i = 0; i <= MAX; i++) {
            start[i] = 0;
        }
        int n = 0;
        int total = Strs.len(text);
        while (total > pos) {
            int l = Hf.byteLenPrintable(text, total, start[n], width, height / 12, 0);
            len[n] = l;
            pos = (pos + l) & 0xffff;
            n++;
            start[n] = pos;
        }
        nLines = n;
    }
}
