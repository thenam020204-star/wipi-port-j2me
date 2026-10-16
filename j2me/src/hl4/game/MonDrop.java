package hl4.game;

import hl4.sys.Bytes;
import hl4.sys.Rng;

/**
 * The item a killed monster leaves behind: MONSTER_dropItem @0xa52d0, DropItem_drop @0xd150c, DropItem_init @0xd1490, Item_getIconNameGrade @0x84b30,
 * Item_setDataSKinIdx @0x84adc, Item_skinDataName @0x848cc.  Written from the disassembly (the pseudo-C of the 6-slot switch was checked against it).
 */
public final class MonDrop {
    private MonDrop() {
    }

    private static void later(String what) {
        throw new IllegalStateException(what + ": later");
    }

    /** C.18.17254 @0xd8da1: 9 rows (monster level / 10 - 1, clamped to 0..8) x 6 item kinds -> the item index of the equipment drops (-1: none) */
    private static final byte[] KIND = {-1, -1, -1, -1, -1, -1, -1, -1, -1, 20, -1, -1, -1, 20, -1, 20, -1, -1, 26, 20, -1, 21, -1, -1, -1, 21, -1, 21, -1, -1, 27, 22, 28, 22,
            -1, -1, 27, 23, 28, 23, -1, 24, 27, 24, 29, 23, -1, 25, 27, 25, 29, 23, -1, 25};

    private static final int[] RECT = new int[4];

    /** the three bytes at sp+0x7d..0x7f (-1 unless a case sets them) */
    private static int p5;
    private static int p6;
    private static int p7;

    private static byte[] itemTable(int type) {
        if (type >= 0 && type < 14 && G.itemData[type] != null) {
            return G.itemData[type];
        }
        return Items.getId(type);
    }

    /** Item_getIconNameGrade(type, idx, nameOut, nameOutOff, iconOut(obj, offset), copyName): the grade byte of the item (-1 without a data table) */
    private static int getIconNameGrade(int type, int idx, byte[] o, int nameOff, int iconOff) {
        byte[] d = itemTable(type);
        if (d == null) {
            return -1;
        }
        int off = Items.getOffset(d, (short) idx);
        int len = (short) d[off + 1];
        int grade = d[off];
        Bytes.copy(d, off + 2, o, nameOff, len);
        if (type <= 7 && o[iconOff] == -1) {
            o[iconOff] = d[off + 6 + len];
        }
        return grade;
    }

    /** Item_setDataSKinIdx(type, idx) */
    private static int skinIdx(int type, int idx) {
        byte[] d = itemTable(type);
        if (d == null) {
            return -1;
        }
        int off = Items.getOffset(d, (short) idx);
        return d[off + 7 + d[off + 1]];
    }

    /** Item_skinDataName(dest, destOff, type, idx): the skin name of the item's skin record */
    private static void skinName(byte[] dst, int dOff, int type, int idx) {
        byte[] t = type >= 0 && type < 7 ? G.itemSd[type] : null;
        if (t == null) {
            return;
        }
        int off = Items.getOffset(t, (short) idx);
        int len = t[off];
        int n = 0;
        while (n < 0x1b && dst[dOff + n] != 0) {
            n++;
        }
        Bytes.fill(dst, dOff, 0, n);
        if (len > 0) {
            Bytes.copy(t, off + 1, dst, dOff, len);
        }
    }

    /** DropItem_drop(x, y, type, idx, p5, p6, p7, p8) */
    static void drop(int x, int y, int type, int idx, int a5, int a6, int a7, int a8) {
        if (idx < 0) {
            return;
        }
        Obj o = Pool.getNew(Pool.DROP);
        if (o == null) {
            return;
        }
        byte[] m = o.m;
        Bytes.w32(m, 8, x);                                     // DropItem_init
        Bytes.w32(m, 0xc, y - 8);
        m[0x24] = (byte) type;
        Bytes.w16(m, 0x22, idx);
        m[0x25] = 0;
        m[0x26] = (byte) a5;
        m[0x27] = (byte) a6;
        m[0x28] = (byte) a7;
        m[0x29] = (byte) a8;
        Bytes.w16(m, 0x20, 0);
        Bytes.fill(m, 0x45, 0, 0x1b);
        Bytes.fill(m, 0x2a, 0, 0x1b);
        m[0x25] = (byte) getIconNameGrade(type, idx, m, 0x45, 0x29);
        if ((type & 0xff) <= 6) {
            skinName(m, 0x2a, type, skinIdx(type, idx));
        } else {
            Bytes.fill(m, 0x2a, 0, 0x1b);
        }
        G.dropedItem.addObject(o);
    }

    /** the magnet rule of the hero (+0x180 / +0x184 = a 64-bit amount): a positive amount makes the item land on the hero and be picked up at once */
    private static boolean heroMagnet() {
        byte[] h = Pool.hero.m;
        int hi = Bytes.s32(h, 0x184);
        if (hi > 0) {
            return true;
        }
        return hi == 0 && Bytes.s32(h, 0x180) != 0;
    }

    private static void landOrPick(int x, int y, int type, int idx, int sel) {
        if (heroMagnet()) {
            Char h = Pool.hero;
            drop(h.x(), h.y(), type, idx, p5, p6, p7, sel);
            later("ItemBag_pickUp (item dropped onto the hero)");
        } else {
            drop(x, y, type, idx, p5, p6, p7, sel);
        }
    }

