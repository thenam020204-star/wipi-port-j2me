package hl4.game;

import hl4.gfx.Img;
import hl4.gfx.Sprite;
import hl4.gfx.SprProps;
import hl4.sys.Bytes;
import hl4.sys.Res;

/** Hero data and image loading (Hero_loadSpr/Data/reLoad, HERO_loadSkillInfo/ArmsSkillInfo/ParticleInfo, loadSprHeroImages, MP_getFolderImg). */
public final class HeroLoad {
    private HeroLoad() {
    }

    private static String n3(int v) {
        return v < 10 ? "00" + v : (v < 100 ? "0" + v : String.valueOf(v));
    }

    /** Hero_loadSpr @0x8e5bc: the CIF and the attack-property file of the class */
    public static void loadSpr() {
        Char h = Pool.hero;
        int cls = h.m[0x16b];
        h.m[0x16b] = (byte) cls;                                 // HERO_set_class
        h.spr = new Sprite(Res.load("/H4/CIF/_H_" + n3(cls + 1) + "_CIF"), 2);
        h.spr.props = SprProps.parse(Res.load("/H4/EXD/_H_" + n3(cls + 1) + "_EXD"));
    }

    /** Hero_loadData @0x8f364 */
    public static void loadData() {
        Char h = Pool.hero;
        Hero.loadBasicStat(h);
        loadSkillInfo(h);
        if ((h.m[0x16b] & 0xff) < 2) {
            loadArmsSkillInfo(h);
        }
        Particles.loadParticleInfo();
        Particles.loadParticleData();
    }

    /** HERO_loadSkillInfo @0x8ee7c: 16 skill records of HDAT/_H_S<class> into hero+0x294 (0x28 bytes each) */
    public static void loadSkillInfo(Char h) {
        byte[] m = h.m;
        byte[] d = Res.loadDes("/HDAT/_H_S" + n3(m[0x16b]));
        int p = 0;
        for (int k = 0; k < 16; k++) {
            int q = p + 3 + (d[p + 2] & 0xff);
            int o = 0x294 + k * 0x28;
            m[o] = d[q];
            m[o + 1] = d[q + 1];
            m[o + 2] = d[q + 2];
            Bytes.w16(m, o + 4, Bytes.s16(d, q + 3));
            m[o + 6] = d[q + 5];
            int v = Bytes.s16(d, q + 6);
            Bytes.w16(m, o + 8, v);
            Bytes.w16(m, o + 10, v);
            m[o + 12] = d[q + 8];
            m[o + 13] = d[q + 9];
            m[o + 14] = d[q + 10];
            m[o + 15] = d[q + 11];
            m[o + 16] = d[q + 12];
            m[o + 17] = d[q + 13];
            m[o + 18] = d[q + 14];
            m[o + 19] = d[q + 15];
            for (int j = 0; j < 4; j++) {
                m[o + 0x14 + j] = d[q + 0x10 + 4 * j];
                Bytes.w16(m, o + 0x18 + 2 * j, Bytes.s16(d, q + 0x11 + 4 * j));
                m[o + 0x20 + j] = d[q + 0x13 + 4 * j];
            }
            p = q + 0x21 + (d[q + 0x20] & 0xff);
        }
    }

    /** HERO_loadArmsSkillInfo @0x8ed1c: 40 records of HDAT/_H_SA into hero+0x614 (0x1a bytes each) */
    public static void loadArmsSkillInfo(Char h) {
        byte[] m = h.m;
        byte[] d = Res.loadDes("/HDAT/_H_SA");
        int q = 2;
        for (int k = 0; k < 40; k++) {
            int o = 0x614 + k * 0x1a;
            Bytes.w16(m, o, Bytes.s16(d, q));
            m[o + 2] = d[q + 2];
            int v = Bytes.s16(d, q + 3);
            Bytes.w16(m, o + 6, v);
            Bytes.w16(m, o + 4, v);
            m[o + 8] = d[q + 5];
            for (int j = 0; j < 4; j++) {
                m[o + 9 + j] = d[q + 6 + 4 * j];
                Bytes.w16(m, o + 0xe + 2 * j, Bytes.s16(d, q + 7 + 4 * j));
                m[o + 0x16 + j] = d[q + 9 + 4 * j];
            }
            q += 0x18;
        }
    }

    // ---- images --------------------------------------------------------------------------------------------------------------------------

    private static boolean helmet() {
        return Opt.v[6] == 1;
    }

    /** MP_getFolderImg(part) @0xccc80: the image folder number of a body part (equipment skin, class default, or the event costume) */
    public static int folderImg(int p) {
        Char h = Pool.hero;
        int k;
        switch (p) {
            case 0:
            case 5:
            case 7:
                k = -1;
                break;
            case 1:
            case 2:
            case 3:
            case 4:
                k = p;
                break;
            default:
                k = 0;
                break;
        }
        boolean a;
        Obj it = null;
        if (k < 0) {
            a = true;
        } else if (G.equip[k] == null) {
            a = true;
        } else {
            it = G.equip[k];
            a = false;
            if (k == 0) {
                it = G.equip[0];
                if (!helmet()) {
                    a = true;
                }
            }
        }
        if (a) {
            if (((p - 4) & 0xff) < 2) {
                return Tab.FOLDER_IMG[h.m[0x16b] + p * 4];
            }
        } else {
            int u = Items.skinDataLoad(it) & 0xffff;
            if (u != 0) {
                return (short) u < 0 ? -1 : (short) u;
            }
        }
        if (h.m[0xd0e] != 0) {
            return Tab.FOLDER_IMG2[p];
        }
        return Tab.FOLDER_IMG[h.m[0x16b] + p * 4];
    }

    /** loadSprHeroImages(spr, part, class) @0xccdd4 */
    public static void loadSprHeroImages(Sprite spr, int part, int cls) {
        int folder = folderImg(part);
        int skin;
        if (part >= 1 && part <= 4 && G.equip[part] != null) {
            skin = G.equip[part].m[0x157] & 0xff;
            if (skin == 0xff) {
                skin = 0xffff;
            }
        } else if (part == 6 && G.equip[0] != null) {
            skin = G.equip[0].m[0x157] & 0xff;
            if (skin == 0xff) {
                skin = 0xffff;
            }
        } else {
            skin = 0xffff;
        }
        if (folder != -1) {
            spr.setImages(part, Img.loadSprSet("/H4/" + n3(part) + "/_HIMG_" + n3(part) + "_" + n3(folder), (short) skin, Maps.timeReturn(G.map[0x68])));
        }
    }

    /** Hero_reLoad @0x97a14: (re)loads the body-part images for the current equipment and time of day */
    public static void reLoad() {
        Char h = Pool.hero;
        if (h.m[0x16b] == 2) {
            Sprite.extraEffect = null;
        }
        h.spr.freeImages();
        int slot = Maps.timeReturn(G.map[0x68]);
        h.spr.freeImages();
        int cls = h.m[0x16b];
        loadSprHeroImages(h.spr, 0, cls);
        for (int i = 1; i < 8; i++) {
            loadSprHeroImages(h.spr, i, cls);
        }
        if (cls == 2) {
            Sprite.extraEffect = Img.loadAll("/H4/007/_HIMG_007_" + n3(0), slot);
        }
        h.spr.f68 = 0;                                          // SPR_set_restore_pal
    }
}
