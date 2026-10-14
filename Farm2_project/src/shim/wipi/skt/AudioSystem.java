package wipi.skt;

import wipi.host.Snd;

public class AudioSystem {
    public static AudioClip getAudioClip(String type) {
        return new Clip();
    }

    public static int getMaxVolume(String type) {
        return 100;
    }

    public static int getVolume(String type) {
        return Snd.volume;
    }

    public static void setVolume(String type, int v) {
        Snd.setVolume(v);
    }
}
