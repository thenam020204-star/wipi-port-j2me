package hl4.game;

import hl4.gfx.Hf;
import hl4.sys.Bytes;

/**
 * Picked-up item notices (PickUpItem_* / MP_initPickUpItem @0x9dba8): pool type 0xe, 5 slots of 0x48 bytes, kept in the list at _game+0x48
 * (G.pickUps).  Slot: +8 x (slides in from the right edge), +0xc y, +0x20 counter, +0x21 phase (2 slide in, 1 hold, 0 slide out), +0x22 / +0x23
 * width (text + 0x14) and the part still to slide, +0x24 text[0x20], +0x44 grade (name colour).  Also ItemBag_pickUp @0x99900 (OK key on items).
 */
public final class PickUp {
    private PickUp() {
    }

    /** ITEMNAME_COLOR @0xd9ab0 (rodata): r, g, b per grade 0..5 */
    private static final int[] NAME_RGB = {255, 255, 255, 255, 216, 0, 0, 176, 255, 181, 85, 255, 255, 33, 42, 62, 255, 36};
    /** "슬롯부족" @0xd8940 */
    private static final byte[] NO_SLOT = {-67, -67, -73, -44, -70, -50, -63, -73, 0, 0};

    private static final byte[] NAME = new byte[100];          // ItemBag_pickUp's sp+0x14 buffer
    private static final int[] RECT = new int[4];
    /** the notice text copied out of its slot (+0x24, NUL padded to the 100 bytes the original measures and draws) */
    private static final byte[] TXT = new byte[100];

    private static byte[] text(byte[] m) {
        int n = 0;
        for (int i = 0x24; i < m.length && m[i] != 0; i++) {
            TXT[n++] = m[i];
        }
        while (n < TXT.length) {
            TXT[n++] = 0;
        }
        return TXT;
    }

    /** MP_initPickUpItem(k): OBJ header reset, x = y = 0, state bytes +0x20/+0x21 = 0, text buffer +0x24..+0x43 cleared */
    public static void initSlot(int k) {
        Obj o = Pool.pools[Pool.PICKUP][k];
        o.objSet(1);
        Bytes.w32(o.m, Obj.X, 0);
        Bytes.w32(o.m, Obj.Y, 0);
        o.m[0x21] = 0;
        o.m[0x20] = 0;
        Bytes.fill(o.m, 0x24, 0, 0x20);
    }

    /** PickUpItem_init @0x99b90 */
    public static void init() {
        for (int k = 0; k < 5; k++) {
            initSlot(k);
        }
    }

    /** strcpy-like append used by the MC_knlSprintk formats below; returns the new end (writing past 'max' would overrun the struct) */
    private static int put(byte[] d, int at, byte[] s, int off, int max) {
        int i = off;
        while (i < s.length && s[i] != 0) {
            if (at >= max) {
                throw new IllegalStateException("MC_knlSprintk overruns the buffer (the original writes past it): later");
            }
            d[at++] = s[i++];
        }
        return at;
    }

    private static int putChar(byte[] d, int at, int c, int max) {
        if (at >= max) {
            throw new IllegalStateException("MC_knlSprintk overruns the buffer (the original writes past it): later");
        }
        d[at] = (byte) c;
        return at + 1;
    }

    private static int putInt(byte[] d, int at, int v, int max) {
        if (v < 0) {
            at = putChar(d, at, '-', max);
            v = -v;
        }
        int div = 1;
        while (v / div >= 10) {
            div *= 10;
        }
        for (; div > 0; div /= 10) {
            at = putChar(d, at, '0' + v / div % 10, max);
        }
        return at;
    }

    /** PickUpItem_addInfo(p1, grade, text, have, need) @0x997cc: a new notice ("%s" or "%s(%d/%d)" when both counts are positive) */
    static void addInfo(int p1, int grade, byte[] text, int have, int need) {
        need = (byte) need;
        LList l = G.pickUps;
        if (l.size > 3) {
            Obj h = l.head;
            if (h.m[0x21] == 1) {
                h.m[0x20] = 0;
                h.m[0x21] = 0;
            }
        }
        Obj o = Pool.getNew(Pool.PICKUP);
        if (o == null) {
            return;
        }
        byte[] m = o.m;
        m[0x21] = 2;
        m[0x44] = (byte) grade;
        m[0x20] = 0;
        Bytes.w32(m, 8, 0);
        Bytes.w32(m, 0xc, 0);
        Bytes.fill(m, 0x24, 0, 0x20);
        int e = put(m, 0x24, text, 0, 0x44);
        if (need > 0 && have > 0) {
            e = putChar(m, e, '(', 0x44);
            e = putInt(m, e, have, 0x44);
            e = putChar(m, e, '/', 0x44);
            e = putInt(m, e, need, 0x44);
            e = putChar(m, e, ')', 0x44);
        }
        if (e < 0x44) {
            m[e] = 0;
        }
        int w = (Hf.width(text(m), 100, 1) + 0x14) & 0xff;
        m[0x23] = (byte) w;
        m[0x22] = (byte) w;
        l.addObject(o);
    }

    /** PickUpItem_endProcess @0x99690: 2 -> 1 after 6 ticks, 1 -> 0 after 0x32, removed after 5 ticks of phase 0 */
    static void endProcess() {
        LList l = G.pickUps;
        Obj p = l.head;
        while (p != null) {
            byte[] m = p.m;
            int ph = m[0x21];
            m[0x20]++;
            int c = m[0x20];
            if (ph == 2) {
                if (c > 5) {
                    m[0x20] = 0;
                    m[0x21] = 1;
                }
            } else if (ph == 1) {
                if (c > 0x31) {
                    m[0x20] = 0;
                    m[0x21] = 0;
                }
            } else if (ph == 0 && c > 4) {
                l.remove(p);                                 // the original then reads the removed node's next link
                G.questMenu[0x210d] = -1;
            }
            p = p.next;
        }
    }

