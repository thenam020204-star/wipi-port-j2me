package wipi.host;

import javax.microedition.midlet.MIDlet;

/** MIDP entry point; starts the WIPI Jlet named by the "Farm-Jlet" attribute. */
public class FarmMidlet extends MIDlet {
    protected void startApp() {
        String j = getAppProperty("Farm-Jlet");
        Host.start(this, j == null ? "TheFarm2" : j.trim());
    }

    protected void pauseApp() {
        Host.pause();
    }

    protected void destroyApp(boolean unconditional) {
        Host.destroy();
    }
}
