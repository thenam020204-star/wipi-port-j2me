package hl4.game;

import hl4.sys.Bytes;

/** Script callbacks (EVT_OnInitEvent @0x6d230, EVT_OnEvent @0x6d214, EVT_OnCondition @0x6cf34, EVT_OnFunction @0x6ddd0, Evt_OnMessageBox @0x6c62c). */
public final class Evt {
    private static final byte[] e = G.event;
    private static final byte[] m = G.map;

    private Evt() {
    }

    private static void later(String what) {
        throw new IllegalStateException(what + ": later");
    }

    /** EVT_OnInitEvent(i, file, ptr): ptr mimics the original pointer (the event body starts at ptr + 1) */
    public static void onInitEvent(int i, byte[] f, int ptr) {
        int r = Event.rec(i);
        Bytes.w16(e, r, f[ptr + 1] << 4);
        Bytes.w16(e, r + 2, f[ptr + 2] << 4);
        e[r + 4] = f[ptr + 3];
        e[r + 5] = f[ptr + 4];
        e[r + 6] = f[ptr + 5];
        e[r + 7] = f[ptr + 6];
        e[r + 8] = f[ptr + 7];
        Maps.addEventObject(i, (byte) i);
        if ((e[8] & 0xff) - 1 == i) {
            Maps.createMinimapTile();
        }
    }

    /** EVT_OnEvent: skips the 7 header bytes of an event */
    public static boolean onEvent(It.Tok t) {
        t.pos += 7;
        return true;
    }

    /** EVT_OnCondition(kind, token) @0x6cf34 */
    public static boolean onCondition(int kind, It.Tok t) {
        byte[] b = new byte[6];
        switch (kind) {
            case 0:
                b[0] = (byte) t.getByte();
                return Items.leftSlotCnt() == b[0];
            case 1:
                t.get(b, 2);
                for (int k = 0; k < 7; k++) {
                    Obj it = G.equip[k];
                    if (it != null && it.m[0x20] == b[0] && (it.m[0x22] & 0xff) == (b[1] & 0xff)) {
                        return true;
                    }
                }
                return false;
            case 2:
                t.get(b, 3);
                later("EVT_OnCondition 2 (Item_getItemCnt)");
                return false;
            case 3:
                b[0] = (byte) t.getByte();
                b[1] = (byte) t.getByte();
                later("EVT_OnCondition 3 (attribute/skill points)");
                return false;
            case 4:
                later("EVT_OnCondition 4 (money)");
                return false;
            case 5: {
                int a = t.getByte() & 0xff;
                int v = t.getByte();
                return Quest.getSwitchQuest(a) == v;
            }
            case 6:
                t.get(b, 2);
                return e[(b[0] & 0xff) + 0x824] == b[1];
            case 7: {
                int a = t.getByte() & 0xff;
                int v = t.getByte();
                return Quest.getSwitchDefault(a) == v;
            }
            default:
                return false;
        }
    }

    /** Evt_OnMessageBox(title, ids, n) @0x6c62c: a choice box */
    public static void onMessageBox(int title, int[] ids, int n) {
        later("Evt_OnMessageBox");
    }

    private static Char npcEvt(int k) {
        for (int i = 0; i < G.npcList.count && i < 128; i++) {
            Obj o = G.npcList.a[i];
            if (o != null && o.m[Obj.EVENT] == k) {
                return (Char) o;
            }
        }
        return null;
    }

