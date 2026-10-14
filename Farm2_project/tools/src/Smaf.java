

import java.io.ByteArrayOutputStream;
import java.util.Vector;

/**
 * SMAF (MMF) converter: score tracks -> Standard MIDI File, PCM (Yamaha ADPCM) tracks -> WAV.
 * Follows the mapping used by dlunch's smaf-player (MIT) that the reference emulator uses.
 */
public final class Smaf {
    public static final String MIDI = "audio/midi";
    public static final String WAV = "audio/x-wav";

    /** Result of a conversion: mime type + data (null data when nothing playable). */
    public static final class Converted {
        public String type;
        public byte[] data;
        public int durationMs;
    }

    static int be32(byte[] b, int o) {
        return ((b[o] & 0xFF) << 24) | ((b[o + 1] & 0xFF) << 16) | ((b[o + 2] & 0xFF) << 8) | (b[o + 3] & 0xFF);
    }

    static int be16(byte[] b, int o) {
        return ((b[o] & 0xFF) << 8) | (b[o + 1] & 0xFF);
    }

    static int timebase(int raw) {
        switch (raw) {
            case 0: return 1;
            case 1: return 2;
            case 2: return 4;
            case 3: return 5;
            case 0x10: return 10;
            case 0x11: return 20;
            case 0x12: return 40;
            case 0x13: return 50;
            default: return 1;
        }
    }

    // ------------------------------------------------------------------
    // events: long key = time << 20 | order, value = byte[] midi message

    static final class Ev {
        int time;
        int order;
        int seq;
        byte[] msg;
    }

    private final Vector events = new Vector();
    private int seqCounter;
    private int endTime;
    private byte[] pcm16;       // decoded PCM for a wave-only file
    private int pcmRate;

    private void add(int time, int order, byte[] msg) {
        Ev e = new Ev();
        e.time = time;
        e.order = order;
        e.seq = seqCounter++;
        e.msg = msg;
        events.addElement(e);
    }

    private void add3(int time, int order, int a, int b, int c) {
        add(time, order, new byte[]{(byte) a, (byte) b, (byte) c});
    }

    public static Converted convert(byte[] data) {
        Converted c = new Converted();
        try {
            if (data == null || data.length < 8 || data[0] != 'M' || data[1] != 'M' || data[2] != 'M' || data[3] != 'D') return c;
            Smaf s = new Smaf();
            int size = be32(data, 4);
            int end = Math.min(data.length, 8 + size);
            int o = 8;
            while (o + 8 <= end) {
                int id0 = data[o] & 0xFF, id1 = data[o + 1] & 0xFF, id2 = data[o + 2] & 0xFF, id3 = data[o + 3] & 0xFF;
                int len = be32(data, o + 4);
                int body = o + 8;
                if (body + len > data.length) len = data.length - body;
                if (id0 == 'M' && id1 == 'T' && id2 == 'R') s.scoreTrack(data, body, len);
                else if (id0 == 'A' && id1 == 'T' && id2 == 'R') s.pcmTrack(data, body, len);
                o = body + len;
            }
            if (!s.events.isEmpty()) {
                c.type = MIDI;
                c.data = s.toMidi();
                c.durationMs = s.endTime;
            } else if (s.pcm16 != null) {
                c.type = WAV;
                c.data = wav(s.pcm16, s.pcmRate);
                c.durationMs = (int) ((s.pcm16.length / 2) * 1000L / Math.max(1, s.pcmRate));
            }
        } catch (Throwable t) {
            c.data = null;
        }
        return c;
    }

    // ------------------------------------------------------------------
    // score track

    static final int MAX_CH = 64;
    int formatType;
    int[] channelTypes = new int[MAX_CH];
    int[] programs = new int[MAX_CH];
    int[] volumes = new int[MAX_CH];
    int[] expressions = new int[MAX_CH];
    int[] velocities = new int[MAX_CH];
    int[] bankMsb = new int[MAX_CH];
    int[] bankLsb = new int[MAX_CH];
    boolean[] forcedRhythm = new boolean[MAX_CH];
    int[] realMap = new int[MAX_CH];
    int handyOffset;

