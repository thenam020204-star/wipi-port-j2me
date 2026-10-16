package hl4.game;

/** The original's PointerList: 128 slots + a count (PointerList_* @0x96840..): _npcList, _monsterList, _targetEffectList, the item bag's pointer list. */
public final class PList {
    public final Obj[] a = new Obj[128];
    public int count;

    /** PointerList_release */
    public void release() {
        for (int i = 0; i < 128; i++) {
            a[i] = null;
        }
        count = 0;
    }

    public boolean isEmpty() {
        return count < 1;
    }

    /** PointerList_addObject @0x968b0 */
    public void add(Obj o) {
        if (count == 0x80) {
            return;
        }
        for (int i = 0; i < 128; i++) {
            if (a[i] == null) {
                a[i] = o;
                count++;
                return;
            }
        }
    }

    /** PointerList_remove @0x96850 (slot 127 is never examined: PointerList_isEnd(i) = i > 0x7e) */
    public void remove(Obj o) {
        int i = 0;
        for (;;) {
            if (i > 0x7e) {
                return;
            }
            if (a[i] == o) {
                break;
            }
            i++;
        }
        a[i] = null;
        if (count > 0) {
            count--;
        }
    }
}
