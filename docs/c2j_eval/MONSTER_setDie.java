    /** MONSTER_setDie @0xa3428  (c2j draft: 6 C2J-CHECK) */
    static boolean setDie(Char o, int v) {
        int cVar1, iVar2;
        iVar2 = (Monsters.isDie(o) ? 1 : 0);
        if (iVar2 == 0) {
            if (Bytes.s8(o.m, 0x30a) == 0) {
                if (Bytes.s8(o.m, 0x324) != 0) {
                    Bytes.w8(o.m, 0x324, 0);
                }
                iVar2 = Monsters.getCurHp(o);
                if (0 < iVar2) {
                    return false;
                }
                iVar2 = (Monsters.isBoss(o) ? 1 : 0);
                if (iVar2 == 0) {
                    cVar1 = (byte) deathMotion(o);
                    MonProc.setNextMotionDirection(o, cVar1, 0, 0);
                } else {
                    if (true) throw new IllegalStateException("MONSTER_setDie: Boss_setDie: later"); // C2J-CHECK callee not ported
                    cVar1 = (byte) (0 /* Boss_setDie: later */);
                    MonProc.setNextMotionDirection(o, cVar1, 0, 0);
                }
                MonProc.changeMotionAtOnce(o);
                Monsters.attackCancel(o);
                Chars.initBuff(o);
                Bytes.w16(o.m, 0x28e, 0);
                Bytes.w8(o.m, 0x3eb, 0);
                Bytes.w8(o.m, 0x29e, 0xff);
                Bytes.w8(o.m, 0x166, 4);
                Bytes.w8(o.m, 0x40f, v);
                if (Bytes.s8(o.m, 0x35e) != 0) {
                    if (true) throw new IllegalStateException("MONSTER_setDie: summoned monster (_summonList): later"); // C2J fixed: _summonList not ported
                }
                MonProc.ai(o);                              // C2J fixed: MONSTER_AI = MonProc.ai (javadoc without the original name)
                Bytes.w8(o.m, 0x325, 0xc);
                return true;
            }
        }
        return true;
    }

