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

    /** Effect_process(e) @0x563e8: advances the frame; past the last frame (+0x23) it wraps to 0 and reports the end */
    public static boolean process(Obj e) {
        byte[] m = e.m;
        m[0x22]++;
        if (m[0x23] < m[0x22]) {
            m[0x22] = 0;
            return true;
        }
        return false;
    }

    /**
     * Effect_draw(e, x, y) @0x56ac0: damage numbers (kinds 0/4) or critical numbers (kinds 1/5, zoomed 200% up to frame 5, 160% at 6, 120% at 7, then
     * 100%) fading out from frame 7 (alpha 0xcc, 0x99, 0x66, 0x33, 0 at frame 11); kinds 4/5 use the hero's number images.
     */
    public static void draw(Obj e, int x, int y) {
        byte[] m = e.m;
        int f = m[0x22];
        int kind = m[0x20];
        boolean hero = ((kind - 4) & 0xff) <= 1;
        int sy = f > 5 ? 100 : 200;
        int sx = sy;
        int alpha;
        if (f == 6) {
            sx = sy = 0xa0;
            alpha = 0xff;
        } else if (f == 7) {
            sx = sy = 0x78;
            alpha = 0xcc;
        } else if (f == 8) {
            alpha = 0x99;
        } else if (f == 9) {
            alpha = 0x66;
        } else if (f == 10) {
            alpha = 0x33;
        } else {
            alpha = f == 11 ? 0 : 0xff;
        }
        if (kind == 4 || kind == 0) {
            drawNumber(e, x, y - 0x1c, 0x11, 100, 100, 0, alpha, hero);      // EFFECT_drawDamage @0x56848
        } else if (kind == 5 || kind == 1) {
            drawNumber(e, x, y - 0x1e, 0x11, sx, sy, 0, alpha, hero);        // EFFECT_drawCriDamage @0x565cc
        }
    }

    /**
     * EFFECT_drawDamage (e, x, y, anchor, effect, alpha, hero) @0x56848 and EFFECT_drawCriDamage (e, x, y, anchor, scaleX, scaleY, effect, alpha,
     * hero) @0x565cc share this body (drawDamage = scale 100/100): nothing when the value +0x24 < 0; the digit step is (width of digit image 9 - 3)
     * * scaleX / 100; anchor 0x11 centres the row.  One more digit appears per frame from the most significant one (the newest is drawn 1 px higher),
     * every digit drops by frame - 4 after frame 5.
     */
    private static void drawNumber(Obj e, int x, int y, int anchor, int scx, int scy, int effect, int alpha, boolean hero) {
        byte[] m = e.m;
        if (Bytes.s32(m, 0x24) < 0) {
            return;
        }
        int f = m[0x22];
        hl4.gfx.Gbm d9 = Icons.damageNumber[9];
        int step = ((short) d9.w - 3) * scx / 100;
        int cnt = m[0x36];
        if (anchor == 0x11) {
            x -= cnt * step / 2;
        }
        int vis = cnt;
        int first = 0;
        if (f < cnt) {
            vis = f + 1;
            first = cnt - vis;
        }
        int x0 = x + step / 2;
        int lift = 0;
        hl4.gfx.Frame fr = Gfx.scr;
        for (int i = cnt - 1; i >= first; i--) {
            if (f < cnt && i == first) {
                lift = -1;
            }
            hl4.gfx.Gbm img = (hero ? Icons.heroDamageNumber : Icons.damageNumber)[m[0x2c + i]];
            int drop = f > 5 ? f - 4 : 0;
            hl4.gfx.Fx.drawImgRotateEx(fr, x0 + step * (cnt - 1 - i), y + lift + drop, (short) img.aux, (short) img.h, img, 0, 0, 0, 0, scx, scy,
                    effect, alpha, 0x11);
            hl4.gfx.Fx.restorePalette(img);
        }
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
