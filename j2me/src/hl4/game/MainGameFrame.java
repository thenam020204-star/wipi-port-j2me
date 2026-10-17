package hl4.game;

import hl4.sys.Bytes;

/** The in-game frame (MainGameFrame_* @0x9a290..0x9d6a8): per tick Process / Render / EndProcess over _game (296 bytes). */
public final class MainGameFrame implements FrameHandler {
    public static final MainGameFrame I = new MainGameFrame();

    private MainGameFrame() {
    }

    /** _game+8: the monster shown in the info bar */
    public static Obj infoMonster;

    /** MainGameFrame_initMonsterInfo(monster, hp, p3) @0x993bc */
    public static void initMonsterInfo(Obj mon, int p2, int p3) {
        byte[] g = G.game;
        int f;
        if (p3 == 0) {
            f = 0;
        } else {
            if (infoMonster != null) {
                return;
            }
            f = 0;
        }
        boolean z = mon == null;
        if (z || p2 == 0 || p2 < 0) {
            f = 0;
        } else {
            f = 1;
        }
        Bytes.w32(g, 0xc, 0);
        infoMonster = mon;
        g[5] = (byte) f;
        g[6] = g[5];
        if (f != 0) {
            g[5] = (byte) p2;
        }
    }

    /** MainGameFrame_Initialize @0x9d6a8 */
    public static void initialize() {
        byte[] g = G.game;
        g[0x120] = 0;
        g[0] = 0;
        g[0x54] = 0;
        Maps.linkedListInitAll();
        g[2] = 0;
        Gauges.initList();
        Lenz.make(5);
        g[0x55] = 0;
        G.map[0x2d9] = (byte) 0xff;
        Maps.variableInit();
        G.map[0x375] = 1;
    }

    /** MainGameFrame_MapChangeInitialize @0x9d5f0 */
    public static void mapChangeInitialize() {
        byte[] g = G.game;
        g[0] = 0;
        g[0x46] = 0;
        if (G.map[0x79] != 1 && g[2] == 2) {
            g[0x5c] = 0;
            throw new IllegalStateException("MainGameFrame_endMapCalling: later");
        }
        Gfx.fillRectRGB(Gfx.back, 0, 0, Glob.gameW, Glob.gameH, 0xd, 0xd, 0xf);
        Maps.initDrawFlag();
    }

    /** MainGameFrame_GameOver @0x9d4dc: the hero's HP reached 0 */
    static void gameOver() {
        byte[] g = G.game;
        if (g[0] == 0) {
            g[0] = 2;
            g[0x56] = 0;
            HeroCtl.setMotion(Pool.hero, 5, 0);                 // CHAR_heroMotionDie
            Pool.hero.m[0x166] = 4;
            MapFx.setScreenEffect(4, -0x32, 0x46);
            g[0x5b] = 0;
        }
    }

    /** _txtGMenu @0x14db0c: /GMenu/_GMENU_TXT (LinkedText), loaded for the game-over popup */
    static byte[][] txtGMenu;

    /** MainGameFrame_initGameOver(p1) @0x9d254: p1 = 1 opens the popup (choices: revive (0) if a revive item (8, 0x10) is held, else the shop (1); 1; 2) */
    static void initGameOver(int p1) {
        byte[] g = G.game;
        if (p1 == 1) {
            g[0x5a] = 0;
            g[0x57] = g[0x58] = g[0x59] = -1;
            g[0x5b] = 0;
            txtGMenu = hl4.data.Text.linkedAll(hl4.sys.Res.load("/GMenu/_GMENU_TXT"));
            int n = g[0x5a];
            if (Items.getItemCnt(8, 0x10) > 0) {
                g[0x57 + n] = 0;
            } else {
                g[0x57 + n] = (byte) p1;
                G.gameMenu[8] = (byte) p1;
            }
            n = (byte) (n + 1);
            g[0x57 + n] = 1;
            g[0x57 + (byte) (n + 1)] = 2;
            g[0x5a] = (byte) (n + 2);
            Popup.setBox(Popup.box, 4, 0x5e, g[0x5a] == 2 ? 0x31 : 0x44, g[0x5a]);
        }
        g[0x56] = (byte) p1;
    }

