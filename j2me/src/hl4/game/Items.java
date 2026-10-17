package hl4.game;

import hl4.sys.Bytes;
import hl4.sys.Clock;
import hl4.sys.Res;
import hl4.sys.Rng;

/**
 * Items and the item bag (Item_* @0x92xxx..0x96xxx, ItemBag_*, ITEM_*).  An item is a pooled 0x270-byte struct (type 8, or type 12 in the
 * arms bag); field offsets are the original's.  Known fields: +0x20 type (s8), +0x22 index (s16), +0x24 equip slot (0xff = none), +0x25 count,
 * +0x27 name (0x1b), +0x42 skin name (0x1b), +0x60 price (s32), +0x64/+0x65/+0x66/+0x67 grade/skin/..., +0x6a/+0x6c/+0x6e/+0x70/+0x72 stat pairs,
 * +0x76.. option levels, +0x83 description (200), +0x168..+0x16a option values, +0x16d/+0x16e/+0x16f upgrade bonus/kind/level,
 * +0x17d/+0x1b9 option names, +0x258 acquisition time (s64).
 */
public final class Items {
    private Items() {
    }

    /** Item_getOffset @0x92c48: offset of the body of record idx in a {s16 len, body[len]} table */
    public static int getOffset(byte[] d, int idx) {
        int p = 0;
        int n = 0;
        for (;;) {
            int len = Bytes.s16(d, p);
            n++;
            if (n == idx + 1) {
                break;
            }
            p += 2 + len;
        }
        return p + 2;
    }

    /** Item_getItemCnt(type, idx) @0x8361c: how many of the item the bag holds (-1 when type or idx is -1) = Item_getItemCnt_ETC(type, idx, 0) */
    public static int getItemCnt(int type, int idx) {
        return getItemCntEtc(type, idx);
    }

    /** Item_getItemCnt_ETC(type, idx) @0x83570: the loop of getItemCnt (the original's third register argument is never read) */
    public static int getItemCntEtc(int type, int idx) {
        if (type == -1 || idx == -1) {
            return -1;
        }
        LList l = G.itemBag;
        if (l.isEmpty()) {
            return 0;
        }
        int n = 0;
        for (Obj p = l.head; p != null; p = p.next) {
            if (getType(p) == type && getIdx(p) == idx) {
                n += getCnt(p);
            }
        }
        return n;
    }

    /**
     * Remove_item(type, idx, n) @0x93c80: takes n of (type, idx) out of the bag (equipped items are skipped); a stack bigger than n is reduced,
     * smaller ones are removed whole (Event_QuestCheck(type, idx, -remaining) first).  After a removal the walk resumes at the bag's SECOND node
     * (head->next), as the original does.  True when everything was taken.
     */
    public static boolean removeItem(int type, int idx, int n) {
        LList bag = G.itemBag;
        if (bag.size <= 0) {
            return n < 1;
        }
        Obj p = bag.head;
        while (p != null) {
            if (isEquipSetSlot(p) || getType(p) != type || getIdx(p) != idx) {
                p = p.next;
                continue;
            }
            if (getCnt(p) > n) {
                addCnt(p, (byte) -n);
                return true;
            }
            Quest.check(p.m[0x20], (short) p.m[0x22], -n);
            bag.remove(p);
            n -= getCnt(p);
            p = bag.head == null ? null : bag.head.next;
        }
        return n < 1;
    }

    /**
     * Item_RepaySet(path, idx, many, apply) @0x96184: the reward record idx of a repay table (DES file; {s16 len, body} records; body = 1 or 3
     * entries of {u8 type, s8 idx, s32 value}).  Types <= 0xc / 0x17 are items (a negative value takes items away), 0xd money, 0xe exp,
     * 0xf / 0x10 attribute / skill points, 0x11 summon exp, 0x12 / 0x13 one more guardian / arms slot.  The entries are listed at _event+0x57c
     * (type) / +0x580 (value or index) for the reward popup; an item that does not fit goes to the overflow list _game+0x5d.. .
     */
    public static void repaySet(String path, int idx, int many, int apply) {
        byte[] d = Res.loadDes(path);
        if (d == null) {
            return;
        }
        byte[] e = G.event;
        byte[] g = G.game;
        int off = getOffset(d, idx & 0xff);
        Bytes.w32(e, 0x588, -1);
        e[0x57c] = -1;
        Bytes.w32(e, 0x580, -1);
        e[0x57d] = -1;
        Bytes.w32(e, 0x584, -1);
        e[0x57e] = -1;
        boolean any = false;
        int n = many == 0 ? 1 : 3;
        for (int i = 0; i < n; i++) {
            int q = off + i * 6;
            int typeU = d[q] & 0xff;
            int type = (byte) typeU;
            int sub = d[q + 1];
            int val = Bytes.s32(d, q + 2);
            if (type > 0) {
                e[0x57b]++;
            }
            boolean item = typeU <= 0xc || type == 0x17;
            if (item) {
                if (apply != 0) {
                    if (type == 0x17) {
                        armsSetItem(0xe, sub);
                    } else if (val < 0) {
                        removeItem(type, sub, val < 0 ? -val : val);
                    }
                }
                Bytes.w32(e, 0x580 + 4 * i, sub);
            }
            switch (type - 0xd) {
                case 0:
                    Bytes.w32(e, 0x580 + 4 * i, val);
                    if (apply != 0) {
                        Hero.addCurMoney(Pool.hero, Bytes.s32(e, 0x580 + 4 * i));
                    }
                    break;
                case 1:
                    Bytes.w32(e, 0x580 + 4 * i, val);
                    if (apply != 0) {
                        MonDie.addCurExp(Pool.hero, Bytes.s32(e, 0x580 + 4 * i));
                    }
                    break;
                case 2:
                    if (apply != 0) {
                        Bytes.w32(e, 0x580 + 4 * i, val);
                        Bytes.w16(Pool.hero.m, 0x220, Bytes.u16(Pool.hero.m, 0x220) + val);
                    }
                    break;
                case 3:
                    if (apply != 0) {
                        Bytes.w32(e, 0x580 + 4 * i, val);
                        Bytes.w16(Pool.hero.m, 0x222, Bytes.u16(Pool.hero.m, 0x222) + val);
                    }
                    break;
                case 4:
                    if (apply != 0) {
                        throw new IllegalStateException("Item_RepaySet: SUMMON_addExp: later");
                    }
                    break;
                case 5:
                case 6:
                    if (apply != 0) {
                        throw new IllegalStateException("Item_RepaySet: guardian / arms slot count: later");
                    }
                    break;
                default:
                    break;
            }
            e[0x57c + i] = (byte) typeU;
            if (val < 0) {
                e[0x57c + i] = -1;
            }
            if (item && val >= 0 && apply != 0 && type != 0x17) {
                if (!setItem(type, sub, (byte) val) && leftSlotCnt() == 0) {
                    int old = Bytes.s32(e, 0x580 + 4 * i);
                    int ty = e[0x57c + i];
                    int k = g[0x5d];
                    Bytes.w32(e, 0x580 + 4 * i, -1);
                    e[0x57c + i] = -1;
                    g[k + 0x64] = (byte) val;
                    g[0x5d]++;
                    g[k + 0x5e] = (byte) ty;
                    g[k + 0x61] = (byte) old;
                }
            }
            if (e[0x57c + i] != -1) {
                any = true;
            }
        }
        if (apply != 0) {
            if (any) {
                if (e[0x206] != 0) {
                    Event.initSkip();
                }
                e[0x58c] = -1;
                Event.popupReset(1);
            } else if (g[0x5d] > 0) {
                throw new IllegalStateException("GMenu_repayErrorPopSet (bag full): later");
            }
        }
    }

