package hl4.gfx;

/**
 * CIF sprite index (docs/phaseC_formats.md section 6; verified 148/148 against SPR_loadSprite).
 * s8 n0, n0 bytes (ignored by the loader), u8 nAnim; per anim: s8 nDir; per direction: u8 nFrames; per frame: s8 nParts + parts.
 * Part size: mode == 2 -> 5 (10 if bit 7 of the first byte is set) else 4 (9).  frameStart[a][d][f] = offset of the nParts byte.
 */
public final class Cif {
    public final byte[] data;
    public final int mode;
    public int nAnim;
    public int[][][] frameStart;
    public int end;

    public Cif(byte[] data, int mode) {
        this.data = data;
        this.mode = mode;
        int n0 = data[0];
        int p = n0 <= 0 ? 1 : n0 + 1;
        nAnim = data[p++] & 0xff;
        frameStart = new int[nAnim][][];
        for (int a = 0; a < nAnim; a++) {
            int nDir = data[p++];
            int nd = nDir > 0 ? nDir : 0;
            frameStart[a] = new int[nd][];
            for (int d = 0; d < nd; d++) {
                int nf = data[p++] & 0xff;
                frameStart[a][d] = new int[nf];
                for (int f = 0; f < nf; f++) {
                    frameStart[a][d][f] = p;
                    int npart = data[p++];
                    for (int k = 0; k < npart; k++) {
                        boolean lg = (data[p] & 0x80) != 0;
                        p += mode == 2 ? (lg ? 10 : 5) : (lg ? 9 : 4);
                    }
                }
            }
        }
        end = p;
    }
}
