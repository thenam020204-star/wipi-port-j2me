import org.kwis.msp.lcdui.Graphics;
import org.kwis.msp.lcdui.Image;

import wipi.host.Txt;

/**
 * Game menu (tabs 0-4: info, seeds, bag, quests, system) and the shops /
 * pickers (5 buy, 6 sell, 7 bait, 8 skills, 9 pets, 11 exhibit, 12 feed) of
 * the new UI, drawn live every frame in a full-screen panel sized to the
 * screen. The 176x202 GMBuff the game still allocates and paints into is
 * never shown (tools/ui_hooks.txt makes it 1x1).
 *
 * State read here is the game's own: GM[0] screen, GM[1] focus level (0 =
 * tab bar), GM[2] cursor, GM[3] scroll row, GM[4] shop id. Key handling
 * stays in the original GameMenuKey/SeedKey/InvenKey; the only logic copied
 * (DrawGameMenu, the lotto part of DrawPoint, MakeQuestExplan, LoadPetInfo)
 * is marked.
 */
final class UiGM {
    static final String[] TAB = { "Thông tin", "Hạt giống", "Túi đồ", "Nhiệm vụ",
            "Hệ thống" };
    static final String[] REGION = { "Đồng bằng", "Ven biển", "Vùng núi", "Mọi vùng" };
    static final String COIN = " xu";

    // layout of the current frame
    static int px, py, pw, ph, cx, cy, cw, ch, cell, gx, gy, ix, iy, iw, ih, fy;

    static void layout(GameCanvas gc) {
        int W = gc.getWidth(), H = gc.getHeight();
        px = 2;
        py = 2;
        pw = W - 4;
        ph = H - 4;
        int lh = UiKit.lh();
        fy = py + ph - (Txt.height() + 8) - 4; // footer (money / keys)
        cx = px + 7;
        cw = pw - 14;
        cy = py + 6 + 26; // below the tab strip / title ribbon
        ch = fy - 4 - cy;
        // 8 x 5 grid; info box below it (2 text lines)
        int infoH = 2 * lh + 10;
        cell = (cw - 14) / 8;
        int byH = (ch - infoH - 22) / 5;
        if (byH < cell) {
            cell = byH;
        }
        if (cell > 28) {
            cell = 28;
        }
        if (cell < 17) {
            cell = 17;
        }
        gx = cx + (cw - 10 - 8 * cell) / 2;
        gy = cy + 18;
        ix = cx;
        iw = cw;
        iy = gy + 5 * cell + 6;
        ih = fy - 4 - iy;
        if (ih > infoH + 8) {
            ih = infoH + 8;
        }
    }

    // ------------------------------------------------------------ replaced methods

    /** Make* builders of the 176x202 buffer: the new UI draws live, nothing to build. */
    static void MakeGameMenu(GameCanvas gc, Graphics g) {
    }

    static void MakeShopMenu(GameCanvas gc, Graphics g, int n) {
    }

    static void MakeSkillShop(GameCanvas gc, Graphics g) {
    }

    static void MakePetShop(GameCanvas gc, Graphics g) {
    }

    /** 1x1 stand-in for the never-shown 176x202 menu buffers (saves ~140 KB of heap). */
    static Image buf(int w, int h) {
        return Image.createImage(1, 1);
    }

    /** GameCanvas.MakeSubBuf (logic copied: GMBuff/gmg/w2/h2; the frame is drawn by the new windows). */
    static void MakeSubBuf(GameCanvas gc) {
        gc.GMBuff = Image.createImage(1, 1);
        gc.gmg = gc.GMBuff.getGraphics();
        gc.w2 = (gc.getWidth() - 176) / 2;
        gc.h2 = (gc.getHeight() - 202) / 2;
    }

    static String qName = "", qText = "";

    /** GameCanvas.MakeQuestExplan (logic copied): the hint text of the quest, shown by DrawGameMenu. */
    static void MakeQuestExplan(GameCanvas gc) {
        int who = Integer.parseInt(gc.m.QExplan.substring(0, 2));
        gc.FBuff = Image.createImage(1, 1);
        gc.fg = gc.FBuff.getGraphics();
        qText = gc.m.QExplan.substring(2, gc.m.QExplan.length());
        qName = who >= 0 ? gc.n.name[who] : "로이";
        gc.QExplan = true;
    }

    static int petInfo;

    /** GameCanvas.LoadPetInfo (logic copied): PBuff stands for "pet info open", drawn by petInfo(). */
    static void LoadPetInfo(GameCanvas gc, int n) {
        gc.PBuff = Image.createImage(1, 1);
        gc.pg = gc.PBuff.getGraphics();
        petInfo = n;
        gc.PInfo = 1;
    }

    /** GameCanvas.DrawGameMenu (logic copied; the buffer blit replaced by the live panel). */
    static void DrawGameMenu(GameCanvas gc, Graphics g) {
        if (gc.OPTION) {
            gc.OptKey();
        } else if (gc.EXPLAN) {
            gc.ExplanKey();
        } else if (gc.LOAD) {
            gc.LoadKey();
            gc.tani[8] = (gc.tani[8] + 1) % 4;
            gc.KeyCode = -999;
            if (gc.State == 5) {
                return;
            }
        }
        if (!(gc.OPTION || gc.EXPLAN || gc.LOAD)) {
            frame(gc, g);
            if (gc.GM[0] == 10) {
                g.drawImage(gc.FBuff, gc.w2, gc.h2, 4);
            }
            if (gc.State == 10) {
                gc.DrawPoint(g);
            }
            if (gc.QExplan) {
                questHint(gc, g);
            }
        }
        if (!gc.OPTION) {
            if ((gc.Tou == 0 || gc.Tou == 3) && gc.c.movex == 0 && gc.c.movey == 0 && gc.fade[0] == -1) {
                gc.n.AniPlay();
            }
            if (gc.State != 9 && gc.KeyCode != -999) {
                if (gc.QExplan) {
                    gc.QExplan = false;
                    gc.FBuff = null;
                    gc.fg = null;
                    gc.KeyCode = -999;
                } else {
                    gc.GameMenuKey();
                }
            }
        }
    }

    // ------------------------------------------------------------ frame: panel, tabs, footer