    /** Item_isCountable(type) @0x82820: types 8..13 stack */
    public static boolean isCountable(int type) {
        return ((type - 8) & 0xff) < 6;
    }

    /** Item_isEquipSetSlot(item) @0x82920: the item sits in an equipment slot (+0x24 u8 <= 6); false for null */
    public static boolean isEquipSetSlot(Obj it) {
        return it != null && (it.m[0x24] & 0xff) < 7;
    }

    /** Item_getCnt(item) @0x827f4 (s8) */
    public static int getCnt(Obj it) {
        return it.m[0x25];
    }

    /** Item_getIdx(item) @0x827ec (s16) */
    public static int getIdx(Obj it) {
        return Bytes.s16(it.m, 0x22);
    }

    /** Item_getType(item) @0x827e4 (s8) */
    public static int getType(Obj it) {
        return it.m[0x20];
    }

    /** Item_addCnt @0x927fc */
    public static int addCnt(Obj it, int n) {
        int v = (it.m[0x25] + n) & 0xff;
        it.m[0x25] = (byte) v;
        if ((v & 0x80) != 0) {
            it.m[0x25] = 0;
        }
        return it.m[0x25];
    }

    /** Item_init @0x940bc / ITEM_set: the free-slot state of an item */
    public static void init(Obj it) {
        byte[] m = it.m;
        Bytes.w16(m, 0x23e, 0);
        m[0x24] = (byte) 0xff;
        m[0x20] = (byte) 0xff;
        Bytes.w16(m, 0x22, 0xffff);
        m[0x65] = (byte) 0xff;
        m[0x16e] = (byte) 0xff;
        m[0x26] = (byte) 0xff;
        m[0x170] = 0;
        m[0x64] = 0;
        m[0x16b] = 0;
        m[0x16c] = 0;
        m[0x16d] = 0;
        m[0x16f] = 0;
        m[0x260] = 0;
        m[0x168] = 0;
        m[0x169] = 0;
        m[0x16a] = 0;
        m[0x15a] = 0;
        m[0x15b] = 0;
        m[0x15c] = 0;
        m[0x213] = 0;
        m[0x214] = 0;
        m[0x215] = 0;
        m[0x216] = 0;
        m[0x217] = 0;
        m[0x233] = 0;
        m[0x238] = 0;
        for (int k = 0; k < 4; k++) {
            m[0x234 + k] = 0;
            m[0x239 + k] = 0;
            Bytes.w16(m, 0x240 + 2 * k, 0);
        }
        m[0x7b] = 0;
        m[0x76] = 0;
        m[0x79] = 0;
        Bytes.w16(m, 0x7c, 0);
        m[0x77] = 0;
        m[0x7a] = 0;
        Bytes.w16(m, 0x7e, 0);
        m[0x78] = 0;
        Bytes.w16(m, 0x80, 0);
        Bytes.w32(m, 0x264, 0);
        Bytes.fill(m, 0x42, 0, 0x1b);
        Bytes.fill(m, 0x1b9, 0, 0x5a);
        Bytes.fill(m, 0x17d, 0, 0x3c);
    }

    /** Item_newItem @0x94dd4 */
    public static Obj newItem(int poolType) {
        Obj it = Pool.getNew(poolType);
        if (it != null) {
            init(it);
        }
        return it;
    }

    /** Item_createItem(a, type, idx, cnt) @0x85720 = Item_createItem_Base(8, ...) */
    public static Obj createItem(int a, int type, int idx, int cnt) {
        return createItemBase(Pool.ITEM, a, type, idx, cnt);
    }

    /** Item_createItem_Base @0x956a4 */
    public static Obj createItemBase(int poolType, int a, int type, int idx, int cnt) {
        Obj it = newItem(poolType);
        if (it != null) {
            it.m[0x20] = (byte) type;
            Bytes.w16(it.m, 0x22, idx);
            if (!isNetworkItem(it)) {
                getItem(it, type, idx, cnt);
                it.m[0x171] = (byte) a;
                if (a == 0) {
                    it.m[0x16b] = (byte) Rng.rand(0, 5);
                }
            }
        }
        return it;
    }

