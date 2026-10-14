import org.kwis.msp.lcdui.Graphics;
import org.kwis.msp.lcdui.Image;
import wipi.host.Txt;

/**
 * In-game HUD drawn over the map (the map now fills the screen; the original
 * 16 px top bar and 32 px bottom bar are gone):
 *   - clock panel, top right: season + day, sky strip with sun/moon (original
 *     art State[12..15], same formula), today's weather (+ tomorrow with the
 *     forecast skill), time;
 *   - HP / FP gauges, bottom right, vertical;
 *   - toolbar: exactly the content of the original action box
 *     (DrawDownAction: action icon + DownMent / money / watering gauge / crop
 *     growth, seed in hand). It sits at the bottom and jumps to the top while
 *     the player stands behind it.
 * Bodies replaced (tools/ui_body.txt): init, DrawUp, DrawDown, DrawHp,
 * DrawTime, DrawDay, DrawWeather, DrawDownAction, DrawUpState, DrawMiniTime.
 * Every game-state write of those methods is kept (copied from CFR, checked
 * against the bytecode); only drawing changed.
 */
final class UiHud {
    // Stardew-like wood frame
    static final int WOOD_D = 0x5B2E0E, WOOD = 0xC8803A, WOOD_L = 0xE9B66A, PAPER = 0xF8DDA6, SLOT = 0xF1C886;

    /** GameCanvas.init: as the original, but the map takes the whole height (no bars). */
    static void init(GameCanvas gc) {
        gc.bsize = 16;
        gc.width = gc.getWidth();
        gc.height = gc.getHeight(); // was getHeight() - 48 (top 16 + bottom 32 bars)
        gc.big = (gc.width - 176) / 2;
        gc.ubig = (gc.width - 176) / 2;
        if (gc.width >= 200) {
            gc.bigstate = true;
            gc.ubig = (gc.width - 186) / 2;
        }
        gc.w = gc.width % gc.bsize / 2;
        gc.h = gc.height % gc.bsize / 2; // was + 16 (top bar)
        gc.maxx = gc.width / gc.bsize;
        gc.maxy = gc.height / gc.bsize;
        gc.bw = gc.maxx * gc.bsize;
        gc.bh = gc.maxy * gc.bsize;
        gc.mapx = 20;
        gc.mapy = 20;
        UiHud.gc = gc;
    }

    // ------------------------------------------------------------ replaced bar methods

    /** DrawUp: the top bar buffer is not shown any more; the HUD is drawn by DrawHp. */
    static void DrawUp(GameCanvas gc, Graphics g) {
        gc.URepaint = false;
    }

    /** DrawDown: as the original (bar + DrawHp), the bar being the HUD now. */
    static void DrawDown(GameCanvas gc, Graphics g, boolean bl) {
        gc.DrawHp(g);
        if (bl) {
            gc.DRepaint = false;
        }
    }

    /** DrawTime / DrawDay / DrawWeather: part of the clock panel, drawn by DrawHp. */
    static void none(GameCanvas gc, Graphics g) {
    }

    /** DrawHp: last HUD call of every PlayGame frame -> clock, gauges, tutorial marker. */
    static void DrawHp(GameCanvas gc, Graphics g) {
        UiHud.gc = gc;
        javax.microedition.lcdui.Graphics m = g.g;
        UiKit.saveClip(g);
        m.setClip(0, 0, gc.getWidth(), gc.getHeight());
        clock(gc, g);
        boolean talk = gc.State == 6 || gc.State == 9;
        if (!talk) {
            gauges(gc, g);
        }
        if (tutTick == UiKit.tick || tutTick == UiKit.tick - 1) {
            if (tut == 4 && toolTick != UiKit.tick) {
                toolbar(gc, g);
            }
            tutMark(gc, g);
        }
        UiKit.restoreClip(g);
    }

    // ------------------------------------------------------------ clock panel

    static int clockX, clockY, clockW, clockH;
    // element boxes for the tutorial marker: sky, time, weather, day
    static int skyX, skyY, timeX, timeY, timeW, weaX, weaY, dayX, dayY, dayW;

