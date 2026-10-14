package wipi.host;

import org.kwis.msp.lcdui.Graphics;

/** Debug overlay drawn after the game's paint (only with Farm-Debug: 1). */
public final class Overlay {
    static void paint(Graphics g) {
        if (!Opt.debug || Log.last == null) {
            return;
        }
        javax.microedition.lcdui.Graphics m = g.g;
        m.setClip(0, 0, Host.lw, Host.lh);
        m.setColor(0x400000);
        m.fillRect(0, 0, Host.lw, 14);
        Txt.draw(m, Log.count + " " + Log.last, 1, 1, 0xffff00);
    }
}
