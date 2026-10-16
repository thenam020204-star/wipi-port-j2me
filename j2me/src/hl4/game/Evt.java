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

    /** Npc_getEvt(k) @0xa8a48: the NPC created for event record k */
    static Char npcEvt(int k) {
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
            case 0x21: {
                int c = t.getByte();
                if (e[0x206] == 0) {
                    int cur = m[0x69];
                    int nv;
                    if ((c & 0xff) == cur) {
                        m[0x6b] = 0;                                    // Map_setBgmPlay(0)
                        nv = c;
                    } else {
                        Sound.freeBgm();
                        if (c == -1) {
                            nv = -1;
                        } else {
                            Sound.loadBgm(c);
                            nv = c;
                        }
                    }
                    m[0x6a] = m[0x69];                                  // Map_Set_BGM(nv)
                    m[0x69] = (byte) nv;
                    if ((c & 0xff) != 0xff) {
                        return true;
                    }
                } else if (c != -1) {
                    m[0x69] = (byte) c;
                    return true;
                }
                Sound.freeBgm();
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
            case 0x2e: {
                t.get(b, 4);
                if (e[0x206] != 0) {
                    return true;
                }
                int idx = b[1];
                int partner = ((idx + (idx >>> 31)) & ~1) + ((idx + 1) % 2);
                if (e[partner * 0xbc + 0x214] != 0) {
                    int old = e[0x505] & 0xff;
                    e[0x50a] = (byte) old;
                    e[0x50b] = (byte) (old <= 1 ? 1 : 0);
                    e[old * 0xbc + 0x23e] = 1;
                    e[0x506] = (byte) ((idx & 1) != 0 ? 2 : 3);
                    Bytes.w32(e, 0x200, 0x2f);
                    e[0x204] = 1;
                } else {
                    e[0x506] = (byte) ((idx & 1) != 0 ? 2 : 3);
                    Bytes.w32(e, 0x200, 0x3c);
                }
                e[0x508] = b[0];
                e[0x509] = b[2];
                e[0x50e] = b[3];
                e[0x50d] = b[1];
                e[0x505] = b[1];
                return false;
            }
            case 0x2f: {
                int s = t.getByte() & 0xff;
                e[0x50a] = (byte) s;
                if (s == 4) {
                    e[0x472] = 1;
                    e[0x23e] = 1;
                    e[0x2fa] = 1;
                    e[0x3b6] = 1;
                } else {
                    e[0x50b] = (byte) (s <= 1 ? 1 : 0);
                    e[s * 0xbc + 0x23e] = 1;
                }
                return false;
            }
            case 0x30: {
                int v = t.getByte() & 0xff;
                if (e[0x206] != 0) {
                    return true;
                }
                e[0x50e] = (byte) v;
                e[0x50d] = 0;
                Dialog.talkBoxReset(v, 0, 1);
                return false;
            }
            case 0x31: {
                int c = t.getByte();
                if (e[0x206] == 0) {
                    Sound.playEff(c);
                }
                return true;
            }
            case 0x25: {
                t.get(b, 4);
                LoadFrame.nowLoading = true;
                e[0x944] = 0;
                Event.moveNextScene(b[0], b[1], b[2], b[3], -1);
                return true;
            }
            case 0x26: {
                t.get(b, 4);
                e[0x944] = 1;
                Event.setScreen(0, 1);
                Bytes.w32(e, 0x600, 0xff);
                Event.moveNextScene(b[0], b[1], b[2], b[3], -1);
                return true;
            }
            case 0xb:
                t.get(b, 2);
                return true;
            case 0xe:
                t.get(b, 4);
                return false;
            case 0x10: {
                t.get(b, 3);
                hero.m[0x172] = b[1];
                HeroCtl.setFrameControl((Char) hero, b[0], b[1], b[2]);
                return true;
            }
            case 0x11:
                Chars.setVisible(hero, t.getByte());
                return true;
            case 0x12:
                t.getByte();
                return true;
            case 0x14: {
                int c = t.getByte();
                Chars.addHp(hero, -(c * Bytes.s32(hero.m, 0xa8) / 100));
                return true;
            }
            case 0x1a: {
                int c = t.getByte();
                Chars.addHp(hero, c * Bytes.s32(hero.m, 0xa8) / 100);
                return true;
            }
            case 0x15:
                HeroCtl.setDirection(hero, t.getByte());
                return true;
            case 0x18: {
                int c = t.getByte();
                t.getByte();
                hero.m[0x171] = (byte) c;
                return true;
            }
            case 0x1e: {
                int a = t.getByte();
                int v = t.getByte();
                Quest.setSwitchQuest(a < 0 ? a + 0x100 : a, v);
                It.changed = true;
                return true;
            }
            case 0x2a:
                t.getByte();
                t.getByte();
                return false;
            case 0x13: {
                int c = t.getByte();
                Bytes.w16(Pool.hero.m, 0xd0e, c == 1 ? 1 : 0);          // HERO_setEventCostume
                HeroLoad.reLoad();
                return true;
            }
            case 0x19: {
                t.get(b, 4);
                Bytes.w32(e, 0x94c, b[3]);
                if (e[0x206] != 0) {
                    b[2] = (byte) 0xff;
                }
                e[0x5eb] = 1;
                Event.setHeromove2(b[1], b[0], b[2]);
                return false;
            }
            case 0x1f: {
                t.get(b, 2);
                Event.setQuestStatus(b[0], b[1]);
                Quest.getStatusList(b[0]);
                It.changed = true;
                if (e[0x58c] == -1) {
                    Bytes.w32(e, 0x200, 0x32);
                    return false;
                }
                return true;
            }
            case 0x32: {
                if (e[0x206] != 0) {
                    Event.initSkip();
                }
                e[0x58c] = (byte) t.getByte();
                Event.popupReset(1);
                Maps.initDrawFlag();
                return false;
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