    static void frame(GameCanvas gc, Graphics g) {
        layout(gc);
        int k = gc.GM[0];
        UiKit.panel(g, px, py, pw, ph);
        if (k <= 4) {
            tabs(gc, g);
        } else if (k == 5 || k == 6) {
            if (gc.GM[4] < 7) {
                buySell(gc, g);
            } else {
                UiKit.ribbon(g, px + 3, py + 3, pw - 6, gc.GM[4] == 7 ? "Cửa hàng mồi" : "Chợ nông sản");
            }
        } else {
            UiKit.ribbon(g, px + 3, py + 3, pw - 6, title(k));
        }
        if (k != 10) {
            footer(gc, g);
        }
    }

    static String title(int k) {
        switch (k) {
        case 7:
            return "Chọn mồi câu";
        case 8:
            return "Học kỹ năng";
        case 9:
            return "Cửa hàng thú cưng";
        case 10:
            return "Vé số nông trại";
        case 11:
            return "Chọn nông sản dự thi";
        case 12:
            return "Chọn thức ăn";
        default:
            return "";
        }
    }

    /** Tab strip of the game menu: five icon tabs, the open one carries its name. */
    /** Set by tabs(): the open tab's label did not fit (narrow screens). */
    static boolean narrow;

    static void tabs(GameCanvas gc, Graphics g) {
        javax.microedition.lcdui.Graphics m = g.g;
        int k = gc.GM[0];
        int x = px + 5, y = py + 5, w = pw - 10, h = 24;
        String name = TAB[k];
        int iconW = 22;
        int openW = iconW + Txt.width(name) + 10;
        int other = (w - openW - 4 * 2) / 4;
        narrow = other < iconW + 2;
        if (narrow) { // label does not fit: icon-only tabs, the name goes to the footer
            other = iconW + 2;
            openW = w - 4 * (other + 2);
        }
        int tx = x;
        for (int i = 0; i < 5; i++) {
            int tw = i == k ? openW : other;
            boolean open = i == k;
            if (open) {
                boolean focus = gc.GM[1] == 0;
                m.setColor(focus ? UiKit.SEL_D : UiKit.ACC_D);
                m.fillRoundRect(tx, y, tw, h, 10, 10);
                m.setColor(focus ? UiKit.SEL : UiKit.ACC);
                m.fillRoundRect(tx + 1, y + 1, tw - 2, h - 3, 9, 9);
            } else {
                m.setColor(UiKit.LINE);
                m.fillRoundRect(tx, y + 2, tw, h - 2, 10, 10);
                m.setColor(UiKit.BG2);
                m.fillRoundRect(tx + 1, y + 3, tw - 2, h - 5, 9, 9);
            }
            Image ic = gc.im.GM != null && gc.im.GM.length > 5 + i ? gc.im.GM[5 + i] : null;
            int icx = open && !narrow ? tx + 4 : tx + (tw - (ic != null ? ic.getWidth() : 0)) / 2;
            if (ic != null) {
                g.drawImage(ic, icx, y + (h - ic.getHeight()) / 2, 4);
            }
            if (open && !narrow) {
                UiKit.text(g, name, tx + iconW + 4, y + (h - 1 - Txt.height()) / 2,
                        gc.GM[1] == 0 ? UiKit.DARK : UiKit.WHITE, UiKit.LEFT);
            }
            tx += tw + 2;
        }
        // underline joining the open tab to the content
        m.setColor(UiKit.LINE);
        m.fillRect(px + 5, y + h, pw - 10, 2);
    }

    /** Buy / sell tabs of the seed shops (GM[0] 5/6; GM[1] == 0: tab focus). */
    static void buySell(GameCanvas gc, Graphics g) {
        javax.microedition.lcdui.Graphics m = g.g;
        int y = py + 5, h = 24, w = (pw - 14) / 2;
        for (int i = 0; i < 2; i++) {
            int x = px + 5 + i * (w + 4);
            boolean open = gc.GM[0] - 5 == i;
            boolean focus = open && gc.GM[1] == 0;
            if (open) {
                m.setColor(focus ? UiKit.SEL_D : UiKit.ACC_D);
                m.fillRoundRect(x, y, w, h, 10, 10);
                m.setColor(focus ? UiKit.SEL : UiKit.ACC);
                m.fillRoundRect(x + 1, y + 1, w - 2, h - 3, 9, 9);
            } else {
                m.setColor(UiKit.LINE);
                m.fillRoundRect(x, y + 2, w, h - 2, 10, 10);
                m.setColor(UiKit.BG2);
                m.fillRoundRect(x + 1, y + 3, w - 2, h - 5, 9, 9);
            }
            UiKit.text(g, i == 0 ? "Mua" : "Bán", x + w / 2, y + (h - 1 - Txt.height()) / 2,
                    open ? (focus ? UiKit.DARK : UiKit.WHITE) : UiKit.SUB, UiKit.HCENTER);
        }
        m.setColor(UiKit.LINE);
        m.fillRect(px + 5, y + h, pw - 10, 2);
    }

    /** Money and key hints at the bottom. */
    static void footer(GameCanvas gc, Graphics g) {
        javax.microedition.lcdui.Graphics m = g.g;
        int h = Txt.height() + 6;
        int y = fy + 2;
        String hint = hint(gc);
        int mw = UiKit.moneyW(gc.dat3[0]) + Txt.width(COIN) + 22;
        int x = px + pw - 6 - mw;
        m.setColor(UiKit.DARK);
        m.fillRoundRect(x, y, mw, h, h, h);
        m.setColor(0xFFE27A);
        m.fillArc(x + 4, y + (h - 10) / 2, 10, 10, 0, 360);
        m.setColor(UiKit.SEL_D);
        m.drawArc(x + 4, y + (h - 10) / 2, 10, 10, 0, 360);
        int nx = x + 17;
        nx += UiKit.money(g, gc.dat3[0], nx, y + 3, UiKit.WHITE);
        UiKit.text(g, COIN, nx, y + 3, UiKit.WHITE, UiKit.LEFT);
        if (hint != null && Txt.width(hint) < x - px - 12) {
            UiKit.text(g, hint, px + 9, y + 3, UiKit.SUB, UiKit.LEFT);
        }
    }

