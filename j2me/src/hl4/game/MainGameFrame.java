package hl4.game;

/** The in-game frame (MainGameFrame_* @0x9a290..0x9d6a8): per tick Process / Render / EndProcess over _game (296 bytes). */
public final class MainGameFrame implements FrameHandler {
    public static final MainGameFrame I = new MainGameFrame();

    private MainGameFrame() {
    }

    /** MainGameFrame_Initialize @0x9d6a8 */
    public static void initialize() {
        throw new IllegalStateException("MainGameFrame_Initialize: later");
    }

    /** MainGameFrame_MapChangeInitialize @0x9d5f0 */
    public static void mapChangeInitialize() {
        throw new IllegalStateException("MainGameFrame_MapChangeInitialize: later");
    }

    public void process() {
        throw new IllegalStateException("MainGameFrame_Process: later");
    }

    public void render() {
        throw new IllegalStateException("MainGameFrame_Render: later");
    }

    public void endProcess() {
        throw new IllegalStateException("MainGameFrame_EndProcess: later");
    }

    public void keyPressed(int key) {
        throw new IllegalStateException("MainGameFrame_KeyPressed: later");
    }

    public void keyReleased(int key) {
    }

    public void hideNotify() {
    }

    public void showNotify() {
    }
}
