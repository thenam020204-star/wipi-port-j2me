package hl4.game;

import hl4.sys.Bytes;
import hl4.sys.Res;
import hl4.sys.Rng;

/**
 * Skill particle effects: the data side (HERO_loadParticleInfo @0x8daec, HERO_loadParticleData @0x8d940, loadParticleSet @0xc9690).  'sets' = PaticleSets (2 x 0xbc bytes),
 * 'info' = hero+0x1f0 table (14 x 6 bytes from HDAT/_H_PDAT).  The runtime (generateParticles / onIdleParticles / paintParticles) is ported with the hero skills.
 */
public final class Particles {
    public static byte[] sets;
    public static byte[] info;
    public static int curCount;
    public static int maxCount;
    public static byte[] particles;
    /** particle sprites (hero+0xa50 + 0x74*i): CIFs of HDAT/_H_PS### sharing the hero's image sets */
    public static final hl4.gfx.Sprite[] sprites = new hl4.gfx.Sprite[3];

    private Particles() {
    }

    /** HERO_loadParticleInfo @0x8daec */
    public static void loadParticleInfo() {
        info = new byte[0x54];
        byte[] d = Res.load("/HDAT/_H_PDAT");
        int n = (short) Bytes.s16(d, 0);
        int p = 2;
        for (int i = 0; i < n; i++) {
            for (int k = 0; k < 6; k++) {
                info[6 * i + k] = d[p + k];
            }
            p += 6;
        }
    }

    /** HERO_loadParticleData @0x8d940: the particle sets of the class and the particle sprites (HDAT/_H_PS###) */
    public static void loadParticleData() {
        Char h = Pool.hero;
        int cls = h.m[0x16b];
        loadParticleSet(cls);
        int count = Tab.HP_COUNT[cls & 0xff];
        for (int i = 0; i < count; i++) {
            for (int e = 0; e < 14; e++) {
                int o = 6 * e;
                if (info[o + 2] == cls) {
                    int cv = Tab.HP_FILE[i + cls * 3];
                    if (info[o + 3] == cv) {
                        info[o + 3] = (byte) i;
                    }
                    if (info[o + 4] == cv) {
                        info[o + 4] = (byte) i;
                    }
                    if (info[o + 5] == cv) {
                        info[o + 5] = (byte) i;
                    }
                }
            }
            int f = Tab.HP_FILE[i + cls * 3];
            if (f != -1) {
                String n = f < 10 ? "00" + f : (f < 100 ? "0" + f : String.valueOf(f));
                hl4.gfx.Sprite s = new hl4.gfx.Sprite(Res.load("/HDAT/_H_PS" + n), 0x11);
                s.sets = h.spr.sets;                        // HERO_Copy_Sprite_ImageInfo: the sprite shares the hero's image arrays
                s.orig = h.spr.orig;
                sprites[i] = s;
            }
        }
    }

