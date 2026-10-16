package hl4.game;

import hl4.sys.Bytes;

/**
 * The event/cut-scene system state and helpers (Event_*, EVENT_*, EVT_* @0x68xxx..0x6fxxx) over the _event byte struct (2388 bytes).  Known fields:
 * +8 event count of the scene, +0x1fe event running, +0x200/+0x204 (s32 -1 = none), +0x20c/+0x210 camera step / steps, +0x206 skip flag, +0x207 fade flag,
 * +0x5e4.. camera, +0x5ec/+0x5ee screen effect, +0x609 subtitle flag, +0x60f.. , +0x624/0x724/0x824 quest flag arrays, +0x93c.. misc.
 */
public final class Event {
    private Event() {
    }

    public static final byte[] e = G.event;

    /** offset of the 10-byte event record k in _event (Event_getInfo = _event + 10) */
    public static int rec(int k) {
        return 10 + 10 * k;
    }

    /** Event_isEvent @0x695f8: a cut scene is running (_event[0x1fe]) or the game is over (_game[0] == 2) */
    public static boolean isEvent() {
        return G.event[0x1fe] != 0 || G.game[0] == 2;
    }

    /** Event_Set_Screen(color, steps) @0x690e8: starts a screen colour fade; returns 1 when a skip is active (the fade ends at once) */
    public static int setScreen(int p1, int p2) {
        e[0x5ef] = e[0x5ee];
        int[] sc = Tab.SCREEN_COLOR;
        if (p1 == -1) {
            Bytes.w32(e, 0x600, (e[0x5ee] & 0xff) <= 3 ? 0xff : 100);
            int idx = e[0x5ee];
            if (idx != -1) {
                Bytes.w32(e, 0x5f4, sc[idx * 3 + 1]);
                Bytes.w32(e, 0x5f8, sc[idx * 3 + 2]);
                Bytes.w32(e, 0x5f0, sc[idx * 3]);
            }
        } else {
            Bytes.w32(e, 0x5f4, sc[p1 * 3 + 1]);
            Bytes.w32(e, 0x5f8, sc[p1 * 3 + 2]);
            Bytes.w32(e, 0x5f0, sc[p1 * 3]);
            Bytes.w32(e, 0x600, 0);
        }
        e[0x5ee] = (byte) p1;
        if (e[0x206] == 0) {
            Bytes.w32(e, 0x210, p2);
            if (p2 < 1) {
                Bytes.w32(e, 0x210, 1);
            }
            e[0x5ed] = (byte) p2;
            e[0x5ec] = 1;
            Bytes.w32(e, 0x20c, 0);
            return 0;
        }
        screenEnd();
        return 1;
    }

    /** Event_ScreenEnd @0x68ef8 */
    public static void screenEnd() {
        if (e[0x5ee] != (byte) 0xff) {
            int u = (e[0x5ee] - 4) & 0xff;
            if (u < 4) {
                e[0x5ec] = 1;
                Bytes.w32(e, 0x600, 100);
            } else {
                e[0x5ec] = 1;
                Bytes.w32(e, 0x600, 0xff);
            }
            return;
        }
        e[0x5ec] = 0;
        Bytes.w32(e, 0x600, 0);
    }

    private static int div(int a, int b) {
        return b == 0 ? 0 : a / b;
    }

