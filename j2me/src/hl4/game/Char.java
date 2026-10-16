package hl4.game;

import hl4.gfx.Sprite;

/** A character struct (hero / NPC / monster / summon): header + SPR struct at +0x20 (here 'spr') + stats; two embedded lists at +0x130 / +0x13c. */
public class Char extends Obj {
    /** SPR struct at +0x20..+0x93 (hl4.gfx.Sprite holds its own numeric fields; the bytes of that region in 'm' stay unused) */
    public Sprite spr = new Sprite();
    /** common effect list (+0x130) and target-effect list (+0x13c) */
    public final LList effects = new LList();
    public final LList targetEffects = new LList();
    /** hero: the attack-property record of the current motion (+0x22c/+0x230) and the cells of the current frame's entry (+0x238) */
    public hl4.gfx.SprProps.Entry[] hitTable;
    public hl4.gfx.SprProps.Cell[] hitCells;
    /** monster: the AI script state behind the heap pointers of +0x214..+0x264 (EnemyAIParsing_LoadData) */
    public EnemyAi ai;

    public Char(int size, int type, int idx) {
        super(size, type, idx);
    }
}
