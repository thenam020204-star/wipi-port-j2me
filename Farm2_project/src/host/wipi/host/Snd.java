package wipi.host;

import java.io.InputStream;
import java.util.Hashtable;
import java.util.Vector;

import javax.microedition.media.Manager;
import javax.microedition.media.Player;
import javax.microedition.media.control.VolumeControl;

/**
 * Sound for com.skt.m.AudioClip. SMAF (.mmf) cannot be played by J2ME phones,
 * so the build converts every /Sound/*.mmf to MIDI (music) or WAV (effects)
 * and leaves a stub "WSND<path>" under the original name. The game's single
 * clip plays one sound at a time, which is kept: a new play stops the old one.
 * Prefetched players are cached (LRU, small: phones limit open players).
 */
public final class Snd {
    public static int volume = 100;
    private static final int MAX_CACHED = 4;
    private static final Hashtable players = new Hashtable();
    private static final Vector lru = new Vector();
    private static Player cur;
    private static boolean dead;

    static void init() {
    }

    public static String nameOf(byte[] d, int off, int len) {
        if (d != null && len > 4 && d[off] == 'W' && d[off + 1] == 'S' && d[off + 2] == 'N' && d[off + 3] == 'D') {
            return new String(d, off + 4, len - 4).trim();
        }
        return null;
    }

    private static Player get(String name) {
        Player p = (Player) players.get(name);
        if (p != null) {
            lru.removeElement(name);
            lru.addElement(name);
            return p;
        }
        while (lru.size() >= MAX_CACHED) {
            String old = (String) lru.elementAt(0);
            lru.removeElementAt(0);
            Player op = (Player) players.remove(old);
            if (op != null && op != cur) {
                try {
                    op.close();
                } catch (Throwable t) {
                }
            }
        }
        InputStream in = Snd.class.getResourceAsStream(name);
        if (in == null) {
            return null;
        }
        try {
            String type = name.endsWith(".wav") ? "audio/x-wav" : "audio/midi";
            p = Manager.createPlayer(in, type);
            p.realize();
            p.prefetch();
        } catch (Throwable t) {
            Log.e("snd " + name, t);
            return null;
        }
        players.put(name, p);
        lru.addElement(name);
        return p;
    }

    public static synchronized void play(Object owner, String name, boolean loop) {
        if (name == null || dead) {
            return;
        }
        stopCur();
        Player p = get(name);
        if (p == null) {
            return;
        }
        try {
            p.setLoopCount(loop ? -1 : 1);
        } catch (Throwable t) {
        }
        try {
            p.setMediaTime(0);
        } catch (Throwable t) {
        }
        apply(p);
        try {
            p.start();
            cur = p;
        } catch (Throwable t) {
            Log.e("snd start " + name, t);
        }
    }

    private static void stopCur() {
        if (cur != null) {
            try {
                cur.stop();
            } catch (Throwable t) {
            }
            cur = null;
        }
    }

    public static synchronized void stop(Object owner) {
        stopCur();
    }

    public static synchronized void setVolume(int v) {
        volume = v < 0 ? 0 : (v > 100 ? 100 : v);
        if (cur != null) {
            apply(cur);
        }
    }

    private static void apply(Player p) {
        try {
            VolumeControl vc = (VolumeControl) p.getControl("VolumeControl");
            if (vc != null) {
                vc.setLevel(volume);
            }
        } catch (Throwable t) {
        }
    }

    static synchronized void shutdown() {
        dead = true;
        stopCur();
        for (int i = 0; i < lru.size(); i++) {
            Player p = (Player) players.get(lru.elementAt(i));
            try {
                p.close();
            } catch (Throwable t) {
            }
        }
        players.clear();
        lru.removeAllElements();
    }
}