    private void resetToneMap() {
        for (int i = 0; i < MAX_CH; i++) {
            channelTypes[i] = 2;
            programs[i] = 0;
            volumes[i] = 100;
            expressions[i] = 127;
            velocities[i] = 64;
            bankMsb[i] = 0;
            bankLsb[i] = 0;
            forcedRhythm[i] = false;
            realMap[i] = -1;
        }
    }

    private void scoreTrack(byte[] d, int o, int len) {
        int end = o + len;
        formatType = d[o] & 0xFF;
        int tbD = timebase(d[o + 2] & 0xFF), tbG = timebase(d[o + 3] & 0xFF);
        int p = o + 4;
        resetToneMap();
        boolean handy = formatType == 0;
        if (handy) {
            int st = be16(d, p);
            p += 2;
            for (int i = 0; i < 4; i++) {
                int x = (st >> ((3 - i) * 4)) & 0xF;
                channelTypes[handyOffset + i] = (x & 3) == 3 ? 3 : 1;
                velocities[handyOffset + i] = 127;
            }
        } else {
            for (int i = 0; i < 16; i++) {
                int t = d[p + i] & 3;
                channelTypes[i] = t == 1 ? 1 : (t == 3 ? 3 : 2);
            }
            p += 16;
        }
        while (p + 8 <= end) {
            int len2 = be32(d, p + 4);
            int body = p + 8;
            if (body + len2 > end) len2 = end - body;
            if (d[p] == 'M' && d[p + 1] == 't' && d[p + 2] == 's' && d[p + 3] == 'q') {
                byte[] seq;
                int so, sl;
                if (formatType == 1) {
                    seq = huffman(d, body, len2);
                    so = 0;
                    sl = seq == null ? 0 : seq.length;
                } else {
                    seq = d;
                    so = body;
                    sl = len2;
                }
                if (seq != null) {
                    if (handy) sequenceHandy(seq, so, sl, tbD, tbG);
                    else sequenceMobile(seq, so, sl, tbD, tbG);
                }
            }
            p = body + len2;
        }
        if (handy) handyOffset += 4;
    }

    private int pseudo(int ch) {
        return formatType == 0 ? Math.min(ch, MAX_CH - 1) : (ch & 15);
    }

    private boolean isRhythm(int ch) {
        ch = pseudo(ch);
        return channelTypes[ch] == 3 || forcedRhythm[ch] || bankMsb[ch] == 0x7d;
    }

    private static final int[] MELODY_ORDER = {0, 1, 2, 3, 4, 5, 6, 7, 8, 10, 11, 12, 13, 14, 15};

    private int realChannel(int ch) {
        ch = pseudo(ch);
        if (isRhythm(ch)) return 9;
        if (realMap[ch] >= 0) return realMap[ch];
        boolean[] used = new boolean[16];
        used[9] = true;
        for (int i = 0; i < MAX_CH; i++) if (realMap[i] >= 0) used[realMap[i]] = true;
        int r = ch & 15;
        for (int i = 0; i < MELODY_ORDER.length; i++) {
            if (!used[MELODY_ORDER[i]]) {
                r = MELODY_ORDER[i];
                break;
            }
        }
        realMap[ch] = r;
        return r;
    }

    private int mapProgram(int ch, int program) {
        ch = pseudo(ch);
        int msb = bankMsb[ch], lsb = bankLsb[ch], pr = program & 0x7F;
        if (msb == 0x7c && lsb == 0x01) {
            switch (pr) {
                case 0x22: return 81;
                case 0x70: return 30;
                case 0x46: return 84;
                case 0x21: return 33;
                case 0x6a: return 87;
                case 0x62: return 98;
            }
        }
        if (msb == 0x7d && lsb == 0 && pr == 2) return 0;
        return pr;
    }

