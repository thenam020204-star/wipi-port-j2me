package hl4.game;

/** One screen of the game (the nBASE_* function-pointer slots set by MainFrame_ElementAt in the original). */
public interface FrameHandler {
    void process();

    void render();

    void endProcess();

    void keyPressed(int key);

    void keyReleased(int key);

    void hideNotify();

    void showNotify();
}