    /** MainGameFrame_drawGameOver @0x9c1d4 (PopupBox_window(9, ..) only computes a height: nothing is drawn but the caption) */
    private static void drawGameOver() {
        Popup.window(9, 0, Glob.gameW / 2 - 0x48, (Glob.gameH - 0x94) >> 1, 0x90, 0x94, 0);       // the result is unused
        hl4.gfx.Hf.setColor(0xff, 0xff, 0xff);
        hl4.gfx.Hf.drawText(Gfx.scr, Glob.gameW / 2 - 0x1e, Glob.gameH / 2 + 10 - (G.game[0x5a] << 3), Strs.GAME_OVER, 0);
    }

    /** MainGameFrame_Process @0x9c978 (= GamePlayProcess): the cut scene tick, every object's pre-process, screen shake, camera and the play-time limit */
    public void process() {
        byte[] g = G.game;
        byte[] e = G.event;
        if (g[0] != 0) {
            return;
        }
        if (e[0x1fe] != 0) {
            Event.tick();
        }
        if (e[0x60f] == 0) {
            Maps.objectPreProcess();
            Cam.shake();
            if (e[0x1fe] == 0) {
                Cam.mapMove();
            }
            if (e[0x609] != 0 && e[0x1fe] == 0) {
                Event.setSubTitle(G.map[0x288], 0, 0x10);
                e[0x609] = 0;
                Scene.getName();
            }
            int c = Bytes.s16(G.map, 0x356);
            if (c <= 0) {
                Pool.hero.m[0x170] = 0;
            } else {
                Bytes.w16(G.map, 0x356, c - 1);
            }
            byte[] h = Pool.hero.m;
            int hi = Bytes.s32(h, 0x184);
            if (hi > 0 || (hi == 0 && Bytes.s32(h, 0x180) != 0)) {
                throw new IllegalStateException("play-time limit check (MC_knlCurrentTime): later");
            }
        }
    }

    /** test hook: skip the (not yet ported) render step while state parity of Process/EndProcess is being checked */
    public static boolean testNoRender;

    /** MainGameFrame_Render @0x9c694 */
    public void render() {
        if (testNoRender) {
            return;
        }
        byte[] g = G.game;
        if (g[0] == 0) {
            drawGame();
        } else if (g[0] == 3) {
            throw new IllegalStateException("MainGameFrame_Render game menu popup (state 3): later");
        } else if (g[0] == 2) {
            drawGame();
            if (g[0x56] != 1) {
                return;
            }
            if (g[0x57] == 0) {
                Popup.drawYesNo(txtGMenu[0x66], G.gameMenu[8], 0xb0, 1);
                g[0x5b] = 0xb;
            } else if (g[0x5b] <= 9) {
                drawGameOver();
            } else {
                hl4.gfx.Hf.semicolon = 1;                       // TF_semiSet(1)
                Popup.drawYesNo(txtGMenu[0x6e], G.gameMenu[8], 0xc4, 1);
                hl4.gfx.Hf.semicolon = 0;
                g[0x5b] = 0xb;
            }
        }
    }

    /** MainGameFrame_Render / MainGameFrame_drawGame @0x9c2ec (the in-game picture, game state 0) */
    public static void drawGame() {
        byte[] g = G.game;
        if (g[0] != 0 && g[0] != 2) {
            throw new IllegalStateException("MainGameFrame_Render for game state != 0 / 2: later");
        }
        byte[] m = G.map;
        MapDraw.drawTile(Gfx.back);
        if (!G.tileList.isEmpty()) {
            throw new IllegalStateException("Map_drawTileList / Tile_draw: later");
        }
        m[0x94] = 0;
        MapObjDraw.drawBottom(Gfx.back);
        MapDraw.copyToScreen();
        Gfx.setClip(0, 0, Glob.gameW, Glob.gameH);
        DropItems.drawItems();
        ObjDraw.drawBottomMonster();
        MapFx.extraScreenEffect();
        ObjDraw.drawMiddle();
        MapFx.fallRandDraw(Gfx.scr);
        MapFx.screenEffect();
        Hud.drawBottomBar();
        drawMonsterInfo();
        DropItems.drawInfos();
        if (g[0] == 0) {
            EventDraw.draw(Gfx.scr);
            EventDraw.subTitle(Gfx.scr);
            EventDraw.skipFade(Gfx.scr);
        }
    }

