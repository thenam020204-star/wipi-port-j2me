package hl4.game;

import hl4.gfx.Sprite;
import hl4.sys.Bytes;
import hl4.sys.Clock;

/** Hero_draw @0x7dbac (disassembly-verified) with the character draw helpers it uses (CHAR_setBlendType @0x38fc4, CHAR_drawEffectList, HERO_Particle_Process @0x7cbb8). */
public final class HeroDraw {
    private HeroDraw() {
    }

    /** _ParticleStartTime (the original keeps a 64-bit millisecond clock; only the difference between two calls is used) */
    private static long particleStart;

    /** CHAR_setBlendType(c, p2): the +0x16d countdown picks a control-palette tint from the 7 x 4 table (5 shorts per entry), idle = bit 7 set */
    public static void setBlendType(Char c, int p2) {
        byte[] m = c.m;
        int u = m[0x16d] & 0xff;
        int hi = u >> 7;
        int sel = p2 <= 0 ? 0 : (hi & 1);
        if (sel != 0) {
            m[0x16d] = 3;
            u = 3;
        } else {
            if (hi != 0) {
                m[0x16d] = (byte) 0xff;
                c.spr.f68 = 0;                                  // SPR_set_restore_pal
                m[0x16d]--;
                return;
            }
        }
        if (((u ^ 0x80) >> 7) != 0 && p2 >= 0) {
            int o = p2 * 20 + (byte) u * 5;
            short[] t = Tab.BLEND_PARAMS;
            c.spr.setControlPal(t[o], t[o + 1], t[o + 2], t[o + 3], t[o + 4]);
        } else {
            c.spr.f68 = 0;
        }
        m[0x16d]--;
    }

    /** CHAR_drawEffectList(c, ox, oy): the target effects and the effect list of the character (both empty in the scenes ported so far) */
    public static void drawEffectList(Char c) {
        if (!c.targetEffects.isEmpty() || !c.effects.isEmpty()) {
            throw new IllegalStateException("CHAR_drawEffectList (live effects): later");
        }
    }

    /** HERO_Particle_Process @0x7cbb8: onIdleParticles(interval) when the particle set exists; no live particles yet */
    private static void particleProcess() {
        long now = Clock.now();
        if (Particles.sets != null) {
            if (Particles.curCount > 0) {
                throw new IllegalStateException("onIdleParticles (live particles): later");
            }
        }
        particleStart = now;
    }

    private static boolean skillCharging(Char h) {
        byte[] m = h.m;
        if (m[0x16b] == 0) {
            return m[0x167] == 0x15;
        }
        if (m[0x16b] == 1) {
            return m[0x167] == 0x14;
        }
        return false;
    }

    private static boolean teleporting(Char h) {
        return h.m[0x189] == 1 && (h.m[0x167] & 0xff) - 8 >= 0 && (h.m[0x167] & 0xff) - 8 < 2;
    }

    /** Hero_draw(hero, ox, oy) */
    public static void draw(Char h, int ox, int oy) {
        byte[] m = h.m;
        byte[] e = G.event;
        if (m[0x18] == 0) {
            return;
        }
        boolean ev = Event.isEvent();
        if (!ev && (m[0x167] & 0xff) <= 1) {
            m[0x167] = (byte) ((m[0x167] & 0xff) + 2);
        }
        if (m[0x16e] == 7) {
            // a boss on the map: the hero's boss-lock sprite (monster sprite, motion 0x12) while a boss is listed
            for (int i = 0; i < 0x7f; i++) {
                Obj b = G.monsterList.a[i];
                if (b != null && Monsters.isBoss(b)) {
                    throw new IllegalStateException("Hero_draw boss-lock branch: later");
                }
            }
        }
        if (!teleporting(h)) {
            ObjDraw.shadow((short) (Bytes.s32(m, 8) + ox), (short) (Bytes.s32(m, 0xc) + oy), 0, m[0x210]);
        }
        int dir;
        if (!ev && HeroCtl.isSkipDir(h, m[0x167])) {
            dir = 0;
        } else if (Bytes.s32(e, 0x94c) == -1) {
            dir = m[0x168] & 0xff;
            if ((byte) dir > 3) {
                dir -= 3;
                m[0x168] = (byte) dir;
            }
        } else {
            dir = Bytes.s32(e, 0x94c) & 0xff;
        }
        if (!ev) {
            int a41 = m[0xa41] & 0xff;
            if (a41 == 2 || a41 == 0) {
                int t = ((m[0x167] & 0xff) - 2) & 0xff;
                if (t <= 1) {
                    m[0x167] = (byte) t;
                }
            } else if ((m[0x167] & 0xff) <= 1) {
                m[0x167] = (byte) ((m[0x167] & 0xff) + 2);
            }
        }
        Sprite s = h.spr;
        if (!Chars.isHideBuff(h)) {
            s.f6f = 0xff;
            s.f6e = 0;
        } else {
            s.f6f = 100;
            s.f6e = 1;
        }
        int bt = m[0x167] == 4 ? 0 : (skillCharging(h) ? 6 : -1);
        setBlendType(h, bt);
        if (m[0xa46] != 0) {
            throw new IllegalStateException("Hero_draw (+0xa46 set): later");
        }
        Bytes.w32(m, 0xa34, 0);
        int a44 = m[0xa44] & 0xff;
        if (a44 == 0) {
            if (m[0x287] == 0) {
                s.setControlPal(0, 0, 0, m[0x288] & 0xff, 0);
            } else if (m[0x287] == 1) {
                s.setControlPal(0xff, 0xff, 0xff, m[0x288] & 0xff, 0);
            }
            if (!ev) {
                throw new IllegalStateException("Hero_draw (play mode): later");
            }
            if (G.map[0x68] == 2) {
                s.setControlPal(0, 0, 0, 0xff, 0x10);
            }
            int dbg = m[0x172];
            m[0x8c] = (byte) s.drawNpc(hl4.gfx.GfxOut.I, Gfx.scr, m[0x167], dbg <= 4 ? 2 : 0, dbg <= 4 ? 3 : 1, (byte) dir, m[0x8c], Bytes.s32(m, 8) + ox,
                    Bytes.s32(m, 0xc) + oy, 0, 0, 0);
            s.f68 = 0;
        } else if ((a44 & 1) != 0) {
            if (!ev) {
                throw new IllegalStateException("Hero_draw (blink, play mode): later");
            }
            if (G.map[0x68] == 2) {
                s.setControlPal(0, 0, 0, 0xff, 0x10);
            }
            int dbg = m[0x172];
            m[0x8c] = (byte) s.drawNpc(hl4.gfx.GfxOut.I, Gfx.scr, m[0x167], dbg <= 4 ? 2 : 0, dbg <= 4 ? 3 : 1, (byte) dir, m[0x8c], Bytes.s32(m, 8) + ox,
                    Bytes.s32(m, 0xc) + oy, 0, 0, -1);
        }
        if (Opt.particle()) {
            particleProcess();
        }
        drawEffectList(h);
        if (m[0x171] != -1) {
            Imgs.imo.f68 = 0;                                       // SPR_set_restore_pal
            Sprite im = Imgs.imo;
            im.f6c = im.drawSprite(hl4.gfx.GfxOut.I, Gfx.scr, m[0x171], 0, im.f6c, ox + Bytes.s32(m, 8), Bytes.s32(m, 0xc) - 0x20 + oy, 0, 0);
            im.repeatFrame(m[0x171], 0);
        }
        if (m[0x16e] == 7 && m[0x210] <= 4) {
            throw new IllegalStateException("Hero_draw tile property 7 effect: later");
        }
    }
}
