package hl4.game;

import hl4.sys.Bytes;
import hl4.sys.Clock;
import hl4.sys.Store;

/** Game data initialisation at game start (MainGameData_*, SaveGameDataInit @0xce8e4, MainGameFrame_load @0x9a290). */
public final class GameLoad {
    private GameLoad() {
    }

    /** MainGameFrame_load(step) @0x9a290: only step 0 does work (the other 8 ticks of the load stage are no-ops) */
    public static void mainGameFrameLoad(int step) {
        if (step != 0) {
            return;
        }
        Char h = Pool.hero;
        Hero.init(h.m[0x16b], 0);
        Hero.loadBasicStat(h);
        Hero.setHeroStatus(1, 0);
        Bytes.w32(G.slotInfo, 0x28, 0);
        Bytes.w32(G.slotInfo, 0x2c, 0);
        Bytes.w32(G.slotInfo, 8, 0);
        Bytes.w32(G.slotInfo, 0xc, 0);
        Bytes.w32(G.slotInfo, 0x18, 0);
        Bytes.w32(G.slotInfo, 0x1c, 0);
        loadStory();
        Icons.loadSkillIcons();
    }

    /** the heavy block of LoadFrame_Process target 6 once the 9 load ticks are over */
    public static void loadHeroAndSummon() {
        HeroLoad.loadSpr();
        HeroLoad.loadData();
        HeroLoad.reLoad();
        if (Pool.hero.m[0x189] == 1) {
            throw new IllegalStateException("SUMMON_create/loadSummonData/Sprite/reLoadSummon: later");
        }
        Icons.skillIcon = hl4.gfx.Img.loadAll("/CM/_CM_SKICN_" + Pool.hero.m[0x16b], -1);
        Icons.effIcon = hl4.gfx.Img.loadAll("/CM/_CM_EFICN", -1);
        if (Hero.jobIdx() != 0) {
            Icons.guardianIcon = hl4.gfx.Img.loadAll("/CM/_CM_SKICN_5", -1);
        }
    }

    /** MainGameData_loadStory @0x9a0ec: a saved game of the slot, or the new-game state */
    public static void loadStory() {
        byte[] save = Store.load("./Hero4Game_" + G.gameMenu[0x16]);
        long now = Clock.now();
        Bytes.w32(G.game, 0x118, (int) now);
        Bytes.w32(G.game, 0x11c, (int) (now >> 32));
        if (save == null) {
            saveGameDataInit();
            Items.setItem(0, 0, 1);
            Items.setEquip(0, Items.itemSearch(0, 0));
            Items.setItem(1, 0, 1);
            Items.setEquip(1, Items.itemSearch(1, 0));
            Items.setItem(2, 0, 1);
            Items.setEquip(2, Items.itemSearch(2, 0));
            Items.setItem(3, 0, 1);
            Items.setEquip(3, Items.itemSearch(3, 0));
            int w = Tab.START_WEAPON[Pool.hero.m[0x16b]];
            Items.setItem(w, 0, 1);
            Items.setEquip(4, Items.itemSearch(w, 0));
            G.event[0x944] = 1;
            Event.setScreen(0, 1);
            Bytes.w32(G.event, 0x600, 0xff);
        } else {
            throw new IllegalStateException("LoadGameData (saved game): later");
        }
    }

    /** SaveGameDataInit @0xce8e4 */
    public static void saveGameDataInit() {
        Char h = Pool.hero;
        byte[] hm = h.m;
        Engine.mainFrame0 = 0;
        Bytes.w32(hm, 0xa28, 0);
        G.game[1] = 0;                                         // MainGameFrame_setGameMode(0)
        int slot = G.gameMenu[0x16];
        Bytes.w32(G.slotInfo, slot * 0x10 + 8, 0);
        Bytes.w32(G.slotInfo, slot * 0x10 + 0xc, 0);
        Bytes.w32(G.map, 0x2d4, 0);
        G.map[0x2d8] = 0;
        Bytes.w32(G.map, 0x268, 0);
        Bytes.w32(G.map, 0x26c, 0);
        Bytes.w32(hm, 0x180, 0);
        Bytes.w32(hm, 0x184, 0);
        Bytes.w16(hm, 0x216, 0);
        Bytes.w16(hm, 0xd0e, 0);
        for (int k = 0; k < 10; k++) {
            G.map[0x36a + k] = 0;
        }
        G.map[0x352] = (byte) 0xff;
        Quest.init();
        Quest.listInit();
        G.questMenu[0x210c] = 0;
        if (Hero.jobIdx() == 0) {
            G.armsSlotCnt = 3;
        } else {
            G.guardianSlotCnt = 2;
            throw new IllegalStateException("Guardian_loadBasicData: later");
        }
        Monsters.questBossDel();
        G.itemBagMax = 3;
        for (int i = 0; i < 7; i++) {
            G.equip[i] = null;
        }
        G.itemBagPtrs.release();
        G.itemBag.release();
        G.armsBag.release();
        Summon.initSkillPoint();
        Summon.init();
    }
}