    static String hint(GameCanvas gc) {
        int k = gc.GM[0];
        if (k <= 4 && gc.GM[1] == 0) {
            return narrow ? TAB[k] : "1/3: đổi thẻ";
        }
        switch (k) {
        case 1:
            return "OK: dùng  0: bỏ";
        case 2:
            return "OK: dùng  0: bỏ";
        case 5:
            return "OK: mua";
        case 6:
            return "OK: bán";
        case 8:
        case 9:
            return "OK: mua";
        default:
            return "OK: chọn";
        }
    }

    // ------------------------------------------------------------ helpers

    static Image itemIcon(GameCanvas gc, int v) {
        if (v >= 100) {
            return gc.im.Item[19 + v - 100];
        }
        if (v >= 80) {
            return gc.im.Item1[v - 80];
        }
        if (v >= 60) {
            return gc.im.Item[(v - 60) / 5];
        }
        return gc.im.Crop[v];
    }

    static String itemName(GameCanvas gc, int v) {
        if (v >= 100) {
            return gc.Milk[v - 100];
        }
        if (v >= 80) {
            return gc.ItemName[v - 75];
        }
        if (v == 75) {
            return gc.ItemName[0];
        }
        if (v >= 60) {
            return gc.fname[v - 60];
        }
        return gc.cname[v];
    }

    /** One grid cell; icon centred; state 0 normal, 1 focus, 2 locked. */
    static void cellBox(Graphics g, int x, int y, Image ic, int state) {
        javax.microedition.lcdui.Graphics m = g.g;
        int c = cell;
        if (state == 1) {
            m.setColor(UiKit.SEL_D);
            m.fillRoundRect(x, y, c - 1, c - 1, 6, 6);
            m.setColor(UiKit.SEL);
            m.fillRoundRect(x + 1, y + 1, c - 3, c - 3, 5, 5);
        } else {
            m.setColor(UiKit.LINE);
            m.fillRoundRect(x, y, c - 1, c - 1, 6, 6);
            m.setColor(state == 2 ? UiKit.LINE : UiKit.BG2);
            m.fillRoundRect(x + 1, y + 1, c - 3, c - 3, 5, 5);
            if (state == 2) {
                m.setColor(UiKit.SUB);
                m.drawLine(x + 4, y + 4, x + c - 6, y + c - 6);
                m.drawLine(x + 4, y + c - 6, x + c - 6, y + 4);
            }
        }
        if (ic != null) {
            g.drawImage(ic, x + (c - 1) / 2, y + (c - 1) / 2, 1 | 2);
        }
    }

    /** Vertical scroll bar right of the grid: pos of max. */
    static void scroll(Graphics g, int pos, int max) {
        if (max <= 0) {
            return;
        }
        javax.microedition.lcdui.Graphics m = g.g;
        int x = gx + 8 * cell + 3, y = gy, h = 5 * cell - 1;
        m.setColor(UiKit.LINE);
        m.fillRoundRect(x, y, 5, h, 5, 5);
        int th = h / (max + 1);
        if (th < 8) {
            th = 8;
        }
        m.setColor(UiKit.ACC_D);
        m.fillRoundRect(x, y + (h - th) * pos / max, 5, th, 5, 5);
    }

    /** Info box under the grid. */
    static void infoBox(Graphics g) {
        javax.microedition.lcdui.Graphics m = g.g;
        m.setColor(UiKit.LINE);
        m.fillRoundRect(ix, iy, iw, ih, 10, 10);
        m.setColor(UiKit.BG2);
        m.fillRoundRect(ix + 1, iy + 1, iw - 2, ih - 2, 9, 9);
    }

    /** Seasons of a crop ("Bốn mùa" or "Xuân, Thu"), right-aligned at x; no allocation. */
    static void seasons(GameCanvas gc, Graphics g, int crop, int xr, int y, int rgb) {
        int mask = gc.cdat0[crop][0];
        if (mask == 15) {
            UiKit.text(g, "Bốn mùa", xr, y, rgb, UiKit.RIGHT);
            return;
        }
        int w = 0, n = 0;
        for (int i = 0; i < 4; i++) {
            if ((mask >> i & 1) == 1) {
                w += Txt.width(gc.SeaDat[i]) + (n > 0 ? Txt.charWidth(',') + 3 : 0);
                n++;
            }
        }
        int x = xr - w;
        n = 0;
        for (int i = 0; i < 4; i++) {
            if ((mask >> i & 1) == 1) {
                if (n > 0) {
                    x += Txt.drawChar(g.g, ',', x, y, rgb) + 3;
                }
                UiKit.text(g, gc.SeaDat[i], x, y, rgb, UiKit.LEFT);
                x += Txt.width(gc.SeaDat[i]);
                n++;
            }
        }
    }

    static void price(Graphics g, int v, int xr, int y, boolean red) {
        String s = COIN;
        int w = UiKit.moneyW(v) + Txt.width(s);
        int x = xr - w;
        javax.microedition.lcdui.Graphics m = g.g;
        int h = Txt.height() + 4;
        m.setColor(red ? UiKit.RED : UiKit.ACC_D);
        m.fillRoundRect(x - 6, y - 2, w + 12, h, h, h);
        x += UiKit.money(g, v, x, y, UiKit.WHITE);
        UiKit.text(g, s, x, y, UiKit.WHITE, UiKit.LEFT);
    }

    static void grade(Graphics g, int q, int xr, int y) {
        String s = q <= 33 ? "Hạ phẩm" : q <= 66 ? "Trung phẩm" : "Thượng phẩm";
        int bg = q <= 33 ? UiKit.ACC_D : q <= 66 ? UiKit.BLUE : UiKit.RED;
        UiKit.pill(g, xr - UiKit.pillW(s), y - 2, s, bg, UiKit.DARK, UiKit.WHITE);
    }

    // ------------------------------------------------------------ DrawPoint: contents

    /** GameCanvas.DrawPoint: everything inside the panel (and the lotto logic, copied). */
    static void DrawPoint(GameCanvas gc, Graphics g) {
        layout(gc);
        switch (gc.GM[0]) {
        case 0:
            info(gc, g);
            break;
        case 1:
            seeds(gc, g);
            break;
        case 2:
            bag(gc, g, 0);
            break;
        case 3:
            quests(gc, g);
            break;
        case 4:
            system(gc, g);
            break;
        case 5:
            shop(gc, g);
            break;
        case 6:
            bag(gc, g, 1);
            break;
        case 7:
        case 11:
        case 12:
            bag(gc, g, 2);
            break;
        case 8:
            skillShop(gc, g);
            break;
        case 9:
            petShop(gc, g);
            break;
        case 10:
            lotto(gc, g);
            break;
        }
    }

