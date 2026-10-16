package hl4.game;

import hl4.sys.Bytes;
import hl4.sys.Res;

/** The summoned creature of the summoner class (the pooled struct MP_getSummon, 0x63c bytes): SUMMON_* @0xd21c4..0xd5834 (init part). */
public final class Summon {
    private Summon() {
    }

    /** SUMMON_set(s) @0xd52d0 = CHAR_set + SUMMON_init + the body of SUMMON_initSkillPoint */
    public static void set(Char s) {
        Chars.set(s);
        init();
        initSkillPoint();
    }

    /** SUMMON_initSkillPoint @0xd21c4 (also run inside SUMMON_set @0xd52d0) */
    public static void initSkillPoint() {
        Char s = Pool.summon;
        byte[] m = s.m;
        m[0x2e6] = 1;
        for (int k = 1; k < 30; k++) {
            m[0x2e6 + 0x1c * k] = 1;
        }
        m[0x278] = 1;
        m[0x274] = 1;
        m[0x2a0] = 1;
        m[0x29c] = 1;
        for (int k = 1; k < 5; k++) {
            int b = 8 * k;
            m[0x278 + b] = 1;
            m[0x274 + b] = 1;
            m[0x2a0 + b] = 1;
            m[0x29c + b] = 1;
        }
        Bytes.w32(m, 0x1cc, 1);
        Bytes.w32(m, 0xa8, 1);
        Bytes.w32(m, 0xa4, 1);
        Bytes.w32(m, 0x1d0, 1);
    }

    /** SUMMON_init @0xd521c */
    public static void init() {
        Char s = Pool.summon;
        byte[] m = s.m;
        Chars.init(s, 0, 0, 0, 0, 4, 1, 3);
        m[0x62e] = 1;
        m[0x263] = 0;
        m[0x214] = 0;
        m[0x215] = 0;
        m[0x216] = 0;
        m[0x217] = 0;
        Bytes.w32(m, 0x2c4, 0);
        m[0x254] = 0;
        m[0x259] = 0;
        m[0x25e] = 0;
        for (int k = 0; k < 4; k++) {
            m[0x255 + k] = 0;
            m[0x25a + k] = 0;
            m[0x25f + k] = 0;
            m[0x264 + k] = 0;
        }
        loadSummonDataInitD(s);
        m[0x639] = 0;
    }

    /** SUMMON_loadSummonData_initD @0xd5174: the 5 level records of HDAT/_H_BS (0x1b bytes each) */
    public static void loadSummonDataInitD(Char s) {
        byte[] d = Res.loadDes("/HDAT/_H_BS");
        for (int k = 0; k < 5; k++) {
            int b = k * 0x1b;
            s.m[0x254 + k] = d[b + 3];
            s.m[0x259 + k] = d[b + 4];
            s.m[0x25e + k] = d[b + 5];
            s.m[0x263 + k] = d[b + 6];
        }
    }
}
