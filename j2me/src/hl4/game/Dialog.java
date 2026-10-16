package hl4.game;

import hl4.gfx.Frame;
import hl4.gfx.GfxOut;
import hl4.gfx.Hf;
import hl4.gfx.Prim;
import hl4.gfx.Sprite;
import hl4.sys.Bytes;

/**
 * The cut-scene dialog windows (Event_ScriptWindow_Open/Close @0x5be1c/0x5bac0, Event_TalkBoxReset/Tick @0x5a3a4/0x599e0, Event_Set_DrawScriptNumber @0x58a58,
 * Event_LoadTextData @0x5bd84, Event_Draw_ScriptWindow @0x5afb8, Event_Draw_Talk @0x5ad90).  Four 0xbc-byte slots at _event+0x214: +0 open, +1 line id,
 * +0x18 text pointer (kept here in {@link #text}), +0x1c text length, +0x20 working text pointer, +0x24 speaker name (kept in {@link #name}), +0x18 / +0x28 enter
 * and leave side + step, +0x30 / +0x34 reveal window, face sprite at +0x48 (kept in {@link Faces}).  The draw order list is _event[0x510..0x513] (-1 = free).
 */
public final class Dialog {
    private Dialog() {
    }

    private static final byte[] e = G.event;

    /** slot +0x218: the text (a scene string, NUL terminated); null = pointer 0 */
    public static final byte[][] text = new byte[4][];
    /** slot +0x224: the speaker name "NAME_DAT" entry (length byte stripped, NUL terminated) */
    public static final byte[][] name = new byte[4][];

    /** the window slide length in steps (C.25.19252 / C.27.19283 {10, 7, 4, 2}: third short) */
    private static final int STEPS = 4;

    /** "=%s " (the '=' and the trailing blank are 4-pixel spaces of the text engine); sprintk buffer of 20 bytes */
    private static final byte[] nameBuf = new byte[20];

    /** stack layout of Event_Draw_ScriptWindow: a[0..13] */
    private static final int[] a = new int[14];

    /** pointer stand-in written into _event where the original keeps a heap address */
    private static final int PTR = 1;

    /** Event_Init_ScriptData's pointer part: +0x218, +0x220, +0x224 */
    public static void clear(int k) {
        text[k] = null;
        name[k] = null;
    }

    private static int slot(int k) {
        return k * 0xbc;
    }

    /** Event_Set_DrawScriptNumber(slot, op): 0 = append, 1 = remove, 2 = move to the end of the draw order */
    public static void setDrawScriptNumber(int p1, int p2) {
        if (p2 == 0) {
            compact();
            for (int k = 0; k < 4; k++) {
                if (e[0x510 + k] == -1) {
                    e[0x510 + k] = (byte) p1;
                    return;
                }
            }
        } else if (p2 == 1) {
            for (int k = 0; k < 4; k++) {
                if (e[0x510 + k] == p1) {
                    e[0x510 + k] = (byte) 0xff;
                    break;
                }
            }
            compact();
        } else if (p2 == 2) {
            int last = -1;
            for (int k = 3; k >= 0; k--) {
                if (e[0x510 + k] != -1) {
                    if (p1 == e[0x510 + k]) {
                        return;
                    }
                    last = k;
                    break;
                }
            }
            int j;
            if (e[0x510] == p1) {
                j = 0;
            } else if (e[0x511] == p1) {
                j = 1;
            } else if (e[0x512] == p1) {
                j = 2;
            } else {
                return;
            }
            for (int t = j + 1; t <= 3; t++) {
                if (e[0x510 + t - 1] != -1) {
                    e[0x510 + t - 1] = e[0x510 + t];
                }
            }
            e[0x510 + last] = (byte) p1;
        }
    }

    /** one bubble pass: an empty entry takes the next one's value */
    private static void compact() {
        for (int k = 0; k < 3; k++) {
            if (e[0x510 + k] == -1) {
                e[0x510 + k] = e[0x510 + k + 1];
                e[0x510 + k + 1] = (byte) 0xff;
            }
        }
    }

