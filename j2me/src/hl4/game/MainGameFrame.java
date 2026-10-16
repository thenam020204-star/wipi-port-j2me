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