    static void sectionTitle(Graphics g, String s) {
        UiKit.text(g, s, cx + 2, cy, UiKit.SUB, UiKit.LEFT);
    }

    // ---- tab 0: info (date, pet, skills)

    static void info(GameCanvas gc, Graphics g) {
        javax.microedition.lcdui.Graphics m = g.g;
        int lh = UiKit.lh();
        // date line
        int y = cy;
        String yr = "Năm ", dy = "Ngày ";
        int x = cx + 2;
        UiKit.text(g, yr, x, y, UiKit.DARK, UiKit.LEFT);
        x += Txt.width(yr);
        x += UiKit.num(g, gc.dat1[0] + 1, x, y, UiKit.DARK, UiKit.LEFT) + 8;
        UiKit.text(g, gc.SeaDat[gc.dat1[1]], x, y, UiKit.ACC_D, UiKit.LEFT);
        x += Txt.width(gc.SeaDat[gc.dat1[1]]) + 8;
        UiKit.text(g, dy, x, y, UiKit.DARK, UiKit.LEFT);
        UiKit.num(g, gc.dat1[2], x + Txt.width(dy), y, UiKit.DARK, UiKit.LEFT);
        y += lh + 4;
        // pet box
        int boxH = 30;
        boolean pf = gc.GM[1] == 1;
        m.setColor(pf ? UiKit.SEL_D : UiKit.LINE);
        m.fillRoundRect(cx, y, cw, boxH, 10, 10);
        m.setColor(pf ? UiKit.SEL : UiKit.BG2);
        m.fillRoundRect(cx + 1, y + 1, cw - 2, boxH - 2, 9, 9);
        UiKit.text(g, "Thú cưng", cx + 8, y + (boxH - Txt.height()) / 2, UiKit.SUB, UiKit.LEFT);
        int lx = cx + 8 + Txt.width("Thú cưng") + 12;
        if (gc.p.nowpet >= 0) {
            g.drawImage(gc.p.APet[gc.p.nowpet][4], lx + 12, y + boxH / 2, 1 | 2);
            UiKit.text(g, gc.p.name[gc.p.dat[gc.p.nowpet][5]], lx + 30, y + (boxH - Txt.height()) / 2, UiKit.DARK,
                    UiKit.LEFT);
            if (pf) {
                UiKit.pointer(g, cx + cw - 14, y + boxH / 2);
            }
        } else {
            UiKit.text(g, "Chưa có", lx, y + (boxH - Txt.height()) / 2, UiKit.SUB, UiKit.LEFT);
        }
        y += boxH + 6;
        // skills 4 x 3
        int rows = 3;
        if (fy - 6 - y >= lh + rows * 23 + 4 + 2 * lh + 10) { // label only when there is room
            UiKit.text(g, "Kỹ năng", cx + 2, y, UiKit.SUB, UiKit.LEFT);
            y += lh;
        }
        int sc = (cw - 6) / 4;
        int avail = fy - 6 - y - (2 * lh + 10) - 4;
        int sh = avail / rows;
        if (sh > 28) {
            sh = 28;
        }
        if (sh < 19) {
            sh = 19;
        }
        int bw = sh < 23 ? sh - 1 : 22;
        for (int i = 0; i < 12; i++) {
            int sx = cx + 3 + (i % 4) * sc, sy = y + (i / 4) * sh;
            boolean f = gc.GM[1] == 2 && gc.GM[2] == i;
            int bx = sx + (sc - bw) / 2;
            m.setColor(f ? UiKit.SEL_D : UiKit.DARK);
            m.fillRoundRect(bx, sy, bw, bw - 1, 6, 6);
            m.setColor(f ? UiKit.SEL : 0x6D5A34);
            m.fillRoundRect(bx + 1, sy + 1, bw - 2, bw - 3, 5, 5);
            if (gc.c.skill[i] > 0 && gc.im.SKILL != null) {
                g.drawImage(gc.im.SKILL[i], bx + bw / 2, sy + (bw - 1) / 2, 1 | 2);
            }
        }
        // details of the focused skill
        int dy2 = y + rows * sh + 4;
        int dh = fy - 4 - dy2;
        if (dh > 2 * lh + 10) {
            dh = 2 * lh + 10;
        }
        m.setColor(UiKit.LINE);
        m.fillRoundRect(cx, dy2, cw, dh, 10, 10);
        m.setColor(UiKit.BG2);
        m.fillRoundRect(cx + 1, dy2 + 1, cw - 2, dh - 2, 9, 9);
        if (gc.GM[1] == 2) {
            int s = gc.GM[2];
            if (gc.c.skill[s] > 0) {
                UiKit.text(g, gc.m.Skillname[s], cx + 8, dy2 + 5, UiKit.DARK, UiKit.LEFT);
                int lx2 = cx + cw - 8;
                lx2 -= UiKit.num(g, gc.c.skill[s], lx2, dy2 + 5, UiKit.ACC_D, UiKit.RIGHT);
                UiKit.text(g, "Lv.", lx2 - 1, dy2 + 5, UiKit.ACC_D, UiKit.RIGHT);
                clipText(g, gc.m.Skillexplan[s], cx + 8, dy2 + 5 + lh, cw - 16, UiKit.SUB);
            } else {
                UiKit.text(g, "Chưa học", cx + 8, dy2 + 5, UiKit.SUB, UiKit.LEFT);
            }
        } else {
            UiKit.text(g, "Chọn kỹ năng để xem", cx + 8, dy2 + 5, UiKit.SUB, UiKit.LEFT);
        }
    }

    /** One line of text, cut at w pixels. */
    static void clipText(Graphics g, String s, int x, int y, int w, int rgb) {
        if (s == null) {
            return;
        }
        int n = s.length(), tw = 0, i = 0;
        for (; i < n; i++) {
            int cw2 = Txt.charWidth(s.charAt(i));
            if (tw + cw2 > w) {
                break;
            }
            tw += cw2;
        }
        Txt.draw(g.g, s, 0, i, x, y, rgb);
    }

    // ---- tab 1: seeds (40)

