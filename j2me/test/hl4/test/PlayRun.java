package hl4.test;

import hl4.game.Engine;
import hl4.sys.Clock;

/**
 * Headless play-through of the Java port (no reference): boots like the MIDlet, answers the consent with '1', then presses OK (-5) every
 * <period> ticks (plus optional extra 'tick:key' taps) and reports the first unported path ("...: later") with its tick.  Not a golden test.
 * Usage: PlayRun <ticks> <period> [tick:key ...]
 */
public class PlayRun {
    public static void main(String[] a) {
        int end = Integer.parseInt(a[0]);
        int period = Integer.parseInt(a[1]);
        java.util.Hashtable extra = new java.util.Hashtable();
        String shots = "," + System.getProperty("shots", "") + ",";
        for (int i = 2; i < a.length; i++) {
            int c = a[i].indexOf(':');
            String v = a[i].substring(c + 1);
            int mode = v.startsWith("p") ? 1 : v.startsWith("r") ? 2 : 0;
            int kv = Integer.decode(mode == 0 ? v : v.substring(1)).intValue();
            extra.put(new Integer(Integer.parseInt(a[i].substring(0, c))), new int[] {mode, kv});
        }
        Clock.setVirtual(1303268400000L);
        Engine.startApp();
        int t = 0;
        try {
            for (t = 1; t < end; t++) {
                int[] k = (int[]) extra.get(new Integer(t));
                if (k != null) {
                    if (k[0] != 2) {
                        Engine.onKey(true, k[1]);
                    }
                    if (k[0] != 1) {
                        Engine.onKey(false, k[1]);
                    }
                } else if (period > 0 && t % period == 0) {
                    int key = Engine.cur == hl4.game.TitleFrame.I && hl4.game.Glob.smsAgree == -1 ? 0x31 : -5;
                    Engine.onKey(true, key);
                    Engine.onKey(false, key);
                }
                Clock.setVirtual(Clock.now() + 50);
                Engine.tick();
                if (shots.indexOf("," + t + ",") >= 0) {
                    short[] px = hl4.game.Gfx.scr.px;
                    byte[] b = new byte[px.length * 2];
                    for (int i = 0; i < px.length; i++) {
                        b[2 * i] = (byte) px[i];
                        b[2 * i + 1] = (byte) (px[i] >> 8);
                    }
                    try {
                        java.io.FileOutputStream fo = new java.io.FileOutputStream(System.getProperty("shotdir", ".") + "/p" + t + ".raw");
                        fo.write(b);
                        fo.close();
                    } catch (java.io.IOException e) {
                        throw new RuntimeException(e.toString());
                    }
                }
                if (t % Integer.parseInt(System.getProperty("every", "2000")) == 0) {
                    byte[] mp = hl4.game.G.map;
                    hl4.game.Char h = hl4.game.Pool.hero;
                    System.out.println("t=" + t + " frame=" + Engine.cur + " scene=" + hl4.sys.Bytes.s32(mp, 0x2d4) + " event=" + hl4.game.G.event[0x1fe]
                            + " game0=" + hl4.game.G.game[0] + " hero=" + (h == null ? "-" : h.x() + "," + h.y() + " hp=" + hl4.sys.Bytes.s32(h.m, 0xa4) + " lv=" + h.m[0x94]));
                }
            }
            System.out.println("PlayRun ok to tick " + t + " frame=" + Engine.class.getName());
        } catch (RuntimeException e) {
            System.out.println("PlayRun stopped at tick " + t + ": " + e);
            StackTraceElement[] st = e.getStackTrace();
            for (int i = 0; i < st.length && i < 8; i++) {
                System.out.println("  at " + st[i]);
            }
        }
    }
}