    static void clock(GameCanvas gc, Graphics g) {
        javax.microedition.lcdui.Graphics m = g.g;
        int W = gc.getWidth();
        int fh = Txt.height();
        boolean fc = gc.c.skill[10] > 0; // forecast skill: tomorrow's weather shown
        clockW = 6 + 48 + 3 + 16 + (fc ? 14 : 0) + 6;
        clockH = 4 + fh + 2 + 13 + 2 + fh + 4;
        clockX = W - clockW - 3;
        clockY = gc.h + 3;
        wood(g, clockX, clockY, clockW, clockH);
        int x = clockX + 6, y = clockY + 4;
        // row 1: season + day ("Hạ 15")
        String sea = gc.SeaDat[gc.dat1[1]];
        int dw = Txt.width(sea) + 4 + UiKit.numW(gc.dat1[2]);
        dayW = dw;
        dayX = clockX + (clockW - dw) / 2;
        dayY = y;
        UiKit.text(g, sea, dayX, y, UiKit.DARK, UiKit.LEFT);
        UiKit.num(g, gc.dat1[2], dayX + Txt.width(sea) + 4, y, UiKit.DARK, UiKit.LEFT);
        y += fh + 2;
        // row 2: sky strip (original art and formula) + weather
        skyX = x;
        skyY = y + 2;
        m.setColor(WOOD_D);
        m.fillRect(x - 1, y + 1, 50, 11);
        m.setClip(x, y + 2, 48, 9);
        Image[] st = gc.im.State;
        if (st != null) {
            if (gc.time >= 6 && gc.time < 18) {
                g.drawImage(st[12], x, y + 2, 4);
                g.drawImage(st[14], x + gc.pintime / 15 + (gc.time - 6) * 4, y + 2, 1);
            } else {
                g.drawImage(st[13], x, y + 2, 4);
                if (gc.time >= 18) {
                    g.drawImage(st[15], x + gc.pintime / 15 + (gc.time - 18) * 4, y + 2, 1);
                } else {
                    g.drawImage(st[15], x + gc.pintime / 15 + (gc.time + 6) * 4, y + 2, 1);
                }
            }
        }
        m.setClip(0, 0, W, gc.getHeight());
        weaX = x + 51;
        weaY = y;
        Image[] wea = gc.im.Wea;
        if (wea != null) {
            g.drawImage(wea[gc.dat1[3]], weaX, y, 4);
            if (fc) {
                // tomorrow, half hidden behind today (like the original strip)
                m.setClip(weaX + 18, y, 12, 13);
                g.drawImage(wea[gc.dat1[5]], weaX + 16, y, 4);
                m.setClip(0, 0, W, gc.getHeight());
            }
        }
        y += 13 + 2;
        // row 3: time hh:mm (24 h)
        int hh = gc.time % 24, mm = gc.pintime;
        timeW = UiKit.numW(hh) + Txt.charWidth(':') + 2 * Txt.charWidth('0');
        timeX = clockX + (clockW - timeW) / 2;
        timeY = y;
        m.setColor(PAPER);
        m.fillRoundRect(clockX + 5, y - 1, clockW - 10, fh + 2, 6, 6);
        int tx = timeX;
        tx += UiKit.num(g, hh, tx, y, UiKit.DARK, UiKit.LEFT);
        tx += Txt.drawChar(m, ':', tx, y, UiKit.DARK);
        tx += Txt.drawChar(m, (char) ('0' + mm / 10 % 10), tx, y, UiKit.DARK);
        Txt.drawChar(m, (char) ('0' + mm % 10), tx, y, UiKit.DARK);
    }

    /** Stardew-like wood frame with a paper inside. */
    static void wood(Graphics g, int x, int y, int w, int h) {
        javax.microedition.lcdui.Graphics m = g.g;
        m.setColor(WOOD_D);
        m.fillRoundRect(x, y, w, h, 8, 8);
        m.setColor(WOOD);
        m.fillRoundRect(x + 1, y + 1, w - 2, h - 2, 7, 7);
        m.setColor(WOOD_L);
        m.drawLine(x + 4, y + 2, x + w - 5, y + 2);
        m.setColor(PAPER);
        m.fillRoundRect(x + 3, y + 3, w - 6, h - 6, 5, 5);
    }

