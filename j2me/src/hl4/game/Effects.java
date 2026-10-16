package hl4.game;

import hl4.sys.Bytes;

/** Visual/battle effects (pooled types 5 = Effect, 6 = TargetEffect): Effect_* @0x6655c, TargetEffect_*, EFF_*, CHAR_addEffect.. (init part). */
public final class Effects {
    private Effects() {
    }

    /** Effect_initEffect(e, kind, sub, p4) @0x6655c */
    public static void initEffect(Obj e, int kind, int sub, int p4) {
        byte[] m = e.m;
        m[0x21] = (byte) sub;
        m[0x22] = 0;
        m[0x20] = (byte) kind;
        if (kind == 2 || kind == 0 || kind == 5 || kind == 4 || kind == 1) {
            m[0x23] = 0xb;
        }
        m[0x28] = 0;
        Bytes.w32(m, 0x24, p4);
        initNumbers(e, p4);
        for (int k = 0; k < 10; k++) {          // EFFECT_initImgType @0x5652c
            m[0x37 + k] = 0;
        }
    }

    /** EFFECT_initNumbers(e, n) @0x564a4: the decimal digits of n, least significant first, at +0x2c.. (+0x36 = count); n < 0 leaves them */
    public static void initNumbers(Obj e, int n) {
        if (n < 0) {
            return;
        }
        byte[] m = e.m;
        for (int k = 0; k < 10; k++) {
            m[0x2c + k] = 0;
        }
        m[0x36] = 0;
        do {
            int q = n / 10;
            m[0x2c + m[0x36]] = (byte) (n - q * 10);
            m[0x36]++;
            n = q;
        } while (n != 0);
    }

    /** TargetEffect_set tail @0x6880c: the target-effect extension of an effect struct */
    public static void targetEffectSet(Obj t) {
        byte[] m = t.m;
        Bytes.w32(m, 0x48, 0);
        Bytes.w32(m, 0x44, 0);
        Bytes.fill(m, 0x64, 0xff, 0x44);
        Bytes.w32(m, 0x58, 0);
        Bytes.w32(m, 0x54, 0);
    }

    /** CHAR_setEffAfterMapMove(c) @0xdb650: re-creates the status-effect sprites of an active buff after a map change (none active at game start) */
    public static void setEffAfterMapMove(Char c) {
        for (int i = 0; i < 4; i++) {
            if (c.m[0x10c + 8 * i] == 1) {
                throw new IllegalStateException("CHAR_setEffAfterMapMove: active buff: later");
            }
        }
    }
}
