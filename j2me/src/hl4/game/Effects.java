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
        m[0x24] = (byte) p4;
        initNumbers(e, p4);
        m[0x37] = 0;
        for (int k = 0; k < 9; k++) {
            m[0x38 + k] = 0;
        }
    }

    /** EFFECT_initNumbers(e, p) (damage-number slots) */
    public static void initNumbers(Obj e, int p) {
        // read in the effect module port (damage numbers); the free-slot state needs nothing from it
    }

    /** TargetEffect_set tail @0x6880c: the target-effect extension of an effect struct */
    public static void targetEffectSet(Obj t) {
        byte[] m = t.m;
        m[0x48] = 0;
        m[0x44] = 0;
        Bytes.fill(m, 0x64, 0xff, 0x44);
        m[0x58] = 0;
        m[0x54] = 0;
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