    /** MONSTER_dropItem(o) */
    static void dropItem(Char o) {
        byte[] m = o.m;
        p5 = -1;
        p6 = -1;
        p7 = -1;
        int x = o.x();
        int y = o.y() - 3;
        int roll = Rng.rand(0, 0xffff);
        int qid = m[0x1e2];
        // the quest item of a kill quest (record type 0xc whose item index is this monster's +0x1e2)
        if (qid >= 0 && roll < 0x8000) {
            byte[] q = G.questMenu;
            int count = q[0x210c];
            for (int k = 0; k < count; k++) {
                int b = k * Quest.REC;
                int ty = q[b + 0x101];
                if (ty == 0xc && qid == (q[b + 0x102] & 0xff)) {
                    if (Coll.nRect(x - 2, y - 2, 4, 4)) {
                        if (heroMagnet()) {
                            Char h = Pool.hero;
                            drop(h.x(), h.y(), 0xc, (short) qid, p5, p6, p7, -1);
                            later("ItemBag_pickUp (quest item dropped onto the hero)");
                        } else {
                            drop(x, y, 0xc, (short) qid, p5, p6, p7, -1);
                        }
                        break;
                    }
                }
            }
        }
        int lvl = m[0x94];
        int row = lvl / 10 - 1;
        row = (byte) row;
        row = row < 0 ? 0 : (row > 8 ? 8 : row);
        for (int i = 0; i < 6; i++) {
            p5 = -1;
            p6 = -1;
            p7 = -1;
            int hiT = Bytes.u16(m, 0x1d4 + 2 * i);
            boolean in = i == 0 || roll > Bytes.u16(m, 0x1d2 + 2 * i);
            if (roll > hiT) {
                in = false;
            }
            if (!in) {
                continue;
            }
            int type = 0;
            int idx = 0;
            int sel = 0;
            boolean skipA = false;                              // piVar10: the kind roll was 0xff
            boolean skipB = false;                              // piVar12: the item index is -1
            switch (i) {
                case 0:
                    type = 9;
                    idx = m[0x1d2] & 0xff;
                    sel = 0;
                    skipB = idx == 0xff;
                    break;
                case 1: {
                    int r = Rng.rand(m[0x1e0], m[0x1e1]) & 0xff;
                    if (r <= 6) {
                        int c = ((lvl / 5) & 0xff) + Rng.rand(0, 1);
                        c = (byte) c;
                        p5 = 0;
                        p6 = 0;
                        p7 = 0;
                        idx = c <= 0 ? 1 : (c > 0x13 ? 0x13 : c);
                        sel = 0;
                        type = r;
                        skipA = r == 0xff;
                    } else if (r == 7) {
                        idx = Rng.rand(0, 1) & 0xff;
                        type = r;
                        sel = 0;
                        skipB = idx == 0xff;
                    } else {
                        continue;
                    }
                    break;
                }
                case 2: {
                    int r = Rng.rand(m[0x1e0], m[0x1e1]) & 0xff;
                    if (r <= 6) {
                        int c = ((lvl / 5) & 0xff) + Rng.rand(0, 1);
                        c = (byte) c;
                        p5 = Rng.rand(m[0x1e3], m[0x1e5]);
                        int rr = (byte) Rng.rand(0, 99);
                        p6 = rr;
                        p7 = 0;
                        if (m[0x1e4] > rr) {
                            p6 = 0;
                            sel = 1;
                        } else if (m[0x1e6] >= rr) {
                            sel = 2;
                        } else {
                            p6 = 0;
                            sel = 1;
                        }
                        if ((byte) p5 != 0) {
                            if ((byte) p6 == 0) {
                                sel = 1;
                            }
                        } else {
                            sel = (byte) p6 != 0 ? 1 : 0;
                        }
                        idx = c <= 0 ? 1 : (c > 0x13 ? 0x13 : c);
                        type = r;
                        skipA = r == 0xff;
                    } else if (r == 7) {
                        idx = Rng.rand(2, 9) & 0xff;
                        type = r;
                        sel = 1;
                        skipB = idx == 0xff;
                    } else {
                        continue;
                    }
                    break;
                }
                case 3:
                case 4:
                case 5: {
                    int r = Rng.rand(m[0x1e0], m[0x1e1]) & 0xff;
                    if (r > 6) {
                        if (r != 7) {
                            continue;
                        }
                        int lo = i == 5 ? 0x14 : 10;
                        int hi = i == 5 ? 0x17 : 0x13;
                        idx = Rng.rand(lo, hi) & 0xff;
                        type = 7;
                        sel = i == 5 ? 3 : 2;
                        skipB = idx == 0xff;
                        break;
                    }
                    int col = i == 3 ? (r > 3 ? 3 : 0) : (i == 4 ? (r > 3 ? 4 : 1) : (r > 3 ? 5 : 2));
                    idx = KIND[row * 6 + col] & 0xff;
                    if (i == 3) {
                        p5 = 0;
                        p6 = Rng.rand(m[0x1e3], m[0x1e5]);
                        p7 = Rng.rand(m[0x1e4], m[0x1e6]);
                        sel = 3;
                    } else if (i == 4) {
                        p5 = Rng.rand(m[0x1e3], m[0x1e5]);
                        p6 = Rng.rand(m[0x1e4], m[0x1e6]);
                        p7 = 0;
                        sel = 0xff;
                    } else {
                        p5 = 0;
                        p6 = 0;
                        p7 = 0;
                        sel = 0xff;
                    }
                    type = r;
                    skipA = r == 0xff;
                    skipB = idx == 0xff;
                    break;
                }
                default:
                    break;
            }
            if (skipA || skipB) {
                continue;
            }
            y += 6;
            if (Coll.nRect(x - 2, y - 2, 4, 4)) {
                landOrPick(x, y, (byte) type, (byte) idx, (byte) sel);
                return;
            }
        }
    }
}
