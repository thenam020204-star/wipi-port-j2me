package hl4.game;

/** Entry into the game from the main menu's story mode: the state the slot / force / class screens leave behind, then the game load (MainMenuFrame_initMenuState(1)). */
public final class GameStart {
    private GameStart() {
    }

    /** New game in slot 0 with the given hero class (the story-mode screens themselves are ported with the menus) */
    public static void newGame(int cls) {
        Opt.load();
        MainMenuFrame.initialize();                    // state the menu leaves behind (_map[0x68] = 0xff, slot 0 ...)
        Sound.menuBgm();                               // LoadFrame target 5: menu BGM bookkeeping
        G.gameMenu[0x16] = 0;
        Pool.hero.m[0x16b] = (byte) cls;
        LoadFrame.setStateGame();
    }
}