    /** Event_TalkBoxReset(textId, slot, narration) */
    public static void talkBoxReset(int textId, int k, int narration) {
        int p = slot(k);
        e[0x5dc] = (byte) narration;
        e[p + 0x214] = 1;
        e[p + 0x240] = 1;
        e[p + 0x215] = (byte) textId;
        e[p + 0x23f] = 0;
        e[0x205] = (byte) k;
        byte[] s = It.getString(textId & 0xff);
        text[k] = s;
        if (s != null) {
            Bytes.w32(e, p + 0x21c, s.length - 1);
            Bytes.w32(e, p + 0x218, PTR);
        } else {
            Bytes.w32(e, p + 0x218, 0);
        }
        Bytes.w32(e, p + 0x258, 0);
        Bytes.w32(e, p + 0x250, Bytes.s32(e, p + 0x21c));
        Bytes.w32(e, p + 0x254, -1);
        Bytes.w32(e, p + 0x244, 0);
        Bytes.w32(e, p + 0x24c, 0);
        Bytes.w32(e, p + 0x248, 0);
        setDrawScriptNumber(k, 2);
    }

    /** Event_LoadTextData(slot, nameIdx) @0x5bd84: the speaker name from NPC/NAME_DAT */
    private static void loadTextData(int k, int idx) {
        byte[] d = hl4.sys.Res.load("/NPC/NAME_DAT");
        if (d != null) {
            int off = Items.getOffset(d, (short) idx);
            int n = d[off] & 0xff;
            byte[] nm = new byte[n + 1];
            System.arraycopy(d, off + 1, nm, 0, n);
            name[k] = nm;
            Bytes.w32(e, slot(k) + 0x224, PTR);
        }
    }

    /** Event_ScriptWindow_Open(slot): first call sets the slot up and starts the slide-in, then one step per call; always 1 */
    public static int scriptWindowOpen(int k) {
        int p = slot(k);
        if (e[p + 0x214] == 0) {
            Event.initScriptData(k);
            setDrawScriptNumber(k, 0);
            e[p + 0x214] = 1;
            e[p + 0x23c] = 1;
            e[p + 0x22c] = e[0x506];
            e[p + 0x22d] = 2;
            e[p + 0x22e] = e[0x508];
            Bytes.w32(e, p + 0x234, 0);
            e[p + 0x230] = e[0x509];
            Bytes.w32(e, p + 0x238, STEPS);
            loadTextData(k, e[p + 0x230]);
            int face = e[p + 0x22e] & 0xff;
            if (face != 0xff) {
                e[p + 0x22f] = (byte) Faces.motion(face);
                Faces.load(k, face);
            }
        } else {
            int st = Bytes.s32(e, p + 0x234) + 1;
            Bytes.w32(e, p + 0x234, st);
            if (st >= Bytes.s32(e, p + 0x238)) {
                e[p + 0x23c] = 0;
                Bytes.w32(e, 0x200, 0x2e);
                e[0x504] = (byte) k;
                talkBoxReset(e[0x50e] & 0xff, e[0x50d] & 0xff, 0);
            }
        }
        return 1;
    }

    /**
     * Event_ScriptWindow_Close(slot or 4 = all) @0x5bac0.  As compiled, a slot that is open is stepped inside one call until its slide-out is finished
     * (the loop re-enters the same slot without leaving the function); 1 = work was done and the command goes on, 0 = finished.
     */
    public static int scriptWindowClose(int p1) {
        int cur = p1 == 4 ? 0 : p1;
        int count = 0;
        for (;;) {
            int limit = p1 == 4 ? 3 : p1;
            int r5 = (byte) cur;
            if (limit < r5) {
                return count != 0 ? 1 : 0;
            }
            int p = slot(r5);
            if (e[p + 0x214] == 0) {
                cur = (cur + 1) & 0xff;
                continue;
            }
            if (e[p + 0x23e] != 0) {
                int side = ((cur + 1) & 0xff) <= 2 ? 1 : 0;
                e[p + 0x23d] = 1;
                e[p + 0x23e] = 0;
                e[0x50b] = (byte) side;
                e[p + 0x22c] = (byte) side;
                e[p + 0x22d] = 2;
                Bytes.w32(e, p + 0x234, 0);
                Bytes.w32(e, p + 0x238, STEPS);
            }
            int st = Bytes.s32(e, p + 0x234) + 1;
            Bytes.w32(e, p + 0x234, st);
            if (st >= Bytes.s32(e, p + 0x238) + 1) {
                e[p + 0x214] = 0;
                e[p + 0x23d] = 0;
                Event.initScriptData(cur & 0xff);
                setDrawScriptNumber(r5, 1);
                Event.releaseFaceimage(cur & 0xff);
                Bytes.w32(e, 0x200, -1);
                if (e[0x204] != 0) {
                    Bytes.w32(e, 0x200, 0x3c);
                    e[0x204] = 0;
                    return 1;
                }
                if (p1 != 4 || r5 == 3) {
                    return 0;
                }
            }
            count = (count + 1) & 0xff;
        }
    }

