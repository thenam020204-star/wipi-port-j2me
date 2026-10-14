package wipi.host;

import java.io.IOException;

import javax.microedition.io.Connection;

/**
 * Connector.open replacement used by the game's GXG billing library.
 * The GXG gateway (ngw.gxg.com) is long gone: purchases and the ranking
 * board are answered by an in-process server (FakeGxg). Nothing goes on air.
 */
public final class Net {
    public static Connection open(String url) throws IOException {
        if (url != null && url.startsWith("socket://ngw.gxg.com")) {
            return new FakeGxg();
        }
        throw new IOException("offline: " + url);
    }
}
