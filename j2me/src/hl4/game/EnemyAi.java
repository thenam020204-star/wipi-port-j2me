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
        int off = init(o, ai, a, 1, 1);
        off = init(o, ai, a, off, 0);
        Bytes.w32(m, 0x264, a.strings.length);
        o.ai = ai;
        testInit(m);
    }

    /**
     * EnemyAIParsing_Init(o, script, buf, off, section) @0xd18f0: reads one section of the AI file that starts at 'off' (section 1: the trigger scripts
     * -> count +0x220, tables +0x224 / +0x22c and the position table of the script struct; section 0: the action scripts -> count +0x234, tables +0x238 /
     * +0x240, the 12-byte action state +0x288 and the positions) and its script bytes (kept as the token 'trigTok' / 'actTok'); returns the offset after
     * the section.  The file is parsed once into the AiData ('a', see hl4.data.AiData, which also derives the trigger positions: 1, then each
     * previous position + 2 + size), so only the counts, the tokens and the action state are set here.
     */
    static int init(Char o, EnemyAi ai, AiData a, int off, int section) {
        byte[] m = o.m;
        int n;
        int len;
        int end;
        if (section != 0) {
            n = a.trigCnt.length;
            len = a.trigScript.length;
            m[0x220] = (byte) n;
            ai.trigTok = new It.Tok(len > 0 ? a.trigScript : null, 0, len);
            end = off + 3 + 2 * n;
        } else {
            n = a.actCnt.length;
            len = a.actScript.length;
            m[0x234] = (byte) n;
            ai.actState = new byte[n * 0xc];
            ai.actTok = new It.Tok(len > 0 ? a.actScript : null, 0, len);
            end = off + 3 + 3 * n;
        }
        return len > 0 ? end + len : end;
    }

    /**
     * EnemyAIParsing_scriptRelease(script) @0xd1758: frees the three blocks of a script struct (token +0x10, script bytes +0, positions +8); nothing
     * when the struct is not there.  Here the token goes (the script bytes belong to the shared AiData).
     */
    static void scriptRelease(Char o, int act) {
        EnemyAi ai = o.ai;
        if (ai == null) {
            return;
        }
        if (act != 0) {
            ai.actTok = null;
        } else {
            ai.trigTok = null;
        }
    }

    /**
     * EnemyAIParsing_Finalize(o) @0xd1794: releases both script structs and every table of the monster (counts / sizes +0x224.., action state +0x288,
     * the strings and their tables +0x24c / +0x258 / +0x260); the pointer slots go back to 0.  Here that is dropping the EnemyAi object.
     */
    static void release(Char o) {
        scriptRelease(o, 0);
        scriptRelease(o, 1);
        o.ai = null;
    }

    /** +0x2b0: the string picked by action 10 (IT_GetString_2), null = none */
    public byte[] speech;

    private static boolean isBoss(Char o) {
        return Bytes.s16(o.m, 0x160) > 0xc7;
    }

    /**
     * EnemyAIParsing_Process @0xd29c4: picks a trigger (once the +0x28e wait has run out), runs the action list, then restarts the wait (9 ticks);
     * while the hero's +0xd16 / +0xd17 flags are set the wait is cleared.
     */
    public static void process(Char o) {
        byte[] m = o.m;
        triggerCheck(o, m[0x2ad] & 0xff, isBoss(o));
        actionCheck(o);
        byte[] h = Pool.hero.m;
        if (h[0xd17] != 0 || h[0xd16] != 0) {
            Bytes.w16(m, 0x28e, 1);
            Bytes.w16(m, 0x28e, 0);
            return;
        }
        int v = Bytes.s16(m, 0x28e);
        if (v > 0) {
            Bytes.w16(m, 0x28e, v - 1);
        } else {
            Bytes.w16(m, 0x28e, 9);
        }
    }

    /** the running read position of EnemyAIParsing_triggerCheck (a stack variable of the original that is NOT reset between triggers) */
    private static final int[] pos = new int[1];

    /** EnemyAIParsing_triggerCheck(o, force, boss) @0xd26cc: the first trigger whose HP threshold and conditions hold selects the action list */
    static boolean triggerCheck(Char o, int force, boolean boss) {
        byte[] m = o.m;
        m[0x2ad] = 0;
        if (force == 0 && Bytes.s16(m, 0x28e) > 0) {
            return false;
        }
        if (m[0x323] == 1) {
            return false;
        }
        Bytes.fill(m, 0x29f, 0xff, 9);
        EnemyAi ai = o.ai;
        if (ai == null) {
            return false;
        }
        AiData a = ai.d;
        if (!boss) {
            m[0x26c] = m[0x26a];
        }
        byte[] data = ai.trigTok.d;
        m[0x26f] = 0;
        int n = m[0x220] & 0xff;
        int hpPct = 0;
        pos[0] = 0;
        for (int i = 0; i < n; i++) {
            int max = Bytes.s32(m, 0xa8);
            if (max > 0) {
                hpPct = Bytes.s32(m, 0xa4) * 100 / max;
            }
            int p = a.trigPos[i] & 0xff;
            if (hpPct > (data[p] & 0xff)) {
                continue;
            }
            pos[0] += p;
            if (!actionOfTrigger(o, data, a.trigCnt[i] & 0xff, pos)) {
                continue;
            }
            if (boss) {
                Bytes.w32(m, 0x270, pos[0]);
                if (pos[0] == Bytes.s32(m, 0x274)) {
                    return false;
                }
                Bytes.w32(m, 0x274, pos[0]);
                m[0x26a] = m[0x269];
            }
            if (m[0x26a] == m[0x26c]) {
                return false;
            }
            if (boss) {
                m[0x28c] = 0;
                m[0x26c] = m[0x26a];
            }
            setActionList(o);
            m[0x29c] = 0;
            return true;
        }
        return false;
    }

    /** EnemyAIParsing_actionOftrigger(o, data, nConds, &pos) @0xd20a8: condition bytes, each followed by a result (0xc8 = and, 5 = default) */
    static boolean actionOfTrigger(Char o, byte[] data, int count, int[] pos) {
        byte[] m = o.m;
        boolean boss = isBoss(o);
        pos[0]++;
        if (count <= 0) {
            return false;
        }
        int b = data[pos[0]++] & 0xff;
        int k = 0;
        while (true) {
            if (b == 5) {
                m[0x269] = data[pos[0]];
                if (!boss) {
                    m[0x26a] = data[pos[0]];
                }
                pos[0]++;
                return true;
            }
            if (isTriggerEqual(o, b, pos, data)) {
                m[0x268] = 0;
                m[0x269] = data[pos[0]];
                if (!boss) {
                    m[0x26a] = data[pos[0]];
                }
                pos[0]++;
                if ((m[0x269] & 0xff) != 0xc8) {
                    return true;
                }
                k++;
                if (k == count) {
                    return false;
                }
            } else {
                int r = data[pos[0]++] & 0xff;
                k++;
                m[0x268] = (byte) (r == 0xc8 ? 1 : 0);
                if (k == count) {
                    return false;
                }
            }
            b = data[pos[0]++] & 0xff;
        }
    }

    /** EnemyAIParsing_isTriggerEqual(o, cond, &pos, data) @0xd1e2c (after a failed 'and' term (+0x268 == 1) only the operand of 4/6/16 is skipped) */
    static boolean isTriggerEqual(Char o, int b, int[] pos, byte[] data) {
        byte[] m = o.m;
        if (m[0x268] == 1) {
            if (b == 6 || b == 4 || b == 0x10) {
                pos[0]++;
            }
            return false;
        }
        switch (b) {
            case 1:
                return flag2(m, 0x2ac);
            case 2: {
                int n = G.monsterList.count;
                if (n == 1) {
                    return true;
                }
                if (!MonProc.bossIsLast(o)) {
                    return false;
                }
                return n <= 7;
            }
            case 3:
                return flag2(m, 0x2aa);
            case 4:
            case 6:
                pos[0]++;
                return true;
            case 7: {
                if (!Chars.isStunBuff(Pool.hero)) {
                    return false;
                }
                byte[] h = Pool.hero.m;
                if (h[0xd17] != 0) {
                    h[0xd17] = 0;
                    return true;
                }
                return false;
            }
            case 9: {
                byte[] h = Pool.hero.m;
                if (h[0xd16] != 0) {
                    h[0xd16] = 0;
                    return true;
                }
                return false;
            }
            case 10:
            case 15:
                return true;
            case 11:
                return flag2(m, 0x2a9);
            case 12:
                return flag2(m, 0x2a8);
            case 13:
                if (m[0x3f0] != 0) {
                    m[0x3f0] = 0;
                    return true;
                }
                return false;
            case 14:
                return m[0x3ef] != 0;
            case 16: {
                int v = data[pos[0]++] & 0xff;
                if (isBoss(o)) {
                    throw new IllegalStateException("Boss_distanceWithHero: later");
                }
                return v >= (byte) MonProc.distanceWithHero(o);
            }
            default:
                return false;
        }
    }

    private static boolean flag2(byte[] m, int off) {
        if (m[off] == 2) {
            m[off] = 0;
            return true;
        }
        return false;
    }

    /** EnemyAIParsing_setAclionList @0xd2644: the action token covers the list of action +0x26a; +0x26b = its follow-up action */
    static boolean setActionList(Char o) {
        byte[] m = o.m;
        int a = m[0x26a];
        if (a == -1) {
            return false;
        }
        EnemyAi ai = o.ai;
        AiData d = ai.d;
        int p = d.actPos[a] & 0xff;
        m[0x26b] = ai.actTok.d[p + 1];
        int st = p + 2;
        ai.actTok.pos = st;
        ai.actTok.end = st + (d.actSize[a] & 0xff);
        m[0x28d] = 0;
        m[0x28c] = 1;
        return true;
    }

    /** EnemyAIParsing_ActionCheck @0xd293c: past the last action of the list the follow-up list (+0x26b, unless bit 7) is loaded */
    static void actionCheck(Char o) {
        byte[] m = o.m;
        if (Bytes.s16(m, 0x28e) > 0) {
            return;
        }
        int a = m[0x26a];
        int done = m[0x29c];
        if (done < (o.ai.d.actCnt[a] & 0xff)) {
            m[0x28c] = 0;
            doActionList(o);
            return;
        }
        int v = m[0x26b] & 0xff;
        if ((v & 0x80) == 0) {
            m[0x26a] = (byte) v;
        }
        setActionList(o);
        m[0x29c] = 0;
        doActionList(o);
    }

    /** EnemyAIParsing_doAclionList @0xd2860: runs the next action of the list (restarting the list when the token is at its end) */
    static boolean doActionList(Char o) {
        byte[] m = o.m;
        int a = m[0x26a];
        if (a == -1) {
            return false;
        }
        EnemyAi ai = o.ai;
        It.Tok t = ai.actTok;
        if (t.isEnd()) {
            int st = (ai.d.actPos[a] & 0xff) + 2;
            t.pos = st;
            t.end = st + (ai.d.actSize[a] & 0xff);
        }
        m[0x26d] = t.d[t.pos];
        t.getByte();
        if (!onAction(o, m[0x26d], t)) {
            m[0x28c] = 1;
            m[0x29c]++;
        }
        return true;
    }

    /** IT_GetByte_2(token, o+0x278, n) */
    private static void get278(Char o, It.Tok t, int n) {
        if (t.d != null) {
            System.arraycopy(t.d, t.pos, o.m, 0x278, n);
            t.pos += n;
        }
    }

    private static void params(Char o) {
        System.arraycopy(o.m, 0x278, o.m, 0x29f, 9);
    }

    /** EnemyAIParsing_onAction(o, action, token) @0xd227c: reads the operands of the action into +0x278 / +0x29f.. (always returns false) */
    static boolean onAction(Char o, int a, It.Tok t) {
        byte[] m = o.m;
        if (Monsters.isBoss(o) && Monsters.isDie(o)) {
            return false;
        }
        m[0x29e] = (byte) a;
        switch (a) {
            case 0:
                get278(o, t, 5);
                break;
            case 1:
                get278(o, t, 4);
                params(o);
                Bytes.w16(m, 0x28e, m[0x2a2]);
                break;
            case 2:
                get278(o, t, 1);
                params(o);
                break;
            case 3:
                get278(o, t, 1);
                params(o);
                Bytes.w16(m, 0x28e, m[0x278]);
                if (Monsters.isBoss(o) && (byte) MonProc.mainStateByCategory(o) != 5) {
                    MonProc.walkCount(o, 0);
                    if (m[0x167] != 0) {
                        Chars.setMainState(o, 0);
                        Monsters.setStandMotion(o);
                        MonProc.standMotionAtOnce(o);
                    }
                }
                break;
            case 4:
                get278(o, t, 3);
                params(o);
                m[0x3ea] = m[0x29f];
                m[0x40e] = m[0x29f];
                if (Monsters.isBoss(o)) {
                    throw new IllegalStateException("Boss_isUnitMotion: later");
                }
                m[0x3eb] = 0;
                break;
            case 5:
            case 7:
            case 9:
                get278(o, t, 2);
                params(o);
                break;
            case 6:
                get278(o, t, 2);
                break;
            case 8:
                get278(o, t, 3);
                params(o);
                throw new IllegalStateException("MONSTER_setSummon: later");
            case 10: {
                int n = t.getByte() & 0xff;
                get278(o, t, n);
                m[0x29e] = 10;
                params(o);
                int r = hl4.sys.Rng.rand(m[0x278], m[0x277 + n]) & 0xff;
                EnemyAi ai = o.ai;
                byte[][] str = ai.d.strings;
                ai.speech = str != null && r < str.length ? str[r] : null;
                break;
            }
            case 11:
                get278(o, t, 4);
                params(o);
                m[0x320] = m[0x29f];
                m[0x321] = m[0x2a0];
                m[0x323] = m[0x2a2];
                m[0x322] = m[0x2a1];
                break;
            case 12:
                get278(o, t, 1);
                throw new IllegalStateException("Boss_AI_FadeSet: later");
            case 13:
                get278(o, t, 3);
                params(o);
                break;
            default:
                break;
        }
        return false;
    }
}