    /** loadParticleSet(class) @0xc9690 */
    public static void loadParticleSet(int cls) {
        curCount = 0;
        sets = new byte[0x178];
        int sVar3 = 0;
        int sVar9 = 0;
        for (int k = 0; k < 2; k++) {
            int file = Tab.PS_FILE[k + cls * 2];
            if (file == -1) {
                continue;
            }
            String n = file < 10 ? "00" + file : (file < 100 ? "0" + file : String.valueOf(file));
            byte[] f = Res.load("/HDAT/_H_P" + n);
            int b = k * 0xbc;
            sets[b + 0xb9] = f[0];
            Bytes.w16(sets, b + 0x48, Bytes.s16(f, 1));
            int cnt = Tab.PS_COUNT[sets[b + 0xb9]];
            int p = 3;
            for (int i = 0; i < cnt; i++) {
                Bytes.w16(sets, b + 0x3c + 2 * i, Bytes.s16(f, p));
                Bytes.w32(sets, b + 0x24 + 4 * i, Bytes.s32(f, p + 2));
                Bytes.w32(sets, b + 0x30 + 4 * i, Bytes.s32(f, p + 6));
                Bytes.w16(sets, b + 0x42 + 2 * i, Bytes.s16(f, p + 10));
                Bytes.w16(sets, b + 0x62 + 2 * i, Bytes.s16(f, p + 0xc));
                Bytes.w16(sets, b + 0x68 + 2 * i, Bytes.s16(f, p + 0xe));
                sets[b + 0x9e + i] = f[p + 0x10];
                sets[b + 0xa1 + i] = f[p + 0x11];
                sets[b + 0xaa + i] = f[p + 0x12];
                sets[b + 0xa4 + i] = f[p + 0x13];
                sets[b + 0xa7 + i] = f[p + 0x14];
                sets[b + 0xad + i] = f[p + 0x15];
                Bytes.w16(sets, b + 0x56 + 2 * i, Bytes.s16(f, p + 0x17));
                Bytes.w16(sets, b + 0x5c + 2 * i, Bytes.s16(f, p + 0x19));
                Bytes.w16(sets, b + 0x4a + 2 * i, Bytes.s16(f, p + 0x1c));
                Bytes.w16(sets, b + 0x50 + 2 * i, Bytes.s16(f, p + 0x1e));
                sets[b + 0xb0 + i] = f[p + 0x20];
                Bytes.w16(sets, b + 0x6e + 2 * i, Bytes.s16(f, p + 0x21));
                Bytes.w16(sets, b + 0x74 + 2 * i, Bytes.s16(f, p + 0x23));
                Bytes.w16(sets, b + 0x7a + 2 * i, Bytes.s16(f, p + 0x25));
                Bytes.w16(sets, b + 0x80 + 2 * i, Bytes.s16(f, p + 0x27));
                sets[b + 0xb3 + i] = f[p + 0x29];
                Bytes.w16(sets, b + 0x86 + 2 * i, Bytes.s16(f, p + 0x2a));
                Bytes.w16(sets, b + 0x8c + 2 * i, Bytes.s16(f, p + 0x2c));
                Bytes.w16(sets, b + 0x92 + 2 * i, Bytes.s16(f, p + 0x2e));
                Bytes.w16(sets, b + 0x98 + 2 * i, Bytes.s16(f, p + 0x30));
                p += 0x32;
            }
            for (int i = 0; i < cnt; i++) {
                sVar9 = (short) (sVar9 + Bytes.s16(sets, b + 0x3c + 2 * i));
                sVar3 = (short) (sVar9 * 6);
                maxCount = sVar3;
            }
        }
        particles = new byte[sVar3 * 0x28];
    }

    // ---- runtime (generateParticles @0xb9e74, generatePosition @0xb9bec, skipTimeParticle @0xba48c, onIdleParticles @0xba844, deleteParticle @0xb9410,
    // ---- paintParticles @0xbaf5c, Draw_SpriteOBJ @0xbad2c, HERO_extra_set_particles @0x7cc60) -------------------------------------------------

    /** the sprite objects of the particle sets: set * 3 + index (the original keeps the pointers at +0, +0xc, +0x18 of the 0xbc-byte set record) */
    private static final hl4.gfx.Sprite[] setSpr = new hl4.gfx.Sprite[6];

    /** sin_table @0xe1a34 / cos_table @0xe1a7c: 72 entries, 5 degrees apart (percent) */
    private static final byte[] SIN = {0, 9, 17, 26, 34, 42, 50, 57, 64, 71, 77, 82, 87, 91, 94, 97, 98, 99, 100, 99, 98, 97, 94, 91, 87, 82, 77, 71, 64, 57, 50, 42, 34,
            26, 17, 9, 0, -9, -17, -26, -34, -42, -50, -57, -64, -71, -77, -82, -87, -91, -94, -97, -98, -99, -100, -99, -98, -97, -94, -91, -87, -82, -77, -71, -64, -57,
            -50, -42, -34, -26, -17, -9};
    private static final byte[] COS = {100, 99, 98, 97, 94, 91, 87, 82, 77, 71, 64, 57, 50, 42, 34, 26, 17, 9, 0, -9, -17, -26, -34, -42, -50, -57, -64, -71, -77, -82,
            -87, -91, -94, -97, -98, -99, -100, -99, -98, -97, -94, -91, -87, -82, -77, -71, -64, -57, -50, -42, -34, -26, -17, -9, 0, 9, 17, 26, 34, 42, 50, 57, 64, 71,
            77, 82, 87, 91, 94, 97, 98, 99};

