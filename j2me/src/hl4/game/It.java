package hl4.game;

/**
 * The script interpreter (IT_* / Script_* / Token_* @0x91718..0x92694).  A scene file holds three sections (events, second script table, strings);
 * the bodies are kept as one concatenated byte array per section plus the cumulative offsets (short), like Script_Init builds them.
 * Event bytes: 7 header bytes, then condition bytes (negative, EVT_OnCondition) and script references (>= 0, indexes into the second table).
 * Script bytes: command opcodes (EVT_OnFunction) with their operand bytes; 0xff = message box.
 */
public final class It {
    /** a token = (data, position, end) */
    public static final class Tok {
        public byte[] d;
        public int pos;
        public int end;

        Tok(byte[] d, int pos, int end) {
            this.d = d;
            this.pos = pos;
            this.end = end;
        }

        public Tok copy() {
            return new Tok(d, pos, end);
        }

        public boolean isEnd() {
            return end <= pos;
        }

        /** Token_GetByteByPtr: signed byte, pos++ (0 when the token has no data) */
        public int getByte() {
            if (d == null) {
                return 0;
            }
            return d[pos++];
        }

        /** IT_GetByte_2 / IT_GetCustom: copies n bytes into out and advances */
        public void get(byte[] out, int n) {
            if (d != null) {
                System.arraycopy(d, pos, out, 0, n);
                pos += n;
            }
        }
    }

    // section 1: events
    public static byte[] evData;
    public static int[] evOff;          // short offsets, count + 1 entries
    public static int evCount;
    public static Tok evTok;            // interpreter+0x10
    // section 2: second table
    public static byte[] scData;
    public static int[] scOff;
    public static int scCount;
    public static Tok scTok;            // interpreter+0x24
    // section 3: strings
    public static byte[][] strings = new byte[0][];

    public static Tok cur;              // interpreter+0x28: the script being run
    public static boolean changed;      // interpreter+0x44
    public static int[] msgIds;         // interpreter+0x3c: message-box line lengths (kept as an array)
    public static int[] msgLens;
    public static int msgPos;           // interpreter+0x40
    public static boolean msgPending;   // interpreter+0x3c != 0

    private static Tok[] queue = new Tok[10];
    private static int qn;

    private It() {
    }

    // ---- queue ---------------------------------------------------------------------------------------------------------------------
    public static void qInit() {
        qn = 0;
    }

    public static void qPush(Tok t) {
        if (qn >= queue.length) {
            Tok[] n = new Tok[queue.length + 5];
            System.arraycopy(queue, 0, n, 0, qn);
            queue = n;
        }
        queue[qn++] = t;
    }

    public static Tok qPop() {
        if (qn <= 0) {
            return null;
        }
        Tok t = queue[0];
        for (int i = 1; i < qn; i++) {
            queue[i - 1] = queue[i];
        }
        queue[--qn] = null;
        return t;
    }

    public static int qSize() {
        return qn;
    }

    /** IT_Release */
    public static void release() {
        while (qPop() != null) {
        }
        cur = null;
        evData = null;
        scData = null;
        evTok = null;
        scTok = null;
        strings = new byte[0][];
        msgPending = false;
        msgLens = null;
        msgIds = null;
        evCount = 0;
        scCount = 0;
        changed = false;
    }

    private static int s16(int v) {
        return (short) v;
    }

    /**
     * IT_Initialize(file, hdr, w1, w2, w3): parses the three sections of the DES-decoded scene file.
     * The events section calls EVT_OnInitEvent(i, rawIndex) for every event (rawIndex mimics the original pointer p2 + n + cum).
     */
    public static void initialize(byte[] f, int hdr, int w1, int w2, int w3) {
        release();
        qInit();
        int p = hdr;
        p = section(f, p, w1, true);
        p = section(f, p, w2, false);
        int n = f[p] & 0xff;
        p++;
        strings = new byte[n][];
        int[] lens = new int[n];
        for (int i = 0; i < n; i++) {
            int l = f[p++] & 0xff;
            if (w3 > 1) {
                l += (f[p++] & 0xff) << 8;
            }
            lens[i] = l;
        }
        for (int i = 0; i < n; i++) {
            strings[i] = new byte[lens[i]];
            System.arraycopy(f, p, strings[i], 0, lens[i]);
            p += lens[i];
        }
        qn = 0;
    }

