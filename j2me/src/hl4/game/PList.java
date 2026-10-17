package hl4.game;

/** The original's PointerList: 128 slots + a count (PointerList_* @0x96840..): _npcList, _monsterList, _targetEffectList, the item bag's pointer list. */
public final class PList {
    public final Obj[] a = new Obj[128];
    public int count;

    /** PointerList_release(list) @0x87134: dlib_memset(list, 0, 0x200) + count = 0 */
    public void release() {
        for (int i = 0; i < 128; i++) {
            a[i] = null;
        }
        count = 0;
    }

    /** PointerList_isEmpty(list) @0x868f0: count < 1 (signed) */
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

    /** PointerList_initAll() @0x87154: PointerList_release(_npcList), (_monsterList), tail call on (_targetEffectList) */
    public static void initAll() {
        G.npcList.release();
        G.monsterList.release();
        G.targetEffectList.release();
    }

    /** PointerList_releaseAll() @0x87198: tail call of PointerList_initAll */
    public static void releaseAll() {
        initAll();
    }
}