    /** Event_Screen @0x68f54: one tick of the screen fade; true while it goes on */
    public static boolean screen() {
        int c = Bytes.s32(e, 0x600);
        int a5ee = e[0x5ee] & 0xff;
        int a5ef = e[0x5ef] & 0xff;
        int steps = e[0x5ed] & 0xff;
        boolean toEnd = false;
        if (a5ee == 0xff) {
            if (a5ef != 0xff) {
                int d;
                if (((a5ef - 4) & 0xff) < 4) {
                    d = steps == 0 ? 100 : div(100, steps);
                } else {
                    d = steps == 0 ? 0xff : div(0xff, steps);
                }
                c -= d;
            }
            if (c < 0) {
                c = 0;
            }
        } else {
            if (((a5ee - 4) & 0xff) < 4) {
                if (a5ef == 0xff) {
                    c += div(100, steps);
                }
            } else if (a5ef == 0xff) {
                c += div(0xff, steps);
            }
            if (c > 0xff) {
                c = 0xff;
                Bytes.w32(e, 0x600, c);
                Bytes.w32(e, 0x20c, Bytes.s32(e, 0x20c) + 1);
                if (Bytes.s32(e, 0x20c) < Bytes.s32(e, 0x210)) {
                    return true;
                }
                toEnd = true;
            }
        }
        Bytes.w32(e, 0x600, c);
        if (!toEnd) {
            Bytes.w32(e, 0x20c, Bytes.s32(e, 0x20c) + 1);
            if (Bytes.s32(e, 0x20c) < Bytes.s32(e, 0x210)) {
                return true;
            }
        }
        screenEnd();
        return false;
    }

    /** Event_Delay(p1) @0x68e88 */
    public static boolean delay(boolean frames) {
        if (frames) {
            if (e[0x943] <= 0) {
                return false;
            }
            e[0x943]--;
            return true;
        }
        if (Bytes.s32(e, 0x210) <= Bytes.s32(e, 0x20c)) {
            return false;
        }
        Bytes.w32(e, 0x20c, Bytes.s32(e, 0x20c) + 1);
        return true;
    }

    /** EVENT_TickMovie @0x6cc8c: the running command's per-tick work; true = the command is still running */
    public static boolean tickMovie() {
        switch (Bytes.s32(e, 0x200)) {
            case 2:
            case 10:
            case 0xd:
            case 0xe:
                Maps.initDrawFlag();
                break;
            case 8:
                return Npcs.anyEventMove() || e[0x5eb] != 0;
            case 0x24:
                return screen();
            case 0x2b:
            case 0x2c:
                return Cam.cameraMove(true);
            case 0x2d:
                return delay(true);
            case 0x2e:
            case 0x30: {
                if (e[0x206] != 0) {
                    e[e[0x205] * 0xbc + 0x23f] = 1;
                }
                int k = e[0x50d] & 0xff;
                if (e[k * 0xbc + 0x23f] != 0) {
                    Bytes.w32(e, 0x200, -1);
                    if (e[0x5dc] != 0) {
                        e[0x5dc] = 0;                                   // Event_Init_Narration
                        Dialog.scriptWindowClose(e[0x50d] & 0xff);
                        k = e[0x50d] & 0xff;
                    }
                }
                if (Dialog.text[k] != null) {
                    Dialog.talkBoxTick(k);
                    return true;
                }
                Bytes.w32(e, 0x200, -1);
                return false;
            }
            case 0x2f:
                return Dialog.scriptWindowClose(e[0x50a] & 0xff) != 0;
            case 0x3c:
                Dialog.scriptWindowOpen(e[0x505] & 0xff);
                return true;
            case 0x32:
                throw new IllegalStateException("Event_Popup (EVENT_TickMovie cmd 50): later");
            default:
                break;
        }
        Bytes.w32(e, 0x200, -1);
        return false;
    }

    private static void resetScriptSlots() {
        for (int k = 0; k < 4; k++) {
            initScriptData(k);
        }
        e[0x504] = (byte) 0xff;
        Bytes.fill(e, 0x510, 0xff, 4);
        Bytes.fill(e, 0x505, 0, 5);
        e[0x505] = (byte) 0xff;
        Bytes.fill(e, 0x50a, 0, 3);
        Bytes.fill(e, 0x50d, 0, 3);
    }

    /** EVENT_Tick @0x6d4c0: advances the running cut scene; true while it is running */
    public static boolean tick() {
        if (e[0x206] != 0) {
            if (e[0x209] - 1 == e[0x208]) {
                resetScriptSlots();
            }
            if (e[0x208] != e[0x209]) {
                return true;
            }
        }
        if (tickMovie()) {
            return true;
        }
        if (e[0x950] != 0) {
            throw new IllegalStateException("EVENT_worlmapTick: later");
        }
        if (It.tick()) {
            return true;
        }
        if (e[0x206] != 0 && G.map[0x6a] != G.map[0x69] && G.map[0x69] != -1) {
            Sound.freeBgm();
            Sound.loadBgm(G.map[0x69]);
            G.map[0x6b] = 1;
        }
        return end();
    }

