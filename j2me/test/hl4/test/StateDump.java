package hl4.test;

import hl4.game.G;
import hl4.game.Obj;
import hl4.game.Pool;

import java.io.PrintStream;

/** State dump in the format of tools/e_state.py (lines: tick name hex): hero, summon, monsters, NPCs, globals and the pool objects in use. */
public class StateDump {
    static void line(PrintStream o, int t, String name, byte[] b) {
        StringBuffer sb = new StringBuffer();
        sb.append(t).append(' ').append(name).append(' ');
        String hx = "0123456789abcdef";
        for (int i = 0; i < b.length; i++) {
            sb.append(hx.charAt((b[i] >> 4) & 15)).append(hx.charAt(b[i] & 15));
        }
        o.println(sb.toString());
    }

    static void pool(PrintStream o, int t, String prefix, int type, boolean onlyUsed) {
        Obj[] a = Pool.pools[type];
        for (int i = 0; i < a.length; i++) {
            if (!onlyUsed || a[i].m[Obj.FREE] == 0) {
                line(o, t, prefix + i, a[i].m);
            }
        }
    }

    public static void write(PrintStream o, int t) {
        line(o, t, "_game", G.game);
        line(o, t, "_map", G.map);
        line(o, t, "_event", G.event);
        line(o, t, "_gameMenu", G.gameMenu);
        line(o, t, "_slotInfo", G.slotInfo);
        line(o, t, "hero", Pool.hero.m);
        line(o, t, "summon", Pool.summon.m);
        pool(o, t, "monster", Pool.MONSTER, false);
        pool(o, t, "npc", Pool.NPC, false);
        pool(o, t, "mapobj", Pool.MAPOBJ, true);
        pool(o, t, "item", Pool.ITEM, true);
        pool(o, t, "arms", Pool.ARMS_ITEM, true);
        pool(o, t, "sprobj", Pool.SPROBJ, true);
        pool(o, t, "effect", Pool.EFFECT, true);
        pool(o, t, "teffect", Pool.TARGET_EFFECT, true);
        pool(o, t, "drop", Pool.DROP, true);
        pool(o, t, "tile", Pool.TILE, true);
    }
}
