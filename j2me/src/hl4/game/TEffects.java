package hl4.game;

import hl4.gfx.SprProps;
import hl4.gfx.Sprite;
import hl4.sys.Bytes;

/**
 * Target effects (TargetEffect_* @0x57a24..0x58820, CHAR_targetEffect @0x3c308, Monster_add*OnMap @0x3b0c8.., *_pre/endProcessOneTargetEffect
 * @0x3a260..0x3ac38) and the plain effects of a character's effect list (CHAR_addEffect @0x3c824, Effect_process @0x563e8).
 * Written from the disassembly; the bullet / lulker / land-mine / emoticon variants and the hero's own skill effects stop with "later".
 */
public final class TEffects {
    private TEffects() {
    }

    private static void later(String what) {
        throw new IllegalStateException(what + ": later");
    }

    /** _commonEffectSpr (loadCommonImage @0xbd35c: /CM/_CM_CMEFFSPR + /CM/_CM_CMEFF through loadSimpleSprite) */
    public static Sprite commonSpr;

    public static void loadCommon() {
        commonSpr = new Sprite(hl4.sys.Res.load("/CM/_CM_CMEFFSPR"), 0x11);
        commonSpr.setImages(0, hl4.gfx.Img.loadAll("/CM/_CM_CMEFF", G.map[0x68]));
    }

    // ---- creation --------------------------------------------------------------------------------------------------------------------------

    /**
     * TargetEffect_init(t, body, spr, kind, eff, num, group, delay, idx4f, v50, dir, x, y, v52, (unused), v29, life) @0x581c8 (the 13 stack
     * arguments of the original, in order).  Kinds above 9 also look up the attack-property record of motion 'eff'.
     */
    static void init(TEff t, int body, Sprite spr, int kind, int eff, int num, int group, int delay, int v4f, int v50, int dir, int x, int y, int v52,
            int v29, int life) {
        byte[] m = t.m;
        m[0x4c] = (byte) body;
        m[0x4e] = (byte) ((byte) delay == -1 ? 1 : 0);
        m[0x4f] = (byte) v4f;
        m[0x50] = (byte) v50;
        m[0x51] = (byte) dir;
        m[0x52] = (byte) v52;
        Bytes.w16(m, 0xa8, (short) life);
        m[0x4d] = (byte) delay;
        t.spr = spr;
        Effects.initEffect(t, kind, (byte) eff, num);
        m[0x28] = (byte) group;
        m[0x23] = (byte) spr.maxFrame((byte) eff, (byte) group);
        m[0x29] = (byte) v29;
        m[0x22] = 0;
        Bytes.w32(m, 0xc, y);
        Bytes.w32(m, 8, x);
        m[0x2a] = (byte) x;
        m[0x2b] = (byte) y;
        if (kind > 9) {
            getProperty(t, (byte) eff);
        }
        m[0xb2] = 0;                                    // Eff_changeDrawPosition(t, 0)
    }

    /** ATTKPRO_getProperty(t+0x48 sprite, t+0x54, motion) */
    static void getProperty(TEff t, int motion) {
        t.hitTable = null;
        int n = 0;
        SprProps pr = t.spr.props;
        if (motion != -1 && pr != null) {
            int idx = pr.idx[motion & 0xff];
            if (idx != -1 && idx >= 0 && idx < pr.n && pr.slots[idx] != null) {
                t.hitTable = pr.slots[idx];
                n = t.hitTable.length;
            }
        }
        Bytes.w32(t.m, 0x58, n);
    }

