    /** MONSTER_isNearWithHero @0xa601c  (c2j draft: 1 C2J-CHECK) */
    static boolean isNearWithHero(Char o) {
        int iVar4, iVar5, local_1c, local_20, local_24, local_28, local_2c, local_30, local_34, local_38, local_48, uStack_3c, uStack_40, uStack_44, uVar1, uVar2;
        Obj uVar3;
        local_38 = Bytes.s32(o.m, 0x148);
        local_34 = Bytes.s32(o.m, 0x14c);
        local_30 = Bytes.s32(o.m, 0x150);
        local_2c = Bytes.s32(o.m, 0x154);
        local_28 = local_38;
        local_24 = local_34;
        local_20 = local_30;
        local_1c = local_2c;
        // C2J fixed: CHAR_getHeroHittedRect(&local_48) fills the hero's hit rect; UM_Collision_Two_Rect's out rect = the class scratch rc
        local_48 = Chars.getHittedRectX(Pool.hero);
        uStack_44 = Chars.getHittedRectY(Pool.hero);
        uStack_40 = Chars.getHittedRectW(Pool.hero);
        uStack_3c = Chars.getHittedRectH(Pool.hero);
        uVar1 = Um.collision(local_28, local_24, local_20, local_1c, local_48, uStack_44, uStack_40, uStack_3c, rc);
        uVar2 = Um.collision(local_38, local_34, local_30, local_2c, local_48, uStack_44, uStack_40, uStack_3c, rc);
        iVar5 = -(((uVar2 | uVar1) >> 0x1f));
        if (iVar5 != 0) {
            uVar3 = Pool.hero;
            iVar4 = (isDiagnal(o, uVar3) ? 1 : 0);
            if (iVar4 != 0) {
                iVar5 = 0;
            }
        }
        return iVar5 != 0;
    }

