    /** HERO_isBasicTargetEff @0x77e74  (c2j draft: 3 C2J-CHECK) */
    static boolean isBasicTargetEff(Char h, int skill) {
        int cVar1, iVar2, iVar4, uVar3;
        iVar4 = 0;
        do { // C2J-CHECK loop
            cVar1 = (byte) (BASIC_EFF[(iVar4 + (Bytes.s8(h.m, 0x16b) * 4))]     /* C2J fixed: memcpy of C.84.20547 = BASIC_EFF */);
            iVar2 = getEffectIdx(h, skill);
            iVar4 = (iVar4 + 1);
            if (cVar1 == iVar2) {
                uVar3 = Bytes.u8(h.m, 0xd12);
                iVar4 = (uVar3 * 0x1000000);
                iVar2 = iVar4;
                if ((uVar3 == 2) || (iVar4 < 0x2000000)) {
                    iVar2 = (uVar3 + 1);
                }
                if ((uVar3 == 2) || (iVar4 < 0x2000000)) {
                    Bytes.w8(h.m, 0xd12, (byte) (iVar2));
                } else {
                    Bytes.w8(h.m, 0xd12, 0);
                }
                return true;
            }
        } while (iVar4 != 4);
        return false;
    }