    /** CHAR_targetEffect(c, tgt, body, kind, eff, s1, s2, life, s4, s5, dir, x, y, v29) @0x3c308: a new target effect owned by tgt (kind 9 = common effect sprite) */
    static void charTargetEffect(Char c, Char tgt, int body, int kind, int eff, int s1, int s2, int life, int s4, int s5, int dir, int x, int y, int v29) {
        if ((byte) eff == -1) {
            return;
        }
        TEff t = (TEff) Pool.getNew(Pool.TARGET_EFFECT);
        if (t == null) {
            return;
        }
        if (kind == 9) {
            // TargetEffect_initCommon @0x58390
            init(t, body, commonSpr, 9, eff, 0, s1, s2, -1, -1, -1, x, y, -1, v29, life);
            t.sprChar = null;
        } else if (kind == 6) {
            later("TargetEffect_initEmoticon");
        } else {
            init(t, body, c.spr, kind, eff, 0, s1, s2, s4, s5, dir, x, y, -1, v29, life);
            t.sprChar = c;
        }
        if (tgt != null) {
            t.owner = tgt;
        }
        tgt.targetEffects.addObject(t);
        G.targetEffectList.add(t);
    }

    /** CHAR_addCommonEffect(c, tgt, body, eff, x, y, s2, life) @0x3c508 */
    static void addCommonEffect(Char c, Char tgt, int body, int eff, int x, int y, int s2, int life) {
        charTargetEffect(c, tgt, body, 9, eff, 0, (byte) s2, (short) life, -1, -1, -1, x, y, 1);
    }

    /** CHAR_addEffect(c, kind, sub, num) @0x3c824: an Effect (pool type 5) in the character's effect list (+0x130) */
    static void addEffect(Char c, int kind, int sub, int num) {
        if (sub == -1) {
            return;
        }
        Obj e = Pool.getNew(Pool.EFFECT);
        if (e == null) {
            return;
        }
        Effects.initEffect(e, kind, sub, num);
        c.effects.addObject(e);
    }

    /**
     * Monster_addTargetEffectOnMap(o, kind, eff, a, delay, life, idx, v50, dir, x, y, v29?, a8, a9, a10, a11) @0x3b0c8 — only the sunken kind 0xf
     * (Monster_addSunkenOnMap @0x3b718: an effect at (x, y) kept in the monster's target-effect list).
     */
    static void addSunkenOnMap(Char o, int eff, int p2, int p3, int life, int idx, int v50, int dir, int x, int y, int s6, int s7, int s8, int s9) {
        if ((byte) eff == -1) {
            return;
        }
        TEff t = (TEff) Pool.getNew(Pool.TARGET_EFFECT);
        if (t == null) {
            return;
        }
        if (o != null) {
            t.owner = o;
        }
        t.m[0xb6] = 0;
        if (o.m[0x30c] == 0x38 && o.m[0x3ea] == 2) {
            t.m[0xb6] = 2;
        }
        // TargetEffect_initSunken @0x586dc -> TargetEffect_init(t, 1, o spr, 0xf, eff, 0, p2, p3, idx, v50, dir, x, y, s6, s7, 1, life)
        init(t, 1, o.spr, 0xf, eff, 0, p2, p3, idx, v50, dir, x, y, s6, 1, life);
        t.sprChar = o;
        o.targetEffects.addObject(t);
        G.targetEffectList.add(t);
    }

    // ---- per-tick ------------------------------------------------------------------------------------------------------------------------

    /** EFFECT_canDrawing @0x56490: the start delay (+0x4d) has run out */
    static boolean canDrawing(TEff t) {
        return t.m[0x4d] <= 0;
    }

    /** TargetEffect_getHitProperty @0x57e8c: the hit entry of the current frame (+0x5c) */
    static boolean getHitProperty(TEff t) {
        if (t.hitTable == null) {
            return false;
        }
        getProperty(t, t.m[0x21]);
        byte[] m = t.m;
        SprProps.Entry[] tb = t.hitTable;
        if (tb == null) {
            return false;
        }
        int n = Bytes.s32(m, 0x58);
        int frame = t.m[0x22];
        for (int i = 0; i < n; i++) {
            if (tb[i].id == (short) frame) {
                Bytes.w16(m, 0x5c, tb[i].id);
                Bytes.w16(m, 0x5e, tb[i].nB);
                t.hitCells = tb[i].cells;
                return true;
            }
        }
        Bytes.fill(m, 0x5c, 0xff, 8);
        t.hitCells = null;
        return false;
    }