    static void seeds(GameCanvas gc, Graphics g) {
        sectionTitle(g, "Hạt giống đang mang");
        boolean in = gc.GM[1] > 0;
        for (int i = 0; i < 40; i++) {
            int x = gx + (i % 8) * cell, y = gy + (i / 8) * cell;
            Image ic = gc.seed[i][0] >= 0 ? gc.im.Seed[gc.seed[i][0] / 18] : null;
            cellBox(g, x, y, ic, in && gc.GM[2] == i ? 1 : 0);
            if (i == 0) { // slot 0: the seed in hand
                javax.microedition.lcdui.Graphics m = g.g;
                m.setColor(UiKit.ACC_D);
                m.fillTriangle(x + 1, y + 1, x + 7, y + 1, x + 1, y + 7);
            }
        }
        infoBox(g);
        if (!in) {
            UiKit.text(g, "Ô đầu tiên = hạt đang cầm", ix + 8, iy + 5, UiKit.SUB, UiKit.LEFT);
            return;
        }
        int s = gc.seed[gc.GM[2]][0];
        if (s < 0) {
            UiKit.text(g, "Ô trống", ix + 8, iy + 5, UiKit.SUB, UiKit.LEFT);
            return;
        }
        int lh = UiKit.lh();
        g.drawImage(gc.im.Crop[s], ix + 6, iy + 5, 4);
        UiKit.text(g, gc.cname[s], ix + 26, iy + 5, UiKit.DARK, UiKit.LEFT);
        seasons(gc, g, s, ix + iw - 8, iy + 5, UiKit.ACC_D);
        if (gc.c.skill[7] > 0) {
            int x = ix + 26;
            UiKit.text(g, "Phẩm chất ", x, iy + 5 + lh, UiKit.SUB, UiKit.LEFT);
            UiKit.num(g, gc.seed[gc.GM[2]][1], x + Txt.width("Phẩm chất "), iy + 5 + lh, UiKit.SUB,
                    UiKit.LEFT);
        }
        UiKit.text(g, REGION[s / 18], ix + iw - 8, iy + 5 + lh, UiKit.SUB, UiKit.RIGHT);
    }

    // ---- tab 2 / sell / pickers: basket (72 slots, 40 shown from row GM[3])

    static void bag(GameCanvas gc, Graphics g, int mode) {
        if (mode == 0) {
            sectionTitle(g, "Túi đồ");
        } else {
            sectionTitle(g, mode == 1 ? "Chọn đồ để bán" : "Chọn từ túi đồ");
        }
        boolean in = gc.GM[1] > 0 || mode == 2;
        int cap = 40 + gc.c.skill[8] * 8;
        for (int i = gc.GM[3] * 8; i < 40 + gc.GM[3] * 8; i++) {
            int x = gx + (i % 8) * cell, y = gy + (i / 8 - gc.GM[3]) * cell;
            if (i >= cap) {
                cellBox(g, x, y, null, 2);
                continue;
            }
            int v = gc.basket[i][0];
            cellBox(g, x, y, v >= 0 ? itemIcon(gc, v) : null, in && gc.GM[2] == i ? 1 : 0);
        }
        scroll(g, gc.GM[3], (72 - 40) / 8);
        infoBox(g);
        if (!in) {
            return;
        }
        int v = gc.basket[gc.GM[2]][0];
        if (v < 0) {
            return;
        }
        int lh = UiKit.lh();
        int q = gc.basket[gc.GM[2]][1];
        g.drawImage(itemIcon(gc, v), ix + 6, iy + 5, 4);
        int nx = ix + 26;
        String nm = itemName(gc, v);
        UiKit.text(g, nm, nx, iy + 5, UiKit.DARK, UiKit.LEFT);
        if (v < 60 && (mode > 0 || gc.c.skill[7] > 0)) {
            int qx = nx + Txt.width(nm) + 4;
            qx += Txt.drawChar(g.g, '(', qx, iy + 5, UiKit.SUB);
            qx += UiKit.num(g, q, qx, iy + 5, UiKit.SUB, UiKit.LEFT);
            Txt.drawChar(g.g, ')', qx, iy + 5, UiKit.SUB);
        }
        int y2 = iy + 5 + lh;
        if (mode == 1) {
            int pr = -1;
            if (v < 60) {
                int base = gc.cdat1[v][7] + gc.cdat1[v][7] * q / 100;
                pr = gc.GM[4] == 3 ? base : base * 80 / 100;
            } else if (v < 75) {
                pr = ((v - 60) % 5 + 1) * 300 + (v - 60) / 5 * 200;
            } else if (v >= 100) {
                pr = gc.milkprice[v - 100];
            }
            if (pr >= 0) {
                UiKit.text(g, "Giá bán", nx, y2, UiKit.SUB, UiKit.LEFT);
                price(g, pr, ix + iw - 10, y2, false);
            }
        } else if (v < 60 && mode == 2) {
            UiKit.text(g, REGION[v / 18], ix + iw - 8, y2, UiKit.SUB, UiKit.RIGHT);
        }
    }

    // ---- tab 3: quests

