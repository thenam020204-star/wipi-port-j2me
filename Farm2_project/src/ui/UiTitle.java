import java.io.InputStream;

import org.kwis.msp.lcdui.Graphics;

import wipi.host.Txt;

/**
 * Title menu, save slots, settings and help pages of the new UI.
 *
 * Body replacements: GameCanvas.DrawMenu, DrawMenuItem, LoadExplanDat
 * (logic copied; the 115x126 vine frame with Korean label images is replaced
 * by a text list, the 176x202 sub-window art by panels). The settings, help,
 * slot and confirm windows are drawn from UiPaint each frame.
 */
final class UiTitle {
    static final String[] ITEMS = { "Vào nông trại", "Hướng dẫn", "Cài đặt",
            "Xếp hạng", "Game hay", "Kho game", "Tặng quà" };
    static final String[] SEASON = { "Xuân", "Hạ", "Thu", "Đông" };

    /** GameCanvas.DrawMenu (State 2): title art, clouds, logo, butterfly, keys, menu list. */
    static void DrawMenu(GameCanvas gc, Graphics g) {
        if (gc.State != 2) {
            return;
        }
        int W = gc.getWidth();
        g.drawImage(gc.Buff2, 0, 0, 4);
        gc.tani[2] = (gc.tani[2] + 1) % (166 + W);
        gc.tani[3] = (gc.tani[3] + 1) % (166 + W);
        gc.tani[4] = (gc.tani[4] + 1) % (166 + W);
        gc.tani[8] = (gc.tani[8] + 1) % 4;
        g.drawImage(gc.im.MN[3], gc.tani[2] - 116, gc.tani[1] + 68, 4);
        g.drawImage(gc.im.MN[3], gc.tani[3] - 116, gc.tani[1] + 22, 4);
        g.drawImage(gc.im.MN[3], gc.tani[4] - 116, gc.tani[1] + 60, 4);
        g.drawImage(gc.im.MN[6], W / 2, gc.tani[5], 1);
        if (gc.KeyCode != -999) {
            if (gc.Menucur[1] == 0) {
                gc.MenuKey();
            }
            gc.KeyCode = -999;
        }
        if (gc.State == 2 && !gc.LOAD) {
            gc.DrawMenuItem(g);
            g.drawImage(gc.im.MN[4 + gc.tani[8] / 2], gc.tani[9], gc.tani[10], 4);
            if (!gc.OPTION && !gc.EXPLAN && !gc.RANK && !gc.WAP) {
                softKeys(g, gc.getWidth(), gc.getHeight(), "Chọn", null);
            }
        }
    }

    /** GameCanvas.DrawMenuItem: list of the 7 entries around Menucur[0]; consumes Menucur[1]. */
    static void DrawMenuItem(GameCanvas gc, Graphics g) {
        int W = gc.getWidth(), H = gc.getHeight();
        int rowH = Txt.height() + 9;
        int top = gc.h;
        if (gc.im.MN != null && gc.im.MN[6] != null && top < gc.tani[5] + gc.im.MN[6].getHeight() + 8) {
            top = gc.tani[5] + gc.im.MN[6].getHeight() + 8; // below the logo
        }
        int vis = 5;
        if (top + vis * rowH + 14 > H - 26) {
            vis = 3;
        }
        int w = 0;
        for (int i = 0; i < ITEMS.length; i++) {
            int iw = Txt.width(ITEMS[i]);
            if (iw > w) {
                w = iw;
            }
        }
        w += 44;
        int h = vis * rowH + 12;
        int x = (W - w) / 2;
        UiKit.panel(g, x, top, w, h);
        int half = vis / 2;
        for (int k = -half; k <= half; k++) {
            int item = (gc.Menucur[0] + k + 7 * 2) % 7;
            int ry = top + 6 + (k + half) * rowH;
            if (k == 0) {
                UiKit.focusRow(g, x + 6, ry, w - 12, rowH);
                UiKit.pointer(g, x + 12, ry + rowH / 2);
                UiKit.text(g, ITEMS[item], W / 2, ry + 4, UiKit.DARK, UiKit.HCENTER);
            } else {
                UiKit.text(g, ITEMS[item], W / 2, ry + 4, UiKit.SUB, UiKit.HCENTER);
            }
        }
        // up / down hints
        javax.microedition.lcdui.Graphics m = g.g;
        int d = UiKit.tick >> 2 & 1;
        m.setColor(UiKit.ACC_D);
        m.fillTriangle(W / 2 - 5, top - 1 - d, W / 2 + 5, top - 1 - d, W / 2, top - 6 - d);
        m.fillTriangle(W / 2 - 5, top + h + 1 + d, W / 2 + 5, top + h + 1 + d, W / 2, top + h + 6 + d);
        if (gc.Menucur[1] < 0) {
            gc.Menucur[0] = (byte) (gc.Menucur[0] - 1);
            if (gc.Menucur[0] < 0) {
                gc.Menucur[0] = 6;
            }
            gc.Menucur[1] = 0;
        } else if (gc.Menucur[1] > 0) {
            gc.Menucur[0] = (byte) (gc.Menucur[0] + 1);
            if (gc.Menucur[0] > 6) {
                gc.Menucur[0] = 0;
            }
            gc.Menucur[1] = 0;
        }
    }

