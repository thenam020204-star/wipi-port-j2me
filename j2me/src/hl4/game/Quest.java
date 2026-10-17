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

    /**
     * Event_Get_QuestStatusList(step) @0x6d8b4 (script command 0x1f): a quest step that just became 2 (active) is loaded into the next record of the quest
     * list (LoadPartQuestData), its sub-steps are marked active, the list is sorted, and the item / monster counters of the new record are brought up to date;
     * steps that became 5 / 6 (done / failed) leave the list.
     */
    public static void getStatusList(int step) {
        byte[] q = G.questMenu;
        byte[] e = G.event;
        step &= 0xff;
        int st = e[step + 0x824] & 0xff;
        if (st == 2) {
            int idx = q[0x210c];
            q[idx * REC + 0x118] = (byte) step;
            loadPart(idx, (byte) (idx + 1));
            int sub = q[idx * REC + 1] & 0xff;
            if ((sub & 0x80) == 0) {
                for (int k = step; k < step + sub + 1; k++) {
                    e[k + 0x824] = 2;
                }
            }
            sort();
            q[0x210c]++;
            int b = (q[0x210c] - 1) * REC;
            int type = q[b + 0x101] & 0xff;
            if (type < 0xd) {
                int c = q[b + 0x102];
                check(type, c, Items.getItemCnt(type, c));
            }
        } else if (st - 5 >= 0 && st - 5 < 2 && q[0x210c] > 0) {
            // the record whose step range [id, id + sub] holds the step: a finished quest (5) pays its reward (per-step entry at +0x10d, the
            // quest items are taken back), then the record leaves the list; the walk goes on with k + 1 (the record shifted into k is skipped)
            for (int k = 0; (byte) k < q[0x210c]; k = (k + 1) & 0xff) {
                int b = k * REC;
                int sub = q[b + 1];
                if (sub < 0) {
                    continue;
                }
                int off = step - (q[b + 0x118] & 0xff);
                if (off < 0 || off > sub) {
                    continue;
                }
                if (e[step + 0x824] == 5) {
                    Items.repaySet("/ITM/DAT/_ITM_Q_REPAY_" + G.game[1], q[b + off + 0x10d], 1, 1);
                    int type = q[b + 0x101] & 0xff;
                    if (type <= 0xc) {
                        int cnt = type == 0xc ? Items.getItemCnt(0xc, q[b + 0x102] & 0xff) : q[b + off + 0x103];
                        Items.removeItem((byte) type, q[b + 0x102] & 0xff, cnt);
                    }
                }
                delObject(k, q[0x210c]);
                q[0x210c]--;
                q[0x210e] = -1;
                q[0x210d] = -1;
            }
        }
    }

    /** DelObject(list, k, count) @0x796d8: records k+1..count-1 move down one place; the freed last record is reset */
    private static void delObject(int k, int count) {
        byte[] q = G.questMenu;
        for (int i = k; i < count - 1; i++) {
            System.arraycopy(q, (i + 1) * REC, q, i * REC, REC);
        }
        int b = (count - 1) * REC;
        q[b] = -1;
        q[b + 1] = -1;
        Bytes.fill(q, b + 3, 0, 0x1b);
        Bytes.fill(q, b + 0x1e, 0, 0x1b);
        Bytes.fill(q, b + 0xe6, 0, 0x1b);
        q[b + 0x101] = -1;
        q[b + 0x102] = -1;
        Bytes.fill(q, b + 0x103, 0, 10);
        Bytes.fill(q, b + 0x10d, 0xff, 10);
        q[b + 0x119] = 0;
        q[b + 0x117] = -1;
        q[b + 0x118] = 0;
    }

    /** LoadPartQuestData(first, end) @0x76648: records first..end-1 of the quest list from /NPC/_QUEST_<class/2>_DAT, keyed by their step id (+0x118) */
    private static void loadPart(int first, int end) {
        byte[] q = G.questMenu;
        int cls = Pool.hero.m[0x16b] / 2;
        byte[] d = hl4.sys.Res.loadDes("/NPC/_QUEST_" + cls + "_DAT");
        if (d == null) {
            return;
        }
        for (int k = first; k < end; k++) {
            int b = k * REC;
            int id = q[b + 0x118] & 0xff;
            q[b + 2] = (byte) id;
            int rec = (short) (id - cls * 100);
            int off = Items.getOffset(d, rec);
            Bytes.fill(q, b + 3, 0, 0x1b);
            Bytes.fill(q, b + 0x1e, 0, 200);
            Bytes.fill(q, b + 0xe6, 0, 0x1b);
            q[b] = d[off];
            q[b + 1] = d[off + 1];
            int l1 = d[off + 2];
            int pos = off + 3 + l1;
            System.arraycopy(d, off + 3, q, b + 3, l1);
            int l2 = d[pos];
            pos++;
            System.arraycopy(d, pos, q, b + 0x1e, l2);
            pos += l2;
            int l3 = d[pos];
            pos++;
            System.arraycopy(d, pos, q, b + 0xe6, l3);
            pos += l3;
            q[b + 0x101] = d[pos];
            q[b + 0x102] = d[pos + 1];
            int sub = q[b + 1];
            if (0 < sub + 1) {
                int p2 = pos + 2;
                int m = 1;
                int j = 0;
                int iv;
                do {
                    iv = m;
                    q[b + j + 0x103] = d[p2];
                    q[b + j + 0x10d] = d[p2 + 1];
                    try {
                        p2 = Items.getOffset(d, (short) (((id - cls * 100) & 0xffff) + iv));
                        p2 = p2 + 3 + d[p2 + 2];
                        p2 = p2 + 1 + d[p2];
                        p2 = p2 + 3 + d[p2];
                    } catch (ArrayIndexOutOfBoundsException x) {
                        break;                              // the last pass of the original reads past the table (its result is not used)
                    }
                    m = iv + 1;
                    j = iv;
                } while (iv < sub + 1);
            }
        }
    }

    /** sortQuestList @0x71c78: exchange sort of the records by their step id (+2, signed) */
    private static void sort() {
        byte[] q = G.questMenu;
        int count = q[0x210c];
        if (count == 0) {
            return;
        }
        byte[] tmp = new byte[REC];
        int i = 0;
        int outer = 0;
        while (outer < count - 1) {
            int next = outer + 1;
            if (next < count) {
                int p = outer * REC;
                int inner = next;
                do {
                    if (q[p + REC + 2] < q[i + 2]) {
                        System.arraycopy(q, i, tmp, 0, REC);
                        System.arraycopy(q, p + REC, q, i, REC);
                        System.arraycopy(tmp, 0, q, p + REC, REC);
                    }
                    p += REC;
                    inner++;
                } while (inner < count);
            }
            i += REC;
            outer = next;
        }
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