    static void quests(GameCanvas gc, Graphics g) {
        javax.microedition.lcdui.Graphics m = g.g;
        int lh = UiKit.lh();
        int y = cy;
        boolean in = gc.GM[1] > 0;
        // main quest card
        int mainH = 2 * lh + 18;
        card(g, cx, y, cw, mainH, in && gc.GM[2] == 0);
        UiKit.pill(g, cx + 6, y - 2, "Chính", UiKit.RED, UiKit.DARK, UiKit.WHITE);
        if (gc.c.quest[1] == 0) {
            int tx = cx + 8;
            if (gc.m.QImg != null) {
                g.drawImage(gc.m.QImg, cx + 8, y + mainH - 4, 36);
                tx = cx + 12 + gc.m.QImg.getWidth();
            }
            UiKit.text(g, gc.n.name[gc.m.QNPC], tx, y + Txt.height() + 2, UiKit.DARK, UiKit.LEFT);
            clipText(g, gc.m.QTitle, tx, y + Txt.height() + 2 + lh, cx + cw - 36 - tx, UiKit.ACC_D);
            int bx = cx + cw - 30;
            m.setColor(UiKit.DARK);
            m.fillRoundRect(bx, y + 10, 22, 22, 6, 6);
            m.setColor(gc.m.QItem >= 0 ? 0x6D5A34 : UiKit.LINE);
            m.fillRoundRect(bx + 1, y + 11, 20, 20, 5, 5);
            if (gc.m.QItem >= 0) {
                g.drawImage(gc.im.Item[4 + gc.m.QItem], bx + 11, y + 21, 1 | 2);
            } else if (gc.im.GM != null && gc.im.GM.length > 32) {
                g.drawImage(gc.im.GM[32], bx + 11, y + 21, 1 | 2);
            }
        } else {
            UiKit.text(g, "Không có nhiệm vụ", cx + cw / 2, y + (mainH - Txt.height()) / 2 + 2, UiKit.SUB,
                    UiKit.HCENTER);
        }
        y += mainH + 6;
        // two side quests
        int subH = 2 * lh + 10;
        int nx = cx + 10 + UiKit.pillW("Phụ");
        for (int n = 0; n < 2; n++) {
            card(g, cx, y, cw, subH, in && gc.GM[2] == n + 1);
            UiKit.pill(g, cx + 5, y + 4, "Phụ", UiKit.BLUE, UiKit.DARK, UiKit.WHITE);
            if (gc.opt[31 + n * 5] == -1) {
                UiKit.text(g, gc.n.name[gc.opt[32 + n * 5]], nx, y + 6, UiKit.DARK, UiKit.LEFT);
                int have = 0;
                String what = null;
                switch (gc.opt[33 + n * 5]) {
                case 0:
                    what = gc.cname[gc.opt[34 + n * 5]];
                    have = gc.c.CountBasket(gc.opt[34 + n * 5]);
                    break;
                case 1:
                    what = gc.fname[gc.opt[34 + n * 5]];
                    have = gc.c.CountBasket(60 + gc.opt[34 + n * 5]);
                    break;
                case 2:
                case 3:
                case 4:
                    what = gc.Milk[gc.opt[33 + n * 5] - 2];
                    have = gc.c.CountBasket(100 + gc.opt[33 + n * 5] - 2);
                }
                int need = gc.opt[35 + n * 5];
                int ry = y + 6 + lh;
                if (what != null) {
                    UiKit.text(g, what, nx, ry, UiKit.SUB, UiKit.LEFT);
                }
                int rx = cx + cw - 10;
                rx -= UiKit.num(g, need, rx, ry, have >= need ? UiKit.ACC_D : UiKit.SUB, UiKit.RIGHT);
                Txt.drawChar(g.g, '/', rx - Txt.charWidth('/'), ry, UiKit.SUB);
                rx -= Txt.charWidth('/');
                UiKit.num(g, have < need ? have : need, rx, ry, have >= need ? UiKit.ACC_D : UiKit.SUB, UiKit.RIGHT);
            } else {
                UiKit.text(g, "Trống", nx, y + 6, UiKit.SUB, UiKit.LEFT);
            }
            y += subH + 6;
        }
    }

    static void card(Graphics g, int x, int y, int w, int h, boolean focus) {
        javax.microedition.lcdui.Graphics m = g.g;
        m.setColor(focus ? UiKit.SEL_D : UiKit.LINE);
        m.fillRoundRect(x, y, w, h, 10, 10);
        m.setColor(focus ? UiKit.SEL : UiKit.BG2);
        m.fillRoundRect(x + 1, y + 1, w - 2, h - 2, 9, 9);
    }

    /** Quest hint window (MakeQuestExplan). */
    static void questHint(GameCanvas gc, Graphics g) {
        int W = gc.getWidth(), H = gc.getHeight();
        int w = W - 30;
        if (w > 220) {
            w = 220;
        }
        int lh = UiKit.lh();
        int n = UiKit.wrap(qText, w - 24);
        int h = 3 + UiKit.rh() + 8 + n * lh + 12;
        int x = (W - w) / 2, y = (H - h) / 2;
        int ty = UiKit.titled(g, x, y, w, h, qName) + 6;
        UiKit.lines(g, qText, n, x + 12, ty, lh, UiKit.DARK, UiKit.LEFT, w - 24);
    }

    // ---- tab 4: system

    static void system(GameCanvas gc, Graphics g) {
        int rowH = Txt.height() + 12;
        int bw = cw - 30;
        if (bw > 200) {
            bw = 200;
        }
        int total = 5 * (rowH + 6);
        int y = cy + (fy - 6 - cy - total) / 2;
        for (int i = 0; i < 5; i++) {
            UiKit.button(g, cx + (cw - bw) / 2, y + i * (rowH + 6), bw, rowH, gc.GMOPT[i], gc.GM[1] > 0 && gc.GM[2] == i);
        }
    }

    // ---- 5: buy (seed shops 0-3, bait 7, crops 8)

    static void shop(GameCanvas gc, Graphics g) {
        boolean in = gc.GM[1] > 0;
        int k = gc.GM[4];
        byte[][] items = gc.shop[k < 7 ? k : k == 7 ? 4 : 5];
        for (int i = 0; i < 40; i++) {
            int x = gx + (i % 8) * cell, y = gy + (i / 8) * cell;
            int v = items[i][0];
            Image ic = null;
            if (v >= 0) {
                ic = k < 7 ? gc.im.Seed[v / 18] : k == 7 ? gc.im.Item[(v - 60) / 5] : gc.im.Crop[v];
            }
            cellBox(g, x, y, ic, in && gc.GM[2] == i ? 1 : 0);
        }
        sectionTitle(g, k < 7 ? "Hạt giống" : k == 7 ? "Mồi câu" : "Nông sản");
        infoBox(g);
        if (!in) {
            return;
        }
        int v = items[gc.GM[2]][0];
        if (v < 0) {
            return;
        }
        int lh = UiKit.lh();
        int q = items[gc.GM[2]][1];
        int nx = ix + 26;
        int y2 = iy + 5 + lh;
        int pr;
        if (k < 7) {
            g.drawImage(gc.im.Crop[v], ix + 6, iy + 5, 4);
            UiKit.text(g, gc.cname[v], nx, iy + 5, UiKit.DARK, UiKit.LEFT);
            grade(g, q, ix + iw - 8, iy + 5);
            seasons(gc, g, v, nx + 2 + width(gc, v), y2, UiKit.ACC_D);
            pr = gc.cdat1[v][6] + gc.cdat1[v][6] * q / 100;
            if (gc.p.skill >= 9 && gc.p.skill <= 11) {
                pr -= pr * (10 * (gc.p.skill - 8)) / 100;
            }
        } else if (k == 7) {
            g.drawImage(gc.im.Item[(v - 60) / 5], ix + 6, iy + 5, 4);
            UiKit.text(g, gc.fname[v - 60], nx, iy + 5, UiKit.DARK, UiKit.LEFT);
            pr = (((v - 60) % 5 + 1) * 450 + (v - 60) / 5 * 200) * 13;
        } else {
            g.drawImage(gc.im.Crop[v], ix + 6, iy + 5, 4);
            UiKit.text(g, gc.cname[v], nx, iy + 5, UiKit.DARK, UiKit.LEFT);
            pr = (gc.cdat1[v][7] + gc.cdat1[v][7] * q / 100) * 13;
        }
        price(g, pr, ix + iw - 10, k < 7 ? y2 + 3 : y2, pr > gc.dat3[0]);
    }

