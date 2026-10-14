import org.kwis.msp.lcdui.Graphics;

/**
 * GameCanvas.paint of the new UI. The state machine, key handling and every
 * game-state write of the original paint() are copied unchanged (CFR output,
 * cross-checked with FernFlower and the bytecode); only the drawing of the
 * dialogue, the confirm boxes (WAP, CASH, RANK), the notice toast and the
 * skip hint is new.
 */
final class UiPaint {
    static void paint(GameCanvas gc, Graphics graphics) {
        UiKit.tick++;
        UiDebug.poll(gc);
        int n;
        if (gc.State >= 5 || gc.State == 2) {
            if (gc.Explan != null) {
                if (gc.KeyCode == 8 || gc.KeyCode == 53 || gc.KeyCode == 99 || gc.ExplanDelay <= 0) {
                    gc.Explan = null;
                }
                gc.KeyCode = -999;
            }
            if (gc.PInfo > 0) {
                if (gc.KeyCode != -999) {
                    gc.PBuff = null;
                    gc.pg = null;
                    gc.PInfo = 0;
                    if (gc.State == 10) {
                        gc.m.LoadPopup(55, 0, 0);
                    } else {
                        gc.m.LoadPopup(7, 0, 0);
                    }
                }
                gc.KeyCode = -999;
            }
        }
        if (gc.Space) {
            if (gc.h > 0) {
                graphics.setColor(7162112);
                graphics.fillRect(0, 0, gc.getWidth(), gc.h);
                graphics.fillRect(0, gc.getHeight() - gc.h, gc.getWidth(), gc.h);
                if (gc.State == 5 || gc.State == 6 || gc.State == 7 || gc.State == 9) {
                    gc.DrawUp(graphics);
                    gc.DrawDown(graphics, true);
                    gc.DRepaint = true;
                    gc.URepaint = true;
                }
            }
            gc.Space = false;
        }
        switch (gc.State) {
            case 16: {
                graphics.setColor(0xFFFFFF);
                graphics.fillRect(0, 0, gc.getWidth(), gc.getHeight());
                graphics.drawImage(gc.im.MN[0], (gc.getWidth() - gc.im.MN[0].getWidth()) / 2, (gc.getHeight() - gc.im.MN[0].getHeight()) / 2, 0 | 4);
                ++gc.ani;
                if (gc.ani < 30 && gc.KeyCode == -999) break;
                gc.KeyCode = -999;
                gc.LoadTitle();
                break;
            }
            case 0: {
                graphics.setColor(0xFFFFFF);
                graphics.fillRect(0, 0, gc.getWidth(), gc.getHeight());
                if (gc.ani <= 50) {
                    graphics.drawImage(gc.im.MN[0], (gc.getWidth() - gc.im.MN[0].getWidth()) / 2, (gc.getHeight() - gc.im.MN[0].getHeight()) / 2, 0 | 4);
                } else {
                    graphics.drawImage(gc.im.MN[1], (gc.getWidth() - gc.im.MN[1].getWidth()) / 2, (gc.getHeight() - gc.im.MN[1].getHeight()) / 2, 0 | 4);
                }
                ++gc.ani;
                if (gc.KeyCode != -999) {
                    gc.ani = gc.ani < 50 ? 50 : 100;
                }
                if (gc.ani >= 100) {
                    gc.ani = 0;
                    gc.im.LoadTitleMenu(3, gc.getWidth(), 0);
                    gc.State = 16;
                }
                gc.KeyCode = -999;
                break;
            }
            case 17: {
                break;
            }
            case 13: {
                gc.DrawEnding0(graphics);
                break;
            }
            case 14: {
                gc.DrawEnding1(graphics);
                break;
            }
            case 15: {
                gc.DrawEnding2(graphics);
                break;
            }
            case 1: {
                gc.DrawTitle(graphics);
                break;
            }
            case 2: {
                gc.DrawMenu(graphics);
                break;
            }
            case 5: 
            case 6: 
            case 7: 
            case 127: {
                gc.PlayGame(graphics);
                if (gc.State == 6) {
                    gc.DrawUp(graphics);
                }
                if (gc.darkani >= 0) {
                    gc.e.OpenDark(graphics);
                }
                if (gc.State == 6) {
                    if (gc.DRepaint) {
                        gc.n.DrawTalk(graphics);
                    }
                    gc.DrawTalk(graphics); // portrait included
                }
                if (gc.opt[2] != 1 && (gc.Tou < 0 || gc.Tou > 4)) break;
                String skip = "#: B\u1ecf qua";
                UiKit.pill(graphics, 4, gc.h + 4, skip, UiKit.DARK, UiKit.DARK, UiKit.WHITE); // top left: the clock is top right
                break;
            }
            case 9: {
                if (gc.n.popup >= 0) {
                    gc.n.popup = (byte)-1;
                }
                if (gc.GMBuff == null) {
                    gc.PlayGame(graphics);
                }
                if (gc.State == 9 && gc.n.TBuff != null) {
                    gc.DrawTalk(graphics); // portrait included
                } else if (gc.GMBuff != null) {
                    gc.DrawGameMenu(graphics);
                    gc.DrawPoint(graphics);
                }
                if (gc.GMBuff == null && gc.n.TBuff == null) {
                    gc.DrawDown(graphics, true);
                }
                gc.DrawPopup(graphics);
                break;
            }
            case 8: {
                gc.e.DrawDark(graphics);
                break;
            }
            case 10: {
                gc.DrawUp(graphics);
                gc.DrawDown(graphics, true);
                gc.DrawDownAction(graphics);
                gc.DrawGameMenu(graphics);
                break;
            }
            case 11: {
                switch (gc.minikind) {
                    case 0: {
                        gc.PlayMini0(graphics);
                        break;
                    }
                    case 1: {
                        gc.PlayMini1(graphics);
                        break;
                    }
                    case 2: {
                        gc.PlayMini2(graphics);
                    }
                }
                break;
            }
            case 12: {
                graphics.setColor(0);
                graphics.fillRect(0, 0, gc.getWidth(), gc.getHeight());
                if (gc.ani <= 20) {
                    graphics.setAlpha(50 + gc.ani * 10);
                } else if (gc.ani >= 80) {
                    graphics.setAlpha(250 - (gc.ani - 80) * 10);
                }
                graphics.drawImage(gc.im.mini[0], gc.getWidth() / 2, gc.getHeight() / 2, 3);
                graphics.setAlpha(255);
                ++gc.ani;
                if (gc.ani < 100) break;
                gc.DrawUp(graphics);
                gc.DrawDown(graphics, true);
                gc.DRepaint = true;
                gc.URepaint = true;
                gc.im.mini = null;
                gc.State = 7;
            }
        }
        if (gc.State >= 5) {
            if (gc.opt[7] == 1) {
                if (gc.State == 5) {
                    --gc.ani;
                }
                gc.DrawMiniTime(graphics, gc.ani, 18);
                if (gc.ani <= 4 && gc.c.movex == 0 && gc.c.movey == 0) {
                    gc.opt[7] = 0;
                    gc.c.FindNearSpace(8);
                    gc.TalkSet(9, 18, "");
                    gc.c.hp[0] = 0;
                }
            } else if (gc.opt[13] == 1) {
                if (gc.State == 5) {
                    --gc.ani;
                }
                gc.DrawMiniTime(graphics, gc.ani, 22);
                if (gc.ani <= 4 && gc.c.movex == 0 && gc.c.movey == 0) {
                    gc.opt[13] = 0;
                    gc.c.FindNearSpace(22);
                    gc.TalkSet(23, 22, "");
                    gc.c.hp[0] = 0;
                    gc.c.HPFP(-5000, 0, 3);
                }
            } else if (gc.opt[18] == 1) {
                if (gc.State == 5) {
                    --gc.ani;
                }
                gc.DrawMiniTime(graphics, gc.ani, 5);
                if (gc.ani <= 4 && gc.c.movex == 0 && gc.c.movey == 0) {
                    gc.opt[18] = 0;
                    gc.c.FindNearSpace(46);
                    gc.TalkSet(0, 418, "");
                }
            } else if (gc.opt[27] == 1) {
                if (gc.State == 5) {
                    --gc.ani;
                }
                gc.DrawMiniTime(graphics, gc.ani, 29);
                if (gc.ani <= 4 && gc.c.movex == 0 && gc.c.movey == 0) {
                    gc.c.FindNearSpace(19);
                    gc.c.FindNearSpace(20);
                    gc.opt[27] = 0;
                    gc.TalkSet(20, 9, "");
                }
            }
            if (gc.PInfo > 0) {
                UiGM.petInfo(gc, graphics);
            }
        }
        if (gc.OPTION) {
            UiTitle.option(gc, graphics);
        } else if (gc.EXPLAN) {
            UiTitle.explan(gc, graphics);
        } else if (gc.WAP) {
            UiKit.dialog(graphics, gc.getWidth(), gc.getHeight(), "K\u1ebft n\u1ed1i m\u1ea1ng",
                    "D\u00f9ng t\u00ednh n\u0103ng n\u00e0y s\u1ebd t\u1ed1n ph\u00ed k\u1ebft n\u1ed1i. B\u1ea1n c\u00f3 mu\u1ed1n k\u1ebft n\u1ed1i kh\u00f4ng?",
                    "C\u00f3", "Kh\u00f4ng", gc.GM[1]);
        } else if (gc.CASH) {
            switch (gc.cashState) {
                case 0: {
                    UiKit.dialog(graphics, gc.getWidth(), gc.getHeight(), "Mua h\u00e0ng", cashMsg(gc.cashprice),
                            "C\u00f3", "Kh\u00f4ng", gc.GM[1]);
                    break;
                }
                case 1: {
                    UiKit.dialog(graphics, gc.getWidth(), gc.getHeight(), "\u0110ang thanh to\u00e1n",
                            "\u0110ang t\u00ednh ph\u00ed, xin \u0111\u1eebng ng\u1eaft k\u1ebft n\u1ed1i.", null, null, -1);
                    gc.ani = (gc.ani + 1) % 4;
                    break;
                }
                case 2: {
                    UiKit.dialog(graphics, gc.getWidth(), gc.getHeight(), "Mua h\u00e0ng", "Mua th\u00e0nh c\u00f4ng!", "OK", null, 0);
                    break;
                }
                case 3: {
                    UiKit.dialog(graphics, gc.getWidth(), gc.getHeight(), "Mua th\u1ea5t b\u1ea1i",
                            "\u0110\u00e3 v\u01b0\u1ee3t qu\u00e1 h\u1ea1n m\u1ee9c mua.", "OK", null, 0);
                    break;
                }
                case 4: {
                    UiKit.dialog(graphics, gc.getWidth(), gc.getHeight(), "Mua th\u1ea5t b\u1ea1i",
                            "Kh\u00f4ng th\u1ec3 ho\u00e0n t\u1ea5t giao d\u1ecbch.", "OK", null, 0);
                    break;
                }
                case 5: {
                    UiKit.dialog(graphics, gc.getWidth(), gc.getHeight(), "K\u1ebft n\u1ed1i th\u1ea5t b\u1ea1i",
                            "Kh\u00f4ng k\u1ebft n\u1ed1i \u0111\u01b0\u1ee3c m\u00e1y ch\u1ee7.", "OK", null, 0);
                }
            }
        } else if (gc.RANK) {
            if (gc.GM[1] == 0) {
                UiKit.dialog(graphics, gc.getWidth(), gc.getHeight(), "B\u1ea3ng x\u1ebfp h\u1ea1ng",
                        "K\u1ebft n\u1ed1i m\u00e1y ch\u1ee7 x\u1ebfp h\u1ea1ng s\u1ebd t\u1ed1n ph\u00ed. B\u1ea1n c\u00f3 mu\u1ed1n k\u1ebft n\u1ed1i kh\u00f4ng?",
                        "C\u00f3", "Kh\u00f4ng", gc.GM[2]);
            } else if (gc.GM[1] == 1) {
                UiKit.dialog(graphics, gc.getWidth(), gc.getHeight(), "B\u1ea3ng x\u1ebfp h\u1ea1ng",
                        "\u0110ang k\u1ebft n\u1ed1i...", null, null, -1);
                gc.GM[2] = (byte)((gc.GM[2] + 1) % 5);
            } else if (gc.GM[1] == 2) {
                UiRank.list(gc, graphics);
            } else if (gc.GM[1] == 3) {
                UiKit.dialog(graphics, gc.getWidth(), gc.getHeight(), "B\u1ea3ng x\u1ebfp h\u1ea1ng",
                        "K\u1ebft n\u1ed1i m\u00e1y ch\u1ee7 x\u1ebfp h\u1ea1ng th\u1ea5t b\u1ea1i.", "OK", null, 0);
            }
        } else if (gc.LOAD) {
            UiTitle.load(gc, graphics);
            if (gc.State == 17) {
                ++gc.lani;
                if (gc.lani >= 2) {
                    gc.LOAD = false;
                    gc.im.mini = null;
                    gc.im.MN = null;
                    gc.GMBuff = null;
                    gc.gmg = null;
                    gc.Buff = null;
                    if (gc.ani == 0) {
                        gc.nownosound = true;
                    }
                    gc.GameSetup(true);
                    gc.Space = true;
                    if (gc.ani == 0) {
                        gc.ThisSave = gc.Menucur[4];
                    } else {
                        gc.darkani = -1;
                        gc.LoadGame(gc.Menucur[4]);
                        gc.Space = true;
                    }
                }
            }
        }
        if ((gc.State >= 5 || gc.State == 2) && gc.Explan != null) {
            UiKit.toast(graphics, gc.getWidth(), gc.getHeight(), gc.Explan);
        }
    }

    private static String cashText;
    private static int cashFor = -1;

    /** Purchase notice, built once per price. */
    static String cashMsg(int price) {
        if (price != cashFor || cashText == null) {
            cashText = "Khi mua s\u1ebd t\u00ednh c\u01b0\u1edbc v\u00e0 ph\u00ed d\u1ecbch v\u1ee5 " + price
                    + " won. B\u1ea1n c\u00f3 mu\u1ed1n mua kh\u00f4ng?";
            cashFor = price;
        }
        return cashText;
    }
}