    private int mapNote(int ch, int note) {
        int ci = pseudo(ch);
        if (formatType == 0) {
            if (isRhythm(ch)) return Math.min(programs[ci], 127);
            return clamp(note + 36, 0, 127);
        }
        note = clamp(note, 0, 127);
        if (!isRhythm(ch)) return note;
        switch (note) {
            case 0x12: return 45;
            case 0x1a: return 41;
            case 0x1f: return 47;
            case 0x4d: return 50;
            case 0x54: return 43;
            case 0x59: return 48;
        }
        return note;
    }

    static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private int[] pos = new int[1];

    private static int vlq(byte[] d, int[] pos) {
        int b = d[pos[0]++] & 0xFF;
        if ((b & 0x80) == 0) return b;
        int r = b & 0x7F;
        while (true) {
            b = d[pos[0]++] & 0xFF;
            r = (r << 7) | (b & 0x7F);
            if ((b & 0x80) == 0) return r;
        }
    }

    private static int handyVlq(byte[] d, int[] pos) {
        int f = d[pos[0]++] & 0xFF;
        if ((f & 0x80) == 0) return f;
        int s = d[pos[0]++] & 0xFF;
        return (((f & 0x7F) + 1) << 7) | s;
    }

    private void note(int time, int ch, int note, int vel, int gate) {
        int ci = pseudo(ch);
        int n = mapNote(ch, note);
        int v;
        if (formatType == 0 && channelTypes[ci] == 3) {
            v = clamp(volumes[ci] * expressions[ci] / (expressions[ci] < 64 ? 102 : 100), 1, 127);
        } else if (vel >= 0) {
            v = Math.min(vel, 127);
            velocities[ci] = v;
        } else v = velocities[ci];
        int rc = realChannel(ch);
        add3(time, 30, 0x90 | rc, n, Math.max(1, v));
        add3(time + gate, 20, 0x80 | rc, n, 0);
        if (time + gate > endTime) endTime = time + gate;
    }

    private void sequenceMobile(byte[] d, int o, int len, int tbD, int tbG) {
        int end = o + len;
        pos[0] = o;
        int now = 0;
        int[] octave = new int[MAX_CH];
        // preclassify rhythm by bank select 0x7d
        while (pos[0] < end) {
            int dur = vlq(d, pos);
            now += dur * tbD;
            int st = d[pos[0]++] & 0xFF;
            int hi = st & 0xF0, ch = st & 0x0F;
            if (hi == 0x80) {
                int nt = d[pos[0]++] & 0xFF;
                int gate = vlq(d, pos);
                note(now, ch, nt + octave[ch] * 12, -1, gate * tbG);
            } else if (hi == 0x90) {
                int nt = d[pos[0]++] & 0xFF;
                int vel = d[pos[0]++] & 0xFF;
                int gate = vlq(d, pos);
                note(now, ch, nt + octave[ch] * 12, vel, gate * tbG);
            } else if (hi == 0xA0) {
                pos[0] += 2;
            } else if (hi == 0xB0) {
                int ctl = d[pos[0]++] & 0x7F, val = d[pos[0]++] & 0x7F;
                int ci = pseudo(ch);
                if (ctl == 0) bankMsb[ci] = val;
                else if (ctl == 32) bankLsb[ci] = val;
                else if (ctl == 7) volumes[ci] = val;
                add3(now, 5, 0xB0 | realChannel(ch), ctl, val);
            } else if (hi == 0xC0) {
                int pr = d[pos[0]++] & 0x7F;
                int ci = pseudo(ch);
                if (bankMsb[ci] == 0x7d) forcedRhythm[ci] = true;
                int mp = isRhythm(ch) ? 0 : mapProgram(ch, pr);
                programs[ci] = mp;
                add(now, 6, new byte[]{(byte) (0xC0 | realChannel(ch)), (byte) mp});
            } else if (hi == 0xD0) {
                pos[0] += 1;
            } else if (hi == 0xE0) {
                int lsb = d[pos[0]++] & 0x7F, msb = d[pos[0]++] & 0x7F;
                add3(now, 5, 0xE0 | realChannel(ch), lsb, msb);
            } else if (st == 0xF0) {
                int l = vlq(d, pos);
                pos[0] += l; // Yamaha specific exclusive: not forwarded
            } else if (st == 0xFF) {
                int b2 = d[pos[0]++] & 0xFF;
                if (b2 == 0x2F) {
                    pos[0]++;
                    break;
                }
            }
        }
        if (now > endTime) endTime = now;
    }