    /** Width of the seasons text of a crop (for left alignment). */
    static int width(GameCanvas gc, int crop) {
        int mask = gc.cdat0[crop][0];
        if (mask == 15) {
            return Txt.width("Bốn mùa");
        }
        int w = 0, n = 0;
        for (int i = 0; i < 4; i++) {
            if ((mask >> i & 1) == 1) {
                w += Txt.width(gc.SeaDat[i]) + (n > 0 ? Txt.charWidth(',') + 3 : 0);
                n++;
            }
        }
        return w;
    }

    // ---- 8: skill shop

    static void skillShop(GameCanvas gc, Graphics g) {
        javax.microedition.lcdui.Graphics m = g.g;
        int lh = UiKit.lh();
        int y = cy;
        int rowH = (fy - 6 - y - (2 * lh + 10)) / 5 - 4; // leave room for 2 lines of description
        if (rowH > 24) {
            rowH = 24;
        }
        if (rowH < 20) {
            rowH = 20;
        }
        for (int n = 0; n < 5; n++) {
            int ry = y + n * (rowH + 4);
            boolean f = gc.GM[2] == n;
            card(g, cx, ry, cw, rowH, f);
            int id = gc.sshop[gc.GM[4]][n][0];
            m.setColor(UiKit.DARK);
            m.fillRoundRect(cx + 4, ry + (rowH - 20) / 2, 20, 20, 5, 5);
            if (id < 0) {
                continue;
            }
            if (gc.im.SKILL != null) {
                g.drawImage(gc.im.SKILL[id], cx + 14, ry + rowH / 2, 1 | 2);
            }
            int ty = ry + (rowH - Txt.height()) / 2;
            int tx = cx + 30;
            UiKit.text(g, gc.m.Skillname[id], tx, ty, UiKit.DARK, UiKit.LEFT);
            tx += Txt.width(gc.m.Skillname[id]) + 6;
            if (gc.sshop[gc.GM[4]][n][1] <= 0) {
                UiKit.text(g, "đã có", tx, ty, UiKit.SUB, UiKit.LEFT);
            } else {
                int lv = gc.c.skill[id];
                UiKit.text(g, "Lv.", tx, ty, UiKit.ACC_D, UiKit.LEFT);
                UiKit.num(g, lv + 1, tx + Txt.width("Lv."), ty, UiKit.ACC_D, UiKit.LEFT);
                int pr = gc.m.skillprice[id * 4 + lv];
                price(g, pr, cx + cw - 10, ty, pr > gc.dat3[0]);
            }
        }
        int dy = y + 5 * (rowH + 4) + 2;
        int dh = fy - 4 - dy;
        if (dh > 3 * lh + 10) {
            dh = 3 * lh + 10;
        }
        if (dh > lh) {
            m.setColor(UiKit.LINE);
            m.fillRoundRect(cx, dy, cw, dh, 10, 10);
            m.setColor(UiKit.BG2);
            m.fillRoundRect(cx + 1, dy + 1, cw - 2, dh - 2, 9, 9);
            int id = gc.sshop[gc.GM[4]][gc.GM[2]][0];
            if (id >= 0) {
                int n = UiKit.wrap(gc.m.Skillexplan[id], cw - 16);
                if (n > (dh - 8) / lh) {
                    n = (dh - 8) / lh;
                }
                UiKit.lines(g, gc.m.Skillexplan[id], n, cx + 8, dy + 5, lh, UiKit.DARK, UiKit.LEFT, cw - 16);
            }
        }
    }

    // ---- 9: pet shop

    static void petShop(GameCanvas gc, Graphics g) {
        javax.microedition.lcdui.Graphics m = g.g;
        int lh = UiKit.lh();
        int listW = cw * 2 / 5;
        if (listW < 70) {
            listW = 70;
        }
        int rowH = 26;
        int vis = 5;
        int y = cy;
        for (int n = 0; n < vis; n++) {
            int i = gc.GM[3] + n;
            int ry = y + n * (rowH + 3);
            card(g, cx, ry, listW, rowH, gc.GM[2] == i);
            if (gc.im.PS != null && i < gc.im.PS.length && gc.im.PS[i] != null) {
                g.drawImage(gc.im.PS[i], cx + 14, ry + rowH / 2, 1 | 2);
            }
            if (i < gc.p.name.length && gc.p.name[i] != null) {
                clipText(g, gc.p.name[i], cx + 28, ry + (rowH - Txt.height()) / 2, listW - 32, UiKit.DARK);
            }
        }
        // scroll
        int max = gc.p.checkpet - 5;
        if (max > 0) {
            int sx = cx + listW + 3, sh = vis * (rowH + 3) - 3;
            m.setColor(UiKit.LINE);
            m.fillRoundRect(sx, y, 5, sh, 5, 5);
            int th = Math.max(8, sh / (max + 1));
            m.setColor(UiKit.ACC_D);
            m.fillRoundRect(sx, y + (sh - th) * gc.GM[3] / max, 5, th, 5, 5);
        }
        // details
        int dx = cx + listW + 12, dw = cx + cw - dx;
        int dh = fy - 6 - y; // the details card runs down to the footer
        card(g, dx, y, dw, dh, false);
        int i = gc.GM[2];
        UiKit.text(g, gc.p.name[i], dx + dw / 2, y + 6, UiKit.DARK, UiKit.HCENTER);
        if (gc.im.PS != null && i < gc.im.PS.length && gc.im.PS[i] != null) {
            g.drawImage(gc.im.PS[i], dx + dw / 2, y + 10 + lh, 1);
        }
        int ty = y + 10 + lh + 22;
        int pr = gc.p.PDat[i][1];
        price(g, pr, dx + dw - 8, ty, gc.dat3[0] < pr);
        if (Txt.width("Giá") + UiKit.moneyW(pr) + Txt.width(COIN) + 30 <= dw) {
            UiKit.text(g, "Giá", dx + 8, ty, UiKit.SUB, UiKit.LEFT);
        }
        ty += lh + 4;
        String cash = "cash";
        int cx2 = dx + dw - 8;
        UiKit.text(g, cash, cx2, ty, UiKit.SUB, UiKit.RIGHT);
        UiKit.num(g, gc.p.petprice[i], cx2 - Txt.width(cash) - 4, ty, UiKit.SUB, UiKit.RIGHT);
        ty += lh + 2;
        int sk = gc.p.PDat[i][2];
        if (gc.p.SkillE != null && sk < gc.p.SkillE.length) {
            int n = UiKit.wrap(gc.p.SkillE[sk], dw - 14);
            int maxl = (y + dh - 4 - ty) / lh;
            if (n > maxl) {
                n = maxl;
            }
            UiKit.lines(g, gc.p.SkillE[sk], n, dx + 7, ty, lh, UiKit.DARK, UiKit.LEFT, dw - 14);
        }
    }