    // ------------------------------------------------------------ HP / FP gauges

    static int gx, gy, gw, gh; // gauge box (HP at gx, FP at gx + gw + 3)

    static void gauges(GameCanvas gc, Graphics g) {
        int W = gc.getWidth();
        int fh = Txt.height();
        gw = 11;
        gh = gc.bh / 4;
        if (gh < 36) {
            gh = 36;
        }
        if (gh > 72) {
            gh = 72;
        }
        gx = W - 2 * gw - 3 - 4;
        gy = gc.h + gc.bh - gh - 4;
        gauge(g, gx, gy, gw, gh, gc.c.hp[0], 0xE8572A, 'H', fh);
        gauge(g, gx + gw + 3, gy, gw, gh, gc.c.hp[1], 0x3C86E0, 'F', fh);
    }

    /** One vertical gauge (value 0..5000) with a letter cap above it. */
    static void gauge(Graphics g, int x, int y, int w, int h, int v, int rgb, char cap, int fh) {
        javax.microedition.lcdui.Graphics m = g.g;
        if (v < 0) {
            v = 0;
        }
        if (v > 5000) {
            v = 5000;
        }
        // cap with the letter
        int cy = y - fh - 3;
        m.setColor(WOOD_D);
        m.fillRoundRect(x - 1, cy, w + 2, fh + 3, 5, 5);
        m.setColor(WOOD);
        m.fillRoundRect(x, cy + 1, w, fh + 1, 4, 4);
        Txt.drawChar(m, cap, x + (w - Txt.charWidth(cap)) / 2, cy + 2, UiKit.WHITE);
        // tube
        m.setColor(WOOD_D);
        m.fillRect(x - 1, y - 1, w + 2, h + 2);
        m.setColor(WOOD);
        m.fillRect(x, y, w, h);
        m.setColor(0x3A2410);
        m.fillRect(x + 2, y + 2, w - 4, h - 4);
        int f = (h - 4) * v / 5000;
        boolean low = v < 1000;
        if (f > 0 && !(low && (UiKit.tick >> 2 & 1) != 0)) {
            m.setColor(low ? UiKit.RED : rgb);
            m.fillRect(x + 2, y + 2 + h - 4 - f, w - 4, f);
            m.setColor(0xFFFFFF);
            m.drawLine(x + 3, y + 2 + h - 4 - f, x + 3, y + h - 3);
        }
    }

    // ------------------------------------------------------------ toolbar

    static int tbX, tbY, tbW, tbH;
    static boolean tbTop; // toolbar moved to the top (player behind it)
    static int toolTick = -9;

    /**
     * GameCanvas.DrawDownAction. The original only draws (no state writes);
     * the content and its conditions are kept 1:1, the box is the toolbar.
     */
    static void DrawDownAction(GameCanvas gc, Graphics g) {
        if (gc.State == 6 || gc.State == 9) {
            return; // the dialogue box covers the bottom of the screen
        }
        toolbar(gc, g);
    }

    static void place(GameCanvas gc) {
        int W = gc.getWidth();
        int bottom = gc.h + gc.bh;
        tbH = 26;
        int right = W - (2 * 11 + 3 + 4) - 6; // keep clear of the gauges
        tbW = right - 6;
        if (tbW > 220) {
            tbW = 220;
        }
        int yb = bottom - tbH - 4;
        // player's feet / head on screen (tile y, the sprite is ~27 px tall)
        int py = gc.h + (gc.c.y + 1) * gc.bsize - gc.scy;
        if (!tbTop && py > yb - 2 && gc.Where == gc.c.Where) {
            tbTop = true;
        } else if (tbTop && (py < yb - 2 * gc.bsize || gc.Where != gc.c.Where)) {
            tbTop = false;
        }
        if (tbTop) {
            int avail = clockX - 4 - 6; // left of the clock panel
            if (avail >= 120) {
                tbW = Math.min(tbW, avail);
                tbX = 6 + (avail - tbW) / 2;
                tbY = gc.h + 4;
            } else { // narrow: below the clock panel (and the count-down chip)
                tbW = W - 12;
                if (tbW > 220) {
                    tbW = 220;
                }
                tbX = (W - tbW) / 2;
                tbY = clockY + clockH + Txt.height() + 12;
            }
        } else {
            tbX = 6 + (right - 6 - tbW) / 2;
            tbY = yb;
        }
    }