    private static final int[] SHORT_EXPR = {0x00, 0x00, 0x1f, 0x27, 0x2f, 0x37, 0x3f, 0x47, 0x4f, 0x57, 0x5f, 0x67, 0x6f, 0x77, 0x7f};
    private static final int[] SHORT_MOD = {0x00, 0x00, 0x08, 0x10, 0x18, 0x20, 0x28, 0x30, 0x38, 0x40, 0x48, 0x50, 0x60, 0x70, 0x7f};

    private void sequenceHandy(byte[] d, int o, int len, int tbD, int tbG) {
        int end = o + len;
        pos[0] = o;
        int now = 0;
        int[] octave = new int[MAX_CH];
        while (pos[0] < end) {
            if (pos[0] + 4 <= end && d[pos[0]] == 0 && d[pos[0] + 1] == 0 && d[pos[0] + 2] == 0 && d[pos[0] + 3] == 0) break;
            int dur = handyVlq(d, pos);
            now += dur * tbD;
            int st = d[pos[0]++] & 0xFF;
            if (st != 0 && st != 0xFF) {
                int ch = handyOffset + ((st >> 6) & 3);
                int oct = (st >> 4) & 3, voice = st & 15;
                int gate = handyVlq(d, pos);
                note(now, ch, oct * 12 + voice + octave[Math.min(ch, MAX_CH - 1)] * 12, -1, gate * tbG);
            } else if (st == 0) {
                int nb = d[pos[0]++] & 0xFF;
                int ch = handyOffset + ((nb >> 6) & 3);
                int ci = pseudo(ch);
                int et = nb & 0x3F;
                if (et == 0) {
                    pos[0]++;
                } else if (et >= 1 && et <= 0x0e) {
                    expressions[ci] = SHORT_EXPR[et];
                    if (!isRhythm(ch)) add3(now, 5, 0xB0 | realChannel(ch), 7, Math.min(127, volumes[ci] * expressions[ci] / 127));
                } else if (et >= 0x11 && et <= 0x1e) {
                    int v = Math.min(0x3fff, (et - 0x10) * 16384 / 16);
                    add3(now, 5, 0xE0 | realChannel(ch), v & 0x7F, (v >> 7) & 0x7F);
                } else if (et >= 0x21 && et <= 0x2e) {
                    add3(now, 5, 0xB0 | realChannel(ch), 1, SHORT_MOD[et - 0x20]);
                } else {
                    int val = d[pos[0]++] & 0xFF;
                    switch (et) {
                        case 0x30: {
                            if (channelTypes[ci] == 3) {
                                programs[ci] = Math.min(val, 127);
                                add(now, 6, new byte[]{(byte) 0xC9, 0});
                            } else {
                                if (bankMsb[ci] == 0x7d) forcedRhythm[ci] = true;
                                int mp = isRhythm(ch) ? 0 : mapProgram(ch, val);
                                programs[ci] = mp;
                                add(now, 6, new byte[]{(byte) (0xC0 | realChannel(ch)), (byte) mp});
                            }
                            break;
                        }
                        case 0x31:
                            if ((val & 0x80) != 0) forcedRhythm[ci] = true;
                            bankMsb[ci] = val & 0x7F;
                            add3(now, 5, 0xB0 | realChannel(ch), 0, val & 0x7F);
                            break;
                        case 0x32:
                            if (val <= 4) octave[Math.min(ch, MAX_CH - 1)] = val;
                            else if (val >= 0x81 && val <= 0x84) octave[Math.min(ch, MAX_CH - 1)] = -(val - 0x80);
                            break;
                        case 0x33:
                            add3(now, 5, 0xB0 | realChannel(ch), 1, val & 0x7F);
                            break;
                        case 0x34: {
                            int v = clamp(8192 + (val - 128) * 64, 0, 16383);
                            add3(now, 5, 0xE0 | realChannel(ch), v & 0x7F, (v >> 7) & 0x7F);
                            break;
                        }
                        case 0x36:
                        case 0x3b:
                            expressions[ci] = Math.min(val, 127);
                            if (!isRhythm(ch)) add3(now, 5, 0xB0 | realChannel(ch), 7, Math.min(127, volumes[ci] * expressions[ci] / 127));
                            break;
                        case 0x37:
                            volumes[ci] = Math.min(val, 127);
                            if (isRhythm(ch)) add3(now, 5, 0xB9, 7, 100);
                            else add3(now, 5, 0xB0 | realChannel(ch), 7, Math.min(127, volumes[ci] * expressions[ci] / 127));
                            break;
                        case 0x3a:
                            add3(now, 5, 0xB0 | realChannel(ch), 10, val & 0x7F);
                            break;
                        default:
                            break;
                    }
                }
            } else { // 0xFF
                int nb = d[pos[0]++] & 0xFF;
                if (nb == 0xF0) {
                    while (pos[0] < end && (d[pos[0]] & 0xFF) != 0xF7) pos[0]++;
                    pos[0]++;
                }
            }
        }
        if (now > endTime) endTime = now;
    }

