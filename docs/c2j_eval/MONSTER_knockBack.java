    /** MONSTER_knockBack @0xa2c30  (c2j draft: 14 C2J-CHECK) */
    static void knockBack(Char o, int power, int dirIn) {
        int cVar2, cVar9, iVar1, iVar7, iVar8, uVar3, uVar4, uVar5, uVar6;
        Chars.getDirection(o);
        uVar3 = Chars.getX(o);
        uVar4 = Chars.getY(o);
        uVar5 = Bytes.s32(Pool.hero.m, 8) /* CHAR_getHeroX */;
        uVar6 = Bytes.s32(Pool.hero.m, 0xc) /* CHAR_getHeroY */;
        cVar2 = (byte) (Um.turnDirection(uVar3, uVar4, uVar5, uVar6));
        cVar2 = (byte) Um.turnOpposite(cVar2);          // C2J fixed: UM_turnOppositeDirection = Um.turnOpposite
        Bytes.w8(o.m, 0x2ac, 2);
        iVar7 = (Monsters.isDie(o) ? 1 : 0);
        if (cVar2 == -(1)) {
            cVar2 = (byte) 0;
        }
        if ((iVar7 != 0) || ((iVar7 = Monsters.getCurHp(o)) < 1) || ((iVar7 = (Monsters.bodyRemain(o) ? 1 : 0)) != 0)) {
            return;
        }
        iVar7 = (Monsters.isObject(o) ? 1 : 0);
        if (iVar7 == 0) {
            Bytes.w32(o.m, 0x368, 0);
            Bytes.w32(o.m, 0x364, 0);
            if ((power != 0) && (power != 1)) {
                cVar9 = (byte) (byte) (power);
                if (4 < power) {
                    cVar9 = (byte) (cVar9 + -(4));
                }
                iVar7 = cVar2;
                cVar2 = (byte) Um.vecX(iVar7);
                iVar1 = cVar9;
                Bytes.w32(o.m, 0x364, (cVar2 * iVar1));
                cVar2 = (byte) Um.vecY(iVar7);
                Bytes.w32(o.m, 0x368, (iVar1 * cVar2));
                setAttackedMotion(o, 1);
                kx[0] = Bytes.s32(o.m, 0x364);              // C2J fixed: &o->0x364 / &o->0x368 are in/out parameters
                ky[0] = Bytes.s32(o.m, 0x368);
                iVar8 = (Coll.charCanMove(o, kx, ky, true, true) ? 1 : 0);
                Bytes.w32(o.m, 0x364, kx[0]);
                Bytes.w32(o.m, 0x368, ky[0]);
                if (iVar8 != 0) {
                    Chars.setPosition(o, Bytes.s32(o.m, 0x364), Bytes.s32(o.m, 0x368));
                }
                cVar2 = (byte) Um.vecX(iVar7);
                Bytes.w32(o.m, 0x364, (iVar1 * cVar2));
                cVar2 = (byte) Um.vecY(iVar7);
                Bytes.w8(o.m, 0x175, 0);
                Bytes.w32(o.m, 0x368, (iVar1 * cVar2));
                return;
            }
            if (Bytes.s8(o.m, 0x324) != 0) {
                Bytes.w8(o.m, 0x324, 0);
            }
            iVar7 = (Monsters.isObject(o) ? 1 : 0);
            if (iVar7 == 0) {
                cVar2 = (byte) hitMotion(o);
                cVar9 = (byte) MonProc.lookHero(o);
                MonProc.setNextMotionDirection(o, cVar2, cVar9, /*?p4*/0);
                MonProc.changeMotionAtOnce(o);
                Monsters.attackCancel(o);
                cVar2 = (byte) (isImmune(o, 9));
                if (cVar2 == 1) {
                    Bytes.w8(o.m, 0x36c, 0);
                    Bytes.w8(o.m, 0x176, 1);
                } else {
                    cVar2 = (byte) (isImmune(o, 9));
                    if (cVar2 == 2) {
                        Bytes.w8(o.m, 0x36c, 2);
                        Bytes.w8(o.m, 0x176, 1);
                    } else {
                        Bytes.w8(o.m, 0x36c, 3);
                    }
                }
                Bytes.w8(o.m, 0x16d, 3);
                Bytes.w8(o.m, 0x2ac, 2);
                Bytes.w16(o.m, 0x28e, 0);
                Bytes.w8(o.m, 0x3eb, 0);
                Bytes.w8(o.m, 0x35c, 1); // MONSTER_setAttackCommand
                Bytes.w8(o.m, 0x35c, 1); // MONSTER_resetAttackCommand
                Bytes.w8(o.m, 0x348, 0);
                return;
            }
            cVar2 = (byte) hitMotion(o);
        } else {
            cVar2 = (byte) hitMotion(o);
        }
        Chars.setNextMotionDirection(o, cVar2, 0);
        Bytes.w8(o.m, 0x340, 0);
        Bytes.w8(o.m, 0x8f, 0xff);
        Bytes.w8(o.m, 0x8e, 0);
        return;
    }