    /** Soft-key labels in the bottom corners. */
    static void softKeys(Graphics g, int W, int H, String left, String right) {
        int h = Txt.height() + 5;
        if (left != null) {
            UiKit.pill(g, 3, H - h - 3, left, UiKit.ACC, UiKit.DARK, UiKit.WHITE);
        }
        if (right != null) {
            UiKit.pill(g, W - 3 - UiKit.pillW(right), H - h - 3, right, UiKit.DARK, UiKit.DARK, UiKit.WHITE);
        }
    }

    // ------------------------------------------------------------ settings (OPTION)

    static final String[] OPT = { "Nhạc nền", "Âm thanh", "Tốc độ", "Rung" };

    static void option(GameCanvas gc, Graphics g) {
        int W = gc.getWidth(), H = gc.getHeight();
        int w = W - 20;
        if (w > 232) {
            w = 232;
        }
        int rowH = Txt.height() + 13;
        int h = 3 + UiKit.rh() + 8 + 4 * rowH + 10;
        int x = (W - w) / 2, y = (H - h) / 2;
        int cy = UiKit.titled(g, x, y, w, h, "Cài đặt") + 4;
        int vx = x + w - 14 - 5 * 13; // value column
        for (int r = 0; r < 4; r++) {
            int ry = cy + r * rowH;
            boolean f = gc.GM[2] == r;
            if (f) {
                UiKit.focusRow(g, x + 6, ry, w - 12, rowH - 2);
            }
            int ty = ry + (rowH - 2 - Txt.height()) / 2;
            UiKit.text(g, OPT[r], x + 14, ty, f ? UiKit.DARK : UiKit.SUB, UiKit.LEFT);
            javax.microedition.lcdui.Graphics m = g.g;
            int by = ry + (rowH - 2 - 10) / 2;
            if (r < 3) {
                for (int k = 0; k < 5; k++) {
                    int bh = 4 + k * 2;
                    m.setColor(gc.opdat[r] > k ? UiKit.ACC_D : UiKit.LINE);
                    m.fillRect(vx + k * 13, by + 10 - bh, 9, bh);
                }
            } else {
                String s = gc.opdat[3] == 0 ? "Bật" : "Tắt";
                int pw = UiKit.pillW(s);
                UiKit.pill(g, vx + (5 * 13 - 4 - pw) / 2, ry + (rowH - 2 - Txt.height() - 5) / 2, s,
                        gc.opdat[3] == 0 ? UiKit.ACC : UiKit.SUB, UiKit.DARK, UiKit.WHITE);
            }
            if (f) {
                UiKit.sideArrows(g, vx - 10, vx + 5 * 13 + 5, ry + (rowH - 2) / 2, UiKit.DARK);
            }
        }
        gc.GM[3] = (byte) ((gc.GM[3] + 1) % 10);
        softKeys(g, W, H, null, "Xong");
    }

    // ------------------------------------------------------------ help (EXPLAN)

    static String expTitle = "", expBody = "";

    /** GameCanvas.LoadExplanDat: page count GM[4] and the text of page GM[2] (pages 15/16: contact/rating). */
    static void LoadExplanDat(GameCanvas gc) {
        if (gc.GM[2] == 15) {
            gc.GM[4] = 17;
            return;
        }
        if (gc.GM[2] == 16) {
            gc.GM[4] = 17;
            return;
        }
        String s = "";
        try {
            InputStream in = gc.getClass().getResourceAsStream("/Data/Explan.dat");
            byte[] b = new byte[in.available()];
            in.read(b, 0, b.length);
            in.close();
            gc.GM[4] = (byte) (gc.getInt(b, 0) + 2);
            int off = gc.getInt(b, 4 + gc.GM[2] * 8);
            int len = gc.getInt(b, 8 + gc.GM[2] * 8);
            byte[] t = new byte[len];
            System.arraycopy(b, off, t, 0, len);
            s = wipi.host.Enc.txt(t);
        } catch (Exception e) {
        }
        int at = s.indexOf('@');
        expTitle = at < 0 ? s : s.substring(0, at);
        expBody = at < 0 ? "" : s.substring(at + 1);
    }