    private static byte[] huffman(byte[] src, int off, int len) {
        int decodedLen = be32(src, off);
        int[] bitPos = {(off + 4) * 8};
        int limit = (off + len) * 8;
        int[] left = new int[511], right = new int[511];
        int[] avail = {256};
        int root = readTree(src, bitPos, limit, left, right, avail);
        if (root < 0) return null;
        byte[] out = new byte[decodedLen];
        for (int i = 0; i < decodedLen; i++) {
            int node = root;
            while (node >= 256) {
                int b = bit(src, bitPos, limit);
                if (b < 0) return null;
                node = b == 1 ? right[node] : left[node];
            }
            out[i] = (byte) node;
        }
        return out;
    }

    private static int bit(byte[] s, int[] bp, int limit) {
        if (bp[0] >= limit) return -1;
        int b = (s[bp[0] >> 3] >> (7 - (bp[0] & 7))) & 1;
        bp[0]++;
        return b;
    }

    private static int readTree(byte[] s, int[] bp, int limit, int[] left, int[] right, int[] avail) {
        int b = bit(s, bp, limit);
        if (b < 0) return -1;
        if (b == 1) {
            int node = avail[0]++;
            if (avail[0] > 511) return -1;
            left[node] = readTree(s, bp, limit, left, right, avail);
            right[node] = readTree(s, bp, limit, left, right, avail);
            if (left[node] < 0 || right[node] < 0) return -1;
            return node;
        }
        int v = 0;
        for (int i = 0; i < 8; i++) {
            int x = bit(s, bp, limit);
            if (x < 0) return -1;
            v = (v << 1) | x;
        }
        return v;
    }

    // ------------------------------------------------------------------
    // PCM audio track (ADPCM)