    /**
     * TargetEffect_preProcess @0x57f10: copies the hit cell of the frame to +0x64 (cell types 3/4), screen shake (5), screen tint (10), particles (13);
     * returns 1 when the frame has hit cells (bullets 2, land mines 3).
     */
    static int preProcess(TEff t) {
        if (!canDrawing(t)) {
            return 0;
        }
        byte[] m = t.m;
        int r = 0;
        if (getHitProperty(t)) {
            Bytes.fill(m, 0x64, 0, 0x44);
            SprProps.Cell[] cells = t.hitCells;
            int n = Bytes.s16(m, 0x5e);
            boolean tint = false;
            for (int k = 0; k < n; k++) {
                SprProps.Cell c = cells[k];
                int ty = (byte) c.type;
                int[] g = c.g[m[0x28]];
                if (ty == 3 || ty == 4) {
                    c.store(m, 0x64);
                } else if (ty == 5) {
                    int a = g[0];
                    int b = g[1];
                    if (a > 0) {
                        Cam.setShake(1, (byte) a, b > 0 ? 2 : 0);
                    } else if (b > 0) {
                        Cam.setShake(1, (byte) b, 1);
                    }
                } else if (ty == 10) {
                    G.map[0x34e] = (byte) g[0];
                    G.map[0x34f] = (byte) g[1];
                    tint = (g[1] & 0xff) != 0;
                } else if (ty == 13) {
                    if (Opt.particle()) {
                        later("HERO_extra_set_particles");
                    }
                }
                if (!tint) {
                    G.map[0x34e] = (byte) 0xff;
                }
            }
            r = 1;
        }
        int k = m[0x20];
        if (m[0x4c] == 0) {
            if (k == 0xd) {
                later("Eff_bulletEffMovePosition");
            }
        } else if (m[0x4c] == 1) {
            if (k == 0xd) {
                later("Eff_bulletEffMovePosition");
            } else if (k == 0x10) {
                later("Eff_lulkerEffMovePosition");
            }
        }
        return k == 0xe ? 3 : (byte) r;
    }

    /** Effect_process @0x563e8: next frame, wrapping; true when it wrapped */
    static boolean effectProcess(Obj e) {
        byte[] m = e.m;
        int f = (m[0x22] + 1) & 0xff;
        m[0x22] = (byte) f;
        if (m[0x23] < (byte) f) {
            m[0x22] = 0;
            return true;
        }
        return false;
    }

    /** TargetEffect_endProcess @0x57a24: advances the effect; true when it is finished (life over, animation over, or off the map) */
    static boolean endProcess(TEff t) {
        byte[] m = t.m;
        int d = m[0x4d];
        if (d > 0) {
            d = (d - 1) & 0xff;
            m[0x4d] = (byte) d;
            if (d == 0 && Bytes.u16(m, 0x20) == 0x110c) {
                Sound.playEff(8);
            }
            return false;
        }
        if (m[0xb1] > 0) {
            m[0xb1]--;
        }
        int loop = m[0x4e] & 0xff;
        int heroCls = Pool.hero.m[0x16b];
        if (loop != 0) {
            int f = (m[0x22] + 1) & 0xff;
            m[0x22] = (byte) f;
            if (m[0x23] < (byte) f) {
                m[0x22] = 0;
            }
            if (m[0x20] == 0xe && heroCls == 1 && m[0x4f] == 6 && m[0x21] == 0xf && m[0xb1] == 0) {
                later("TargetEffect_endProcess: land mine 0xe (hero class 1)");
            }
            return false;
        }
        int life = Bytes.s16(m, 0xa8);
        if (life == -1) {
            if (m[0x20] == 0xe) {
                later("TargetEffect_endProcess: land mine 0xe");
            }
            boolean chain = ((m[0x4f] & 0xff) - 0x22 & 0xff) <= 2;
            int s21 = m[0x21];
            if ((heroCls == 0 || heroCls == 1) && chain) {
                if (s21 == 0x25 || s21 == 0x26) {
                    later("TargetEffect_endProcess: chain pull effects 0x25/0x26");
                }
            } else {
                int k = m[0x20] & 0xff;
                if (k == 0x18 || k == 0xc) {
                    getProperty(t, s21);
                }
            }
            return effectProcess(t);
        }
        if (life != 0) {
            if (Monsters.isBoss((Char) t.owner)) {
                int f = (m[0x22] + 1) & 0xff;
                m[0x22] = (byte) f;
                if (m[0x23] >= (byte) f) {
                    return false;
                }
                m[0x22] = 0;
                int v = (Bytes.u16(m, 0xa8) - 1) & 0xffff;
                Bytes.w16(m, 0xa8, v);
                return (short) v <= 0;
            }
            int v = (Bytes.u16(m, 0xa8) - 1) & 0xffff;
            Bytes.w16(m, 0xa8, v);
            if ((short) v <= 0) {
                return true;
            }
            int f = (m[0x22] + 1) & 0xff;
            m[0x22] = (byte) f;
            if (m[0x23] >= (byte) f) {
                return false;
            }
            m[0x22] = 0;
            return false;
        }
        int x = Bytes.s32(m, 8);
        int y = Bytes.s32(m, 0xc);
        int w = Bytes.s32(m, 0x70);
        int h = Bytes.s32(m, 0x74);
        if (x + w < 0 || y + h < 0 || x - w > Bytes.s32(G.map, 0x84) || y - h > Bytes.s32(G.map, 0x88)) {
            return true;
        }
        int f = (m[0x22] + 1) & 0xff;
        m[0x22] = (byte) f;
        if (m[0x23] >= (byte) f) {
            return false;
        }
        m[0x22] = 0;
        return false;
    }