    private static final byte[] LV = {'L', 'V', 0};
    private static final byte[] LVSTR = new byte[8];
    private static final byte[] NAME = new byte[32];

    /** MainGameFrame_drawMonsterInfo @0x8aca4: the monster bar slides in (+0x46 0..5) while the show counter (+5) runs and slides out afterwards */
    static void drawMonsterInfo() {
        byte[] g = G.game;
        if (g[6] == 0 || infoMonster == null || Event.isEvent()) {
            return;
        }
        drawInfo();
        g[5]--;
        if (g[5] == -1) {
            initMonsterInfo(null, 0, 0);
        }
        if (g[5] > 0) {
            if (g[0x46] <= 4) {
                g[0x46]++;
            }
        } else if (g[0x46] > 0) {
            g[0x46]--;
        }
    }

    /**
     * MainGameFrame_drawInfo(1, 0) @0x8a8d4 (monster variant, width 100): rank marks (_uiGame 7, m[0x33c] / 2), the frame, the HP fill, the dimmed
     * body, "name" and "LV nn".
     */
    private static void drawInfo() {
        byte[] g = G.game;
        byte[] m = infoMonster.m;
        hl4.gfx.Frame f = Gfx.scr;
        int w = 0x64;
        int y = g[0x46] * 3 - 0xf;
        int x = Glob.gameW - 0x65;
        int n = (m[0x33c] + ((m[0x33c] >> 31) & 1)) >> 1;
        if (n > 0) {
            hl4.gfx.Gbm ic = hl4.gfx.Img.get(Icons.uiGame, 7);
            int xx = x + 0x1d - n * 2;
            for (int i = 0; i < n; i++, xx += 4) {
                hl4.gfx.Img.draw(f, xx, y, ic, 9);
            }
        }
        hl4.gfx.Fx.fillRectBlend(f, x, y, w, 4, 0, 0xff, 0x0b0b0b);
        int ty = y + 1;
        hl4.gfx.Fx.fillRectBlend(f, x + 1, ty, w - 2, 2, 0, 0xff, 0x535b2b);
        int mx = Bytes.s32(m, 0xa8);
        if (mx != 0) {
            int cur = Monsters.getCurHp(infoMonster);
            hl4.gfx.Fx.fillRectBlend(f, x + 1, ty, (w - 2) * cur / mx, 2, 0, 0xff, 0xff1c24);
            y += 4;
            ty = y + 1;
        }
        hl4.gfx.Fx.fillRectBlend(f, x + 1, y, w - 2, 0xd, 0, 0x80, 0x0b0b0b);
        hl4.gfx.Hf.setColor(0x96, 0x96, 0x96);
        int q = 0;
        while (q < 31 && m[0x95 + q] != 0) {
            NAME[q] = m[0x95 + q];
            q++;
        }
        NAME[q] = 0;
        hl4.gfx.Hf.drawText(f, x + 0x19, ty, NAME, 0);
        int lv = m[0x94];
        if (lv < 0) {
            return;
        }
        int k = 0;
        if (lv <= 9) {
            LVSTR[k++] = '0';
        }
        if (lv > 9) {
            LVSTR[k++] = (byte) ('0' + lv / 10);
        }
        LVSTR[k++] = (byte) ('0' + lv % 10);
        LVSTR[k] = 0;
        hl4.gfx.Hf.drawText(f, x + 3, y + 6, LV, 1);
        hl4.gfx.Hf.setColor(0xff, 0xff, 0xff);
        hl4.gfx.Hf.drawText(f, x + 0xd, y + 6, LVSTR, 1);
    }

