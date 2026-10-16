package hl4.game;

import hl4.sys.Bytes;

/** Scene change state machine (SCENE_Change @0x6f0bc, SCENE_End @0x6dbd8): the load of a scene is spread over LoadFrame ticks (step = _loadCnt). */
public final class Scene {
    private Scene() {
    }

    /** SCENE_Change(step) */
    public static void change(int step) {
        step = (byte) step;
        throw new IllegalStateException("SCENE_Change(" + step + "): later");
    }

    /** SCENE_End @0x6dbd8 */
    public static int end() {
        throw new IllegalStateException("SCENE_End: later");
    }
}