    static final String[] INFO_K = { "Tên game", "Độ tuổi", "Phát hành", "Nhà SX" };
    static final String[] INFO_V = { "The Farm 2", "12+", "2008.01.31", "Com2uS" };

    static void explan(GameCanvas gc, Graphics g) {
        int W = gc.getWidth(), H = gc.getHeight();
        int w = W - 12;
        if (w > 280) {
            w = 280;
        }
        int lh = UiKit.lh();
        int h = H - 40;
        if (h > 12 * lh + 40) {
            h = 12 * lh + 40;
        }
        int x = (W - w) / 2, y = (H - h) / 2 - 6;
        int cy = UiKit.titled(g, x, y, w, h, "Hướng dẫn") + 6;
        // page chip on the ribbon
        int px = x + w - 12;
        px -= UiKit.num(g, gc.GM[4], px, y + 6, UiKit.WHITE, UiKit.RIGHT);
        px -= Txt.drawChar(g.g, '/', px - Txt.charWidth('/'), y + 6, UiKit.WHITE);
        UiKit.num(g, gc.GM[2] + 1, px, y + 6, UiKit.WHITE, UiKit.RIGHT);
        UiKit.sideArrows(g, x - 9, x + w + 8, y + h / 2, UiKit.DARK);
        int iw = w - 24;
        if (gc.GM[2] == 15) {
            int yy = cy + 6;
            for (int i = 4; i <= 7; i++) {
                if (gc.im.GM != null && gc.im.GM.length > i && gc.im.GM[i] != null) {
                    g.drawImage(gc.im.GM[i], W / 2, yy, 1);
                    yy += gc.im.GM[i].getHeight() + 10;
                }
            }
        } else if (gc.GM[2] == 16) {
            int rowH = lh + 6;
            int vx = 0;
            for (int r = 0; r < 4; r++) {
                vx = Math.max(vx, Txt.width(INFO_K[r]));
            }
            vx = Math.max(vx, Txt.width("S\u1ed1 ph\u00e2n lo\u1ea1i"));
            vx = x + 14 + vx + 10;
            for (int r = 0; r < 6; r++) {
                int ry = cy + r * rowH;
                if ((r & 1) == 0) {
                    UiKit.fill(g, UiKit.BG2, x + 8, ry, w - 16, rowH);
                }
                int ty = ry + 4;
                if (r < 4) {
                    UiKit.text(g, INFO_K[r], x + 14, ty, UiKit.SUB, UiKit.LEFT);
                    UiKit.text(g, INFO_V[r], vx, ty, UiKit.DARK, UiKit.LEFT);
                } else {
                    UiKit.text(g, r == 4 ? "Số phân loại" : "Số đăng ký", x + 14, ty,
                            UiKit.SUB, UiKit.LEFT);
                    if (gc.im.GM != null && gc.im.GM.length > 4 + r && gc.im.GM[4 + r] != null) {
                        g.drawImage(gc.im.GM[4 + r], vx, ry + rowH / 2, 4 | 2);
                    }
                }
            }
        } else {
            UiKit.text(g, expTitle, x + 14, cy, UiKit.RED, UiKit.LEFT);
            UiKit.para(g, expBody, x + 12, cy + lh + 4, iw, UiKit.DARK, false);
        }
        softKeys(g, W, H, null, "Xong");
    }

    // ------------------------------------------------------------ save slots (LOAD)