    /** GMenu_isNetworkItem @0x719ec */
    public static boolean isNetworkItem(Obj it) {
        return Tab.ITEM_SELL_CNT[it.m[0x20]] <= Bytes.s16(it.m, 0x22);
    }

    /** Item_getID @0x94a54 (types >= 14: the data table is loaded per call) */
    public static byte[] getId(int type) {
        if (type == -1) {
            return null;
        }
        String s = type < 10 ? "0" + type : String.valueOf(type);
        return Res.loadDes("/ITM/DAT/_ITM_" + s + "_DAT");
    }

    /** Item_getItem @0x955f0 */
    public static void getItem(Obj it, int type, int idx, int cnt) {
        byte[] d;
        if (type < 0xe) {
            d = G.itemData[type];
        } else {
            d = getId(type);
        }
        if (d != null) {
            int off = getOffset(d, idx);
            Bytes.w16(it.m, 0x22, idx);
            it.m[0x20] = (byte) type;
            setData(it, d, off, type, 0);
            setCnt(it, cnt);
        }
    }

    /** Item_loadOptionData @0x941e8 */
    public static void loadOptionData(Obj it, int k, int optIdx) {
        byte[] t = G.itemOption;
        if (t == null) {
            return;
        }
        byte[] m = it.m;
        int off = getOffset(t, optIdx & 0xffff);
        int len = t[off] & 0xff;
        int u = optIdx > 0x6b ? 1 : 0;
        off++;
        boolean tail = false;
        if (len == 0) {
            if (optIdx < 0x6a) {
                tail = true;
            }
        } else if (optIdx < 0x6a) {
            int dst = 0x1b9 + k * 0x1e;
            Bytes.fill(m, dst, 0, 0x1e);
            Bytes.copy(t, off, m, dst, len);
            off += len;
            tail = true;
        } else {
            int dst = 0x17d + u * 0x1e;
            Bytes.fill(m, dst, 0, 0x1e);
            Bytes.copy(t, off, m, dst, len);
            off += len;
        }
        if (tail) {
            m[0x215 + k] = t[off];
            m[0x79 + k] = t[off + 1];
            Bytes.w16(m, 0x7c + k * 2, t[off + 2] & 0xff);
        } else {
            m[0x213 + u] = t[off];
            m[0x161 + u] = t[off + 1];
            Bytes.w16(m, 0x164 + u * 2, t[off + 2] & 0xff);
        }
    }

    /** Item_skinDataLoad @0x95134 */
    public static int skinDataLoad(Obj it) {
        return (short) skinDataLoadUi(it, -1, -1);
    }

    /** Item_skinDataLoad_Ui @0x94f80 */
    public static int skinDataLoadUi(Obj it, int p2, int p3) {
        byte[] m = it.m;
        if (p2 >= 7) {
            return 0;
        }
        int t = p2 == -1 ? (short) m[0x20] : (short) p2;
        byte[] d = Res.loadDes("/ITM/DAT/_ITM_" + t + "_SD");
        if (d == null) {
            return -1;
        }
        int off = getOffset(d, p3 == -1 ? (short) m[0x65] : (short) p3);
        int n = d[off];
        Bytes.fill(m, 0x42, 0, 0x1b);
        if (n > 0) {
            Bytes.copy(d, off + 1, m, 0x42, n);
        }
        int p = n + off + 1;
        int q = p + 1;
        int r = d[p];
        m[0x157] = d[q];
        if (m[0x20] < 2) {
            m[0x248] = d[q + 1];
        }
        return r;
    }