    /** EVT_OnFunction(cmd, token) @0x6ddd0: one script command; true = continue with the next command, false = wait */
    public static boolean onFunction(int cmd, It.Tok t) {
        byte[] b = new byte[6];
        Obj hero = Pool.hero;
        e[0x1fe] = 1;
        m[0x34f] = 0;
        m[0x34e] = (byte) 0xff;
        Bytes.w32(e, 0x200, cmd);
        switch (cmd) {
            case 0: {
                t.get(b, 4);
                Char n = npcEvt(b[0]);
                if (n == null) {
                    return false;
                }
                Chars.setDirection(n, b[2]);
                Npcs.setNextMotionDirection(n, b[1], b[2]);
                if (Chars.process(n, false)) {
                    Npcs.chageMainState(n);
                }
                Npcs.setLoop(n, b[3] & 0xff);
                return true;
            }
            case 1:
                t.get(b, 2);
                e[(b[0] & 0xff) * 10 + 0x11] = b[1];
                return true;
            case 2: {
                t.get(b, 2);
                int type = e[(b[0]) * 10 + 0xe] & 0xff;
                Maps.changeEvtObjectImage(type, b[0], (short) b[1]);
                if (type < 3) {
                    Maps.initDrawFlag();
                }
                return false;
            }
            case 3: {
                t.get(b, 5);
                int type = e[b[0] * 10 + 0xe];
                Maps.changeEvtObjectImage(type, b[0], b[1]);
                Char n = npcEvt(b[0]);
                if (n != null) {
                    Chars.setDirection(n, b[3]);
                    if (((b[4] + 1) & 0xff) < 2) {
                        Npcs.setLoopNextMotionDirection(n, b[2]);
                    } else {
                        Npcs.setNextMotionDirection(n, b[2], b[3]);
                    }
                    Npcs.setLoop(n, b[4] & 0xff);
                }
                return true;
            }
            case 4:
                t.get(b, 2);
                e[(b[0] & 0xff) * 10 + 0x10] = b[1];
                return true;
            case 5: {
                int a = t.getByte();
                int d = t.getByte();
                int type = e[a * 10 + 0xe] & 0xff;
                if (type > 5) {
                    Chars.setDirection(npcEvt(a), d);
                    return true;
                }
                if (type != 0 && d == 1) {
                    Maps.chageEvtObjDir(a);
                    if (type - 1 < 2 && type - 1 >= 0) {
                        Maps.initDrawFlag();
                    }
                }
                return true;
            }
            case 6: {
                int a = t.getByte() & 0xff;
                int v = t.getByte();
                t.getByte();
                Char n = npcEvt((byte) a);
                if (n != null) {
                    n.m[0x171] = (byte) v;
                    return true;
                }
                return false;
            }
            case 7: {
                t.get(b, 5);
                Char n = npcEvt(b[0]);
                if (n == null) {
                    return false;
                }
                if (b[4] != 4) {
                    n.m[0x1b4] = 0;
                }
                int speed = b[3];
                if (e[0x206] != 0) {
                    speed = -1;
                }
                int dx = (byte) (b[2] * Um.vecX(b[1]));
                int dy = (byte) (b[2] * Um.vecY(b[1]));
                Npcs.evnetMove(n, dx, dy, speed, b[4]);
                e[0] = 1;
                return e[0x206] != 0;
            }
            case 8:
                return false;
            case 9: {
                t.get(b, 4);
                Char n = npcEvt(b[0]);
                if (n == null) {
                    Bytes.w16(e, b[0] * 10 + 10, b[1] << 4);
                    Bytes.w16(e, b[0] * 10 + 0xc, b[2] << 4);
                    e[b[0] * 10 + 0x13] = b[3];
                } else {
                    Npcs.evnetMove(n, b[1], b[2], 3, b[3]);
                }
                return true;
            }
            case 0xf:
                t.get(b, 2);
                m[b[0] + 0x36a] = b[1];
                return true;
            case 0x20: {
                int a = t.getByte() & 0xff;
                int v = t.getByte();
                Quest.setSwitchDefault((byte) a, v);
                Quest.check(0xe, (byte) a, v);
                It.changed = true;
                return true;
            }
            case 0x23: {
                int c = t.getByte();
                MapFx.setFGI(c);
                return true;
            }
            case 0x24: {
                int c0 = t.getByte() & 0xff;
                int c1 = t.getByte();
                return Event.setScreen((byte) c0, c1) != 0;
            }
            case 0x2b: {
                int x = t.getByte();
                int y = t.getByte();
                int c = t.getByte();
                if (e[0x206] != 0 || c == 1) {
                    c = -1;
                }
                Cam.setCameraMove(x * m[0x7e] + m[0x7e] / 2, y * m[0x7f] + m[0x7f] / 2, c, -2);
                return false;
            }
            case 0x2d: {
                int v = t.getByte() & 0xff;
                if (e[0x206] == 0) {
                    e[0x943] = (byte) v;
                    Bytes.w32(e, 0x20c, 0);
                    return false;
                }
                e[0x943] = 0;
                return true;
            }
            case 0x31: {
                int c = t.getByte();
                if (e[0x206] == 0) {
                    Sound.playEff(c);
                }
                return true;
            }
            case 0x37: {
                int c = t.getByte();
                if (c == 1) {
                    later("EVENT_ending");
                    return false;
                }
                return true;
            }
            default:
                later("EVT_OnFunction cmd " + cmd);
                return false;
        }
    }
}
