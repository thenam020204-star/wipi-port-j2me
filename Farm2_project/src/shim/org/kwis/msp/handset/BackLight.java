package org.kwis.msp.handset;

import wipi.host.Host;

public class BackLight {
    public static void alwaysOn() {
        Host.backlight(true);
    }

    public static void on(int ms) {
        Host.backlight(true);
    }

    public static void off() {
        Host.backlight(false);
    }
}
