package hl4.game;

import hl4.sys.Bytes;

/**
 * Map collision (tile property = high nibble of layer B: 0, 7, 8, 9, 10 are walkable) and character movement checks:
 * Map_Get_TIleProperty @0xa1438, Map_canMove, Map_Check_CollisionZone_n_Rect @0xa1cf8, ..._Move @0xa1a58, ..._MoveFlag @0xa1724,
 * CHAR_canMove @0x49d70, CHAR_isRectMoveable(_Tele) @0x49c64, CHAR_checkCollision @0x49a34, CHAR_isMoveable @0x49e7c.
 */
public final class Coll {
    private static final byte[] m = G.map;

    private Coll() {
    }

    /** Map_Get_TIleProperty(x, y): tile coordinates are signed bytes in the callers; indexes outside the layer read as 1 (blocked; the original reads unrelated memory) */
    public static int tileProp(int x, int y) {
        byte[] b = Maps.layerB;
        int i = x + m[0x7a] * y;
        if (b == null || i < 0 || i >= b.length) {
            return 1;
        }
        return (b[i] & 0xff) >> 4;
    }

    public static boolean canMoveTile(int p) {
        return p == 0 || p == 7 || p == 8 || p == 9 || p == 10;
    }

    /** Map_canMove(x, y) with the (char) truncation of the callers */
    public static boolean canMove(int x, int y) {
        return canMoveTile(tileProp((byte) x, (byte) y));
    }