    /** MainGameFrame_EndProcess @0x9d3a0 */
    public void endProcess() {
        byte[] g = G.game;
        if (g[0] == 0 || g[0] == 2) {
            g[0x55]++;
            if (g[0x55] > 10) {
                g[0x55] = 0;
            }
            Maps.objectEndProcess();
            PickUp.endProcess();
            DropItems.endProcess();
            Event.endProcess();
            if (g[0] == 2) {
                if (g[0x56] == 0) {
                    g[0x5b]++;
                    if (g[0x5b] > 10) {
                        g[0x5b] = 10;
                    }
                    if (MapFx.screenEffectCheck(4)) {
                        initGameOver(1);
                    }
                } else if (g[0x56] == 1 && g[0x57] == 1 && g[0x5b] <= 9) {
                    g[0x5b]++;
                }
            }
        } else if (g[0] == 3 && Engine.changeFrame != 0) {
            throw new IllegalStateException("MainGameFrame_EndProcess game menu leave: later");
        }
        if (Engine.changeFrame != 0) {
            throw new IllegalStateException("MainGameFrame_changeFrame -> GameMenuFrame_returnMainMenu (teardown, LoadFrame_setState(5, 3)) / game menu: later");
        }
    }

    /** MainGameFrame_KeyPressed @0x8cc20 -> GamePlayKeyPressed @0x8ca44 (game state 0); a running cut scene takes every key (Event_Key) */
    public void keyPressed(int key) {
        if (G.game[0] == 2) {
            gameOverKeyPressed(key);
            return;
        }
        if (G.game[0] != 0) {
            throw new IllegalStateException("MainGameFrame_KeyPressed for game state != 0 / 2: later");
        }
        if (G.event[0x1fe] != 0) {
            Event.key(key);
            return;
        }
        Char hero = Pool.hero;
        if (key == -5 || key == 0x35) {
            Coll.heroMoveRect();
            if (PickUp.bagPickUp()) {
                HeroCtl.moveStop(hero);
                hero.m[0x166] = 0;
                hero.m[0x20d] = 0;
                hero.m[0x169] = -1;
                return;
            }
            if (G.event[0x1fe] == 0) {
                Event.checkOKKey();
            }
        }
        if (Event.isEvent()) {
            return;
        }
        HeroCtl.keyPress(hero, key);
        if (key == -8) {
            if (Bytes.s32(hero.m, 0xa4) > 0) {
                throw new IllegalStateException("GameMenu open (soft key): later");
            }
        } else if (key < -8) {
            if (key == -16 && Bytes.s32(hero.m, 0xa4) > 0) {
                throw new IllegalStateException("exit to menu (soft key 2): later");
            }
        } else if (key == -7) {
            if (G.map[0x375] != 0) {
                throw new IllegalStateException("NpcUi_Initialize(0, 0xb): later");
            }
        } else if (key == 0x2a) {
            G.game[3] = (byte) (G.game[3] == 0 ? 1 : 0);
        }
    }

    /** MainGameFrame_GameOverKeyPressed @0x9a694: keys count only while the popup is up; left / right / 4 / 6 toggle, OK / 5 confirms once it has been drawn */
    private static void gameOverKeyPressed(int key) {
        byte[] g = G.game;
        byte[] gm = G.gameMenu;
        if (g[0x56] != 1) {
            return;
        }
        if (key > 0x3a97 && key != 0x3cd1) {
            throw new IllegalStateException("Touch_keyCommonPopup (pointer area codes): not generated by the port");
        }
        if (key == -3 || key == -4 || key == 0x34 || key == 0x36) {
            int v = gm[8] + 1;
            gm[8] = (byte) (v % 2);
        } else if (key == -5 || key == 0x35) {
            if (g[0x5b] <= 10) {
                return;
            }
            if (gm[8] != 0) {
                Engine.requestChange(1);
            } else if (g[0x57] == 0) {
                throw new IllegalStateException("MainGameFrame_revive: later");
            } else if (g[0x57] == 1) {
                throw new IllegalStateException("NpcUi_Initialize(6, 10) (revive item shop): later");
            }
            Popup.end(Popup.box);
            if (gm[8] != 0) {
                Touch.setFrame(0x3a);
            }
        }
    }

    /** MainGameFrame_KeyReleased @0x8a3b0 -> HERO_Key_Release: nothing happens while an event runs (HERO_Key_Release returns on Event_isEvent) */
    public void keyReleased(int key) {
        if (G.game[0] != 0) {
            return;
        }
        HeroCtl.keyRelease(Pool.hero, key);
    }

    public void hideNotify() {
    }

    public void showNotify() {
    }
}