    /** PickUpItem_drawItems @0x99af0 (portrait): the notices stacked 16 px apart above y = H - 0x28 */
    static void drawItems() {
        LList l = G.pickUps;
        if (l.size <= 0 || l.head == null) {
            return;
        }
        int y = 0x10 - (l.size << 4) + Glob.gameH - 0x28;
        for (Obj p = l.head; p != null; p = p.next) {
            draw(p, Glob.gameW, y);
            y += 0x10;
        }
    }

    /** PickUpItem_draw(o, x0, y0) @0xe0c60 (portrait): slide by width / 5 per frame, dark box (alpha 0x80) and the name right-aligned */
    private static void draw(Obj o, int x0, int y0) {
        byte[] m = o.m;
        int w = m[0x22];
        int d = (byte) (w / 5);
        int ph = m[0x21];
        int x = Bytes.s32(m, 8);
        if (ph == 2) {
            if (x >= Glob.gameW - (w - d)) {
                x -= d;
                Bytes.w32(m, 8, x);
                m[0x23] = (byte) (m[0x23] - d);
                if (m[0x23] < 0) {
                    x += m[0x23];
                    Bytes.w32(m, 8, x);
                }
            }
        } else if (ph == 0) {
            if (x <= Glob.gameW - d) {
                x += d;
                Bytes.w32(m, 8, x);
            }
        }
        int y = Bytes.s32(m, 0xc);
        Gfx.fillRectAlpha(Gfx.scr, x - m[0x22] + x0, y - 3 + y0, m[0x22], 0x10, 0x80, 0xb, 0xb, 0xb);
        int gr = m[0x44];
        if (gr == 0) {
            Hf.setColor(0xff, 0xff, 0xff);
        } else {
            if (gr < 0) {
                throw new IllegalStateException("PickUpItem_draw: negative grade reads before ITEMNAME_COLOR (undefined)");
            }
            int k = gr > 4 ? 5 : gr;
            Hf.setColor(NAME_RGB[k * 3], NAME_RGB[k * 3 + 1], NAME_RGB[k * 3 + 2]);
        }
        Hf.drawAnchorH(Gfx.scr, x0 + Bytes.s32(m, 8), y0 + y, text(m), 100, 0, 2);
    }

    /** isItemPlaced(rect) @0x99428: the first dropped item whose 16 x 16 box at (+8, +0xc) overlaps the rectangle */
    private static Obj isItemPlaced(int x, int y, int w, int h) {
        if (G.dropedItem.isEmpty()) {
            return null;
        }
        for (Obj p = G.dropedItem.head; p != null; p = p.next) {
            if (Um.collision(Bytes.s32(p.m, 8), Bytes.s32(p.m, 0xc), 0x10, 0x10, x, y, w, h, RECT) != 0) {
                return p;
            }
        }
        return null;
    }

    /** pickUp_DropitemName(item, out): "%s %s" (name +0x45, skin name +0x2a) for types <= 6, else "%s" */
    private static void dropItemName(Obj it, byte[] out) {
        byte[] m = it.m;
        int e = put(out, 0, m, 0x45, out.length - 1);
        if (m[0x24] <= 6) {
            e = putChar(out, e, ' ', out.length - 1);
            e = put(out, e, m, 0x2a, out.length - 1);
        }
        out[e] = 0;
    }

    /** ItemBag_pickUp(rect) @0x99900 (rect = the hero's move rectangle at hero+0x148): every dropped item under it goes into the bag */
    static boolean bagPickUp() {
        byte[] hm = Pool.hero.m;
        int rx = Bytes.s32(hm, 0x148);
        int ry = Bytes.s32(hm, 0x14c);
        int rw = Bytes.s32(hm, 0x150);
        int rh = Bytes.s32(hm, 0x154);
        Obj it = isItemPlaced(rx, ry, rw, rh);
        if (it == null) {
            return false;
        }
        Bytes.fill(NAME, 0, 0, 100);
        do {
            byte[] m = it.m;
            if (!Items.checkAndSetItem(m[0x24], Bytes.s16(m, 0x22), 1, 1, m[0x26], m[0x27], m[0x28], m[0x29])) {
                addInfo(1, 0, NO_SLOT, 0, 0);
                return false;
            }
            byte[] q = G.questMenu;
            int count = q[0x210c];
            boolean quest = false;
            for (int k = 0; k < count; k++) {
                int b = k * Quest.REC;
                int ty = q[b + 0x101];
                if (ty <= 0xc && m[0x24] == ty && Bytes.s16(m, 0x22) == (q[b + 0x102] & 0xff)) {
                    Bytes.fill(NAME, 0, 0, Strs.len(NAME));
                    dropItemName(it, NAME);
                    addInfo(0, 0, NAME, q[b + 0x119], q[b + 0x103]);
                    quest = true;
                    break;
                }
            }
            if (!quest) {
                Bytes.fill(NAME, 0, 0, Strs.len(NAME));
                dropItemName(it, NAME);
                addInfo(0, m[0x29], NAME, 0, 0);
            }
            G.dropedItem.remove(it);
            it = isItemPlaced(rx, ry, rw, rh);
        } while (it != null);
        return true;
    }
}