    /** nSin_(a) @0xb9580: sin_table[(a % 360) / 5] */
    private static int nSin(int a) {
        return SIN[(a - a / 360 * 360) / 5];
    }

    /** nCos_(a) @0xb95ec */
    private static int nCos(int a) {
        return COS[(a - a / 360 * 360) / 5];
    }

    private static void later(String what) {
        throw new IllegalStateException(what + ": later");
    }

    /** setParticleSetEffectObject(set, spr0, spr1, spr2, a, b, c) @0xb9658 */
    static void setSetEffectObject(int setOff, hl4.gfx.Sprite s0, hl4.gfx.Sprite s1, hl4.gfx.Sprite s2, int a, int b, int c) {
        int k = setOff / 0xbc * 3;
        if (s0 != null) {
            setSpr[k] = s0;
        }
        if (s1 != null) {
            setSpr[k + 1] = s1;
        }
        if (s2 != null) {
            setSpr[k + 2] = s2;
        }
        sets[setOff + 0xb6] = (byte) a;
        sets[setOff + 0xb7] = (byte) b;
        sets[setOff + 0xb8] = (byte) c;
    }

    /** HERO_extra_set_particles(info index, x, y) @0x7cc60: the particle effect 'info' of the hero's class at (x, y) */
    static void extraSetParticles(int idx, int x, int y) {
        Char h = Pool.hero;
        if (idx < 0) {
            return;
        }
        int o = idx * 6;
        if (info[o + 2] != h.m[0x16b]) {
            return;
        }
        hl4.gfx.Sprite[] sp = new hl4.gfx.Sprite[3];
        int[] val = new int[3];
        for (int k = 0; k < 3; k++) {
            int v = info[o + 3 + k];
            if (v >= 0) {
                sp[k] = sprites[v];
                val[k] = sprites[v].maxFrame(0, 0);
            }
        }
        int setOff = info[o] * 0xbc;
        setSetEffectObject(setOff, sp[0], sp[1], sp[2], val[0], val[1], val[2]);
        generate(idx, (short) x, (short) y, setOff, 0, info[o] & 0xff, 0);
    }

    /** generatePosition(mode, p2, p3, p4, p5) @0xb9bec: x in the low half, y in the high half */
    private static int generatePosition(int mode, int p2, int p3, int p4, int p5) {
        int x = 0;
        int y = 0;
        switch (mode) {
            case 0:
                x = p2;
                y = p3;
                break;
            case 1:
            case 5: {
                if (mode == 1) {
                    if (p2 == p4) {
                        Rng.rand(p3, p5);
                    } else {
                        Rng.rand(p2, p4);
                    }
                }
                int r = Rng.rand(0, 3) & 0xff;
                if (r == 0) {
                    x = Rng.rand(p2, p4);
                    y = p3;
                } else if (r == 1) {
                    x = Rng.rand(p2, p4);
                    y = p5;
                } else if (r == 2) {
                    y = Rng.rand(p3, p5);
                    x = p2;
                } else {
                    y = Rng.rand(p3, p5);
                    x = p4;
                }
                break;
            }
            case 2: {
                int a = (short) Rng.rand(0, 0x167);
                int b = (short) Rng.rand(0, p4);
                int r = p4 - b;
                x = nSin(a) * r / 100 + p2;
                y = nCos(a) * r / 100 + p3;
                break;
            }
            case 3:
                x = Rng.rand(p2, p4);
                y = Rng.rand(p3, p5);
                break;
            case 4: {
                int a = (short) Rng.rand(0, 0x167);
                x = nSin(a) * p4 / 100 + p2;
                y = nCos(a) * p4 / 100 + p3;
                break;
            }
            default:
                later("generatePosition mode " + mode);
        }
        return ((short) y << 16) | ((short) x & 0xffff);
    }

    /** a random number between two short bounds of the set record (the original's UM_Rand(|a|, |b|) with the sign of b) */
    private static int randSigned(int a, int b) {
        if (a == b) {
            return a;
        }
        int r = Rng.rand(a < 0 ? -a : a, b < 0 ? -b : b);
        return b < 0 ? -r : r;
    }