    private static int section(byte[] f, int p0, int w, boolean events) {
        int n = f[p0] & 0xff;
        int[] off = new int[n + 1];
        int cum = 0;
        int q = p0 + 1;
        int[] lens = new int[n];
        for (int i = 0; i < n; i++) {
            if (events) {
                Evt.onInitEvent(i, f, p0 + n + s16(cum));
            }
            int l = f[q++] & 0xff;
            if (w > 1) {
                l += (f[q++] & 0xff) << 8;
            }
            lens[i] = l;
            cum = (cum + l) & 0xffff;
            off[i + 1] = s16(cum);
        }
        int total = s16(cum);
        byte[] data = null;
        if (total > 0) {
            data = new byte[total];
            System.arraycopy(f, q, data, 0, total);
            q += total;
        }
        Tok t = new Tok(data, 0, total);
        if (events) {
            evData = data;
            evOff = off;
            evCount = n;
            evTok = t;
        } else {
            scData = data;
            scOff = off;
            scCount = n;
            scTok = t;
        }
        return q;
    }

    // ---- execution -----------------------------------------------------------------------------------------------------------------
    /** IT_CheckEvent(idx): runs the condition part of event idx; true when scripts were queued */
    public static boolean checkEvent(int idx) {
        if (cur == null) {
            if (idx >= 0 && idx < evCount) {
                int a = evOff[idx];
                if (a != evOff[idx + 1]) {
                    evTok.pos = a;
                    evTok.end = evOff[idx + 1];
                    doEvent(evTok, false);
                    return qn > 0;
                }
            }
        }
        return false;
    }

    /** IT_DoEvent(token, nested) @0x923bc */
    public static boolean doEvent(Tok t, boolean nested) {
        boolean res = false;
        if (nested || Evt.onEvent(t)) {
            while (true) {
                if (t.isEnd()) {
                    return res;
                }
                int b = t.getByte();
                if (b >= 0 && b < scCount) {
                    Tok s = scTok.copy();
                    res = true;
                    s.pos = scOff[b];
                    s.end = scOff[b + 1];
                    qPush(s);
                    continue;
                }
                if (b == -1) {
                    int n = t.getByte();
                    byte[] buf = new byte[n > 0 ? n : 0];
                    for (int i = 0; i < n; i++) {
                        buf[i] = (byte) t.getByte();
                    }
                    Tok sub = t.copy();
                    int start = t.pos;
                    int end = t.pos;
                    for (int i = 0; i < n; i++) {
                        end += buf[i];
                    }
                    int a = start;
                    for (int i = 0; i < n; i++) {
                        sub.pos = a;
                        sub.end = a + buf[i];
                        if (doEvent(sub, true)) {
                            res = true;
                            break;
                        }
                        a += buf[i];
                    }
                    t.pos = end;
                    continue;
                }
                if (!Evt.onCondition(-2 - b, t)) {
                    return res;
                }
            }
        }
        return res;
    }

    /** IT_Tick @0x92694: true while a script is running */
    public static boolean tick() {
        if (!msgPending) {
            do {
                if (cur == null) {
                    cur = qPop();
                    if (cur != null) {
                        if (!doScript(cur)) {
                            return true;
                        }
                    }
                } else if (!doScript(cur)) {
                    return true;
                }
            } while (qn > 0);
            if (changed) {
                changed = false;
                return checkEvent(0);
            }
            return false;
        }
        return true;
    }

    /** IT_DoScript(token) @0x91c08: true = the script ended, false = it yields (wait / message box) */
    public static boolean doScript(Tok t) {
        while (true) {
            if (t.isEnd()) {
                cur = null;
                return true;
            }
            int b = t.getByte();
            if (b == -1) {
                int title = t.getByte() & 0xff;
                int n = t.getByte();
                if (n != 0) {
                    int[] ids = new int[n];
                    int[] lens = new int[n];
                    int sum = 0;
                    for (int i = 0; i < n; i++) {
                        ids[i] = t.getByte() & 0xff;
                        lens[i] = t.getByte() & 0xff;
                        sum += lens[i];
                    }
                    msgLens = lens;
                    msgPending = true;
                    msgPos = t.pos;
                    Evt.onMessageBox(title, ids, n);
                    t.pos = t.pos + sum;
                    return false;
                }
            }
            if (!Evt.onFunction(b, t)) {
                return false;
            }
        }
    }

    /** IT_AlertConfirm(sel) @0x92314: continues with the sub-block of the selected message-box line */
    public static void alertConfirm(int sel) {
        int pos = msgPos;
        qPush(cur);
        Tok n = scTok.copy();
        for (int i = 0; i < sel; i++) {
            pos += msgLens[i] & 0xff;
        }
        int len = msgLens[sel] & 0xff;
        cur = n;
        n.d = curData;
        n.pos = pos;
        n.end = pos + len;
        msgLens = null;
        msgPending = false;
    }

    /** the data array the message-box sub-blocks live in (the script token's data) */
    public static byte[] curData;

    /** IT_GetString(idx): the string bytes (without the trailing NUL), null when out of range */
    public static byte[] getString(int idx) {
        if (idx < strings.length && idx >= 0) {
            return strings[idx];
        }
        return null;
    }
}