    private void pcmTrack(byte[] d, int o, int len) {
        int end = o + len;
        int waveType = be16(d, o + 2);
        int fmt = (waveType >> 12) & 7;
        int rateCode = (waveType >> 8) & 15;
        int rate;
        switch (rateCode) {
            case 0: rate = 4000; break;
            case 1: rate = 8000; break;
            case 2: rate = 11025; break;
            case 3: rate = 22050; break;
            default: rate = 44100; break;
        }
        int p = o + 6;
        // collect wave data chunks; play the first wave referenced by sequence (effects use one wave)
        byte[] firstWave = null;
        int waveNum = -1;
        int seqFirst = -1;
        byte[][] waves = new byte[64][];
        while (p + 8 <= end) {
            int l = be32(d, p + 4);
            int body = p + 8;
            if (body + l > end) l = end - body;
            if (d[p] == 'A' && d[p + 1] == 'w' && d[p + 2] == 'a') {
                int n = d[p + 3] & 0x3F;
                byte[] w = new byte[l];
                System.arraycopy(d, body, w, 0, l);
                waves[n] = w;
                if (firstWave == null) {
                    firstWave = w;
                    waveNum = n;
                }
            } else if (d[p] == 'A' && d[p + 1] == 't' && d[p + 2] == 's' && d[p + 3] == 'q') {
                // find the first wave message
                int[] q = {body};
                int qe = body + l;
                while (q[0] < qe && seqFirst < 0) {
                    vlq(d, q);
                    int fb = d[q[0]++] & 0xFF;
                    if (fb == 0xFF) {
                        int sb = d[q[0]++] & 0xFF;
                        if (sb == 0xF0) q[0] += (d[q[0]] & 0xFF) + 1;
                    } else if (fb != 0) {
                        seqFirst = fb & 0x3F;
                        vlq(d, q);
                    } else {
                        int sb = d[q[0]++] & 0x3F;
                        if (sb == 0x34 || sb == 0x36 || sb == 0x3b || sb == 0x37 || sb == 0x3a) q[0]++;
                    }
                }
            }
            p = body + l;
        }
        byte[] w = seqFirst >= 0 && waves[seqFirst] != null ? waves[seqFirst] : firstWave;
        if (w == null) return;
        short[] pcm;
        if (fmt == 1) pcm = adpcm(w);
        else {
            pcm = new short[w.length];
            for (int i = 0; i < w.length; i++) pcm[i] = (short) (w[i] << 8);
        }
        byte[] b = new byte[pcm.length * 2];
        for (int i = 0; i < pcm.length; i++) {
            b[i * 2] = (byte) pcm[i];
            b[i * 2 + 1] = (byte) (pcm[i] >> 8);
        }
        pcm16 = b;
        pcmRate = rate;
    }

    // Yamaha (MA-3/YMU) ADPCM, identical to ffmpeg's adpcm_yamaha (the reference MMF decoder)
    private static final int[] SCALE = {230, 230, 230, 230, 307, 409, 512, 614};

    static short[] adpcm(byte[] data) {
        short[] out = new short[data.length * 2];
        int pred = 0, step = 127;
        int k = 0;
        for (int i = 0; i < data.length; i++) {
            int b = data[i] & 0xFF;
            for (int nib = 0; nib < 2; nib++) {
                int s = nib == 0 ? (b & 15) : (b >> 4); // low nibble first
                int delta = s & 7;
                int diff = (step * (1 + (delta << 1))) / 8;
                pred = clamp((s & 8) != 0 ? pred - diff : pred + diff, -32768, 32767);
                step = clamp((step * SCALE[delta]) >> 8, 127, 24576);
                out[k++] = (short) pred;
            }
        }
        // short fade out: the last predictor value is rarely 0, avoid a click at the end
        int fade = Math.min(k, 40);
        for (int i = 0; i < fade; i++) out[k - 1 - i] = (short) (out[k - 1 - i] * i / fade);
        return out;
    }