    /** generateParticles(owner, x, y, set, index, setId, p7) @0xb9e74: creates the particles of one entry of a set; false when nothing was created */
    static boolean generate(int owner, int x, int y, int setOff, int idx, int setId, int p7) {
        byte[] pb = particles;
        int cnt0 = (short) curCount;
        int n = Bytes.s16(sets, setOff + 0x3c + 2 * idx);
        if ((short) maxCount - cnt0 < n) {
            n = (short) maxCount - cnt0;
        }
        if (Bytes.s16(sets, setOff + 0x68 + 2 * idx) < 1 || Bytes.s32(sets, setOff + 0x30 + 4 * idx) < 1 || (short) maxCount < owner) {
            return false;
        }
        int end = n + cnt0;
        int last = curCount;
        if (cnt0 < end) {
            int q = cnt0 * 0x28;
            int slot = cnt0;
            int k = 0;
            do {
                pb[q] = sets[setOff + 0xb9];
                pb[q + 1] = (byte) idx;
                pb[q + 3] = (byte) owner;
                int a = Bytes.s16(sets, setOff + 0x4a + 2 * idx);
                int b = Bytes.s16(sets, setOff + 0x50 + 2 * idx);
                Bytes.w16(pb, q + 4, a == b ? a : randSigned(a, b));
                a = Bytes.s16(sets, setOff + 0x56 + 2 * idx);
                b = Bytes.s16(sets, setOff + 0x5c + 2 * idx);
                Bytes.w16(pb, q + 6, a == b ? a : randSigned(a, b));
                int pos = generatePosition(sets[setOff + idx + 0xb0], Bytes.s16(sets, setOff + 0x6e + 2 * idx), Bytes.s16(sets, setOff + 0x74 + 2 * idx),
                        Bytes.s16(sets, setOff + 0x7a + 2 * idx), Bytes.s16(sets, setOff + 0x80 + 2 * idx));
                int px = (short) (x + (pos & 0xffff));
                int py = (short) (y + (pos >>> 16));
                Bytes.w16(pb, q + 8, px);
                Bytes.w16(pb, q + 0xc, px);
                Bytes.w16(pb, q + 0xe, py);
                Bytes.w16(pb, q + 0x12, py);
                pos = generatePosition(sets[setOff + idx + 0xb3], Bytes.s16(sets, setOff + 0x86 + 2 * idx), Bytes.s16(sets, setOff + 0x8c + 2 * idx),
                        Bytes.s16(sets, setOff + 0x92 + 2 * idx), Bytes.s16(sets, setOff + 0x98 + 2 * idx));
                Bytes.w16(pb, q + 0xa, (short) x + (short) pos);
                Bytes.w16(pb, q + 0x10, (short) y + (short) (pos >> 16));
                pb[q + 0x26] = sets[setOff + 0x9e + idx];
                int i1 = Bytes.s32(sets, setOff + 0x24 + 4 * idx);
                int i2 = Bytes.s32(sets, setOff + 0x30 + 4 * idx);
                Bytes.w32(pb, q + 0x14, i1 == i2 ? i1 : Rng.rand(i1, i2));
                int j1 = Bytes.s16(sets, setOff + 0x62 + 2 * idx);
                int j2 = Bytes.s16(sets, setOff + 0x68 + 2 * idx);
                Bytes.w32(pb, q + 0x18, j1 == j2 ? j1 : Rng.rand(j1, j2));
                if (Bytes.s32(pb, q + 0x18) < Bytes.s32(pb, q + 0x14)) {
                    Bytes.w32(pb, q + 0x14, Bytes.s32(pb, q + 0x18));
                }
                int t = Bytes.s16(sets, setOff + 0x42 + 2 * idx);
                Bytes.w32(pb, q + 0x1c, 0);
                boolean frame0;
                if (t == 0) {
                    Bytes.w32(pb, q + 0x20, 0);
                    frame0 = p7 != 1;
                } else {
                    Bytes.w32(pb, q + 0x20, Rng.rand(0, t));
                    frame0 = p7 != 1;
                }
                if (frame0) {
                    Bytes.w16(pb, q + 0x24, 0);
                } else {
                    int d = sets[(pb[q + 2] & 0xff) * 0xbc + idx + 0xb6] & 0xff;
                    Bytes.w16(pb, q + 0x24, slot % d);
                }
                pb[q + 2] = (byte) setId;
                int tskip = Bytes.s16(sets, (pb[q + 2] & 0xff) * 0xbc + 0x48);
                if (tskip > 0 && (idx == 0 || (sets[setOff + 0xb9] == 3 && idx == 1))) {
                    skipTime((short) (k + cnt0), tskip, 0);
                }
                slot = k + cnt0 + 1;
                q += 0x28;
                k++;
                last = curCount;
            } while (slot < n + (short) curCount);
        }
        curCount = (short) (last + n);
        if (sets[setOff + 0xb9] == 3 && idx == 0) {
            generate(owner, x, y, setOff, 1, setId, p7);
        }
        return true;
    }

