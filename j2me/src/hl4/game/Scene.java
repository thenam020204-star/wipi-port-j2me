package hl4.game;

import hl4.data.Scn;
import hl4.sys.Bytes;
import hl4.sys.Res;

/** Scene change state machine (SCENE_Change @0x6f0bc, SCENE_End @0x6dbd8): the load of a scene is spread over LoadFrame ticks (step = _loadCnt). */
public final class Scene {
    private static final byte[] m = G.map;
    private static final byte[] e = G.event;

    private Scene() {
    }

    private static String n4(int v) {
        String s = String.valueOf(v);
        while (s.length() < 4) {
            s = "0" + s;
        }
        return s;
    }

    /** Map_Get_SceneBattleType: battle scene (1 or 2) */
    public static boolean battleType() {
        return (m[0x79] - 1 & 0xff) < 2;
    }

    /** SCENE_Change(step) */
    public static void change(int step) {
        step = (byte) step;
        Char hero = Pool.hero;
        if (step == 1) {
            Event.evtInitialize();
        } else if (step == 2) {
            int cls = hero.m[0x16b];
            String path = ((cls + 1) & 0xff) < 3 ? "/MAP/SC/e" : "/MAP/SC/n";
            byte[] f = Res.loadDes(path + n4(Bytes.s32(m, 0x2d4)) + "_scn");
            if (f != null) {
                Bytes.w16(m, 0x44, f[3] & 0xff);
                m[0x288] = f[4];
                m[0x79] = f[5];
                m[0xdc] = 1;
                Bytes.w16(m, 0x48, f[6] & 0xff);
                Bytes.w16(m, 0x4a, f[7] & 0xff);
                Bytes.w16(m, 0x4c, f[8] & 0xff);
                int bgm = f[9];
                e[0x945] = 0;
                if (bgm != m[0x69] && bgm >= 0) {
                    e[0x945] = 1;
                }
                m[0x6a] = m[0x69];
                m[0x69] = (byte) bgm;
                m[0x37d] = m[0x68];
                hl4.gfx.Sprite.lcdEff[10] = f[10] & 0xff;
                m[0x68] = (byte) hl4.gfx.Sprite.lcdEff[10];
                MapFx.setFGI(f[11]);
                e[8] = f[12];
                Maps.initialize();
                It.initialize(f, 0xc, f[0] & 0xff, f[1] & 0xff, f[2] & 0xff);
            }
        } else if (step == 3) {
            if (e[0x945] == 0) {
                m[0x6b] = 0;
            } else {
                Sound.freeBgm();
                Sound.loadBgm(m[0x69]);
            }
            if ((m[0x69] & 0x80) != 0) {
                Sound.freeBgm();
            }
        } else if (step == 4) {
            if (m[0x37d] != m[0x68]) {
                HeroLoad.reLoad();
            }
        } else if (step == 5) {
            if (!battleType()) {
                hero.m[0xa41] = 0;
                if (hero.m[0x1ed] != 0) {
                    Chars.setVisible(Pool.summon, 0);
                    Chars.resetPosition(Pool.summon, -50, -50);
                    hero.m[0x1ed] = 0;
                }
            } else if (G.equip[4] == null) {
                hero.m[0xa41] = 2;
            } else {
                hero.m[0xa41] = 1;
            }
        } else if (step == 6) {
            HeroCtl.setStandMotion(hero, 0, 0, hero.m[0x168]);
            if (hero.m[0x1ed] != 0) {
                throw new IllegalStateException("SCENE_Change 6: summon placement (SUMMON_summonSetPosNearHero): later");
            }
            Effects.setEffAfterMapMove(hero);
        }
    }

    /** Scene_getName @0x6a43c: copies the scene title string (IT string +_map[0x288]) into _map+0x289 (27 bytes, the global at 0x111641) */
    public static void getName() {
        byte[] t = It.getString(m[0x288]);
        for (int i = 0; i < 0x1b; i++) {
            m[0x289 + i] = 0;
        }
        if (t != null) {
            Bytes.copy(t, 0, m, 0x289, t.length);
        }
    }

    /** SCENE_End @0x6dbd8: leaves the current scene */
    public static int end() {
        if (Event.isEvent()) {
            Dialog.scriptWindowClose(Event.e[0x50d]);
        }
        It.cur = null;
        Maps.finalizeMap();
        It.release();
        return Event.end() ? 1 : 0;
    }
}
