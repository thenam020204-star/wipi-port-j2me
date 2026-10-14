package org.kwis.msp.media;

import wipi.host.Host;

public class Vibrator {
    public static void on(int level, int ms) {
        Host.vibrate(ms);
    }

    public static void off() {
        Host.vibrate(0);
    }
}