    /** deleteParticle(i) @0xb9410: the last particle takes the slot */
    static void deleteParticle(int i) {
        if (i != curCount - 1) {
            System.arraycopy(particles, (curCount - 1) * 0x28, particles, (short) i * 0x28, 0x27);
        }
        curCount--;
    }

    /**
     * the position / frame / sprite-state update of one particle shared by onIdleParticles and skipTimeParticle (age = +0x1c, life = +0x18): position
     * interpolated between the start (+8,+0xe) and end (+0xa,+0x10) points, then velocity (+6) and acceleration (+4) terms
     */
    private static void step(int q) {
        byte[] pb = particles;
        int age = Bytes.s32(pb, q + 0x1c);
        int life = Bytes.s32(pb, q + 0x18);
        Bytes.w16(pb, q + 0xc, (age * Bytes.s16(pb, q + 0xa) + Bytes.s16(pb, q + 8) * (life - age)) / life);
        Bytes.w16(pb, q + 0x12, (age * Bytes.s16(pb, q + 0x10) + Bytes.s16(pb, q + 0xe) * (life - age)) / life);
        int set = (pb[q + 2] & 0xff) * 0xbc;
        Bytes.w16(pb, q + 0x24, Bytes.s16(pb, q + 0x24) + 1);
        Bytes.w16(pb, q + 0xc, Bytes.s16(pb, q + 0xc) + (short) (age * Bytes.s16(pb, q + 6) / 1000));
        Bytes.w16(pb, q + 0x12, Bytes.s16(pb, q + 0x12) + (short) (Bytes.s16(pb, q + 4) * age * age / 1000000));
        if ((sets[set + (pb[q + 1] & 0xff) + 0xb6] & 0xff) < Bytes.s16(pb, q + 0x24)) {
            Bytes.w16(pb, q + 0x24, 0);
        }
    }

    /** the sprite state (+0x26) of a particle from the percent of its life gone */
    private static void state(int q, boolean arrived) {
        byte[] pb = particles;
        int base = (pb[q + 2] & 0xff) * 0xbc + (pb[q + 1] & 0xff);
        if (arrived) {
            pb[q + 0x26] = sets[base + 0xa7];
            return;
        }
        int pct = (short) (Bytes.s32(pb, q + 0x1c) * 100 / Bytes.s32(pb, q + 0x14));
        if (100 - (sets[base + 0xad] & 0xff) < pct) {
            pb[q + 0x26] = sets[base + 0xa4];
        } else if ((sets[base + 0xaa] & 0xff) < pct) {
            pb[q + 0x26] = sets[base + 0xa1];
        }
    }

    /** the end of a particle's life: the follow-up particles of the types 1 and 2 */
    private static void lifeEnd(int q) {
        byte[] pb = particles;
        int type = (pb[q] & 0xff) | (pb[q + 1] & 0xff) << 8;
        int setId = pb[q + 2] & 0xff;
        if (type == 1) {
            generate(pb[q + 3], Bytes.s16(pb, q + 0xc), Bytes.s16(pb, q + 0x12), setId * 0xbc, 1, setId, 1);
        } else if (type == 2) {
            generate(pb[q + 3], Bytes.s16(pb, q + 0xc), Bytes.s16(pb, q + 0x12), setId * 0xbc, 1, setId, 1);
            generate(pb[q + 3], Bytes.s16(pb, q + 0xc), Bytes.s16(pb, q + 0x12), setId * 0xbc, 2, setId, 1);
        }
        Bytes.w32(pb, q + 0x1c, Bytes.s32(pb, q + 0x14));
    }

