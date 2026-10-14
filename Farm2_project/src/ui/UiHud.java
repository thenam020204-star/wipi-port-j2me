import org.kwis.msp.lcdui.Graphics;

/** Small HUD elements over the map. */
final class UiHud {
    /** GameCanvas.DrawMiniTime(g, ani, div): count-down chip below the top bar. */
    static void DrawMiniTime(GameCanvas gc, Graphics g, int ani, int div) {
        String unit = " giây";
        int s = ani / div;
        int w = wipi.host.Txt.width(unit) + 26;
        int x = (gc.getWidth() - w) / 2, y = gc.h + 2;
        int h = wipi.host.Txt.height() + 6;
        javax.microedition.lcdui.Graphics m = g.g;
        m.setColor(UiKit.DARK);
        m.fillRoundRect(x, y, w, h, h, h);
        m.setColor(s <= 5 ? UiKit.RED : UiKit.ACC);
        m.fillRoundRect(x + 1, y + 1, w - 2, h - 2, h - 2, h - 2);
        int nx = x + 8;
        nx += UiKit.num(g, s, nx, y + 3, UiKit.WHITE, UiKit.LEFT);
        UiKit.text(g, unit, nx, y + 3, UiKit.WHITE, UiKit.LEFT);
    }
}
