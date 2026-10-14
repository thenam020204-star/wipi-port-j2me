package wipi.skt;

import java.io.IOException;

import wipi.host.Snd;

/**
 * AudioClip implementation. Sounds were converted from SMAF at build time;
 * the jar keeps a small stub under the original .mmf name which names the
 * converted file (see wipi.host.Snd).
 */
final class Clip implements AudioClip {
    private String name;

    public void open(byte[] data, int off, int len) throws IOException {
        name = Snd.nameOf(data, off, len);
    }

    public void play() {
        Snd.play(this, name, false);
    }

    public void loop() {
        Snd.play(this, name, true);
    }

    public void stop() {
        Snd.stop(this);
    }

    public void pause() {
        Snd.stop(this);
    }

    public void resume() {
    }

    public void close() {
        Snd.stop(this);
    }
}
