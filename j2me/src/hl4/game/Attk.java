package hl4.game;

import hl4.gfx.SprProps;
import hl4.sys.Bytes;

/**
 * Attack-property records of a character (ATTKPRO_getProperty @0xbfe30, ATTKPRO_getHitProperty @0xc16a4).  The record of the current motion is
 * {table pointer at base, entry count at base+4}; the entry of the current frame is copied to base+8 {s16 frame id, s16 cell count, cells pointer}.
 * The pointers are Char.hitTable / Char.hitCells (hero base 0x22c, monster base 0x2b4).
 */
public final class Attk {
    private Attk() {
    }

    /** ATTKPRO_getProperty(spr, c+base, motion): SPR_getPropertyIdxOfMotion then SPR_getPropertyData, or a zero record */
    public static void getProperty(Char c, int base, int motion) {
        c.hitTable = null;
        int n = 0;
        SprProps pr = c.spr.props;
        if (motion != -1 && pr != null) {
            int idx = pr.idx[motion & 0xff];
            if (idx != -1 && idx >= 0 && idx < pr.n && pr.slots[idx] != null) {
                c.hitTable = pr.slots[idx];
                n = c.hitTable.length;
            }
        }
        Bytes.w32(c.m, base + 4, n);
    }

    /** ATTKPRO_getHitProperty(c+base, frame, c+base+8): false (entry = -1s) when the motion has no entry for the frame */
    public static boolean getHitProperty(Char c, int base, int frame) {
        byte[] m = c.m;
        SprProps.Entry[] t = c.hitTable;
        if (t == null) {
            return false;
        }
        int n = Bytes.s32(m, base + 4);
        for (int i = 0; i < n; i++) {
            if (t[i].id == (short) frame) {
                Bytes.w16(m, base + 8, t[i].id);
                Bytes.w16(m, base + 10, t[i].nB);
                c.hitCells = t[i].cells;
                return true;
            }
        }
        Bytes.fill(m, base + 8, 0xff, 8);
        c.hitCells = null;
        return false;
    }
}
