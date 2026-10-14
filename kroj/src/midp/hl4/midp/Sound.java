package hl4.midp;

import hl4.core.Smaf;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Hashtable;
import java.util.Vector;
import javax.microedition.media.Manager;
import javax.microedition.media.Player;
import javax.microedition.media.control.VolumeControl;

/**
 * MMAPI sound system.
 *
 * Audio is converted at build time to plain .mid (music) / .wav (effects); the original SMAF
 * resources are replaced by "HL4SND\0path" stubs. The game re-uses one clip handle per channel and
 * does "stop, free, set data, play" for every effect, so:
 *  - sounds are kept in banks (one per file) and never re-read or re-created,
 *  - every effect bank owns a small pool of prefetched players (overlapping / rapid re-triggers,
 *    e.g. gun fire) and all effect players are prefetched in the background at start-up,
 *  - one-shot effects are not cut when the game frees/stops the handle (they end naturally);
 *    music and looping sounds follow stop/free exactly,
 *  - a global limit keeps the number of simultaneous effect voices bounded.
 */
final class Sound {
    static final String MIDI = "audio/midi", WAV = "audio/x-wav";
    static final int POOL = 3;          // players per effect (extra ones only when re-triggered)
    static final int MAX_VOICES = 6;    // simultaneous effect voices
    static final int MAX_PLAYERS = 24;  // prefetched players kept alive (LRU)

    /** One sound file. */
    static final class Bank {
        String key, type;
        byte[] data;
        Player[] pool;
        int next;
        boolean music;
    }

    /** Handle given to the game (one per created clip). */
    static final class Clip {
        Bank bank;
        Player current;     // player used by the last play of this handle
        int volume = -1;
        boolean loop;
        boolean freed;
        Clip extra; // PCM part of a score+audio SMAF, played alongside
    }

    private final Hashtable banks = new Hashtable();
    private final Vector lru = new Vector();       // Player, least recently used first
    private final Hashtable owner = new Hashtable(); // Player -> Bank
    private Bank music;
    private final Vector voices = new Vector();  // effect players started most recently last
    private final Vector clips = new Vector();
    boolean disabled;

    // ------------------------------------------------------------------
    // loading

    /** "HL4SND\0/path" stub written by the build (SndConvert) -> resource path, else null. */
    static String stub(byte[] d) {
        if (d == null || d.length < 8 || d[0] != 'H' || d[1] != 'L' || d[2] != '4' || d[3] != 'S' || d[4] != 'N' || d[5] != 'D' || d[6] != 0) return null;
        int e = 7;
        while (e < d.length && d[e] != 0) e++;
        StringBuffer sb = new StringBuffer();
        for (int i = 7; i < e; i++) sb.append((char) (d[i] & 0xFF));
        return sb.toString();
    }

    /** second path of a stub ("HL4SND\0path1\0path2"), else null */
    static String stub2(byte[] d) {
        if (stub(d) == null) return null;
        int e = 7;
        while (e < d.length && d[e] != 0) e++;
        if (e + 1 >= d.length) return null;
        StringBuffer sb = new StringBuffer();
        for (int i = e + 1; i < d.length && d[i] != 0; i++) sb.append((char) (d[i] & 0xFF));
        return sb.length() > 0 ? sb.toString() : null;
    }

