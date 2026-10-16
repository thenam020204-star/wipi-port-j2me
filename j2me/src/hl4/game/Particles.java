package hl4.game;

import hl4.sys.Bytes;
import hl4.sys.Res;

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
}
