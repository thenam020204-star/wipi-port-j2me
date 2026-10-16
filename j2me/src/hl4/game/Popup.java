package hl4.game;

/** Popup boxes / dialog window state (PopupBox_* @0xcbb10..). */
public final class Popup {
    /** _evtBox (48 bytes): the event dialog box state; +0x2c = open flag */
    public static final byte[] evtBox = new byte[48];

    private Popup() {
    }

    /** PopupBox_end(box) @0xcbb10: closes the box, resets the menu-state stack and the Touch skin state */
    public static int end(byte[] box) {
        box[0x2c] = 0;
        Touch.setFrame(0x65);                       // stack pointer = 0 and state 0x65
        return 1;
    }
}