    /** Event_TalkBoxTick(slot) @0x599e0: lays out the next text page on request and reveals one glyph per tick; 1 = still revealing */
    public static int talkBoxTick(int k) {
        int p = slot(k);
        byte[] s = text[k];
        if (s == null) {
            return 0;
        }
        if (e[p + 0x240] != 0) {
            int maxW;
            int lines;
            if (e[0x5dc] != 0) {
                maxW = 0x96;
                lines = 4;
            } else if ((e[p + 0x22e] & 0xff) == 0xff) {
                maxW = Glob.gameW - 0xf;
                lines = 3;
            } else {
                maxW = Glob.gameW - 0x64;
                lines = 3;
            }
            int shown = Bytes.s32(e, p + 0x248);
            int n = Hf.byteLenPrintable(s, Bytes.s32(e, p + 0x21c), Bytes.s32(e, p + 0x244), maxW, lines, 1);
            e[p + 0x240] = 0;
            Bytes.w32(e, p + 0x248, n + shown);
            Bytes.w32(e, p + 0x24c, Bytes.s32(e, p + 0x244));
        }
        int cur = Bytes.s32(e, p + 0x24c);
        if (cur < Bytes.s32(e, p + 0x248)) {
            int start = Bytes.s32(e, p + 0x244);
            int r = Hf.nextLen(s, Bytes.s32(e, p + 0x21c), start, cur - start) + start;
            Bytes.w32(e, p + 0x24c, r);
            if (Bytes.s32(e, p + 0x248) <= r) {
                Bytes.w32(e, p + 0x24c, Bytes.s32(e, p + 0x248));
            }
            return 1;
        }
        return 0;
    }

