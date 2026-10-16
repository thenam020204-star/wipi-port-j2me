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
}