    static void toolbar(GameCanvas gc, Graphics g) {
        javax.microedition.lcdui.Graphics m = g.g;
        toolTick = UiKit.tick;
        if (clockW == 0) {
            clock(gc, g);
        }
        place(gc);
        UiKit.saveClip(g);
        m.setClip(0, 0, gc.getWidth(), gc.getHeight());
        int x = tbX, y = tbY, w = tbW, h = tbH;
        m.setColor(WOOD_D);
        m.fillRoundRect(x, y, w, h, 8, 8);
        m.setColor(WOOD);
        m.fillRoundRect(x + 1, y + 1, w - 2, h - 2, 7, 7);
        int s = h - 6; // slot size
        int ix = x + 3, sx = x + w - 3 - s, mx = ix + s + 3, mw = sx - 3 - mx;
        slot(m, ix, y + 3, s, s);
        slot(m, mx, y + 3, mw, s);
        slot(m, sx, y + 3, s, s);
        int ty = y + 3 + (s - Txt.height()) / 2;
        int icx = ix + s / 2, icy = y + 3 + s / 2;
        Image[] na = gc.im.NowAct;
        CharacterDat c = gc.c;
        m.setClip(mx + 1, y + 3, mw - 2, s);
        if (c.nowact < 0 || gc.fade[3] > 0 || gc.fade[0] >= 0 || c.State == 3 || gc.Where != c.Where
                || gc.opt[26] == 4) {
            if (c.State == 3 && c.work[0] == 5 || gc.opt[26] == 4) {
                // watering: can level
                water(m, gc, mx + 4, icy - 3, mw - 8, 6);
                m.setClip(0, 0, gc.getWidth(), gc.getHeight());
                if (na != null) {
                    g.drawImage(na[4], icx, icy, 1 | 2);
                }
            } else {
                // idle: money
                UiKit.money(g, gc.dat3[0], mx + mw - 4 - UiKit.moneyW(gc.dat3[0]), ty, UiKit.DARK);
                m.setClip(0, 0, gc.getWidth(), gc.getHeight());
                if (na != null) {
                    g.drawImage(na[10], icx, icy, 1 | 2);
                }
            }
        } else {
            if (c.nowact == 4) {
                UiKit.text(g, c.DownMent, mx + 4, ty - 2, UiKit.DARK, UiKit.LEFT);
                water(m, gc, mx + 4, y + 3 + s - 5, mw - 8, 3);
            } else if (c.nowact != 9 || c.DownMent.equals("금액확인")) {
                UiKit.text(g, c.DownMent, mx + 4, ty, UiKit.DARK, UiKit.LEFT);
            } else if (c.Where < 3 && gc.cpoint[c.Where][c.x][c.y] >= 0) {
                int cp = gc.cpoint[c.Where][c.x][c.y];
                UiKit.text(g, gc.cname[gc.crop[cp][1]], mx + 4, ty - 2, UiKit.DARK, UiKit.LEFT);
                if (c.skill[7] > 1) {
                    int bw = mw - 8;
                    int by = y + 3 + s - 5;
                    bar(m, mx + 4, c.skill[7] > 2 ? by - 1 : by, bw, 2, gc.crop[cp][3], 0xE8B400);
                    if (c.skill[7] > 2) {
                        bar(m, mx + 4, by + 2, bw, 2, gc.crop[cp][5], 0x64C8F2);
                    }
                }
            }
            m.setClip(0, 0, gc.getWidth(), gc.getHeight());
            if (na != null) {
                g.drawImage(na[c.nowact], icx, icy, 1 | 2);
            }
            if (c.nowact == 3 && gc.seed[0][0] >= 0) {
                int sd = gc.seed[0][0];
                m.setColor(sd >= 54 ? 0x6FAFFF : sd >= 36 ? 0xFF5555 : sd >= 18 ? 0x70D548 : 0xEBE180);
                m.drawRect(sx, y + 3, s - 1, s - 1);
                m.drawRect(sx + 1, y + 4, s - 3, s - 3);
                g.drawImage(gc.im.Crop[sd], sx + s / 2, y + 3 + s / 2, 1 | 2);
            }
        }
        UiKit.restoreClip(g);
    }