    /**
     * Item_setData @0x95158: fills the item from record 'off' of data table d: +0x26 = first byte, name (len byte + bytes), s32 price at +0x60,
     * then the per-type tail.  Types 0..7 (equipment), 8 (potion-like), 9/12 (name only), 10, 11, 14 have their own tails; type 13 and > 14 stop.
     */
    public static void setData(Obj it, byte[] d, int off, int type, int p5) {
        byte[] m = it.m;
        m[0x26] = d[off];
        Bytes.fill(m, 0x27, 0, 0x1b);
        m[0x20] = (byte) type;
        int len = d[off + 1] & 0xff;
        if (len != 0) {
            Bytes.copy(d, off + 2, m, 0x27, len);
        }
        int a = off + 2 + len;
        Bytes.w32(m, 0x60, Bytes.s32(d, a));
        int b = a + 4;
        switch (type) {
            case 8: {
                int l = d[b] & 0xff;
                Bytes.fill(m, 0x83, 0, 200);
                if (l != 0) {
                    Bytes.copy(d, a + 5, m, 0x83, l);
                }
                int c = a + 5 + l;
                for (int k = 0; k < 3; k++) {
                    m[0x76 + k] = d[c + 3 * k];
                    Bytes.w16(m, 0x7c + 2 * k, Bytes.s16(d, c + 1 + 3 * k));
                }
                return;
            }
            case 9:
            case 12: {
                int l = d[b];
                Bytes.fill(m, 0x83, 0, 200);
                if (l != 0) {
                    Bytes.copy(d, a + 5, m, 0x83, l);
                }
                return;
            }
            case 10: {
                int l = d[b] & 0xff;
                Bytes.fill(m, 0x83, 0, 200);
                if (l != 0) {
                    Bytes.copy(d, a + 5, m, 0x83, l);
                }
                int c = a + 5 + l;
                m[0x64] = d[c];
                for (int k = 0; k < 3; k++) {
                    m[0x14b + k] = d[c + 1 + 3 * k];
                    m[0x14e + k] = d[c + 2 + 3 * k];
                    m[0x151 + k] = d[c + 3 + 3 * k];
                }
                m[0x154] = d[c + 10];
                m[0x155] = d[c + 11];
                m[0x156] = d[c + 12];
                return;
            }
            case 11: {
                int l = d[b] & 0xff;
                Bytes.fill(m, 0x83, 0, 200);
                if (l != 0) {
                    Bytes.copy(d, a + 5, m, 0x83, l);
                }
                int c = a + 5 + l;
                m[0x158] = d[c];
                m[0x15a] = d[c + 1];
                m[0x15b] = d[c + 2];
                m[0x15c] = d[c + 3];
                m[0x15d] = d[c + 4];
                for (int k = 0; k < 3; k++) {
                    m[0x15e + k] = d[c + 5 + k];
                }
                return;
            }
            case 13:
                return;
            case 14: {
                int l = d[b] & 0xff;
                Bytes.fill(m, 0x83, 0, 200);
                if (l != 0) {
                    Bytes.copy(d, a + 5, m, 0x83, l);
                }
                int c = a + 5 + l;
                m[0x173] = d[c];
                m[0x174] = d[c + 1];
                return;
            }
            default:
                break;
        }
        if (type < 0 || type > 7) {
            return;
        }
        // equipment (types 0..7)
        int r = b;
        if (p5 == 0) {
            m[0x64] = d[r];
            m[0x65] = d[r + 1];
            m[0x66] = d[r + 2];
            m[0x67] = d[r + 3];
        }
        r += 4;
        m[0x68] = d[r];
        Bytes.w16(m, 0x6a, Bytes.s16(d, r + 1));
        Bytes.w16(m, 0x72, Bytes.s16(d, r + 3));
        int sl = r + 5;
        if (type <= 3) {
            Bytes.w16(m, 0x6c, Bytes.s16(d, sl));
            Bytes.w16(m, 0x74, Bytes.s16(d, sl + 2));
            sl += 4;
        }
        for (int k = 0; k < 3; k++) {
            if (p5 == 0) {
                m[0x168 + k] = d[sl + k];
                m[0x76 + k] = d[sl + k];
            }
            loadOptionData(it, k, m[0x168 + k] & 0xff);
        }
        m[0x82] = d[sl + 3];
        if (m[0x20] != 7) {
            skinDataLoad(it);
            if (m[0x20] < 2) {
                loadOptionData(it, 0, m[0x248] & 0xff);
            }
        }
        if (m[0x16f] > 0) {
            loadOptionData(it, 0, (m[0x16e] + 0x6c) & 0xff);
        }
    }

    /** ITEM_calcUseAbliity @0x92a38: attack/defence values (+0x6e / +0x70) from the base values, the upgrade bonus (+0x16d) and level (+0x16f) */
    public static void calcUseAbility(Obj it) {
        byte[] m = it.m;
        int type = m[0x20];
        if (type > 6 || type < 0) {
            return;
        }
        int d = m[0x16d];
        int lvl = m[0x16f];
        int kind = m[0x16e] & 0xff;
        boolean armor = type <= 3;
        if (lvl > 0) {
            int t = lvl * 10;
            t = (lvl * t + t) >> 1;
            t += 100;
            if (kind < 2) {
                if (armor) {
                    Bytes.w16(m, 0x70, Bytes.s16(m, 0x6c) + d);
                }
                Bytes.w16(m, 0x6e, ((d + Bytes.s16(m, 0x6a)) * t) / 100);
            } else if (kind == 2) {
                if (armor) {
                    Bytes.w16(m, 0x70, ((d + Bytes.s16(m, 0x6c)) * t) / 100);
                }
                Bytes.w16(m, 0x6e, Bytes.s16(m, 0x6a) + d);
            } else {
                if (armor) {
                    Bytes.w16(m, 0x70, Bytes.s16(m, 0x6c) + d);
                }
                Bytes.w16(m, 0x6e, Bytes.s16(m, 0x6a) + d);
            }
        } else {
            if (armor) {
                Bytes.w16(m, 0x70, Bytes.s16(m, 0x6c) + d);
            }
            Bytes.w16(m, 0x6e, Bytes.s16(m, 0x6a) + d);
        }
    }

    // ---- bag ---------------------------------------------------------------------------------------------------------------------

    /** ItemBag_leftSlotCnt @0x92880 */
    public static int leftSlotCnt() {
        return (G.itemBagMax * 0x10 - G.itemBag.size) & 0xff;
    }

    /** ItemBag_getCur(_, i) @0x9375c */
    public static Obj bagGetCur(int i) {
        if (G.itemBag.isEmpty() || G.itemBag.size - 1 < i || i < 0) {
            return null;
        }
        return G.itemBag.getAt((short) i);
    }

    /** ItemBag_addItem(_, item) @0x93328 */
    public static void bagAdd(Obj it) {
        G.itemBag.addObject(it);
    }

    /** itemSearch(type, idx) @0x938fc: the bag item with that type and index (the original returns it in r0; null = none) */
    public static Obj itemSearch(int type, int idx) {
        for (int i = 0; i < G.itemBag.size; i++) {
            Obj it = bagGetCur((byte) i);
            if (it.m[0x20] == type && Bytes.s16(it.m, 0x22) == idx) {
                return it;
            }
        }
        return null;
    }

    /** Item_findEqualTypeIdxWithCnt @0x93950 */
    public static Obj findEqualTypeIdxWithCnt(LList l, int type, int idx, int maxCnt) {
        if (l != null && !l.isEmpty()) {
            for (Obj p = l.head; p != null; p = p.next) {
                if (p.m[0x20] == type && Bytes.s16(p.m, 0x22) == idx && getCnt(p) < maxCnt) {
                    return p;
                }
            }
        }
        return null;
    }

