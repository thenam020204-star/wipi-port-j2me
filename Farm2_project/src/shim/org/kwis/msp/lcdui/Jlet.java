package org.kwis.msp.lcdui;

import wipi.host.Host;

/** WIPI-Java application base class (the MIDlet equivalent). */
public abstract class Jlet {
    private static Jlet active;

    public Jlet() {
        active = this;
    }

    public static Jlet getActiveJlet() {
        return active;
    }

    protected abstract void startApp(String[] args);

    protected void pauseApp() {
    }

    protected void resumeApp() {
    }

    protected abstract void destroyApp(boolean unconditional);

    public final void notifyDestroyed() {
        Host.exit();
    }

    public final void notifyPaused() {
    }

    public String getAppProperty(String key) {
        return Host.appProperty(key);
    }

    /** Host bridges. */
    public final void hostStart(String[] a) {
        startApp(a);
    }

    public final void hostPause() {
        pauseApp();
    }

    public final void hostResume() {
        resumeApp();
    }

    public final void hostDestroy(boolean u) {
        destroyApp(u);
    }
}