    /** Pet info window (PInfo, opened from the info tab or a popup). */
    static void petInfo(GameCanvas gc, Graphics g) {
        int W = gc.getWidth(), H = gc.getHeight();
        int n = petInfo;
        int w = W - 40;
        if (w > 200) {
            w = 200;
        }
        int lh = UiKit.lh();
        String txt = null;
        int sk = gc.p.PDat[gc.p.dat[n][5]][2];
        if (gc.p.SkillE != null && sk < gc.p.SkillE.length) {
            txt = gc.p.SkillE[sk];
        }
        int lines = txt != null ? UiKit.wrap(txt, w - 24) : 0;
        int h = 3 + UiKit.rh() + 8 + 34 + 8 + lines * lh + 12;
        int x = (W - w) / 2, y = (H - h) / 2;
        int ty = UiKit.titled(g, x, y, w, h, gc.p.name[gc.p.dat[n][5]]) + 4;
        javax.microedition.lcdui.Graphics m = g.g;
        m.setColor(UiKit.BG2);
        m.fillRoundRect(x + w / 2 - 20, ty, 40, 34, 10, 10);
        g.drawImage(gc.p.APet[n][4], x + w / 2, ty + 30, 1 | 32);
        ty += 42;
        if (txt != null) {
            UiKit.lines(g, txt, lines, x + 12, ty, lh, UiKit.DARK, UiKit.LEFT, w - 24);
        }
    }

    // ---- 10: lotto (logic copied from DrawPoint; the board is still the original art)

    static void lotto(GameCanvas gc, Graphics g) {
        if (gc.GM[2] < 5) {
            g.drawImage(gc.im.GM[3 + gc.GM[1] % 4 / 2], gc.w2 + 9 + gc.GM[2] * 19, gc.h2 + 42 + gc.ani / 2, 0 | 4);
        } else if (gc.GM[2] < 8) {
            g.drawImage(gc.im.GM[5 + gc.GM[1] % 4 / 2], gc.w2 + 44 + (gc.GM[2] - 5) * 20, gc.h2 + 64 + gc.ani / 2, 0 | 4);
        }
        if (gc.GM[2] < 8) {
            gc.ani = (gc.ani + 1) % 4;
        } else if (gc.ani < 20) {
            ++gc.ani;
        }
        if (gc.GM[1] > 0) {
            gc.GM[1] = (byte) (gc.GM[1] + 1);
            if (gc.GM[1] >= 12) {
                if (gc.GM[2] < 5) {
                    gc.gmg.drawImage(gc.im.WTile[1], 4 + gc.GM[2] * 19, 47, 0 | 4);
                    gc.gmg.drawImage(gc.im.Crop[gc.lotto[2 + gc.GM[2]][0]], 4 + gc.GM[2] * 19, 47, 0 | 4);
                } else {
                    gc.gmg.drawImage(gc.im.WTile[0], 39 + (gc.GM[2] - 5) * 20, 66, 0 | 4);
                    gc.gmg.drawImage(gc.im.Seed[gc.lotto[gc.GM[2] - 5 + 7][0]], 39 + (gc.GM[2] - 5) * 20, 66, 0 | 4);
                }
                gc.GM[1] = 0;
                gc.GM[2] = (byte) (gc.GM[2] + 1);
            }
        }
        if (gc.ani == 20) {
            gc.ani = 21;
            if (gc.lotto[7][0] == gc.lotto[8][0] && gc.lotto[8][0] == gc.lotto[9][0]) {
                int n = gc.c.EmptySeed();
                if (n == -1) {
                    gc.ExplanSet("씨앗이 가득차서 보상이 없습니다.", 1);
                } else {
                    gc.seed[n][0] = gc.lotto[7][0] == 3 ? (byte) (54 + gc.Rand(3)) : (byte) (gc.lotto[7][0] * 18 + gc.Rand(17));
                    gc.seed[n][1] = (byte) (50 + gc.Rand(49));
                    gc.ExplanSet(gc.cname[gc.seed[n][0]] + " 씨앗 획득", 0);
                }
            } else if (gc.lotto[0][0] == 0) {
                gc.ExplanSet(gc.lotto[0][2] + "팜 획득", 0);
                gc.dat3[0] = gc.dat3[0] + gc.lotto[0][2];
            } else if (gc.p.nowmaxpet >= 15) {
                gc.ExplanSet("팻이 가득차 보상이 없습니다", 1);
            } else {
                gc.p.InsertPet(gc.lotto[0][2]);
                gc.p.nowmaxpet = (byte) (gc.p.nowmaxpet + 1);
                gc.ExplanSet(gc.p.name[gc.lotto[0][2]] + " 획득", 0);
            }
            gc.GM[2] = 10;
            gc.SaveGame(false);
        }
    }
}
