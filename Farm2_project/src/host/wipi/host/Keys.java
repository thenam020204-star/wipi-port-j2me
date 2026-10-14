package wipi.host;

import javax.microedition.lcdui.Canvas;

/**
 * MIDP key code -> WIPI raw key code.
 * Digits, '*' and '#' are identical. Arrow/fire keys become WIPI -1..-5.
 * Left soft key = WIPI soft-1 (-6, game action 90: opens the farm menu),
 * right soft key and the 'C'/clear key = WIPI CLR (-16, action 99: back),
 * because most non-Korean handsets have no CLR key.
 */
public final class Keys {
    public static final int UP = -1, DOWN = -2, LEFT = -3, RIGHT = -4, FIRE = -5, SOFT1 = -6, SOFT2 = -7, CLR = -16;

    public static int toWipi(Canvas c, int k) {
        if (k >= '0' && k <= '9' || k == '*' || k == '#') {
            return k;
        }
        switch (k) {
        case -6: case -21: case 21: case -202: case 57345:
            return SOFT1;
        case -7: case -22: case 22: case -203: case 57346:
            return CLR;
        case -8: case -12: case 8: case -204:
            return CLR;
        case -5: case -20: case 20: case 10: case 13:
            return FIRE;
        default:
            break;
        }
        int a = 0;
        try {
            a = c.getGameAction(k);
        } catch (Throwable t) {
            a = 0;
        }
        switch (a) {
        case Canvas.UP:
            return UP;
        case Canvas.DOWN:
            return DOWN;
        case Canvas.LEFT:
            return LEFT;
        case Canvas.RIGHT:
            return RIGHT;
        case Canvas.FIRE:
            return FIRE;
        default:
            break;
        }
        // qwerty/emulator letters as soft keys
        if (k == 'q' || k == 'Q') {
            return SOFT1;
        }
        if (k == 'w' || k == 'W') {
            return CLR;
        }
        return 0;
    }
}