    /** Item_checkSetInSlot @0x93ae0 */
    public static int checkSetInSlot(Obj it, int cnt, int max, int freeSlots, int write) {
        if (cnt < 1) {
            return -1;
        }
        if (it == null) {
            if (freeSlots != 0) {
                return cnt;
            }
        } else {
            int cur = getCnt(it);
            if (cur + cnt <= max) {
                if (write != 0) {
                    addCnt(it, cnt);
                    if (it.m[0x20] < 0xd) {
                        Quest.check(it.m[0x20], (byte) it.m[0x22], cnt);
                        return 0;
                    }
                }
                return 0;
            }
            if (freeSlots != 0) {
                int c2 = getCnt(it);
                if (max <= c2) {
                    return cnt;
                }
                int c3 = getCnt(it);
                int rest = (byte) (cnt - ((max - c3) & 0xff));
                int q = rest / max;
                int rem = rest % max;
                if (rem > 0) {
                    q++;
                }
                if ((byte) q <= freeSlots) {
                    if (write == 0) {
                        return rest;
                    }
                    addCnt(it, (byte) (max - c3));
                    return rest;
                }
                return -1;
            }
        }
        return -2;
    }

    /** ItemBag_setItem(_, type, idx, cnt) @0x95cfc */
    public static boolean setItem(int type, int idx, int cnt) {
        return checkAndSetItemDn(null, type, idx, cnt, 1, 0, 0, 0, -1, 0);
    }

    /** ItemBag_checkAndSetItem(_, type, idx, cnt, p5, p6, p7, p8, p10) @0x95c3c = DN(null, ..., p9 = -1) */
    public static boolean checkAndSetItem(int type, int idx, int cnt, int p5, int p6, int p7, int p8, int p10) {
        return checkAndSetItemDn(null, type, idx, cnt, p5, p6, (byte) p7, (byte) p8, -1, (byte) p10);
    }

    /**
     * ItemBag_checkAndSetItemDN @0x95790: puts 'cnt' items of (type, idx) into the bag, topping up existing stacks (p5 != 0 writes) and
     * creating new items (p6/p7/p8 = option bonuses, p9 = skin index, p10 = grade); 'donor' (when not null) supplies the option fields.
     */
    public static boolean checkAndSetItemDn(Obj donor, int type, int idx, int cnt, int p5, int p6, int p7, int p8, int p9, int p10) {
        int left = leftSlotCnt();
        LList bag = G.itemBag;
        int maxc = isCountable(type) ? 99 : 1;
        Obj f = findEqualTypeIdxWithCnt(bag, type, idx, maxc);
        if (f != null) {
            Bytes.copy(f.m, 0, G.resultItem, 0, 0x270);
        }
        int r0 = (byte) checkSetInSlot(f, cnt, 99, left, p5);
        if (r0 == -2 || r0 == -1) {
            return false;
        }
        if (r0 != 0 && p5 != 0) {
            maxc = isCountable(type) ? 99 : 1;
            f = findEqualTypeIdxWithCnt(bag, type, idx, maxc);
            if (f != null) {
                Bytes.copy(f.m, 0, G.resultItem, 0, 0x270);
            }
            int rem = checkSetInSlot(f, r0, 99, left, p5) & 0xff;
            while (rem != 0 && (byte) rem > 0) {
                int n;
                if (!isCountable(type)) {
                    if (leftSlotCnt() == 0) {
                        return false;
                    }
                    n = 1;
                } else if ((byte) rem <= 99) {
                    n = rem;
                } else {
                    n = 99;
                }
                Obj it = createItem(0, type, idx, (byte) n);
                long now = Clock.now();
                Bytes.w32(it.m, 0x258, (int) now);
                Bytes.w32(it.m, 0x25c, (int) (now >> 32));
                Bytes.copy(it.m, 0, G.resultItem, 0, 0x270);
                byte[] m = it.m;
                if (p7 > 0 || p6 > 0 || p8 > 0) {
                    for (int k = 0; k < 3; k++) {
                        int add = k == 0 ? p6 : (k == 1 ? p7 : p8);
                        m[0x168 + k] = (byte) (add + m[0x76 + k]);
                        loadOptionData(it, k, m[0x168 + k] & 0xff);
                        if (m[0x20] <= 6 && Bytes.s16(m, 0x22) <= 0x13) {
                            if (m[0x168 + k] > 0) {
                                m[0x64]++;
                            }
                        } else {
                            m[0x64] = (byte) p10;
                        }
                    }
                    if (p9 != -1) {
                        m[0x65] = (byte) p9;
                        if (m[0x20] != 7) {
                            skinDataLoadUi(it, m[0x20], p9);
                        }
                    }
                }
                int ty;
                if (donor == null) {
                    ty = m[0x20];
                } else {
                    byte[] dm = donor.m;
                    m[0x168] = dm[0x168];
                    m[0x169] = dm[0x169];
                    Bytes.copy(dm, 0x250, m, 0x250, 8);
                    Bytes.copy(dm, 0x258, m, 0x258, 8);
                    m[0x16c] = dm[0x16c];
                    m[0x16d] = dm[0x16d];
                    m[0x260] = dm[0x260];
                    Bytes.w32(m, 0x264, Bytes.s32(dm, 0x264));
                    m[0x16a] = dm[0x16a];
                    m[0x16e] = dm[0x16e];
                    m[0x16f] = dm[0x16f];
                    m[0x16b] = dm[0x16b];
                    for (int k = 0; k < 3; k++) {
                        loadOptionData(it, k, m[0x168 + k] & 0xff);
                        ty = m[0x20];
                        if (ty <= 6 && Bytes.s16(m, 0x22) <= 0x13 && m[0x168 + k] > 0) {
                            m[0x64]++;
                        }
                    }
                    ty = m[0x20];
                }
                if (ty <= 1) {
                    loadOptionData(it, 0, m[0x248] & 0xff);
                }
                if (m[0x16f] > 0) {
                    loadOptionData(it, 0, (m[0x16e] + 0x6c) & 0xff);
                }
                calcUseAbility(it);
                bagAdd(it);
                Bytes.copy(it.m, 0, G.resultItem, 0, 0x270);
                if (type < 0xd) {
                    Quest.check(type, idx, (byte) n);
                }
                rem = (rem - n) & 0xff;
            }
        }
        return true;
    }

