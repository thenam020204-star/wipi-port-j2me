import java.awt.Robot;
import java.awt.event.KeyEvent;

/** Test helper for the MicroEmulator run: args = "delayMs:KEYNAME" pairs (VK_ constants without the prefix, e.g. 800:ENTER 400:RIGHT). Not part of the product. */
public class KeyRobot {
    public static void main(String[] a) throws Exception {
        Robot r = new Robot();
        for (int i = 0; i < a.length; i++) {
            String[] p = a[i].split(":");
            Thread.sleep(Long.parseLong(p[0]));
            int k = KeyEvent.class.getField("VK_" + p[1]).getInt(null);
            r.keyPress(k);
            Thread.sleep(60);
            r.keyRelease(k);
        }
    }
}
