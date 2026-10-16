package hl4.game;

import hl4.gfx.SprProps;
import hl4.gfx.Sprite;

/**
 * A target effect (pool type 6, 0xb8 bytes): an Effect header (+0x20 kind, +0x21 effect/motion id, +0x22 frame, +0x23 max frame, +0x24 number,
 * +0x28 cell group, +0x29, +0x2a/+0x2b low bytes of the start position, +0x2c.. digits) plus +0x44 owner, +0x48 sprite, +0x4c body flag, +0x4d delay,
 * +0x4e loop, +0x4f skill / effect index, +0x50, +0x51 attack direction, +0x52, +0x54 attack-property record of the effect motion, +0x5c hit entry
 * of the current frame, +0x64 the copied hit cell (0x44 bytes), +0xa8 life counter, +0xb1/+0xb2/+0xb4/+0xb6 flags.
 * The heap pointers of the original (+0x44, +0x48, +0x54, +0x60) are the fields below.
 */
public final class TEff extends Obj {
    /** +0x44: the character the effect belongs to (the original stores owner + 0x10) */
    public Obj owner;
    /** +0x48 */
    public Sprite spr;
    /** the character whose SPR struct +0x48 points into (null for a global sprite): its header ints (anchor at SPR+0/+4) live in its bytes */
    public Char sprChar;
    /** +0x54 / +0x60 */
    public SprProps.Entry[] hitTable;
    public SprProps.Cell[] hitCells;

    public TEff(int size, int type, int idx) {
        super(size, type, idx);
    }
}
