package org.kwis.msf.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** WIPI socket (an interface in WIPI: the game uses invokeinterface). Never created offline. */
public interface Socket {
    InputStream getInputStream() throws IOException;

    OutputStream getOutputStream() throws IOException;

    void close() throws IOException;
}