    /** EVENT_CheckEvent(idx) @0x5d63c */
    public static boolean checkEvent(int idx) {
        if (idx < 0 || !It.checkEvent(idx)) {
            return false;
        }
        Obj h = Pool.hero;
        HeroCtl.moveStop(h);
        h.m[0x166] = 0;
        Bytes.w32(h.m, 0x158, 0);
        Bytes.w32(h.m, 0x15c, 0);
        h.m[0x167] = 0;
        return tick();
    }

    /** EVENT_onLoad @0x5d6b0 */
    public static boolean onLoad() {
        e[0x948] = 5;
        return checkEvent(0);
    }

    public static void setQuestStatus(int step, int status) {
        throw new IllegalStateException("Event_Set_QuestStatus: later");
    }


    /** Event_initInfo(k) @0x68824: 10-byte records at +0xa (the scene's script object table) */
    public static void initInfo(int k) {
        int b = k * 10;
        e[b + 0x11] = 7;
        e[b + 0x13] = 0;
        Bytes.w16(e, b + 0xc, 0xffff);
        e[b + 0x12] = (byte) 0xff;
        Bytes.w16(e, b + 10, 0xffff);
        e[b + 0xe] = 0;
        e[b + 0xf] = (byte) 0xff;
        e[b + 0x10] = 0;
    }

    /** Event_Init_HeromoveValue @0x68c78 */
    public static void initHeromoveValue() {
        Bytes.w32(e, 0x5e4, -1);
        e[0x5eb] = 0;
        e[0x5e8] = (byte) 0xff;
        Bytes.w32(e, 0x5e0, -1);
    }

    public static void initSkip() {
        e[0x206] = 0;
    }

    /** Event_Init_ScriptData(k) @0x689c4: 0xbc-byte script slots at +0x214.. */
    public static void initScriptData(int k) {
        int p = k * 0xbc;
        Bytes.w32(e, p + 0x228, 0);
        e[p + 0x22e] = (byte) 0xff;
        e[p + 0x230] = 0;
        e[p + 0x240] = 0;
        e[p + 0x214] = 0;
        Bytes.w32(e, p + 0x234, 0);
        Bytes.w32(e, p + 0x238, 0);
        e[p + 0x22c] = 0;
        e[p + 0x22d] = 0;
        e[p + 0x215] = 0;
        e[p + 0x23c] = 0;
        e[p + 0x23d] = 0;
        e[p + 0x23e] = 0;
        e[p + 0x23f] = 0;
        Bytes.w32(e, p + 0x244, 0);
        Bytes.w32(e, p + 0x248, 0);
        Bytes.w32(e, p + 0x24c, 0);
        Bytes.w32(e, p + 0x250, 0);
        Bytes.w32(e, p + 0x254, 0);
        Bytes.w32(e, p + 0x258, 0);
        Bytes.w32(e, p + 0x218, 0);
        Bytes.w32(e, p + 0x220, 0);
        Bytes.w32(e, p + 0x224, 0);
        Dialog.clear(k);
    }

    /** Event_Release_Faceimage(k) @0x6ba68: frees the face sprite/image of script slot k */
    public static void releaseFaceimage(int k) {
        e[k * 0xbc + 0x22e] = (byte) 0xff;
        Faces.release(k);
    }

    public static void subtitleOneDraw(int v) {
        e[0x609] = (byte) v;
    }

    public static void setCameraType(int v) {
        Bytes.w16(e, 0x93a, v);
    }

    public static int getCameraType() {
        return Bytes.s16(e, 0x93a);
    }

