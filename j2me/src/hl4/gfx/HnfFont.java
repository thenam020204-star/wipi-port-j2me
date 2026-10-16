package hl4.gfx;

import hl4.sys.Res;

/**
 * HNF bitmap fonts (docs/phaseC_formats.md section 4; verified per glyph against HF_DrawText, 2350 syllables + 82 ASCII).
 * Glyph composition only; the text layout engine (control codes, colours, line breaks) is Phase E.
 * Hangul glyph rows are 16-bit (bit 15 = leftmost pixel); Latin rows are 8-bit (bit 7 = leftmost).
 */
public final class HnfFont {
    private final int kh;                 // Hangul glyph height
    private final int kbytes;             // bytes per Hangul glyph
    private final byte[] kor;             // whole _FT_KOR10X11 file
    private final int choOff;
    private final int jungOff;
    private final int jongOff;
    private final byte[] type844;
    private final byte[] hanInfo;
    private final byte[] hanCode;
    private final byte[][] eng = new byte[2][];   // [0] = 5x11, [1] = 7x7 file
    private final int[] engH = new int[2];

    public HnfFont() {
        kor = Res.load("/FT/_FT_KOR10X11");
        if (kor == null || kor[0] != 'H' || kor[1] != 'N' || kor[2] != 'F') {
            throw new IllegalStateException("font");
        }
        kh = kor[5] & 0xff;
        kbytes = kh * ((kor[4] & 0xff) / 8);
        choOff = 9;
        jungOff = choOff + 160 * kbytes;
        jongOff = jungOff + 88 * kbytes;
        type844 = Res.load("/FT/_FT_TYPE844");
        hanInfo = Res.load("/FT/_FT_HANINFO");
        hanCode = Res.load("/FT/_FT_HANCODE");
        String[] names = {"/FT/_FT_ENGL5X11", "/FT/_FT_ENGS7X7"};
        for (int i = 0; i < 2; i++) {
            eng[i] = Res.load(names[i]);
            engH[i] = eng[i][5] & 0xff;
        }
    }

    public int hangulHeight() {
        return kh;
    }

    /** Johab-like code of an EUC-KR KS X 1001 syllable, or -1. */
    public int johab(int lead, int trail) {
        if (lead >= 0xb0 && lead <= 0xc8 && trail >= 0xa1 && trail <= 0xfe) {
            int i = (lead - 0xb0) * 94 + (trail - 0xa1);
            return ((hanInfo[2 * i] & 0xff) << 8) | (hanInfo[2 * i + 1] & 0xff);
        }
        return -1;
    }

    /** First/second byte of the _FT_HANINFO entry of a KS X 1001 syllable (0 when the pair is outside the table: the original reads unrelated memory there). */
    public int hanInfoHi(int lead, int trail) {
        int i = (lead - 0xb0) * 94 + (trail - 0xa1);
        return i >= 0 && 2 * i + 1 < hanInfo.length ? hanInfo[2 * i] & 0xff : 0;
    }

    public int hanInfoLo(int lead, int trail) {
        int i = (lead - 0xb0) * 94 + (trail - 0xa1);
        return i >= 0 && 2 * i + 1 < hanInfo.length ? hanInfo[2 * i + 1] & 0xff : 0;
    }

    /** Composes the glyph of a Johab code into rows[0..hangulHeight()): cho glyph copied, jung/jong OR-ed in. */
    public void compose(int code, int[] rows) {
        int cho = hanCode[(code >> 10) & 0x1f] & 0xff;
        int jung = hanCode[0x1f + ((code >> 5) & 0x1f)] & 0xff;
        int jong = hanCode[0x3e + (code & 0x1f)] & 0xff;
        int hj = jong != 0 ? 1 : 0;
        int jungSet = type844[44 + 20 * hj + cho] & 0xff;
        int choSet = type844[22 * hj + jung] & 0xff;
        int jongSet = type844[84 + jung] & 0xff;
        int n = kbytes;
        int co = choOff + (choSet * 20 + cho) * n;
        for (int r = 0; r < kh; r++) {
            rows[r] = ((kor[co + 2 * r + 1] & 0xff) << 8) | (kor[co + 2 * r] & 0xff);
        }
        if (jung != 0) {
            int jo = jungOff + (jungSet * 22 + jung) * n;
            for (int r = 0; r < kh; r++) {
                rows[r] |= ((kor[jo + 2 * r + 1] & 0xff) << 8) | (kor[jo + 2 * r] & 0xff);
            }
        }
        if (hj != 0) {
            int go = jongOff + (jongSet * 28 + jong) * n;
            for (int r = 0; r < kh; r++) {
                rows[r] |= ((kor[go + 2 * r + 1] & 0xff) << 8) | (kor[go + 2 * r] & 0xff);
            }
        }
    }

    public int latinHeight(int font) {
        return engH[font];
    }

    /** Latin glyph rows (font 0 = 5x11, 1 = 7x7) of ASCII ch (32..126); '\\' uses glyph slot 4 (@0x75f8c). */
    public void latin(int font, int ch, int[] rows) {
        int idx = ch == 0x5c ? 4 : ch - 32;
        int h = engH[font];
        for (int r = 0; r < h; r++) {
            rows[r] = eng[font][6 + idx * h + r] & 0xff;
        }
    }

    /** HF_getRealWidthEng @0x74d78. */
    public static int latinWidth(int font, int ch) {
        if (font != 0) {
            return 5;
        }
        return ch == 0x5c ? 7 : 5;
    }
}
