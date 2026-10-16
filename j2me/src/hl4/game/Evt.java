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
            case 0:
                later("EVT cmd 0x00");
                return false;
            case 2: {
                t.get(b, 2);
                int type = e[(b[0]) * 10 + 0xe] & 0xff;
                Maps.changeEvtObjectImage(type, b[0], (short) b[1]);
                if (type < 3) {
                    Maps.initDrawFlag();
                }
                return false;
            }
            case 5:
                later("EVT cmd 0x05");
                return false;
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
                later("EVT cmd 0x07 (NPC_evnetMove)");
                return false;
            }
            case 8:
                return false;
            case 9:
                later("EVT cmd 0x09");
                return false;
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
