package hl4.game;

import hl4.sys.Bytes;

/** Inc/dec gauge animation state in _game (IncDecGauge_init @0x994d4, IncDecGaugeList_init @0x99608): two 16-byte blocks at +0x10 and +0x20 with enable bytes +0x1e / +0x2e. */
public final class Gauges {
    private Gauges() {
    }

    public static void init(int k) {
        byte[] g = G.game;
        if (k == 0) {
            Bytes.fill(g, 0x10, 0, 0x10);
            g[0x1e] = 1;
        } else if (k == 1) {
            Bytes.fill(g, 0x20, 0, 0x10);
            g[0x2e] = 1;
        }
    }

    public static void initList() {
        init(0);
        Bytes.fill(G.game, 0x20, 0, 0x10);
        G.game[0x2e] = 1;
    }
}