    /** Eff_getEffectColRect(t, out) @0x56440: the copied hit cell's rectangle of group +0x28, relative to the effect position */
    static void colRect(TEff t, int[] out) {
        byte[] m = t.m;
        int g = m[0x28] * 16;
        out[0] = Bytes.s32(m, 8) + Bytes.s32(m, 0x68 + g);
        out[1] = Bytes.s32(m, 0xc) + Bytes.s32(m, 0x6c + g);
        out[2] = Bytes.s32(m, 0x70 + g);
        out[3] = Bytes.s32(m, 0x74 + g);
    }

    /** Eff_removeEffectInList(list, t, flags) @0x3a0e8: flag 0 also drops it from _targetEffectList; any flag unlinks (and returns) it */
    static void removeInList(LList l, TEff t, boolean f0, boolean f1, boolean f2) {
        if (f0) {
            G.targetEffectList.remove(t);
        }
        if (f0 || f1 || f2) {
            l.remove(t);
        }
    }

    /** OBJ_equalMapIdx(o, idx) @0xb92cc */
    static boolean equalMapIdx(Obj o, int idx) {
        int c = Bytes.s16(o.m, Obj.MAPIDX);
        return c == -1 || c == idx;
    }

    private static final int[] er = new int[4];
    private static final int[] hr = new int[4];
    private static final int[] out = new int[4];

    /** EFF_preProcessOneTargetEffect(list, t) @0x3abc0: by the owner's type */
    static void preProcessOne(LList l, TEff t) {
        Obj ow = t.owner;
        if (ow == null) {
            return;
        }
        int ty = Obj.structType(ow);
        if (ty == 2) {
            heroPreProcess(l, t);
        } else if (ty == 3) {
            monsterPreProcess(l, t);
        } else if (ty == 4) {
            later("Summon_preProcessOneTargetEffect");
        }
    }

    /** EFF_endProcessOneTargetEffect(list, t) @0x3a454 */
    static void endProcessOne(LList l, TEff t) {
        Obj ow = t.owner;
        if (ow == null) {
            return;
        }
        int ty = Obj.structType(ow);
        if (ty == 2) {
            heroEndProcess(l, t);
        } else if (ty == 3) {
            monsterEndProcess(l, t);
        } else if (ty == 4) {
            later("Summon_endProcessOneTargetEffect");
        }
    }

    /** Hero_preProcessOneTargetEffect(list, t) @0x3a8a8: effects with a body (+0x4c != 0) only animate here; the hero's own skill effects are later */
    static void heroPreProcess(LList l, TEff t) {
        int r = (byte) preProcess(t);
        if (t.m[0x4c] != 0) {
            return;
        }
        if (r == 1 || r == 2 || r == 3) {
            later("Hero_preProcessOneTargetEffect: hero skill effect (HERO_serch_monster_area)");
        }
    }

