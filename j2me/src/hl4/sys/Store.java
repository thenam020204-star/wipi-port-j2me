package hl4.sys;

/**
 * Persistent files of the original (MX_sd* / UM_FileRead / UM_FileWrite: "./Hero4Sms", "./Hero4OptSave", "./Hero4Game_n" ...), here an
 * in-memory table; the RMS-backed version arrives with the save system (every file is DES-encrypted by the game itself).
 */
public final class Store {
    private static final String[] names = new String[16];
    private static final byte[][] data = new byte[16][];
    /** the original's save container ("kickass" header file) exists once anything was written; before that every UM_FileRead fails with -1102 */
    private static boolean sd;

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
        sd = true;
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

    /**
     * UM_FileRead(name, buf, len) @0xca57c: tail call of MX_sdLoadData(name, buf, len & 0xffff): the number of bytes copied (min(len, stored size)); -1102 (0xfffffbb2)
     * while the container does not exist yet, -1107 for a missing file, -1108 for an empty one.  Store keeps the file image exactly as the caller wrote it (the game DES-encrypts it itself).
     */
    public static int fileRead(String name, byte[] buf, int len) {
        len &= 0xffff;
        if (!sd) {
            return -1102;
        }
        for (int i = 0; i < names.length; i++) {
            if (name.equals(names[i])) {
                if (data[i].length == 0) {
                    return -1108;
                }
                int n = data[i].length < len ? data[i].length : len;
                System.arraycopy(data[i], 0, buf, 0, n);
                return n;
            }
        }
        return -1107;
    }

    /**
     * UM_FileWrite(name, buf, len) @0xca4dc: tail call of MX_sdSaveData(name, buf, len & 0xffff): the file becomes the first len & 0xffff bytes of buf (created or
     * replaced, whatever its old size); returns that length.  The platform failures of the original (-1106, -1107 ...) have no counterpart.
     */
    public static int fileWrite(String name, byte[] buf, int len) {
        len &= 0xffff;
        byte[] c = new byte[len];
        System.arraycopy(buf, 0, c, 0, len);
        save(name, c);
        return len;
    }

    /**
     * UM_FileEncoding(name, buf, len) @0xca4e8: crc = MD5(buf[0..len)); MX_desEncrypt(buf, len) IN PLACE (the caller's buffer is left encrypted); writes crc[16] + the encrypted
     * bytes with UM_FileWrite(name, ., len + 16).
     */
    public static void fileEncoding(String name, byte[] buf, int len) {
        byte[] out = new byte[len + 16];
        Md5.crc(buf, len, out);
        Des.encrypt(buf, len);
        System.arraycopy(buf, 0, out, 16, len);
        fileWrite(name, out, len + 16);
    }

    /**
     * UM_FileDecoding(name, out, len) @0xca588: reads len + 16 bytes (UM_FileRead); 0 when nothing could be read (n <= 0); otherwise out = file[16..16+len) decrypted with
     * MX_desDecrypt, and the MD5 of out compared with file[0..16): 1 equal, -1 different (out is filled in either case).
     */
    public static int fileDecoding(String name, byte[] out, int len) {
        byte[] tmp = new byte[len + 16];
        int n = fileRead(name, tmp, len + 16);
        if (n <= 0) {
            return 0;
        }
        System.arraycopy(tmp, 16, out, 0, len);
        Des.decrypt(out, len);
        byte[] crc = new byte[16];
        Md5.crc(out, len, crc);
        for (int i = 0; i < 16; i++) {
            if (crc[i] != tmp[i]) {
                return -1;
            }
        }
        return 1;
    }
}