    static void slot(javax.microedition.lcdui.Graphics m, int x, int y, int w, int h) {
        m.setColor(0x9A5A22);
        m.fillRect(x, y, w, h);
        m.setColor(SLOT);
        m.fillRect(x + 1, y + 1, w - 2, h - 2);
        m.setColor(0xFBE3B0);
        m.drawLine(x + 1, y + h - 2, x + w - 2, y + h - 2);
    }

    /** Watering-can level: dat1[4] of (skill[3] + 1) * 5 units (original: x3 px of (skill+1)*15). */
    static void water(javax.microedition.lcdui.Graphics m, GameCanvas gc, int x, int y, int w, int h) {
        int cap = (gc.c.skill[3] + 1) * 15;
        int bw = w * cap / 60; // the full can at skill 3 fills the slot
        if (bw > w) {
            bw = w;
        }
        m.setColor(0x3A2410);
        m.fillRect(x, y, bw, h);
        int f = gc.dat1[4] * 3 * bw / cap;
        if (f > bw) {
            f = bw;
        }
        if (f > 0) {
            m.setColor(0x00C8FF);
            m.fillRect(x, y, f, h);
        }
    }

    /** Crop growth bar (original: value / 111 px, i.e. 0..~90 px). */
    static void bar(javax.microedition.lcdui.Graphics m, int x, int y, int w, int h, int v, int rgb) {
        int f = v / 111 * w / 90;
        if (f > w) {
            f = w;
        }
        if (f > 0) {
            m.setColor(rgb);
            m.fillRect(x, y, f, h);
        }
    }

    // ------------------------------------------------------------ tutorial marker (Effect.DrawFade)

    static GameCanvas gc;
    static int tut = -1, tutTick = -9;

    /**
     * Effect.DrawFade highlights the parts of the old bars (sky, time,
     * weather, day, action box, HP, FP: fade[1] 0..6) with drawRect at fixed
     * offsets; those calls land here and the marker is drawn around the same
     * element of the HUD (after the HUD, in DrawHp).
     */
    static void tutRect(Graphics g, int x, int y, int w, int h) {
        if (gc != null) {
            tut = gc.fade[1];
            tutTick = UiKit.tick;
        }
    }

    static void tutMark(GameCanvas gc, Graphics g) {
        javax.microedition.lcdui.Graphics m = g.g;
        int x, y, w, h;
        int fh = Txt.height();
        switch (tut) {
        case 0:
            x = skyX; y = skyY; w = 48; h = 9;
            break;
        case 1:
            x = timeX; y = timeY; w = timeW; h = fh;
            break;
        case 2:
            x = weaX; y = weaY; w = 16; h = 13;
            break;
        case 3:
            x = dayX; y = dayY; w = dayW; h = fh;
            break;
        case 4:
            x = tbX; y = tbY; w = tbW; h = tbH;
            break;
        case 5:
            x = gx; y = gy - fh - 3; w = gw; h = gh + fh + 3;
            break;
        case 6:
            x = gx + gw + 3; y = gy - fh - 3; w = gw; h = gh + fh + 3;
            break;
        default:
            return;
        }
        m.setColor(gc.fade[2] / 4 % 2 == 0 ? 0xFF0000 : 0xFFFF00);
        m.drawRect(x - 2, y - 2, w + 3, h + 3);
        m.drawRect(x - 3, y - 3, w + 5, h + 5);
    }

    // ------------------------------------------------------------ DrawUpState / DrawMiniTime