    // ---- equipment ---------------------------------------------------------------------------------------------------------------

    /** Item_setEquipSlotNum(item, slot) @0x8293c (ignores null) */
    public static void setEquipSlotNum(Obj it, int slot) {
        if (it != null) {
            it.m[0x24] = (byte) slot;
        }
    }

    /** Item_releaseEquip @0x92950 */
    public static void releaseEquip(Obj it) {
        if (it == null) {
            return;
        }
        int slot = it.m[0x24];
        it.m[0x24] = (byte) 0xff;
        it.m[0x260] = 0;
        if (slot < 0) {
            return;
        }
        G.equip[slot] = null;
    }

    /** Item_releaseAllEquip @0x937b4: releases every bag item that sits in 'slot' */
    public static void releaseAllEquip(int slot) {
        for (int i = 0; i < G.itemBag.size; i++) {
            Obj it = bagGetCur((byte) i);
            if (it.m[0x24] == slot) {
                releaseEquip(it);
            }
        }
    }

    /** Item_setEquip(slot, item) @0x938ec */
    public static void setEquip(int slot, Obj it) {
        Obj cur = G.equip[slot];
        if (((slot - 5) & 0xff) < 2 && isEquipSetSlot(it)) {
            releaseEquip(it);
        }
        if (it != cur && isEquipSetSlot(cur)) {
            releaseEquip(cur);
            releaseAllEquip(slot);
        }
        if (it != null) {
            setEquipSlotNum(it, slot);
        }
        G.equip[slot] = it;
    }

    /** Item_dataPreLoad @0x966ac: the item data tables, skin tables and the option table */
    public static void dataPreLoad() {
        for (int t = 0; t < 14; t++) {
            byte[] d = getId2(t);
            if (d == null) {
                return;
            }
            G.itemData[t] = d;
        }
        for (int t = 0; t < 7; t++) {
            byte[] d = Res.loadDes("/ITM/DAT/_ITM_" + t + "_SD");
            if (d == null) {
                return;
            }
            G.itemSd[t] = d;
        }
        byte[] o = Res.loadDes("/ITM/DAT/_ITM_OPTION");
        if (o != null) {
            G.itemOption = o;
        }
    }

    /** Item_getSizeID @0x94d54: the data file of a type (two-digit name) */
    private static byte[] getId2(int type) {
        return getId(type);
    }

    /** Item_dataPreRelease @0x96614 */
    public static void dataPreRelease() {
        for (int t = 0; t < 14; t++) {
            G.itemData[t] = null;
        }
        for (int t = 0; t < 7; t++) {
            G.itemSd[t] = null;
        }
        G.itemOption = null;
    }

    // ---- small accessors, debug helpers -------------------------------------------------------------------------------------------

    /** Item_release @0x82744: empty in the original (bx lr) */
    public static void release() {
    }

    /** Item_Initialize @0x82794: empty in the original (bx lr) */
    public static void initialize() {
    }

    /** Item_tempInvenInit @0x82748: resets the temporary inventory / reward-overflow state in _game (+0x5d..+0x66) */
    public static void tempInvenInit() {
        byte[] g = G.game;
        g[0x66] = 0;
        g[0x63] = -1;
        g[0x5d] = 0;
        g[0x5e] = -1;
        g[0x61] = -1;
        g[0x64] = 0;
        g[0x5f] = -1;
        g[0x62] = -1;
        g[0x65] = 0;
        g[0x60] = -1;
    }

    /** Item_setCnt(item, cnt) @0x827a0 */
    public static void setCnt(Obj it, int cnt) {
        it.m[0x25] = (byte) cnt;
    }

    /** Item_releaseEquipSlotNum(item) @0x82948: setEquipSlotNum(item, -1) */
    public static void releaseEquipSlotNum(Obj it) {
        setEquipSlotNum(it, -1);
    }

    /** Item_isRandomBox(item) @0x82850: a type-8 item with index 0x19..0x1c (no null check) */
    public static boolean isRandomBox(Obj it) {
        if (it.m[0x20] != 8) {
            return false;
        }
        int idx = Bytes.s16(it.m, 0x22);
        return idx > 0x18 && idx <= 0x1c;
    }

    /** Item_setTempEquip(slot, item) @0x8299c: _equip[slot] = item (no range check) */
    public static void setTempEquip(int slot, Obj it) {
        G.equip[slot] = it;
    }

    /**
     * Item_getEquip(slot) @0x8338c: the first bag item whose +0x24 (s8) is <= 6 and equals 'slot' (the compare is against the full int, so
     * slot -1 finds the first item that is NOT equipped)
     */
    public static Obj getEquip(int slot) {
        for (Obj p = G.itemBag.head; p != null; p = p.next) {
            int v = p.m[0x24];
            if (v <= 6 && v == slot) {
                return p;
            }
        }
        return null;
    }

    /** Item_findEqualTypeIdx(list, type, idx) @0x839e0 = Item_findEqualTypeIdxWithCnt(list, type, idx, 100) */
    public static Obj findEqualTypeIdx(LList l, int type, int idx) {
        return findEqualTypeIdxWithCnt(l, type, idx, 100);
    }

