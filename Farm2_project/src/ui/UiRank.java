import org.kwis.msp.lcdui.Graphics;

/** Ranking list (RANK, GM[1] == 2): five rows per page (GM[3]), own rank below. */
final class UiRank {
    static void list(GameCanvas gc, Graphics g) {
        int W = gc.getWidth(), H = gc.getHeight();
        int w = W - 16;
        if (w > 260) {
            w = 260;
        }
        int lh = UiKit.lh();
        int row = lh + 7;
        int h = 3 + UiKit.rh() + 8 + 5 * row + 8 + lh + 10 + 8;
        int x = (W - w) / 2, y = (H - h) / 2;
        int cy = UiKit.titled(g, x, y, w, h, "Bảng xếp hạng") + 6;
        for (int i = gc.GM[3] * 5; i < 5 + gc.GM[3] * 5; i++) {
            int ry = cy + (i % 5) * row;
            if ((i & 1) == 0) {
                UiKit.fill(g, UiKit.BG2, x + 8, ry, w - 16, row - 2);
            }
            int ty = ry + (row - 2 - wipi.host.Txt.height()) / 2;
            int nx = x + 14;
            nx += UiKit.num(g, i + 1, nx, ty, UiKit.ACC_D, UiKit.LEFT);
            UiKit.text(g, ".", nx, ty, UiKit.ACC_D, UiKit.LEFT);
            if (gc.rankpoint[i] >= 0) {
                UiKit.text(g, gc.rankphone[i], x + 44, ty, UiKit.DARK, UiKit.LEFT);
                UiKit.num(g, gc.rankpoint[i], x + w - 14, ty, UiKit.DARK, UiKit.RIGHT);
            } else {
                UiKit.text(g, "---", x + 44, ty, UiKit.SUB, UiKit.LEFT);
                UiKit.num(g, 0, x + w - 14, ty, UiKit.SUB, UiKit.RIGHT);
            }
        }
        int fy = cy + 5 * row + 6;
        UiKit.focusRow(g, x + 8, fy - 3, w - 16, lh + 6);
        UiKit.text(g, "Hạng của bạn", x + 14, fy, UiKit.DARK, UiKit.LEFT);
        int rx = x + w - 14;
        rx -= UiKit.num(g, gc.mypoint, rx, fy, UiKit.DARK, UiKit.RIGHT) + 10;
        UiKit.num(g, gc.myrank, rx, fy, UiKit.DARK, UiKit.RIGHT);
        UiKit.sideArrows(g, x + 6, x + w - 6, y + 3 + UiKit.rh() / 2, UiKit.WHITE);
    }
}
