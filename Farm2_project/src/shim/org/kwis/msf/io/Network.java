package org.kwis.msf.io;

/** Offline port: the WIPI packet network is never available. */
public class Network {
    public static int connect() {
        return -1;
    }

    public static void disconnect() {
    }
}