    static byte[] read(String path) {
        try {
            InputStream in = Sound.class.getResourceAsStream(path);
            if (in == null) return null;
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            return bo.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    private synchronized Bank bank(String path) {
        Bank b = (Bank) banks.get(path);
        if (b == null) {
            b = new Bank();
            b.key = path;
            b.data = read(path);
            b.type = path.endsWith(".mid") ? MIDI : WAV;
            b.music = b.type == MIDI;
            b.pool = new Player[b.music ? 1 : POOL];
            banks.put(path, b);
        }
        return b;
    }

    private synchronized Bank rawBank(byte[] mmf) {
        // not converted at build time: convert now (cached by content)
        int h = 0;
        for (int i = 0; i < mmf.length; i += 7) h = h * 31 + mmf[i];
        String key = "raw:" + mmf.length + ":" + h;
        Bank b = (Bank) banks.get(key);
        if (b == null) {
            b = new Bank();
            b.key = key;
            Smaf.Converted conv = Smaf.convert(mmf);
            b.type = conv.type;
            b.data = conv.data;
            b.music = MIDI.equals(b.type);
            b.pool = new Player[b.music ? 1 : POOL];
            banks.put(key, b);
        }
        return b;
    }

    /** Prefetch all effect players listed in /res/c/snd/list.txt (background, at start-up). */
    void preload() {
        Thread t = new Thread() {
            public void run() {
                try {
                    InputStream in = Sound.class.getResourceAsStream("/sound/list.txt");
                    if (in == null) in = Sound.class.getResourceAsStream("/res/c/snd/list.txt");
                    if (in == null) in = Sound.class.getResourceAsStream("/SND/list.txt");
                    if (in == null) return;
                    StringBuffer sb = new StringBuffer();
                    int c;
                    while ((c = in.read()) >= 0) sb.append((char) c);
                    in.close();
                    String s = sb.toString();
                    int p = 0;
                    while (p < s.length()) {
                        int e = s.indexOf('\n', p);
                        if (e < 0) e = s.length();
                        String path = s.substring(p, e).trim();
                        p = e + 1;
                        if (path.endsWith(".wav")) player(bank(path), 0);
                        Thread.yield();
                    }
                } catch (Throwable t) {
                    System.out.println("sound: preload " + t);
                }
            }
        };
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    private synchronized Player player(Bank b, int i) {
        if (b.pool[i] == null && b.data != null) {
            evict();
            try {
                Player p = Manager.createPlayer(new ByteArrayInputStream(b.data), b.type);
                p.realize();
                p.prefetch();
                b.pool[i] = p;
                owner.put(p, b);
            } catch (Throwable t) {
                System.out.println("sound: cannot create player " + b.key + ": " + t);
                if (i == 0) b.data = null;
            }
        }
        Player p = b.pool[i];
        if (p != null) {
            lru.removeElement(p);
            lru.addElement(p);
        }
        return p;
    }

    /** Keep at most MAX_PLAYERS players: close the least recently used idle one. */
    private void evict() {
        if (lru.size() < MAX_PLAYERS) return;
        for (int k = 0; k < lru.size(); k++) {
            Player q = (Player) lru.elementAt(k);
            if (q.getState() == Player.STARTED) continue;
            lru.removeElementAt(k);
            Bank ob = (Bank) owner.remove(q);
            if (ob != null) for (int i = 0; i < ob.pool.length; i++) if (ob.pool[i] == q) ob.pool[i] = null;
            try {
                q.close();
            } catch (Throwable t) {
            }
            return;
        }
    }

    private synchronized void dropPlayers(Bank b) {
        for (int i = 0; i < b.pool.length; i++) {
            Player q = b.pool[i];
            if (q == null) continue;
            b.pool[i] = null;
            lru.removeElement(q);
            owner.remove(q);
            try {
                q.close();
            } catch (Throwable t) {
            }
        }
    }

    // ------------------------------------------------------------------
    // game interface

    Object create(byte[] data) {
        Clip c = new Clip();
        String ref = stub(data);
        c.bank = ref != null ? bank(ref) : rawBank(data);
        String ref2 = stub2(data);
        if (ref2 != null) {
            c.extra = new Clip();
            c.extra.bank = bank(ref2);
        }
        clips.addElement(c);
        return c;
    }

    void play(Object o, boolean loop) {
        if (disabled || !(o instanceof Clip)) return;
        Clip c = (Clip) o;
        if (c.extra != null) play(c.extra, loop);
        Bank b = c.bank;
        c.loop = loop;
        try {
            if (b.music || loop) {
                if (b.music && music != null && music != b) dropPlayers(music); // one song at a time
                if (b.music) music = b;
                Player p = player(b, 0);
                if (p == null) return;
                if (p.getState() == Player.STARTED) p.stop();
                p.setMediaTime(0);
                p.setLoopCount(loop ? -1 : 1);
                applyVolume(p, c.volume);
                p.start();
                c.current = p;
                return;
            }
            // effect: first idle player of the pool (created lazily), else re-trigger the oldest
            Player p = null;
            for (int i = 0; i < b.pool.length && p == null; i++) {
                Player q = player(b, i);
                if (q != null && q.getState() != Player.STARTED) p = q;
            }
            if (p == null) {
                p = player(b, b.next);
                b.next = (b.next + 1) % b.pool.length;
                if (p == null) return;
                p.stop();
            }
            p.setMediaTime(0);
            p.setLoopCount(1);
            applyVolume(p, c.volume);
            voice(p);
            p.start();
            c.current = p;
        } catch (Throwable t) {
            System.out.println("sound: play failed " + b.key + ": " + t);
        }
    }

    /** Bound the number of simultaneous effect voices (oldest is stopped). */
    private void voice(Player p) {
        voices.removeElement(p);
        for (int i = voices.size() - 1; i >= 0; i--) {
            Player q = (Player) voices.elementAt(i);
            if (q.getState() != Player.STARTED) voices.removeElementAt(i);
        }
        while (voices.size() >= MAX_VOICES) {
            Player q = (Player) voices.elementAt(0);
            voices.removeElementAt(0);
            try {
                q.stop();
            } catch (Throwable t) {
            }
        }
        voices.addElement(p);
    }

    void stop(Object o) {
        if (!(o instanceof Clip)) return;
        Clip c = (Clip) o;
        if (c.extra != null) stop(c.extra);
        // one-shot effects run to their end; music and loops stop now
        if (c.current != null && (c.bank.music || c.loop)) {
            try {
                c.current.stop();
            } catch (Throwable t) {
            }
        }
    }

    void free(Object o) {
        if (!(o instanceof Clip)) return;
        stop(o);
        Clip c = (Clip) o;
        c.freed = true;
        clips.removeElement(c);
    }

    void volume(Object o, int v) {
        if (!(o instanceof Clip)) return;
        Clip c = (Clip) o;
        if (c.extra != null) volume(c.extra, v);
        c.volume = v;
        if (c.current != null) applyVolume(c.current, v);
    }

    private static void applyVolume(Player p, int v) {
        if (p == null || v < 0) return;
        try {
            VolumeControl vc = (VolumeControl) p.getControl("VolumeControl");
            if (vc != null) vc.setLevel(Math.max(0, Math.min(100, v)));
        } catch (Throwable t) {
        }
    }

    boolean isPlaying(Object o) {
        if (!(o instanceof Clip)) return false;
        Clip c = (Clip) o;
        if (c.extra != null && isPlaying(c.extra)) return true;
        return c.current != null && c.current.getState() == Player.STARTED;
    }

    /** App paused: silence everything (players stay prefetched). */
    void pauseAll() {
        java.util.Enumeration e = banks.elements();
        while (e.hasMoreElements()) {
            Bank b = (Bank) e.nextElement();
            for (int i = 0; i < b.pool.length; i++) {
                if (b.pool[i] != null) {
                    try {
                        b.pool[i].stop();
                    } catch (Throwable t) {
                    }
                }
            }
        }
    }

    /** App destroyed: release every player. */
    void stopAll() {
        java.util.Enumeration e = banks.elements();
        while (e.hasMoreElements()) {
            Bank b = (Bank) e.nextElement();
            for (int i = 0; i < b.pool.length; i++) {
                if (b.pool[i] != null) {
                    try {
                        b.pool[i].close();
                    } catch (Throwable t) {
                    }
                    b.pool[i] = null;
                }
            }
        }
        voices.removeAllElements();
    }
}
