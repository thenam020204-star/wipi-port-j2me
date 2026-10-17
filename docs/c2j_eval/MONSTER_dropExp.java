    /** MONSTER_dropExp @0xa50fc  (c2j draft: 6 C2J-CHECK) */
    static void dropExp(Char o) {
        int cVar1, iVar4;
        Obj iVar5, uVar2, uVar3, iVar4_o;
        uVar2 = Pool.hero;
        uVar3 = Pool.hero;
        iVar4 = Fr.calc(0x3ed, uVar2, o, 0xffffffff, 0, uVar3, o);
        addCurExp(Pool.hero, iVar4);                    // C2J fixed: CHAR_heroAddExp(exp) = HERO_add_curExp(hero, exp)
        iVar5 = Pool.hero;
        if (Bytes.s8(iVar5.m, 0x189) == 1) {
            if (true) throw new IllegalStateException("MONSTER_dropExp: SUMMON_addExp: later"); // C2J-CHECK callee not ported
        }
        iVar4_o = Pool.hero;
        if (Bytes.s8(iVar4_o.m, 0x16b) == 3) {
            iVar4_o = Pool.hero;
            if (Bytes.s8(iVar4_o.m, 0x1ed) != 0) {
                if (true) throw new IllegalStateException("MONSTER_dropExp: summon heals: later"); // C2J fixed: summon slot store not ported
            }
        }
        return;
    }