    static byte[] wav(byte[] pcm, int rate) {
        ByteArrayOutputStream o = new ByteArrayOutputStream(pcm.length + 44);
        o.write('R'); o.write('I'); o.write('F'); o.write('F');
        le32(o, 36 + pcm.length);
        o.write('W'); o.write('A'); o.write('V'); o.write('E');
        o.write('f'); o.write('m'); o.write('t'); o.write(' ');
        le32(o, 16);
        le16(o, 1);
        le16(o, 1);
        le32(o, rate);
        le32(o, rate * 2);
        le16(o, 2);
        le16(o, 16);
        o.write('d'); o.write('a'); o.write('t'); o.write('a');
        le32(o, pcm.length);
        o.write(pcm, 0, pcm.length);
        return o.toByteArray();
    }

    static void le32(ByteArrayOutputStream o, int v) {
        o.write(v);
        o.write(v >> 8);
        o.write(v >> 16);
        o.write(v >> 24);
    }

    static void le16(ByteArrayOutputStream o, int v) {
        o.write(v);
        o.write(v >> 8);
    }

    // ------------------------------------------------------------------
    // MIDI writer (format 0, 1 tick = 1 ms)

    private byte[] toMidi() {
        Ev[] ev = new Ev[events.size()];
        events.copyInto(ev);
        // insertion-friendly merge sort by (time, order, seq)
        sort(ev);
        ByteArrayOutputStream trk = new ByteArrayOutputStream();
        // tempo 500000 us/qn with 500 ticks/qn -> 1 tick = 1 ms
        trk.write(0);
        trk.write(0xFF);
        trk.write(0x51);
        trk.write(3);
        trk.write(0x07);
        trk.write(0xA1);
        trk.write(0x20);
        // GM reset-ish: set all channel volumes default, drum channel program 0
        int last = 0;
        for (int i = 0; i < ev.length; i++) {
            int t = ev[i].time;
            writeVlq(trk, t - last);
            last = t;
            trk.write(ev[i].msg, 0, ev[i].msg.length);
        }
        writeVlq(trk, Math.max(0, endTime - last));
        trk.write(0xFF);
        trk.write(0x2F);
        trk.write(0);
        byte[] tb = trk.toByteArray();
        ByteArrayOutputStream f = new ByteArrayOutputStream(tb.length + 22);
        f.write('M'); f.write('T'); f.write('h'); f.write('d');
        be32(f, 6);
        be16(f, 0);
        be16(f, 1);
        be16(f, 500);
        f.write('M'); f.write('T'); f.write('r'); f.write('k');
        be32(f, tb.length);
        f.write(tb, 0, tb.length);
        return f.toByteArray();
    }

    private static void sort(Ev[] a) {
        Ev[] tmp = new Ev[a.length];
        msort(a, tmp, 0, a.length);
    }

    private static boolean le(Ev x, Ev y) {
        if (x.time != y.time) return x.time < y.time;
        if (x.order != y.order) return x.order < y.order;
        return x.seq <= y.seq;
    }

    private static void msort(Ev[] a, Ev[] t, int lo, int hi) {
        if (hi - lo < 2) return;
        int mid = (lo + hi) >>> 1;
        msort(a, t, lo, mid);
        msort(a, t, mid, hi);
        int i = lo, j = mid, k = lo;
        while (i < mid && j < hi) t[k++] = le(a[i], a[j]) ? a[i++] : a[j++];
        while (i < mid) t[k++] = a[i++];
        while (j < hi) t[k++] = a[j++];
        System.arraycopy(t, lo, a, lo, hi - lo);
    }

    static void writeVlq(ByteArrayOutputStream o, int v) {
        if (v < 0) v = 0;
        int buf = v & 0x7F;
        while ((v >>= 7) > 0) {
            buf <<= 8;
            buf |= ((v & 0x7F) | 0x80);
        }
        while (true) {
            o.write(buf & 0xFF);
            if ((buf & 0x80) != 0) buf >>= 8;
            else break;
        }
    }

    static void be32(ByteArrayOutputStream o, int v) {
        o.write(v >> 24);
        o.write(v >> 16);
        o.write(v >> 8);
        o.write(v);
    }

    static void be16(ByteArrayOutputStream o, int v) {
        o.write(v >> 8);
        o.write(v);
    }
}