    static void load(GameCanvas gc, Graphics g) {
        int W = gc.getWidth(), H = gc.getHeight();
        int w = W - 20;
        if (w > 236) {
            w = 236;
        }
        int lh = UiKit.lh();
        int slotH = 2 * lh + 12;
        int h = 3 + UiKit.rh() + 8 + 3 * (slotH + 6) + 6;
        int x = (W - w) / 2, y = (H - h) / 2;
        int cy = UiKit.titled(g, x, y, w, h, gc.State == 10 ? "Tải game" : "Chọn ô lưu") + 4;
        javax.microedition.lcdui.Graphics m = g.g;
        for (int i = 0; i < 3; i++) {
            int sy = cy + i * (slotH + 6);
            boolean f = gc.Menucur[4] == i;
            m.setColor(f ? UiKit.SEL_D : UiKit.LINE);
            m.fillRoundRect(x + 8, sy, w - 16, slotH, 10, 10);
            m.setColor(f ? UiKit.SEL : UiKit.BG2);
            m.fillRoundRect(x + 9, sy + 1, w - 18, slotH - 2, 9, 9);
            // slot number badge
            m.setColor(f ? UiKit.ACC_D : UiKit.SUB);
            m.fillRoundRect(x + 14, sy + (slotH - 22) / 2, 22, 22, 22, 22);
            UiKit.num(g, i + 1, x + 25, sy + (slotH - Txt.height()) / 2, UiKit.WHITE, UiKit.HCENTER);
            int tx = x + 44;
            if (gc.savedat[i][0] >= 0) {
                byte[] s = gc.savedat[i];
                int ty = sy + 5;
                String yr = "N\u0103m ", dy = "Ng\u00e0y ";
                UiKit.text(g, yr, tx, ty, UiKit.DARK, UiKit.LEFT);
                int xx = tx + Txt.width(yr);
                xx += UiKit.num(g, s[0] + 1, xx, ty, UiKit.DARK, UiKit.LEFT) + 8;
                if (s[1] >= 0 && s[1] < 4) {
                    UiKit.text(g, SEASON[s[1]], xx, ty, UiKit.ACC_D, UiKit.LEFT);
                    xx += Txt.width(SEASON[s[1]]) + 8;
                }
                UiKit.text(g, dy, xx, ty, UiKit.DARK, UiKit.LEFT);
                UiKit.num(g, s[2], xx + Txt.width(dy), ty, UiKit.DARK, UiKit.LEFT);
                int ty2 = ty + lh + 1;
                int hx = tx + UiKit.num(g, s[3], tx, ty2, UiKit.SUB, UiKit.LEFT);
                hx += Txt.drawChar(g.g, ':', hx, ty2, UiKit.SUB);
                if (s[4] < 10) {
                    hx += Txt.drawChar(g.g, '0', hx, ty2, UiKit.SUB);
                }
                UiKit.num(g, s[4], hx, ty2, UiKit.SUB, UiKit.LEFT);
            } else {
                UiKit.text(g, gc.State == 10 ? "— Trống —" : "+ Chơi mới", tx,
                        sy + (slotH - Txt.height()) / 2, UiKit.SUB, UiKit.LEFT);
            }
            if (f && gc.Menucur[3] == 0 && gc.State != 17) {
                UiKit.pointer(g, x + w - 20, sy + slotH / 2);
            }
        }
        if (gc.State == 17) {
            UiKit.dialog(g, W, H, "Vui lòng chờ", "Đang tải...", null, null, -1);
        } else if (gc.Menucur[3] == 1) {
            // continue / new game on the chosen slot
            int bw = w - 60, bh = Txt.height() + 9;
            int sy = cy + gc.Menucur[4] * (slotH + 6);
            int py = sy + slotH - 4;
            if (py + 2 * bh + 16 > y + h) {
                py = sy - 2 * bh - 12;
            }
            int px = x + (w - bw - 16) / 2;
            UiKit.panel(g, px, py, bw + 16, 2 * bh + 16);
            UiKit.button(g, px + 8, py + 6, bw, bh, "Chơi tiếp", gc.Menucur[5] == 0);
            UiKit.button(g, px + 8, py + 8 + bh, bw, bh, "Chơi lại từ đầu", gc.Menucur[5] == 1);
        } else if (gc.Menucur[3] == 2) {
            UiKit.dialog(g, W, H, "Tải game",
                    "Dữ liệu chưa lưu sẽ bị mất. Bạn có muốn tải không?",
                    "Có", "Không", gc.Menucur[6]);
        } else if (gc.Menucur[3] == 3) {
            UiKit.dialog(g, W, H, "Chơi mới",
                    "Chơi mới rồi lưu sẽ xóa toàn bộ dữ liệu cũ của ô này. Bắt đầu mới?",
                    "Có", "Không", gc.Menucur[6]);
        }
        if (gc.State != 17) {
            softKeys(g, W, H, "Chọn", "Quay lại");
        }
    }
}