    /** Hero_endProcessOneTargetEffect(list, t) @0x3a3ac */
    static void heroEndProcess(LList l, TEff t) {
        if (!endProcess(t)) {
            return;
        }
        int b = t.m[0x4c];
        if (b == 0 || b == 1 || b == 2) {
            removeInList(l, t, true, false, false);
        }
    }

    /** Monster_preProcessOneTargetEffect(list, t) @0x3a520: a monster's sunken / bullet effect that touches the hero's hit rect attacks him */
    static void monsterPreProcess(LList l, TEff t) {
        Char owner = (Char) t.owner;
        int r = preProcess(t) & 0xff;
        if (Event.isEvent()) {
            return;
        }
        byte[] m = t.m;
        int b = m[0x4c] & 0xff;
        if (b == 0 && r == 1 && Pool.hero.m[0x16b] == 1 && m[0x21] == 0x1d && m[0x4f] == 7) {
            later("Monster_preProcessOneTargetEffect: HERO_serch_monster_area");
        }
        if ((byte) b != 1) {
            removeInList(l, t, false, false, false);
            return;
        }
        boolean f0 = false;
        boolean f2 = false;
        int sr = (byte) r;
        Char h = Pool.hero;
        if (sr == 1) {
            hr[0] = Chars.getHittedRectX(h);
            hr[1] = Chars.getHittedRectY(h);
            hr[2] = Chars.getHittedRectW(h);
            hr[3] = Chars.getHittedRectH(h);
            colRect(t, er);
            int c = Um.collision(er[0], er[1], er[2], er[3], hr[0], hr[1], hr[2], hr[3], out);
            if (c != 0) {
                if (m[0x20] == 0xd) {
                    f0 = true;
                }
                if (equalMapIdx(t, Bytes.s16(G.map, 0x2d4))) {
                    if (m[0x20] == 0xf) {
                        MonBattle.attackHero(owner, m[0x4f], er[0] + (er[2] >> 1), er[1] + (er[3] >> 1));
                    } else {
                        MonBattle.attackHero(owner, m[0x4f], out[0] + (out[2] >> 1), out[1] + (out[3] >> 1));
                    }
                }
            }
        } else if (sr == 2 || sr == 3) {
            later("Monster_preProcessOneTargetEffect: bullet / land mine");
        }
        removeInList(l, t, f0, false, f2);
    }

    /** Monster_endProcessOneTargetEffect(list, t) @0x3a260 */
    static void monsterEndProcess(LList l, TEff t) {
        if (!endProcess(t)) {
            return;
        }
        int b = t.m[0x4c];
        if (b == 0) {
            removeInList(l, t, true, false, false);
        } else if (b == 1) {
            int k = t.m[0x20];
            if (k == 0x10) {
                removeInList(l, t, false, true, false);
            } else if (k == 0xe) {
                removeInList(l, t, false, true, true);
            } else {
                removeInList(l, t, true, false, false);
            }
        }
    }

    /** CHAR_preProcessTargetEffectList(c) @0x3ac38 */
    public static void preProcessList(Char c) {
        LList l = c.targetEffects;
        for (Obj p = l.head; p != null; ) {
            Obj nx = p.next;
            preProcessOne(l, (TEff) p);
            p = nx;
        }
    }

    /** CHAR_endProcessTargetEffectList(c) @0x3a4cc */
    public static void endProcessList(Char c) {
        LList l = c.targetEffects;
        for (Obj p = l.head; p != null; ) {
            Obj nx = p.next;
            endProcessOne(l, (TEff) p);
            p = nx;
        }
    }

    // ---- draw -------------------------------------------------------------------------------------------------------------------------------

    private static boolean heroAirDown() {
        byte[] h = Pool.hero.m;
        return h[0x16b] == 0 && h[0x20f] != 0 && h[0xd0c] == 1;
    }