    /** skipTimeParticle(i, _, delta) @0xba48c: a particle made late is advanced by its delay; the original ends a finished one with deleteParticle(0) */
    private static void skipTime(int i, int unused, int hi) {
        later("skipTimeParticle");
    }

    /** onIdleParticles(ms) @0xba844: advances every particle by the elapsed time */
    static void onIdle(long interval) {
        int lo = (int) interval;
        int hi = (int) (interval >> 32);
        byte[] pb = particles;
        if ((short) curCount <= 0) {
            return;
        }
        int i = 0;
        do {
            int q = (short) i * 0x28;
            step(q);
            boolean arrived = Bytes.s16(pb, q + 0xc) == Bytes.s16(pb, q + 0xa) && Bytes.s16(pb, q + 0x12) == Bytes.s16(pb, q + 0x10);
            state(q, arrived);
            boolean del = false;
            boolean adv = false;
            int amount = 0;
            int delay = Bytes.s32(pb, q + 0x20);
            if (delay > 0) {
                delay -= lo;
                Bytes.w32(pb, q + 0x20, delay);
                if (delay < 0) {
                    amount = -delay;
                    Bytes.w32(pb, q + 0x20, 0);
                    adv = amount > 0;
                }
            } else if (hi == 0 && lo != 0) {
                adv = true;
                amount = lo;
            } else if (hi > 0) {
                adv = true;
                amount = lo;
            }
            boolean check = true;
            if (adv) {
                int age = Bytes.s32(pb, q + 0x1c);
                int life = Bytes.s32(pb, q + 0x14);
                if (life <= age) {
                    del = true;
                    check = false;
                } else {
                    age += amount;
                    Bytes.w32(pb, q + 0x1c, age);
                    if (age < life) {
                        check = false;
                    } else {
                        lifeEnd(q);
                    }
                }
            }
            if (check && Bytes.s32(pb, q + 0x14) <= Bytes.s32(pb, q + 0x1c)) {
                del = true;
            }
            if (del) {
                deleteParticle(i);
                i = (i - 1) & 0xffff;
            }
            i = (i + 1) & 0xffff;
        } while ((short) i < (short) curCount);
    }

    /** Draw_SpriteOBJ(set sprite, x, y, state, frame) @0xbad2c: the particle sprite (motion 0, direction 0) with the alpha 'state', drawn twice (class 2: once more with the extra effect) */
    private static void drawSprite(hl4.gfx.Sprite s, int x, int y, int state, int frame) {
        Char h = Pool.hero;
        s.f6e = 1;
        s.f6f = state;
        hl4.gfx.GfxOut o = hl4.gfx.GfxOut.I;
        s.drawSprite(o, Gfx.scr, 0, 0, (byte) frame, x, y, 0, -1);
        if (h.m[0x16b] == 2) {
            later("SPR_drawSpriteSK (particles of class 2)");
        }
        s.drawSprite(o, Gfx.scr, 0, 0, (byte) frame, x, y, 0, -1);
    }

    /** paintParticles(ox, oy) @0xbaf5c: every particle whose delay is over */
    static void paint(int ox, int oy) {
        byte[] pb = particles;
        for (int i = 0; i < (short) curCount; i++) {
            int q = i * 0x28;
            if (Bytes.s32(pb, q + 0x20) > 0) {
                continue;
            }
            int set = pb[q + 2] & 0xff;
            hl4.gfx.Sprite sp = setSpr[set * 3 + (pb[q + 1] & 0xff)];
            drawSprite(sp, ox + Bytes.s16(pb, q + 0xc), oy + Bytes.s16(pb, q + 0x12), pb[q + 0x26] & 0xff, Bytes.s16(pb, q + 0x24));
        }
    }
}
