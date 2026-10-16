package hl4.game;

import hl4.gfx.Frame;
import hl4.gfx.GfxOut;
import hl4.gfx.Img;
import hl4.gfx.Sprite;
import hl4.sys.Bytes;
import hl4.sys.Res;

/**
 * The TOUCH skin layer of the AND2011 UI (Touch_* / TF_* in the original).  Despite the name it is NOT only touch input: it is the
 * data-driven skin of dialogs and menus.  Each UI "state id" (TF_setFrame(id)) names one animation of one of four CIF sprites
 * (TOUCH/CIF/*.cif, drawn by SPR_drawSprite around the SCREEN CENTRE) plus a table of "extradata" anchors (TOUCH/EXD/*.exd) that the
 * game uses to place texts and menus (menuX/menuY, button labels).  Anchor coordinates are relative to the centre, so layouts follow
 * the game size.  Pointer hit-rectangles (touch_Addrect, area codes) are not part of the port: nothing generates pointer events.
 * EXD file: s8 n; per state k: s16 id, s8 hasList, [s8 count, count x {s16 code, s8 type, type 2/3: s16 x, s16 y, s8 z; type 4: s16 x,
 * y, w, h}].  Verified against the original by the per-tick trace (tools/ref_game.py) - see docs/phaseE.md.
 */
public final class Touch {
    public static final int NONE = 0x65;
    private static final String[] NAMES = {"InGame_base", "Main_base", "Network_Base", "Pop_Up"};

    private static final Sprite[] spr = new Sprite[4];
    private static final byte[][] exd = new byte[4][];
    private static final int[] stFile = new int[256];
    private static final int[] stAnim = new int[256];
    private static final int[] stOff = new int[256];

    /** game area centre (g_touchInfo+0x10/+0x14) and size (+0x18/+0x1c); set by standInit */
    public static int cx;
    public static int cy;
    public static int gw;
    public static int gh;

    private static int curId = -1;       // iRam000fd350
    private static int curFile = -1;     // _DAT_000fd340
    private static int curAnim = -1;     // uRam000fd344
    private static int count;            // iRam000fd33c
    private static final int[][] ex = new int[40][6];     // x, y, z/w, h, code, type
    private static final int[] NULLX = new int[6];        // s_nullExtradata
    private static final int[] stack = new int[4];
    private static int sp;

    private Touch() {
    }

    /** Touch_Initialize @0x47024. */
    public static void init() {
        for (int i = 0; i < 4; i++) {
            exd[i] = Res.load("/TOUCH/EXD/" + NAMES[i] + ".exd");
            byte[] c = Res.load("/TOUCH/CIF/" + NAMES[i] + ".cif");
            spr[i] = new Sprite(c, 2);
        }
        Sprite first = spr[0];
        for (int i = 0; i < first.nSets && i < 20; i++) {
            first.setImages(i, Img.loadAll("/TOUCH/IMG/" + pad3(i), 1));
        }
        for (int i = 1; i < 4; i++) {            // memcpy(spr + 0x28, first + 0x28, 0x10): the four sprites share the image sets
            spr[i].sets = first.sets;
            spr[i].orig = first.orig;
        }
        for (int f = 0; f < 4; f++) {
            parseIndex(f);
        }
        standInit();
        curId = -1;
        sp = 0;
        loadState(NONE);                 // TouchData_Initialize ends with state 0x65
    }

    private static String pad3(int i) {
        return i < 10 ? "00" + i : (i < 100 ? "0" + i : "" + i);
    }

    /** TouchData_Initialize: record where each state lives (file, animation index, offset of its has-list byte). */
    private static void parseIndex(int f) {
        byte[] d = exd[f];
        int p = 0;
        int n = d[p++];
        for (int k = 0; k < n; k++) {
            int id = Bytes.s16(d, p);
            p += 2;
            stOff[id] = p;
            stAnim[id] = k;
            stFile[id] = f;
            int has = d[p++];
            if (has == 0) {
                continue;
            }
            int c = d[p++];
            for (int i = 0; i < c; i++) {
                p += 2;
                int type = d[p++];
                if (type > 1) {
                    if (type < 4) {
                        p += 5;
                    } else if (type == 4) {
                        p += 8;
                    }
                }
            }
        }
    }

    /** Touch_Stand_Initialize @0x460cc: centre and size follow the game area. */
    public static void standInit() {
        gw = Glob.gameW;
        gh = Glob.gameH;
        cx = gw / 2;
        cy = gh / 2;
        NULLX[0] = -cx;
        NULLX[1] = -cy;
        NULLX[2] = 1900;
        NULLX[3] = 1200;
        NULLX[4] = 0x3cd1;
        NULLX[5] = 4;
    }

    public static int getState() {
        return curId;
    }

    /** TF_setFrame / Touch_setState / Touch_pushState / Touch_popState share this body (@0x460a8). */
    private static void loadState(int id) {
        if (curId == id) {
            return;
        }
        curId = id;
        for (int i = 0; i < ex.length; i++) {
            for (int j = 0; j < 6; j++) {
                ex[i][j] = 0;
            }
        }
        if (id == NONE) {
            curFile = -1;
            count = 0;
            curAnim = -1;
            return;
        }
        int f = stFile[id];
        byte[] d = exd[f];
        int anim = stAnim[id];
        int p = stOff[id];
        if (d == null) {
            return;
        }
        int has = d[p++];
        curFile = f;
        curAnim = anim;
        if (has == 0) {
            count = 0;
            System.arraycopy(NULLX, 0, ex[0], 0, 6);
            return;
        }
        count = d[p++];
        for (int i = 0; i < count; i++) {
            int[] e = ex[i];
            e[4] = Bytes.s16(d, p);
            p += 2;
            int type = d[p++];
            e[5] = type;
            if (type > 1) {
                if (type < 4) {
                    e[0] = Bytes.s16(d, p);
                    e[1] = Bytes.s16(d, p + 2);
                    e[2] = d[p + 4];
                    p += 5;
                } else if (type == 4) {
                    e[0] = Bytes.s16(d, p);
                    e[1] = Bytes.s16(d, p + 2);
                    e[2] = Bytes.s16(d, p + 4);
                    e[3] = Bytes.s16(d, p + 6);
                    p += 8;
                }
            }
        }
    }

    public static void setState(int id) {
        loadState(id);
    }

    public static void setFrame(int id) {
        sp = 0;
        loadState(id);
    }

    public static void pushState(int id) {
        int p = sp;
        int cur = curId;
        sp = 1;
        stack[p] = cur;
        loadState(id);
    }

    public static void popState() {
        sp = 0;
        loadState(stack[0]);
    }

    /** Touch_getExtradataWithAreacode: {x, y, z/w, h, code, type} of the anchor with that area code (the full-screen null anchor if absent). */
    public static int[] extra(int code) {
        for (int i = 0; i < count; i++) {
            if (ex[i][4] == code) {
                return ex[i];
            }
        }
        return NULLX;
    }

    /** debugging aid */
    public static Sprite spriteForDebug() {
        return spr[curFile];
    }

    public static int animForDebug() {
        return curAnim;
    }

    /** Touch_draw @0x467c4: the skin sprite of the current state, drawn around the screen centre. */
    public static boolean draw(Frame f) {
        if (curFile == -1) {
            return false;
        }
        spr[curFile].drawSprite(GfxOut.I, f, curAnim, 0, 0, cx, cy, 0, -1);
        return true;
    }
}