    /**
     * TargetEffect_draw(t, ox, oy, pass) @0x56fa0 (pass 2 before the sorted objects, 1 in the sorted list, 0 after the particles): common effects
     * follow their owner; other kinds draw at their position plus the sprite's anchor (+0x29 set: without the anchor).
     */
    public static void draw(TEff t, int ox, int oy, int pass) {
        if (t == null || !equalMapIdx(t, Bytes.s16(G.map, 0x2d4))) {
            return;
        }
        byte[] m = t.m;
        if (m[0xb2] != pass || !canDrawing(t)) {
            return;
        }
        int k = m[0x20];
        if (k == 0x10 || k == 0xd) {
            later("TargetEffect_draw: bullet / lulker");
        }
        hl4.gfx.Frame fr = Gfx.scr;
        if (k == 9) {
            Obj ow = t.owner;
            int ty = Obj.structType(ow);
            if (ty == 2 || ty == 3) {
                Bytes.w32(m, 0xc, Bytes.s32(ow.m, 0xc));
                Bytes.w32(m, 8, Bytes.s32(ow.m, 8));
            }
            int x;
            int y;
            if (m[0x29] != 0) {
                x = Bytes.s32(m, 8) + ox;
                y = Bytes.s32(m, 0xc) + oy;
            } else {
                x = ox + m[0x2a] + Bytes.s32(m, 8);
                y = oy + m[0x2b] + Bytes.s32(m, 0xc);
            }
            if (heroAirDown()) {
                y -= 0x2d;
            }
            commonSpr.f6f = 0xff;                   // SPR_setBlend(commonSpr, 0, 0xff)
            commonSpr.f6e = 0;
            commonSpr.f68 = 0;                      // SPR_set_restore_pal
            m[0x22] = (byte) commonSpr.drawSprite(hl4.gfx.GfxOut.I, fr, m[0x21], m[0x28], m[0x22], x, y, 0, 0);
            return;
        }
        if (k == 6 || k == 7 || k == 0x17 || k == 0x18) {
            later("TargetEffect_draw: kind " + k);
        }
        int ax = t.sprChar != null ? Bytes.s32(t.sprChar.m, 0x20) : 0;
        int ay = t.sprChar != null ? Bytes.s32(t.sprChar.m, 0x24) : 0;
        if (t.sprChar == null && m[0x29] == 0) {
            later("TargetEffect_draw: anchor of a global sprite");
        }
        int x;
        int y;
        if (m[0x29] != 0) {
            x = Bytes.s32(m, 8) + ox;
            y = Bytes.s32(m, 0xc) + oy;
        } else {
            x = ox + ax + Bytes.s32(m, 8);
            y = oy + ay + Bytes.s32(m, 0xc);
        }
        if (m[0x4c] != 0) {
            Char ow = (Char) t.owner;
            MonProc.setBlend(ow, 0, 0xff);
            ow.spr.f68 = 0;
            if (m[0xb6] == 2) {
                later("TargetEffect_draw: SPR_drawSpriteNPC variant (+0xb6 == 2)");
            }
            if (heroAirDown()) {
                y -= 0x2d;
            }
            m[0x22] = (byte) t.spr.drawSprite(hl4.gfx.GfxOut.I, fr, m[0x21], m[0x28], m[0x22], x, y, 0, 0);
            return;
        }
        if (Pool.hero.m[0x16b] == 2) {
            later("TargetEffect_draw: SPR_drawSpriteSK (hero class 2)");
        }
        if (heroAirDown()) {
            y -= 0x2d;
        }
        m[0x22] = (byte) t.spr.drawSprite(hl4.gfx.GfxOut.I, fr, m[0x21], m[0x28], m[0x22], x, y, 0, 0);
    }

    /** the two passes of Map_drawMidddleObject over _targetEffectList */
    public static void drawList(int ox, int oy, int pass) {
        PList l = G.targetEffectList;
        for (int i = 0; i < 0x7f; i++) {             // PointerList_isEnd(i): i > 0x7e
            Obj o = l.a[i];
            if (o != null) {
                draw((TEff) o, ox, oy, pass);
            }
        }
    }
}