    /** Map_Check_CollisionZone_n_Rect: every tile under the rectangle is walkable */
    public static boolean nRect(int p1, int p2, int p3, int p4) {
        if (p3 > 0 && p4 > 0) {
            int tw = m[0x7e];
            int th = m[0x7f];
            int x0 = p1 / tw;
            int y0 = p2 / th;
            int x1 = (p1 + p3) / tw;
            int y1 = (p4 + p2) / th;
            if ((p1 + p3) % tw != 0) {
                x1++;
            }
            if ((p4 + p2) % th != 0) {
                y1++;
            }
            for (int y = y0; y < y1; y++) {
                for (int x = x0; x < x1; x++) {
                    if (!canMove(x, y)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** Map_Check_CollisionZone_n_Rect_MoveFlag(out, x, y, w, h, flag): first blocked tile scanning in the direction of flag (0 left, 1 right, 2 up, 3 down); out = (-10, -10) when none */
    public static void moveFlag(int[] o, int p2, int p3, int p4, int p5, int flag) {
        if (p4 > 0 && p5 > 0) {
            int right = p4 + p2;
            int bottom = p3 + p5;
            int tw = m[0x7e];
            int th = m[0x7f];
            int c0 = p2 / tw;
            int r0 = p3 / th;
            int c1 = right / tw;
            int r1 = bottom / th;
            switch (flag) {
                case 0:
                    if (right % tw == 0) {
                        c1--;
                    }
                    if (bottom % th != 0) {
                        r1++;
                    }
                    for (; c0 - 1 < c1; c1--) {
                        for (int r = r0; r < r1; r++) {
                            if (!canMove(c1, r)) {
                                o[0] = c1;
                                o[1] = r;
                                return;
                            }
                        }
                    }
                    break;
                case 1:
                    if (right % tw != 0) {
                        c1++;
                    }
                    if (bottom % th != 0) {
                        r1++;
                    }
                    for (; c0 < c1; c0++) {
                        for (int r = r0; r < r1; r++) {
                            if (!canMove(c0, r)) {
                                o[0] = c0;
                                o[1] = r;
                                return;
                            }
                        }
                    }
                    break;
                case 2:
                    if (right % tw != 0) {
                        c1++;
                    }
                    if (bottom % th == 0) {
                        r1--;
                    }
                    for (; r0 - 1 < r1; r1--) {
                        for (int c = c0; c < c1; c++) {
                            if (!canMove(c, r1)) {
                                o[0] = c;
                                o[1] = r1;
                                return;
                            }
                        }
                    }
                    break;
                case 3:
                    if (right % tw != 0) {
                        c1++;
                    }
                    if (bottom % th != 0) {
                        r1++;
                    }
                    for (; r0 < r1; r0++) {
                        for (int c = c0; c < c1; c++) {
                            if (!canMove(c, r0)) {
                                o[0] = c;
                                o[1] = r0;
                                return;
                            }
                        }
                    }
                    break;
                default:
                    break;
            }
        }
        o[0] = -10;
        o[1] = -10;
    }

    /** Map_Check_CollisionZone_Move(x, y, w, h, dx[0], dy[0]): shortens an axis-aligned move so it stops at a blocked tile (diagonals are left to the final rectangle test) */
    public static void zoneMove(int p1, int p2, int p3, int p4, int[] dx, int[] dy) {
        int ux = dx[0];
        int uy = dy[0];
        if (uy == 0 && ux == 0) {
            return;
        }
        if (m[0x270] < 1) {
            return;
        }
        boolean xs = ux != 0;
        boolean ys = uy != 0;
        int[] o = new int[2];
        int tw = m[0x7e];
        int th = m[0x7f];
        int kind;
        if (!(xs && ys)) {
            if (xs) {
                kind = ux < 0 ? 0 : 1;
            } else if (ys) {
                kind = uy < 0 ? 2 : 3;
            } else {
                return;
            }
        } else {
            return;
        }
        switch (kind) {
            case 0: {
                int a = ux < 0 ? -ux : ux;
                if (p1 % tw < a) {
                    moveFlag(o, p1 - a, p2, a, p4, 0);
                    if (o[0] != -10) {
                        dx[0] = (tw * o[0] + tw) - p1;
                    }
                }
                break;
            }
            case 1: {
                int r = p3 + p1;
                int rem = r % tw;
                if (rem == 0 || tw - rem < ux) {
                    moveFlag(o, r, p2, ux, p4, 1);
                    if (o[0] != -10) {
                        dx[0] = tw * o[0] - r;
                    }
                }
                break;
            }
            case 2: {
                int a = uy < 0 ? -uy : uy;
                if (p2 % th < a) {
                    moveFlag(o, p1, p2 - a, p3, a, 2);
                    if (o[1] != -10) {
                        dy[0] = (th * o[1] + th) - p2;
                    }
                }
                break;
            }
            default: {
                int b = p4 + p2;
                int rem = b % th;
                if (rem == 0 || th - rem < uy) {
                    moveFlag(o, p1, b, p3, uy, 3);
                    if (o[1] != -10) {
                        dy[0] = th * o[1] - b;
                    }
                }
                break;
            }
        }
    }

    /** CHAR_getHeroMoveRect: refreshes the hero's cached move rectangle */
    public static void heroMoveRect() {
        Obj h = Pool.hero;
        Bytes.w32(h.m, 0x148, Chars.getMoveRectX(h));
        Bytes.w32(h.m, 0x14c, Chars.getMoveRectY(h));
        Bytes.w32(h.m, 0x150, Chars.getMoveRectW(h));
        Bytes.w32(h.m, 0x154, Chars.getMoveRectH(h));
    }

    /** CHAR_checkCollision(c, x, y, w, h, withHero, withMonsters) @0x49a34: another character occupies the rectangle */
    public static boolean charCheckCollision(Obj c, int x, int y, int w, int h, boolean withHero, boolean withMon) {
        int[] o = new int[4];
        if (Event.isEvent()) {
            return false;
        }
        if (withMon && !G.monsterList.isEmpty()) {
            for (int i = 0; i < 128; i++) {
                Obj mo = G.monsterList.a[i];
                if (mo != null && mo != c && Monsters.meetHero(mo) && mo.m[0x33f] != 5 && !Monsters.isDie(mo) && !Monsters.isHidding(mo)
                        && Um.collision(x, y, w, h, Bytes.s32(mo.m, 0x148), Bytes.s32(mo.m, 0x14c), Bytes.s32(mo.m, 0x150), Bytes.s32(mo.m, 0x154), o) < 0) {
                    return true;
                }
            }
        }
        if (!G.npcList.isEmpty()) {
            for (int i = 0; i < 128; i++) {
                Obj n = G.npcList.a[i];
                if (n != null && n != c
                        && Um.collision(x, y, w, h, Bytes.s32(n.m, 0x148), Bytes.s32(n.m, 0x14c), Bytes.s32(n.m, 0x150), Bytes.s32(n.m, 0x154), o) < 0) {
                    return true;
                }
            }
        }
        if (withHero && !Chars.isHero(c)) {
            heroMoveRect();
            Obj hr = Pool.hero;
            return Um.collision(x, y, w, h, Bytes.s32(hr.m, 0x148), Bytes.s32(hr.m, 0x14c), Bytes.s32(hr.m, 0x150), Bytes.s32(hr.m, 0x154), o) < 0;
        }
        return false;
    }

    /** CHAR_isRectMoveable_Tele(c, x, y, w, h, withHero, ignoreEvent, withMonsters) @0x49c64 */
    public static boolean isRectMoveableTele(Obj c, int x, int y, int w, int h, boolean withHero, boolean ignoreEvent, boolean withMon) {
        if (!nRect(x, y, w, h)) {
            return false;
        }
        if (ignoreEvent || !Event.isEvent()) {
            if (charCheckCollision(c, x, y, w, h, withHero, withMon)) {
                return false;
            }
            if (withHero) {
                heroMoveRect();
                Obj hr = Pool.hero;
                int[] o = new int[4];
                int u = Um.collision(x, y, w, h, Bytes.s32(hr.m, 0x148), Bytes.s32(hr.m, 0x14c), Bytes.s32(hr.m, 0x150), Bytes.s32(hr.m, 0x154), o);
                return u == 0;
            }
        }
        return true;
    }

    /** CHAR_isRectMoveable(c, x, y, w, h, withHero, withMonsters) @0x49d24 */
    public static boolean isRectMoveable(Obj c, int x, int y, int w, int h, boolean withHero, boolean withMon) {
        return isRectMoveableTele(c, x, y, w, h, withHero, false, withMon);
    }

    /** CHAR_isRectMoveableTeleport(c, x, y, w, h) @0x49bf0 */
    public static boolean isRectMoveableTeleport(Obj c, int x, int y, int w, int h) {
        if (!nRect(x, y, w, h)) {
            return false;
        }
        if (!Event.isEvent()) {
            return !charCheckCollision(c, x, y, w, h, false, true);
        }
        return true;
    }

    /** CHAR_canMove(c, dx[0], dy[0], withHero, withMonsters) @0x49d70: clamps the step to the map, to blocked tiles and tests the new rectangle */
    public static boolean charCanMove(Obj c, int[] dx, int[] dy, boolean withHero, boolean withMon) {
        int x = Bytes.s32(c.m, 0x148);
        int y = Bytes.s32(c.m, 0x14c);
        int w = Bytes.s32(c.m, 0x150);
        int h = Bytes.s32(c.m, 0x154);
        int nx = x + dx[0];
        if (nx < 0) {
            dx[0] = nx;
            nx = nx + x;
        }
        if (Maps.s32(0x84) < nx) {
            dx[0] = Maps.s32(0x84) - x;
        }
        int ny = y + dy[0];
        if (ny < 0) {
            dy[0] = ny;
            ny = ny + y;
        }
        if (Maps.s32(0x88) < ny) {
            dy[0] = Maps.s32(0x88) - y;
        }
        zoneMove(x, y, w, h, dx, dy);
        if (dx[0] == 0 && dy[0] == 0) {
            return false;
        }
        return isRectMoveable(c, dx[0] + x, dy[0] + y, w, h, withHero, withMon);
    }

    /** CHAR_isMoveable(c, dir) @0x49e7c: could the character take a full-size step in direction dir */
    public static boolean charIsMoveable(Obj c, int dir) {
        int x = Bytes.s32(c.m, 0x148);
        int y = Bytes.s32(c.m, 0x14c);
        int w = Bytes.s32(c.m, 0x150);
        int h = Bytes.s32(c.m, 0x154);
        int[] dx = {w * Um.vecX(dir)};
        int[] dy = {h * Um.vecY(dir)};
        zoneMove(x, y, w, h, dx, dy);
        if (dx[0] != 0 || dy[0] != 0) {
            return isRectMoveable(c, x + dx[0], y + dy[0], w, h, false, true);
        }
        return false;
    }
}