    /** Event_Draw_ScriptWindow(frame, slot, active) @0x5afb8: the panel with its slide animation, the speaker tag and the face */
    public static void drawScriptWindow(Frame f, int k, boolean active) {
        int p = slot(k);
        int w = Glob.gameW;
        int h = Glob.gameH;
        if (e[p + 0x214] == 0) {
            return;
        }
        a[0] = h - 0x51;
        a[1] = h - 0x51;
        a[2] = -4;
        a[3] = -4;
        a[4] = 0;
        a[5] = 0;
        a[6] = 0;
        a[7] = 0;
        a[8] = 8;
        a[9] = 0xc;
        a[10] = 0x18;
        a[11] = w / 9;
        a[12] = w / 6;
        a[13] = w / 3;
        int y0 = a[k];
        int x0 = a[k + 4];
        int stepA = a[8 + (e[p + 0x22d] & 0xff)];
        int stepB = a[11 + (e[p + 0x22d] & 0xff)];
        int st = Bytes.s32(e, p + 0x234);
        int code = e[p + 0x22c] & 0xff;
        boolean opening = e[p + 0x23c] != 0;
        boolean closing = e[p + 0x23d] != 0;
        boolean right = !(k == 0 || k == 2);                      // odd slots: face on the right
        int y = 0;
        int x = 0;
        int fy = 0x45;
        if (!right) {
            if (!opening) {
                if (!closing) {
                    y = y0;
                    x = x0;
                    fy = y + 0x45;
                } else if (code == 2) {
                    y = y0;
                    x = x0 - st * stepB;
                    fy = y + 0x45;
                } else if (code == 1) {
                    y = y0 + st * stepA;
                    x = x0;
                    fy = y + 0x45;
                } else if (code == 0) {
                    y = y0 - st * stepA;
                    x = x0;
                    fy = y + 0x45;
                }
            } else if (code == 3) {
                y = y0;
                x = (st * stepB - w) + x0;
                fy = y + 0x45;
            } else if (code == 0) {
                int t = y0 - st * stepA;
                fy = t + 0x8d;
                y = t + 0x48;
                x = x0;
            } else if (code == 1) {
                y = (st * stepA - 0x48) + y0;
                x = x0;
                fy = y + 0x45;
            }
        } else if (!opening) {
            if (!closing) {
                y = y0;
                x = x0;
                fy = y + 0x45;
            } else if (code == 3) {
                y = y0;
                x = st * stepB + x0;
                fy = y + 0x45;
            } else if (code == 0) {
                y = y0 - st * stepA;
                x = x0;
                fy = y + 0x45;
            } else if (code == 1) {
                y = st * stepA + y0;
                x = x0;
                fy = y + 0x45;
            }
        } else if (code == 2) {
            y = y0;
            x = (x0 - st * stepB) + w;
            fy = y + 0x45;
        } else if (code == 1) {
            y = (st * stepA - 0x48) + y0;
            x = x0;
            fy = y + 0x45;
        } else if (code == 0) {
            int t = y0 - st * stepA;
            fy = t + 0x8d;
            y = t + 0x48;
            x = x0;
        }
        int faceY = (k <= 1 ? 9 : 0x14) + fy;
        int faceX = right ? w : 0;
        int mirror = right ? 1 : 0;
        int c = active ? 0x24 : 0x36;
        GMenu.backFrameMain(f, 0, x, y + 0x15, 0, w - 1, 0x32, c, c, c, 0x19);
        if (e[p + 0x214] != 0 && e[p + 0x23d] == 0 && e[p + 0x23c] == 0) {
            byte[] nm = name[k];
            if (nm != null) {
                int n = nm.length - 1;
                if (n > 17) {
                    n = 17;
                }
                nameBuf[0] = 0x3d;
                System.arraycopy(nm, 0, nameBuf, 1, n);
                nameBuf[n + 1] = 0x20;
                nameBuf[n + 2] = 0;
                int len = n + 2;
                int tx = x;
                if (!right) {
                    tx = (w - Hf.width(nameBuf, len * 7, 0)) - 4;
                }
                int tw = Hf.width(nameBuf, len * 7, 0);
                GMenu.backFrameMain(f, 0, tx, y + 0xb, 0, tw + 4, 0x11, c, c, c, 0x19);
                if (!active) {
                    Hf.setColor(0x9a, 0x9a, 0x9a);
                } else {
                    Hf.setColor(0xff, 0xff, 0xff);
                }
                int nx;
                if (right) {
                    nx = x + 2;
                } else {
                    nx = (w - Hf.width(nameBuf, len * 7, 0)) - 2;
                }
                Hf.drawText(f, nx, y + 0xf, nameBuf, 0);
            }
            if ((e[p + 0x22e] & 0xff) != 0xff) {
                Sprite s = Faces.spr[k];
                if (!active || Sprite.lcdEff[10] == 2) {
                    s.setControlPal(0, 0, 0, 0xff, 0x10);
                } else {
                    s.f68 = 0;
                }
                s.drawSprite(GfxOut.I, f, e[p + 0x22f], 0, s.f6c, faceX, faceY, mirror, 0);
                s.repeatFrame();
                s.f68 = 0;
            }
        }
    }

    /** Event_Draw_Talk(frame, slot, active) @0x5ad90: the revealed part of the current page */
    public static void drawTalk(Frame f, int k, boolean active) {
        int p = slot(k);
        boolean face = (e[p + 0x22e] & 0xff) != 0xff;
        int tx = face ? 0x5a : 5;
        int ty = Glob.gameH - 0x32;
        int x;
        int y;
        switch (k) {
            case 0:
                x = tx;
                y = ty;
                break;
            case 1:
                x = 5;
                y = ty;
                break;
            case 2:
                x = tx;
                y = 0x1b;
                break;
            default:
                x = 5;
                y = 0x1b;
                break;
        }
        if (e[p + 0x214] != 0 && e[p + 0x23d] == 0 && e[p + 0x23c] == 0 && text[k] != null) {
            int maxW = face ? Glob.gameW - 0x5f : Glob.gameW - 10;
            if (!active) {
                Hf.setColor(0x9a, 0x9a, 0x9a);
                Hf.drawColor(f, x, y, maxW, 0, text[k], Bytes.s32(e, p + 0x244), 0, Bytes.s32(e, p + 0x24c), 0, Prim.pixel(0x9a, 0x9a, 0x9a));
            } else {
                Hf.setColor(0xff, 0xff, 0xff);
                Hf.drawAnchorH2(f, x, y, maxW, 0, text[k], Bytes.s32(e, p + 0x244), 0, Bytes.s32(e, p + 0x24c), 0);
            }
        }
    }
}