    /** EVT_Initialize @0x6ef7c */
    public static void evtInitialize() {
        for (int k = 0; k < 0x32; k++) {
            initInfo(k);
        }
        Popup.end(Popup.evtBox);
        Bytes.w16(e, 0x93a, 0xff);
        Bytes.w16(e, 0x93c, 0xff);
        e[0x610] = 0;
        e[0x60f] = 0;
        e[0x611] = 0;
        Bytes.w32(e, 0x20c, 0);
        Bytes.w32(e, 0x210, 0);
        e[0x943] = 0;
        e[0x205] = (byte) 0xff;
        initHeromoveValue();
        e[0x940] = 0;
        e[0x941] = 0;
        if (e[0x944] == 0) {
            e[0x5ec] = 0;
            e[0x5ee] = (byte) 0xff;
            e[0x5ef] = (byte) 0xff;
        }
        e[0x942] = (byte) 0xff;
        initSkip();
        e[0x207] = 0;
        e[0x504] = (byte) 0xff;
        Bytes.fill(e, 0x510, 0xff, 4);
        e[0x945] = 0;
        end();
        e[0x505] = (byte) 0xff;
    }

    /** EVENT_End @0x6d388: resets the script state and snaps the camera onto the hero; true while the camera is still moving */
    public static boolean end() {
        MainGameFrame.initMonsterInfo(null, 0, 0);
        G.map[0x369] = (byte) 0xff;
        G.map[0x374] = 0;
        Bytes.w32(e, 0x94c, -1);
        Bytes.w32(e, 0x200, -1);
        e[0x950] = 0;
        e[0x1fe] = 0;
        Obj h = Pool.hero;
        Bytes.w32(h.m, 0xd18, -1);
        Bytes.w32(h.m, 0xd1c, -1);
        e[0x1ff] = 0;
        initSkip();
        Bytes.w16(e, 0x93a, 0xff);
        e[0x60e] = 0;
        e[0x943] = 0;
        e[0x60d] = 0;
        e[0x608] = 0;
        for (int k = 0; k < 4; k++) {
            initScriptData(k);
            releaseFaceimage(k);
        }
        Bytes.fill(e, 0x510, 0xff, 4);
        Bytes.fill(e, 0x505, 0, 5);
        e[0x505] = (byte) 0xff;
        Bytes.fill(e, 0x50a, 0, 3);
        Bytes.fill(e, 0x50d, 0, 3);
        h.m[0xa31] = 0;
        Cam.setCameraMove(h.x(), h.y(), -1, -1);
        Cam.mapMove();
        return Cam.cameraMove(true);
    }

    /**
     * Event_moveNextScene(p1 scene, p2 tile x, p3 tile y, p4 weather/time, p5) @0x5c724: requests a scene change (_event+0x60f).  p1 0xff = stay, 0xfe = back to the
     * previous scene and position, 0xfd = story save + load frame (not ported).
     */
    public static void moveNextScene(int p1, int p2, int p3, int p4, int p5) {
        byte[] m = G.map;
        if (p1 < 0) {
            p1 = (short) (p1 + 0x100);
        }
        e[0x60f] = 1;
        int w = p4;
        if (p1 != 0xff) {
            if (p1 == 0xfe) {
                Bytes.w32(m, 0x26c, Bytes.s32(m, 0x2cc));
                Bytes.w32(m, 0x268, Bytes.s32(m, 0x2c8));
                Bytes.w32(m, 0x2d4, Bytes.s32(m, 0x2d0));
                w = 1;
            } else if (p1 == 0xfd) {
                throw new IllegalStateException("Event_moveNextScene 0xfd (MainGameData_saveStory): later");
            } else {
                Bytes.w32(m, 0x2c8, Pool.hero.x());
                Bytes.w32(m, 0x2cc, Pool.hero.y());
                Bytes.w32(m, 0x2d0, Bytes.s32(m, 0x2d4));
                Bytes.w32(m, 0x2d4, p1);
                Bytes.w32(m, 0x26c, p3 * 0x10 + 8);                     // Map_Set_Hero_Tile_Position
                Bytes.w32(m, 0x268, p2 * 0x10 + 8);
                Particles.curCount = 0;                                  // destroyAllParticles
            }
        }
        m[0x2d8] = (byte) w;
        e[0x609] = 0;
        m[0x2d9] = (byte) p5;
        byte[] h = Pool.hero.m;
        if (h[0x1ed] != 0) {
            Bytes.w32(h, 8, p2 * 0x10 + 8);
            Bytes.w32(h, 0xc, p3 * 0x10 + 8);
        }
    }

