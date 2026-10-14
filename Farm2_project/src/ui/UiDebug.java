/**
 * Test hook, inert in release: only when the manifest/JAD carries
 * "Farm-Debug-Open" (e.g. "s0,s4,k0,p,g2") the game, each time it is back
 * in plain play (State 5) for 60 frames, opens the next screen of the list through
 * the game's own entry points (s<n> LoadShopMenu(n), k<n> LoadSkillShop(n),
 * p LoadPetShop(), g<n> LoadGameMenu(n)). Lets the headless tests reach every
 * shop without walking to its NPC. The property is read once.
 */
final class UiDebug {
    private static String list; // null: not read yet; "": off
    private static int pos, wait;

    static void poll(GameCanvas gc) {
        if (list == null) {
            String s = wipi.host.Host.appProperty("Farm-Debug-Open");
            list = s == null ? "" : s.trim();
        }
        if (list.length() == 0 || pos >= list.length()) {
            return;
        }
        if (gc.State != 5 || gc.Explan != null || gc.n.TBuff != null) {
            wait = 0;
            return;
        }
        if (++wait < 60) {
            return;
        }
        wait = 0;
        int e = list.indexOf(',', pos);
        if (e < 0) {
            e = list.length();
        }
        char k = list.charAt(pos);
        int n = e > pos + 1 ? Integer.parseInt(list.substring(pos + 1, e)) : 0;
        pos = e + 1;
        wipi.host.Log.i("debug open " + k + n);
        if (k == 's') {
            gc.LoadShopMenu(n);
        } else if (k == 'k') {
            gc.LoadSkillShop(n);
        } else if (k == 'p') {
            gc.LoadPetShop();
        } else if (k == 'g') {
            gc.LoadGameMenu(n);
        }
    }
}
