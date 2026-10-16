package hl4.game;

import hl4.gfx.Sprite;

/** A sprite object (SprObject_*, pool type 16, 0xa0 bytes): header + SPR struct at +0x20 (here 'spr') + +0x8c frame counter, +0x9c mode, +0x9d id, +0x9e flip flags, +0x9f blend param. */
public final class SObj extends Obj {
    public Sprite spr = new Sprite();

    public SObj(int size, int type, int idx) {
        super(size, type, idx);
    }
}
