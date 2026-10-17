    /** MONSTER_distanceWithHero @0xa1784  (c2j draft: 13 C2J-CHECK) */
    static int distanceWithHero(Char o) {
        int bVar1, cVar2, iVar10, iVar11, iVar12, iVar13, iVar8, iVar9, uVar4, uVar5, uVar6, uVar7;
        Obj iVar14, uVar3;
        uVar3 = Pool.hero;
        uVar4 = Chars.getX(o);
        uVar5 = Chars.getY(o);
        uVar6 = Chars.getX(uVar3);
        uVar7 = Chars.getY(uVar3);
        bVar1 = (Um.turnDirection(uVar4, uVar5, uVar6, uVar7)) & 0xff;
        iVar11 = (byte) (bVar1);
        cVar2 = (byte) Um.turnOpposite(iVar11);         // C2J fixed: UM_turnOppositeDirection(r0 = (s8) direction) = Um.turnOpposite
        iVar8 = Chars.moveRectHalfW(uVar3, cVar2);
        iVar9 = Chars.moveRectHalfW(o, iVar11);
        iVar10 = Chars.moveRectHalfH(uVar3, cVar2);
        iVar11 = Chars.moveRectHalfH(o, iVar11);
        iVar12 = Chars.getAbsX(o, uVar3);
        iVar13 = Chars.getAbsY(o, uVar3);
        iVar14 = Pool.hero;
        if (Bytes.s8(iVar14.m, 0xa44) < 1) {
            // C2J fixed: CHAR_isHideBuff(CHAR_getHeroChar()) (0xa186c), then MP_getHero for the two flags
            if (!Chars.isHideBuff(Pool.hero) && (Bytes.s8(iVar14.m, 0xd16) == 0) && (Bytes.s8(iVar14.m, 0x20f) == 0)) {
                if (((bVar1 - 2)) + 0x80000000 < (2) + 0x80000000) {
                    iVar11 = (iVar9 + iVar8);
                } else {
                    iVar11 = (iVar11 + iVar10);
                }
                iVar11 = ((iVar11 * 0x1000000) >> 0x18);
                if (((bVar1 - 2)) + 0x80000000 < (2) + 0x80000000) {
                    iVar13 = (iVar12 - iVar11);
                } else {
                    iVar13 = (iVar13 - iVar11);
                }
                iVar11 = (iVar13 + 0xf);
                if (-(1) < iVar13) {
                    iVar11 = iVar13;
                }
                return ((iVar11 << 0x14) >> 0x18);
            }
        }
        return 0x14;
    }

