package hl4.game;

/**
 * The original's LinkedList struct {head, tail, size} threaded through the objects' prev/next (LinkedList_* @0x9682c..).
 * 'head' is the first object (OBJ prev == null), 'tail' the last.  Insertion keeps the list sorted by OBJ_getSortY only locally:
 * LinkedList_addObject appends and then moves the new node past neighbours that violate the order (one bubble pass, kept as is).
 */
public final class LList {
    public Obj head;
    public Obj tail;
    public int size;

    /** LinkedList_init */
    public void init() {
        head = null;
        tail = null;
        size = 0;
    }

    /** LinkedList_isEmpty: size == 0 */
    public boolean isEmpty() {
        return size == 0;
    }

    /** LinkedList_addLastObject (no size change) */
    public void addLastObject(Obj n) {
        n.prev = tail;
        n.next = null;
        if (tail != null) {
            tail.next = n;
        }
        tail = n;
        if (head == null) {
            head = n;
        }
    }

    /** LinkedList_addFirstObject (no size change) */
    public void addFirstObject(Obj n) {
        n.prev = null;
        n.next = head;
        if (head != null) {
            head.prev = n;
        }
        head = n;
        if (tail == null) {
            tail = n;
        }
    }

    /** LinkedList_addObject @0x96f8c */
    public void addObject(Obj n) {
        if (n == null) {
            return;
        }
        if (size == 0) {
            addLastObject(n);
        } else {
            addLastObject(n);
            sort(n);
        }
        size++;
    }

    /** LinkedList_sort @0x96dfc */
    public void sort(Obj n) {
        if (n == null || size == 0) {
            return;
        }
        if (n.prev != null) {
            if (n.sortY() < n.prev.sortY()) {
                Obj p = n.prev;
                p.next = n.next;
                if (n.next == null) {
                    tail = p;
                } else {
                    n.next.prev = p;
                }
                Obj q = n.prev;
                for (;;) {
                    if (q == null) {
                        n.prev = null;
                        n.next = head;
                        if (head != null) {
                            head.prev = n;
                        }
                        head = n;
                        if (tail == null) {
                            tail = n;
                        }
                        return;
                    }
                    if (q.sortY() <= n.sortY()) {
                        break;
                    }
                    q = q.prev;
                }
                Obj r = q.next;
                if (r == null) {
                    return;
                }
                n.next = r;
                r.prev = n;
                q.next = n;
                n.prev = q;
                return;
            }
        }
        if (n.next != null) {
            if (n.next.sortY() < n.sortY()) {
                Obj nx = n.next;
                nx.prev = n.prev;
                if (n.prev == null) {
                    head = nx;
                } else {
                    n.prev.next = nx;
                }
                Obj q = n.next;
                for (;;) {
                    if (q == null) {
                        n.prev = tail;
                        n.next = null;
                        if (tail != null) {
                            tail.next = n;
                        }
                        tail = n;
                        if (head == null) {
                            head = n;
                        }
                        return;
                    }
                    if (n.sortY() < q.sortY()) {
                        break;
                    }
                    q = q.next;
                }
                Obj p = q.prev;
                n.prev = p;
                p.next = n;
                q.prev = n;
                n.next = q;
            }
        }
    }

    /**
     * LinkedList_remove @0x96fd8: unlinks 'n' (searched from the head; returns false when absent), returns the object to its pool
     * (MP_return) and decrements the size.
     */
    public boolean remove(Obj n) {
        Obj p = head;
        while (p != null && p != n) {
            p = p.next;
        }
        if (p == null) {
            return false;
        }
        Obj pv = p.prev;
        if (pv != null) {
            pv.next = p.next;
        } else {
            head = p.next;
        }
        if (pv == null) {
            pv = p.prev;
        }
        if (p.next == null) {
            tail = pv;
        } else {
            p.next.prev = pv;
        }
        p.next = null;
        p.prev = null;
        Pool.ret(p);
        size--;
        return true;
    }

    /** LinkedList_release @0x970f4: removes every node from the tail backwards, then clears the head */
    public void release() {
        Obj p = tail;
        while (p != null) {
            Obj pv = p.prev;
            remove(p);
            p = pv;
        }
        size = 0;
        head = null;
        tail = null;
    }

    /** LinkedList_find @0x96984 (index counted from the head; the original returns the node AFTER 'idx' links) */
    public Obj getAt(int idx) {
        if (size == 0) {
            return null;
        }
        Obj p = head;
        if (p == null) {
            return null;
        }
        if (idx != 0) {
            short k = 0;
            do {
                p = p.next;
                k++;
                if (p == null) {
                    return null;
                }
            } while (idx != k);
        }
        return p;
    }
}