    /** Event_nextScene @0x689a4: a scene change is pending (_event+0x60f) */
    public static int nextScene() {
        return e[0x60f];
    }

    /** Event_HeroStop @0x699c4 */
    public static void heroStop() {
        Char h = Pool.hero;
        HeroCtl.moveStop(h);
        h.m[0x166] = 0;
        Bytes.w32(h.m, 0x158, 0);
        Bytes.w32(h.m, 0x15c, 0);
        h.m[0x167] = 0;
    }

    /** Event_Next_Scene @0x6a148: starts the load frame of a requested scene change */
    public static void nextSceneStep() {
        if (e[0x60f] != 0) {
            LoadFrame.setState(8, 4);
            heroStop();
            e[0x60f] = 0;
            return;
        }
        if (e[0x610] != 0) {
            throw new IllegalStateException("Event_Next_Scene (_event+0x610: MainGameFrame_Finalize + load target 4): later");
        }
    }

    /**
     * Event_Key(key) @0x59b1c (real address; Ghidra 0x69b1c): keys while a cut scene runs.  Mapped codes: 0x3b35..0x3b37 and 0x3b07/0x3b08 = OK (-5),
     * 0x3b6b = '#'.  '#' starts the skip fade; OK on a dialog (state 0x2e / 0x30) completes the page being typed, shows the next page or finishes the line.
     */
    public static void key(int key) {
        if (key != 0x3cd1 && key > 14999) {
            if (key <= 0x3b37) {
                if (key >= 0x3b35 || key - 0x3b07 == 0 || key - 0x3b07 == 1) {
                    key = -5;
                }
            } else if (key == 0x3b6b) {
                key = 0x23;
            }
        }
        if (e[0x206] != 0) {
            return;
        }
        if (Popup.evtBox[0x2c] != 0) {
            throw new IllegalStateException("PopopBox_key (open box): later");
        }
        int st = Bytes.s32(e, 0x200);
        if (key == 0x23) {
            if (st == 0x32) {
                throw new IllegalStateException("Event_Key popup branch (state 0x32): later");
            }
            if (st == 0x3c || st == 0x2f) {
                return;
            }
            e[0x207] = 1;
            e[0x208] = 0;
            e[0x206] = 1;
            e[0x209] = 5;
            if (e[0x5ef] == 0 || e[0x5ee] == 0) {
                e[0x208] = 5;
            }
            e[0x5ef] = (byte) 0xff;
            e[0x5ec] = 0;
            e[0x608] = 0;
        }
        if (st == 0x32) {
            throw new IllegalStateException("Event_Key popup branch (state 0x32): later");
        }
        if ((st == 0x30 || st == 0x2e) && (key == -5 || key == 0x35)) {
            int k = e[0x205];
            int p = k * 0xbc;
            int cur = Bytes.s32(e, p + 0x24c);
            if (cur < Bytes.s32(e, p + 0x248)) {
                while (Dialog.talkBoxTick(k) != 0) {
                    k = e[0x205];
                }
            } else if (cur < Bytes.s32(e, p + 0x250)) {
                if (e[p + 0x240] == 0) {
                    Bytes.w32(e, p + 0x244, Bytes.s32(e, p + 0x248));
                    e[p + 0x240] = 1;
                }
            } else {
                e[p + 0x23f] = 1;
            }
        }
    }

    /** EVENT_EndProcess @0x6d308 */
    public static void endProcess() {
        nextSceneStep();
        if (e[0x611] != 0) {
            throw new IllegalStateException("Event_ToWorldmap: later");
        }
        if (e[0x206] == 0) {
            e[0x207] = 0;
        }
        if (Popup.evtBox[0x2c] != 0) {
            throw new IllegalStateException("EVENT_EndProcess: evtBox focus (Focus_endProcess / IT_AlertConfirm): later");
        }
    }
}