    /**
     * Item_getIcon(type, idx) @0x84d30 = Item_getIconName(type, idx, 0, 0): the first byte of the item's data record (the icon), -1 when the
     * type has no data file
     */
    public static int getIcon(int type, int idx) {
        byte[] d = getId(type);
        if (d == null) {
            return -1;
        }
        return d[getOffset(d, (short) idx)];
    }

    /** Item_TestQuest(step) @0x84f48: debug; a quest step in state 0/1 is forced to 2 and its record loaded (Event_Get_QuestStatusList) */
    public static void testQuest(int step) {
        byte[] e = G.event;
        if ((e[0x824 + step] & 0xff) > 1) {
            return;
        }
        e[0x824 + step] = 2;
        Quest.getStatusList(step & 0xff);
    }

    /** Item_TestSetItem @0x85d2c: debug; bag size 8, then 100 inserts alternating (3, 9) with option bonus 0x50 and (3, 0x1a) */
    public static void testSetItem() {
        G.itemBagMax = 8;
        for (int i = 0; i < 100; i++) {
            if ((i & 1) == 0) {
                setItemOptAdd(3, 9, 1, 0x50, 0, 0, 0);
            } else {
                setItem(3, 0x1a, 1);
            }
        }
    }

    // ---- bag / arms bag accessors ------------------------------------------------------------------------------------------------

    /** ItemBag_getSlot(_) @0x82838: the bag's item list (_itemBag+4) */
    public static LList bagGetSlot() {
        return G.itemBag;
    }

    /** ItemBag_getSize(tab) @0x828bc: the item count (u8) of the bag list; the argument is unused */
    public static int bagGetSize(int tab) {
        return bagGetSlot().size & 0xff;
    }

    /** ItemBag_getTabItem(i) @0x828dc: the i-th entry of the tab-sorted pointer list (null when the list is empty or i >= its count) */
    public static Obj bagGetTabItem(int i) {
        PList l = G.itemBagPtrs;
        if (l.count == 0) {
            return null;
        }
        if (i >= l.count) {
            return null;
        }
        return l.a[i];
    }

    /** ItemBag_release(list) @0x8356c: LinkedList_release(list) (the callers pass _itemBag+4) */
    public static void bagRelease(LList l) {
        l.release();
    }

    /** ArmsBag_getSlot @0x829d8: the arms bag's list (_armsBag+4) */
    public static LList armsGetSlot() {
        return G.armsBag;
    }

    /** ArmsBag_getSize @0x829f0: the arms bag's item count as s8 */
    public static int armsGetSize() {
        return (byte) armsGetSlot().size;
    }

    /** ArmsBag_addItem(item) @0x83310 */
    public static void armsAddItem(Obj it) {
        armsGetSlot().addObject(it);
    }

    /** ArmsBag_release(list) @0x83568: LinkedList_release(list) (the callers pass _armsBag+4) */
    public static void armsRelease(LList l) {
        l.release();
    }

    /**
     * Item_getItemFrom(list, idx) @0x83624: the idx-th node of a list (counted from the head), null when the list is empty or idx is outside
     * 0..size-1
     */
    public static Obj getItemFrom(LList l, int idx) {
        if (l.isEmpty()) {
            return null;
        }
        if (idx < 0 || idx > l.size - 1) {
            return null;
        }
        Obj p = l.head;
        if (p == null) {
            return null;
        }
        if (idx != 0) {
            short k = 0;
            do {
                p = p.next;
                k++;
            } while (idx != k);
        }
        return p;
    }

    /** ArmsBag_getCur(i) @0x836a0: the i-th arms item (null when out of range) */
    public static Obj armsGetCur(int i) {
        LList l = armsGetSlot();
        if (l.isEmpty()) {
            return null;
        }
        if (i > armsGetSize() - 1 || i < 0) {
            return null;
        }
        return getItemFrom(l, (short) i);
    }

    /** AmrsSearch(type, idx) @0x83704: the arms item with that type and index (null = none) */
    public static Obj armsSearch(int type, int idx) {
        int i = 0;
        for (;;) {
            int cur = i++;
            if (G.armsBag.size <= cur) {            // MP_getArmsBag()+0xc, not through ArmsBag_getSlot
                return null;
            }
            Obj o = armsGetCur((byte) cur);
            if (o.m[0x20] == type && Bytes.s16(o.m, 0x22) == idx) {
                return o;
            }
        }
    }

    /** ArmsBag_setItem(type, idx) @0x85750: adds a new count-1 item to the arms bag unless one of that type and index is already there (returns 0) */
    public static int armsSetItem(int type, int idx) {
        if (armsSearch(type, idx) == null) {
            armsAddItem(createItem(0, type, idx, 1));
        }
        return 0;
    }

    // ---- bag: tab lists, removal -------------------------------------------------------------------------------------------------

    /** GMenu_InvenTypeCheck(item, mode) @0x61878: does the item belong to inventory category 'mode' (0..9; others false) */
    public static boolean invenTypeCheck(Obj it, int mode) {
        byte[] m = it.m;
        if (mode < 0 || mode > 9) {
            return false;
        }
        int u = m[0x20] & 0xff;
        switch (mode) {
            case 0:
                return u >= 4 && u <= 6;
            case 1:
                if (u <= 3 || u == 7) {
                    return true;
                }
                return u == 8 && m[0x21] == 0 && m[0x22] == 0x15 && m[0x23] == 0;
            case 2:
                return m[0x20] == 8;
            case 3:
                return m[0x20] == 0xa;
            case 4:
                return m[0x20] == 9;
            case 5:
            case 6:
            case 7:
                return m[0x20] == mode + 6;
            case 8:
                return false;
            default:
                return m[0x20] == 0xe;
        }
    }

