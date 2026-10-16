package hl4.game;

import hl4.gfx.Gbm;
import hl4.gfx.Img;
import hl4.gfx.Sprite;

/** The image sets and shared sprites held in globals by the original (_imgLogo, _imgGrade, _imgCM, _imgTitleBg ...). */
public final class Imgs {
    public static Gbm[] logo;
    public static Gbm[] grade;
    public static Gbm[] gradeV1;
    public static Gbm[] gradeV2;
    public static Gbm[] gradeS;
    public static Gbm[] gradeV1S;
    public static Gbm[] gradeV2S;
    public static Gbm[] cm;
    public static Gbm[] titleBg;
    public static Gbm[] gmenuComm;
    public static Gbm[] ending;
    public static Gbm[] mmenu;
    /** _MMenuChSpr[0..3]: main-menu character sprites (/CM/_MMENU_CHAR_n_CIF + _BM) */
    public static final Sprite[] mmenuChar = new Sprite[4];
    public static Sprite imo;
    public static Gbm[] shadow;
    public static byte[] txtPopup;
    /** _sprEvtEffect[]: event/title effect sprites (MAP/EFFECT/_EVT_EFF_n_CIF) */
    public static final Sprite[] evtEffect = new Sprite[5];

    private Imgs() {
    }

    /** loadLogoImage @0xcc7e0 */
    public static void loadLogo() {
        logo = Img.loadAll("/l/_LOGO", -1);
        grade = Img.loadAll("/CM/GRADE_BM", -1);
        gradeV1 = Img.loadAll("/CM/GRADE_V_BM1", -1);
        gradeV2 = Img.loadAll("/CM/GRADE_V_BM2", -1);
        gradeS = Img.loadAll("/CM/GRADE_BM_S", -1);
        gradeV1S = Img.loadAll("/CM/GRADE_V_BM1_S", -1);
        gradeV2S = Img.loadAll("/CM/GRADE_V_BM2_S", -1);
    }

    /** deleteLogoImage @0xcc66c (the plain _imgGrade/_imgGrade_s stay: MainFrame_Finalize frees them) */
    public static void deleteLogo() {
        logo = null;
        gradeV1 = null;
        gradeV2 = null;
        gradeV1S = null;
        gradeV2S = null;
    }

    /** loadCommonMenuImage @0xcc77c */
    public static void loadCommonMenu() {
        gmenuComm = Img.loadAll("/GMenu/_GMENU_COMM_BM", -1);
    }

    /** loadTitleImage @0xcc7a8 (once) */
    public static void loadTitle() {
        if (titleBg == null) {
            titleBg = Img.loadAll("/tdf/TITLE_BM", -1);
        }
    }

    /** loadCommonImage @0xcd35c: common images (tinted by the time-of-day slot _map[0x68]) */
    public static void loadCommon(int timeSlot) {
        cm = Img.loadAll("/CM/_CM_IMGCM", 1);
    }

    /** loadEvtEffectCif(cifId, slot) @0xcd1d4 + loadSimpleSprite */
    public static void loadEvtEffect(int cifId, int slot) {
        byte[] c = hl4.sys.Res.load("/MAP/EFFECT/_EVT_EFF_" + cifId + "_CIF");
        Sprite s = new Sprite(c, 0x11);
        s.setImages(0, Img.loadAll("/MAP/EFFECT/_EVT_EFF_BM", -1));
        evtEffect[slot] = s;
    }

    /** deleteEvtEffectCif's sprite part */
    public static void deleteEvtEffects() {
        for (int i = 0; i < evtEffect.length; i++) {
            evtEffect[i] = null;
        }
    }

    /** loadMenuImage @0xcd304 */
    public static void loadMenu() {
        mmenu = Img.loadAll("/CM/_MMENU_BM", -1);
        for (int i = 0; i < 4; i++) {
            Sprite s = new Sprite(hl4.sys.Res.load("/CM/_MMENU_CHAR_" + i + "_CIF"), 0x11);
            s.setImages(0, Img.loadAll("/CM/_MMENU_CHAR_" + i + "_BM", -1));
            mmenuChar[i] = s;
        }
    }

    /** loadGameCommonImage @0xcd484: emotion sprite, shadows, event popup text, damage numbers, in-game UI images, item icons */
    public static void loadGameCommon() {
        // loadIMOImage: SPR_loadSprite(_imgIMO, "/CM/_CM_EMOSPR", 0x11) + its images tinted for the time class _map[0x68]
        imo = new Sprite(hl4.sys.Res.load("/CM/_CM_EMOSPR"), 0x11);
        imo.setImages(0, Img.loadAll("/CM/_CM_EMO", hl4.game.G.map[0x68]));
        shadow = Img.loadAll("/CM/_CM_SHADOW", -1);
        txtPopup = hl4.sys.Res.load("/NPC/_EVENT_POP_TXT");
        byte[] pal = hl4.sys.Res.load("/H4/PAL/_H_200_PAL");
        if (pal != null) {
            Icons.damageNumber = Img.loadAll("/CM/_CM_NUM", -1);
            Icons.heroDamageNumber = Img.loadAll("/CM/_CM_NUM", -1);
            int[] pairs = hl4.data.Pal.parse(pal);
            for (int k = 0; k < 10; k++) {
                hl4.data.Pal.apply(Icons.heroDamageNumber[k].palBase, Icons.heroDamageNumber[k].pal, pairs);
            }
            Icons.uiGame = Img.loadAll("/CM/_CM_INGAMEUI", -1);
            Icons.uiScript = Img.loadAll("/CM/_CM_SCRIPT", -1);
            Icons.itemIcon = Img.loadAll("/ITM/_ITM_ICN", -1);
            hl4.game.G.itemBagPtrs.release();
            hl4.game.G.itemBag.release();
            hl4.game.G.armsBag.release();
        }
    }

    /** deleteMenuImage */
    public static void deleteMenu() {
        mmenu = null;
        for (int i = 0; i < 4; i++) {
            mmenuChar[i] = null;
        }
    }
}
