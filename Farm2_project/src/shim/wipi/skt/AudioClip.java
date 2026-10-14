package wipi.skt;

/** SKT com.skt.m.AudioClip (an interface in the SKT API: the game uses invokeinterface). */
public interface AudioClip {
    void open(byte[] data, int off, int len) throws java.io.IOException;

    void play();

    void loop();

    void stop();

    void pause();

    void resume();

    void close();
}
