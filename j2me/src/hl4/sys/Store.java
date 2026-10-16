package hl4.sys;

/**
 * Persistent files of the original (MX_sd* / UM_FileRead / UM_FileWrite: "./Hero4Sms", "./Hero4OptSave", "./Hero4Game_n" ...), here an
 * in-memory table; the RMS-backed version arrives with the save system (every file is DES-encrypted by the game itself).
 */
public final class Store {
    private static final String[] names = new String[16];
    private static final byte[][] data = new byte[16][];

    private Store() {
    }

    /** LoadGameBase(name): decrypted bytes or null (the DES layer is applied by the caller's format code). */
    public static byte[] load(String name) {
        for (int i = 0; i < names.length; i++) {
            if (name.equals(names[i])) {
                return data[i];
            }
        }
        return null;
    }

    public static void save(String name, byte[] b) {
        int free = -1;
        for (int i = 0; i < names.length; i++) {
            if (name.equals(names[i])) {
                data[i] = b;
                return;
            }
            if (names[i] == null && free < 0) {
                free = i;
            }
        }
        names[free] = name;
        data[free] = b;
    }
}
