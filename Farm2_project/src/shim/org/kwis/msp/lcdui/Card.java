package org.kwis.msp.lcdui;

import wipi.host.Host;

/**
 * WIPI-Java Card: a full-screen drawable that receives key events.
 * The host keeps a persistent back buffer (WIPI screens are not cleared
 * between paints), so paint() may redraw only what changed.
 */
public abstract class Card {
    protected int x, y;

    public Card() {
    }

    public int getWidth() {
        return Host.lw;
    }

    public int getHeight() {
        return Host.lh;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public boolean isShown() {
        return Host.top() == this;
    }

    public void repaint() {
        Host.requestRepaint(this, 0, 0, Host.lw, Host.lh);
    }

    public void repaint(int x, int y, int w, int h) {
        Host.requestRepaint(this, x, y, w, h);
    }

    public void serviceRepaints() {
        Host.flushRepaints();
    }

    public abstract void paint(Graphics g);

    protected boolean keyNotify(int type, int key) {
        return false;
    }

    protected boolean pointerNotify(int type, int x, int y) {
        return false;
    }

    protected void showNotify(boolean shown) {
    }

    /** Bridges for the host (protected methods are not reachable from wipi.host). */
    public final boolean hostKey(int type, int key) {
        return keyNotify(type, key);
    }

    public final void hostShow(boolean shown) {
        showNotify(shown);
    }
}
