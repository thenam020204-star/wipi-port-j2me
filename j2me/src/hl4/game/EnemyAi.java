package hl4.game;

import hl4.data.AiData;
import hl4.sys.Bytes;

/**
 * Monster AI scripts (EnemyAIParsing_* @0xd1714..0xd2a84).  The original keeps heap pointers in the monster struct
 * (+0x214/+0x21c trigger/action script structs {data, len, positions, -, token}, +0x224/+0x22c trigger counts/sizes,
 * +0x238/+0x240 action counts/sizes, +0x288 action state (12 bytes per action), +0x24c/+0x258/+0x260 strings); here they
 * are the fields of an EnemyAi object referenced from Char.ai.  The scalar fields (+0x20c kind, +0x220/+0x234 counts,
 * +0x264 string count, +0x268..+0x2ad state) stay in the struct bytes.
 */
public final class EnemyAi {
    /** parsed /E/AI### file (read-only: shared between the monsters that load the same id) */
    public AiData d;
    /** +0x214 -> +0x10 / +0x21c -> +0x10: the tokens over the trigger / action scripts */
    public It.Tok trigTok;
    public It.Tok actTok;
    /** +0x288: 12 bytes per action */
    public byte[] actState;

    private static final AiData[] cache = new AiData[256];

    private EnemyAi() {
    }

    private static String n3(int v) {
        String t = String.valueOf(v);
        return v < 10 ? "00" + t : (v < 100 ? "0" + t : t);
    }

    /** MONSTER_dataLoad_ai(o, id) @0x9fab4 = EnemyAIParsing_TestInit + EnemyAIParsing_LoadData */
    public static void dataLoad(Char o, int id) {
        testInit(o.m);
        loadData(o, id & 0xff);
    }

    /** EnemyAIParsing_TestInit @0xd1854 (also EnemyAIParsing_Initialize) */
    static void testInit(byte[] m) {
        Bytes.w32(m, 0x290, 0x78);
        Bytes.w32(m, 0x294, 0x78);
        Bytes.w32(m, 0x298, 0xf);
        Bytes.w16(m, 0x28e, 0);
        m[0x29e] = (byte) 0xff;
        Bytes.w32(m, 0x270, -1);
        m[0x26d] = (byte) 0xff;
        m[0x26b] = (byte) 0xff;
        m[0x26c] = (byte) 0xff;
        m[0x26a] = (byte) 0xff;
        Bytes.w32(m, 0x274, 0);
        m[0x28c] = 0;
        m[0x29c] = 0;
        m[0x29d] = 0;
        for (int i = 0; i < 9; i++) {
            m[0x29f + i] = 0;
        }
        m[0x268] = 0;
        m[0x2ad] = 1;
        m[0x2a8] = 0;
        m[0x2a9] = 0;
        m[0x2ac] = 0;
        m[0x2aa] = 0;
    }

    /** EnemyAIParsing_LoadData(o, id) @0xd1c60 (+ EnemyAIParsing_Init for the two sections) */
    static void loadData(Char o, int id) {
        AiData a = cache[id];
        if (a == null) {
            a = AiData.parse(hl4.sys.Res.load("/E/AI" + n3(id)));
            cache[id] = a;
        }
        byte[] m = o.m;
        EnemyAi ai = new EnemyAi();
        ai.d = a;
        m[0x20c] = (byte) a.kind;
        m[0x220] = (byte) a.trigCnt.length;
        ai.trigTok = new It.Tok(a.trigScript.length > 0 ? a.trigScript : null, 0, a.trigScript.length);
        m[0x234] = (byte) a.actCnt.length;
        ai.actState = new byte[a.actCnt.length * 0xc];
        ai.actTok = new It.Tok(a.actScript.length > 0 ? a.actScript : null, 0, a.actScript.length);
        Bytes.w32(m, 0x264, a.strings.length);
        o.ai = ai;
        testInit(m);
    }
}
