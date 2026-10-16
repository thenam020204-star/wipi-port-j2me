package hl4.game;

import hl4.gfx.Gbm;
import hl4.gfx.Img;

/** UI image sets loaded with the game (skill icons, item icons, damage numbers ...). */
public final class Icons {
    public static Gbm[] armsSkIcon;
    public static Gbm[] guardianSkIcon;
    public static Gbm[] skillIcon;
    public static Gbm[] effIcon;
    public static Gbm[] guardianIcon;
    public static Gbm[] damageNumber;
    public static Gbm[] heroDamageNumber;
    public static Gbm[] uiGame;
    public static Gbm[] uiScript;
    public static Gbm[] itemIcon;

    private Icons() {
    }

    /** the icon sets of MainGameFrame_load: arms skills (warrior/gunslinger classes) or guardian skills */
    public static void loadSkillIcons() {
        if (Hero.jobIdx() == 0) {
            armsSkIcon = Img.loadAll("/CM/_ARMS_SKICN", 1);
        } else {
            guardianSkIcon = Img.loadAll("/CM/_GUARDIAN_SKICN", 1);
        }
    }
}
