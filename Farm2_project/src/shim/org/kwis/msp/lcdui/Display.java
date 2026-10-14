package org.kwis.msp.lcdui;

import wipi.host.Host;

/** WIPI-Java Display: a stack of Cards shown on the single host canvas. */
public class Display {
    public static final int KEY_PRESSED = 1;
    public static final int KEY_RELEASED = 2;
    public static final int KEY_REPEATED = 3;

    // WIPI raw key codes
    public static final int KEY_UP = -1;
    public static final int KEY_DOWN = -2;
    public static final int KEY_LEFT = -3;
    public static final int KEY_RIGHT = -4;
    public static final int KEY_FIRE = -5;
    public static final int KEY_COML = -6;
    public static final int KEY_COMR = -7;
    public static final int KEY_CALL = -10;
    public static final int KEY_END = -11;
    public static final int KEY_VOL_UP = -13;
    public static final int KEY_VOL_DOWN = -14;
    public static final int KEY_CLR = -16;

    // WIPI game actions
    public static final int GAME_UP = 1;
    public static final int GAME_LEFT = 2;
    public static final int GAME_RIGHT = 5;
    public static final int GAME_DOWN = 6;
    public static final int GAME_FIRE = 8;

    private static Display def;

    public static synchronized Display getDefaultDisplay() {
        if (def == null) {
            def = new Display();
        }
        return def;
    }

    public static Display getDisplay(String name) {
        return getDefaultDisplay();
    }

    public void pushCard(Card c) {
        Host.push(c);
    }

    public void popCard() {
        Host.pop();
    }

    public void removeAllCards() {
        Host.removeAll();
    }

    public Card getTopCard() {
        return Host.top();
    }

    public int getWidth() {
        return Host.lw;
    }

    public int getHeight() {
        return Host.lh;
    }

    /** WIPI raw key -> game action (0 if the key has none). */
    public static int getGameAction(int key) {
        switch (key) {
        case KEY_UP:
            return GAME_UP;
        case KEY_DOWN:
            return GAME_DOWN;
        case KEY_LEFT:
            return GAME_LEFT;
        case KEY_RIGHT:
            return GAME_RIGHT;
        case KEY_FIRE:
            return GAME_FIRE;
        case KEY_COML:
            return 90;
        case KEY_COMR:
            return 91;
        case KEY_VOL_UP:
            return 96;
        case KEY_VOL_DOWN:
            return 97;
        case KEY_CLR:
            return 99;
        default:
            return 0;
        }
    }

    public static int getKeyCode(int action) {
        switch (action) {
        case GAME_UP:
            return KEY_UP;
        case GAME_DOWN:
            return KEY_DOWN;
        case GAME_LEFT:
            return KEY_LEFT;
        case GAME_RIGHT:
            return KEY_RIGHT;
        case GAME_FIRE:
            return KEY_FIRE;
        case 90:
            return KEY_COML;
        case 91:
            return KEY_COMR;
        case 96:
            return KEY_VOL_UP;
        case 97:
            return KEY_VOL_DOWN;
        case 99:
            return KEY_CLR;
        default:
            return 0;
        }
    }
}
