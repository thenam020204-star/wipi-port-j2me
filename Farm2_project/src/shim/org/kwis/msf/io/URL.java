package org.kwis.msf.io;

import java.io.IOException;

public class URL {
    public static Socket find(String url) throws IOException {
        throw new IOException("offline: " + url);
    }
}
