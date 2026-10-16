package hl4.game;

import hl4.sys.Bytes;

/**
 * Quest bookkeeping: _questMenu (8463 bytes: +0x210c count, +0x210d/+0x210e selection, 30 records of 0x11a bytes) and the quest/event flag
 * arrays inside _event (+0x624, +0x724, +0x824: status per quest-step, 2 = active).
 */
public final class Quest {
    public static final int REC = 0x11a;

    private Quest() {
    }

    /** Quest_Init @0x99628 (QuestInfo_Init is empty) */
    public static void init() {
        Bytes.fill(G.event, 0x624, 0, 0x100);
        Bytes.fill(G.event, 0x724, 0, 0x100);
        Bytes.fill(G.event, 0x824, 0, 0x100);
        G.event[0x7f7] = 1;
    }

    /** GMenu_initUiQuest(k) @0x7549c */
    public static void initUiQuest(int k) {
        byte[] q = G.questMenu;
        int b = k * REC;
        q[0x210d] = (byte) 0xff;
        q[b] = (byte) 0xff;
        q[b + 1] = 0;
        q[b + 0x101] = (byte) 0xff;
        q[b + 0x102] = (byte) 0xff;
        Bytes.fill(q, b + 0x103, 0, 10);
        Bytes.fill(q, b + 0x10d, 0xff, 10);
        q[b + 0x119] = 0;
        Bytes.fill(q, b + 3, 0, 0x1b);
        Bytes.fill(q, b + 0x1e, 0, 200);
        Bytes.fill(q, b + 0xe6, 0, 0x1b);
    }

    /** GMenu_QuestListInit @0x75564 */
    public static void listInit() {
        for (int k = 0; k < 30; k++) {
            initUiQuest(k);
        }
    }

    /** Event_QuestCheck(type, idx, n) @0x6d6d8: counts an item/monster of (type, idx) toward the active quests */
    public static int check(int type, int idx, int n) {
        byte[] q = G.questMenu;
        int count = q[0x210c];
        if (count > 0) {
            for (int k = 0; k < count; k++) {
                int b = k * REC;
                int sub = q[b + 1];
                if (sub >= 0) {
                    int j = 0;
                    do {
                        int step = j + (q[b + 0x118] & 0xff);
                        if (G.event[step + 0x824] == 2 && q[b + 0x101] == type && (q[b + 0x102] & 0xff) == (idx & 0xff)) {
                            if (type < 0xe) {
                                q[0x210d] = (byte) k;
                                int c = q[b + 0x119] & 0xff;
                                q[0x210e] = (byte) j;
                                int nc = c + n;
                                q[b + 0x119] = (byte) nc;
                                if ((nc << 16) < 1) {
                                    q[b + 0x119] = 0;
                                }
                                if (q[b + j + 0x103] <= (q[b + 0x119] & 0xff)) {
                                    Event.setQuestStatus(step, 3);
                                    Event.onLoad();
                                }
                                return 1;
                            }
                            if (n == 1 && G.event[(idx & 0xff) + 0x624] == 1) {
                                Event.setQuestStatus(step, 3);
                                Event.onLoad();
                                return 1;
                            }
                        }
                        j++;
                    } while (j <= sub);
                }
            }
        }
        return 0;
    }

    /** Event_Get_SwitchQuest(i) @0x6941c (signed byte at _event+0x724+i) */
    public static int getSwitchQuest(int i) {
        return G.event[(i & 0xff) + 0x724];
    }

    /** Event_Get_SwitchDefault(i) @0x69450 */
    public static int getSwitchDefault(int i) {
        return G.event[(i & 0xff) + 0x624];
    }

    /** Event_Set_SwitchDefault(i, v) @0x6947c */
    public static void setSwitchDefault(int i, int v) {
        G.event[(i & 0xff) + 0x624] = (byte) v;
    }

    /** Event_Set_SwitchQuest(i, v) @0x694a8 */
    public static void setSwitchQuest(int i, int v) {
        G.event[(i & 0xff) + 0x724] = (byte) v;
        G.event[0x7f8] = 0;
    }
}
