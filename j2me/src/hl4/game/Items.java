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

    /** Item_getItemCnt(type, idx) @0x9361c: how many of the item the bag holds (-1 when type or idx is -1) */
    public static int getItemCnt(int type, int idx) {
        if (type == -1 || idx == -1) {
            return -1;
        }
        int n = 0;
        for (Obj p = G.itemBag.head; p != null; p = p.next) {
            if (getType(p) == type && getIdx(p) == idx) {
                n += getCnt(p);
            }
        }
        return n;
    }

    public static boolean isCountable(int type) {
        return ((type - 8) & 0xff) < 6;
    }

    public static boolean isEquipSetSlot(Obj it) {
        return it != null && (it.m[0x24] & 0xff) < 7;
    }

    public static int getCnt(Obj it) {
        return it.m[0x25];
    }

    public static int getIdx(Obj it) {
        return Bytes.s16(it.m, 0x22);
    }

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

    /** Item_createItem(a, type, idx, cnt) = Item_createItem_Base(8, ...) */
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
            it.m[0x25] = (byte) cnt;
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
}
