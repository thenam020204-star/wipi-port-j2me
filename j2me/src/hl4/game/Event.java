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
        throw new IllegalStateException("Event_ScreenEnd: later");
    }

    public static void setQuestStatus(int step, int status) {
        throw new IllegalStateException("Event_Set_QuestStatus: later");
    }

    public static void onLoad() {
        throw new IllegalStateException("EVENT_onLoad: later");
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
        throw new IllegalStateException("EVENT_End + IT_RegisterFunc_*: later");
    }
}