    /** GMenu_EquiptypeCheck(item, mode) @0x66dfc: does the item fit equipment tab 'mode' (4 = the hero class's weapon type) */
    public static boolean equipTypeCheck(Obj it, int mode) {
        int ty = it.m[0x20];
        if (mode <= 3) {
            return ty == mode;
        }
        if (mode != 4) {
            return ty == 7;
        }
        int cls = Pool.hero.m[0x16b];
        if (cls == 0 || cls == 2) {
            if (ty == 4) {
                return true;
            }
        }
        if (cls == 1 && ty == 5) {
            return true;
        }
        return cls == 3 && ty == 6;
    }

    /**
     * ItemBag_addItemTab(list, tab, mode) @0x839e8: appends to 'list' every bag item that passes the filter of 'mode' (0 inventory category
     * 'tab', 1 equipment tab, 2 inventory category with grade +0x64 <= 2, 3 inventory category with type <= 6) and stores the item's position
     * in the bag list at +0x172
     */
    public static void bagAddItemTab(PList list, int tab, int mode) {
        Obj n = bagGetSlot().head;
        if (n == null) {
            return;
        }
        int pos = 0;
        for (;;) {
            boolean ok;
            if (mode == 0) {
                ok = invenTypeCheck(n, tab);
            } else if (mode == 1) {
                ok = equipTypeCheck(n, tab);
            } else if (mode == 2) {
                ok = invenTypeCheck(n, tab) && n.m[0x64] <= 2;
            } else if (mode == 3) {
                ok = invenTypeCheck(n, tab) && n.m[0x20] <= 6;
            } else {
                ok = false;
            }
            if (ok) {
                list.add(n);
                n.m[0x172] = (byte) pos;
            }
            n = n.next;
            if (n == null) {
                return;
            }
            pos++;
        }
    }

    /** GMenu_InventoryTabSort(tab, mode) @0x6e234: rebuilds the bag's tab pointer list */
    public static void inventoryTabSort(int tab, int mode) {
        G.itemBagPtrs.release();
        bagAddItemTab(G.itemBagPtrs, tab, mode);
    }

    private static final byte[] TMP_IDX = new byte[1];

    /**
     * ItemBag_removeCurItem(_, cnt, idxRef) @0x83dd8: takes 'cnt' off the item at list index ref[off] (s8) (removed and QuestChecked when its
     * count reaches 0); the +0x172 tab position of the following items is decremented, and when the last index was removed the index
     * byte is stepped back (ref[off] is the in/out byte the original receives a pointer to).  False when there is no such item.
     */
    public static boolean bagRemoveCurItem(int cnt, byte[] ref, int off) {
        LList slot = bagGetSlot();
        Obj cur = bagGetCur(ref[off]);
        if (cur == null) {
            return false;
        }
        int r6 = slot.size - 1;
        boolean removed = false;
        if (addCnt(cur, (byte) -cnt) <= 0) {
            removed = true;
            Quest.check(cur.m[0x20], (byte) cur.m[0x22], -cnt);
            slot.remove(cur);
        }
        if (r6 > ref[off]) {
            int r4 = (ref[off] + 1) & 0xff;
            if (r6 > (byte) r4) {
                int r7 = (byte) ((ref[off] & 0xff) + 2) - 1;
                int r5 = 0;
                do {
                    Obj o = bagGetCur((byte) r4);
                    int v = o.m[0x172] & 0xff;
                    r4 = (r4 + 1) & 0xff;
                    r5++;
                    if ((byte) v > 0) {
                        o.m[0x172] = (byte) (v - 1);
                    }
                } while (r6 > r7 + r5);
            }
        }
        if (removed && r6 == ref[off]) {
            if (slot.size <= 0) {
                ref[off] = 0;
            } else {
                ref[off] = (byte) (ref[off] - 1);
            }
        }
        return true;
    }

    /** ItemBag_AllClean(type) @0x83f1c: removes every bag item of that type (whole stacks) */
    public static void bagAllClean(int type) {
        byte[] t = TMP_IDX;
        t[0] = 0;
        for (;;) {
            if ((t[0] & 0xff) >= bagGetSlot().size) {
                return;
            }
            Obj o = bagGetCur(t[0]);
            if (o != null && o.m[0x20] == type) {
                bagRemoveCurItem(o.m[0x25], t, 0);
            }
            t[0]++;
        }
    }

    /** ItemBag_removeCurItem_2(cnt, idxRef) @0x83f90: removeCurItem on the tab-list entry ref[off], then rebuilds the tab list of the current tab */
    public static void bagRemoveCurItem2(int cnt, byte[] ref, int off) {
        Obj it = bagGetTabItem(ref[off]);
        bagRemoveCurItem(cnt, it.m, 0x172);
        inventoryTabSort((byte) (G.itemBagTab - 1), 0);
    }

    /** ItemBag_removeItem(cnt, idxRef) @0x83fd8: removeCurItem (plain bag) or removeCurItem_2 (tab view) depending on _itemBag+1 */
    public static void bagRemoveItem(int cnt, byte[] ref, int off) {
        if (G.itemBagTab == 0) {
            bagRemoveCurItem(cnt, ref, off);
        } else {
            bagRemoveCurItem2(cnt, ref, off);
        }
    }

    /** ItemBag_setItemDN(donor, type, idx, cnt) @0x85dbc = checkAndSetItemDN(donor, type, idx, cnt, 1, 0, 0, 0, -1, 0) */
    public static boolean setItemDn(Obj donor, int type, int idx, int cnt) {
        return checkAndSetItemDn(donor, type, idx, cnt, 1, 0, 0, 0, -1, 0);
    }

    /** ItemBag_setItemOptAdd(_, type, idx, cnt, a, b, c, d) @0x85ca4 = checkAndSetItem with writes on and the four s8 option bonuses */
    public static boolean setItemOptAdd(int type, int idx, int cnt, int a, int b, int c, int d) {
        return checkAndSetItem(type, idx, cnt, 1, (byte) a, (byte) b, (byte) c, (byte) d);
    }
}