    /**
     * GameCanvas.DrawUpState, logic copied (US[] counters); the banner that
     * slides in at the top right is moved below the clock panel.
     */
    static void DrawUpState(GameCanvas gc, Graphics graphics) {
        UiHud.gc = gc;
        if (gc.US[0] >= 0) {
            if (gc.US[0] <= 1) {
                graphics.drawImage(gc.im.UT, gc.w + gc.utx[gc.US[0]], gc.h + 2, 4);
            } else if (gc.US[0] <= 50) {
                graphics.drawImage(gc.im.UT, gc.w + gc.utx[2], gc.h + 2, 4);
            } else if (gc.US[0] == 51) {
                graphics.drawImage(gc.im.UT, gc.w + gc.utx[3], gc.h + 2, 4);
            } else {
                gc.US[0] = -2;
            }
            gc.US[0] = gc.US[0] + 1;
        }
        int y = clockY + clockH + 3; // was h + 2 (inside the top bar)
        if (gc.US[1] >= 0) {
            switch (gc.US[1]) {
                case 0: {
                    graphics.drawImage(gc.im.MN[0], gc.w + gc.width - 16 - gc.US[2], y, 4);
                    graphics.drawImage(gc.im.MN[1], gc.w + gc.width - 10, y, 4);
                    if (gc.US[2] > 0) {
                        graphics.setClip(gc.w + gc.width - 10 - gc.US[2], y, gc.US[2], 21);
                        graphics.drawImage(gc.im.MN[6], gc.w + gc.width - 10 - gc.US[2], y, 4);
                        graphics.setClip(0, 0, gc.width, gc.height + 48);
                    }
                    if (gc.US[2] < gc.im.MN[6].getWidth()) {
                        gc.US[2] = gc.US[2] + 7;
                        break;
                    }
                    gc.US[1] = 1;
                    gc.US[2] = 0;
                    break;
                }
                case 1: {
                    graphics.drawImage(gc.im.MN[0], gc.w + gc.width - 16 - gc.im.MN[6].getWidth(), y, 4);
                    graphics.drawImage(gc.im.MN[6], gc.w + gc.width - 10 - gc.im.MN[6].getWidth(), y, 4);
                    graphics.drawImage(gc.im.MN[1], gc.w + gc.width - 10, y, 4);
                    gc.US[2] = gc.US[2] + 1;
                    if (gc.US[2] < 40) break;
                    gc.US[1] = 2;
                    gc.US[2] = gc.im.MN[6].getWidth();
                    break;
                }
                case 2: {
                    graphics.drawImage(gc.im.MN[0], gc.w + gc.width - 16 - gc.US[2], y, 4);
                    graphics.drawImage(gc.im.MN[1], gc.w + gc.width - 10, y, 4);
                    if (gc.US[2] > 0) {
                        graphics.setClip(gc.w + gc.width - 10 - gc.US[2], y, gc.US[2], 21);
                        graphics.drawImage(gc.im.MN[6], gc.w + gc.width - 10 - gc.US[2], y, 4);
                        graphics.setClip(0, 0, gc.width, gc.height + 48);
                    }
                    gc.US[2] = gc.US[2] - 7;
                    if (gc.US[2] >= 0) break;
                    gc.US[1] = -1;
                    gc.im.MN = null;
                }
            }
        }
    }

    /** GameCanvas.DrawMiniTime(g, ani, div): count-down chip under the clock panel. */
    static void DrawMiniTime(GameCanvas gc, Graphics g, int ani, int div) {
        String unit = " giây";
        int s = ani / div;
        int w = Txt.width(unit) + 26;
        int h = Txt.height() + 6;
        int x = gc.getWidth() - w - 4, y = clockY + clockH + 3;
        javax.microedition.lcdui.Graphics m = g.g;
        m.setColor(UiKit.DARK);
        m.fillRoundRect(x, y, w, h, h, h);
        m.setColor(s <= 5 ? UiKit.RED : UiKit.ACC);
        m.fillRoundRect(x + 1, y + 1, w - 2, h - 2, h - 2, h - 2);
        int nx = x + 8;
        nx += UiKit.num(g, s, nx, y + 3, UiKit.WHITE, UiKit.LEFT);
        UiKit.text(g, unit, nx, y + 3, UiKit.WHITE, UiKit.LEFT);
    }
}
