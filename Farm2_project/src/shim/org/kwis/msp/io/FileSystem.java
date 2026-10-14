package org.kwis.msp.io;

import java.io.IOException;

import wipi.host.Store;

/** WIPI private file system, backed by RMS (one record store per file). */
public class FileSystem {
    public static boolean isFile(String name) {
        return Store.exists(name);
    }

    public static boolean exists(String name) {
        return Store.exists(name);
    }

    public static boolean isDirectory(String name) {
        return false;
    }

    public static void remove(String name) throws IOException {
        Store.remove(name);
    }

    public static void rename(String from, String to) throws IOException {
        byte[] b = Store.read(from);
        if (b == null) {
            throw new IOException(from);
        }
        Store.write(to, b, b.length);
        Store.remove(from);
    }

    public static int available() {
        return Store.available();
    }
}
