import hl4.core.Smaf;
import java.io.File;
import java.nio.file.Files;

/**
 * Build-time audio conversion: every SMAF clip (*_MMF) of a game resource directory becomes a plain
 * J2ME file (.mid for score tracks, .wav for PCM tracks) plus a tiny stub under the original name
 * ("HL4SND\0" + resource path of the converted file) that the MIDlet resolves at play time.
 *
 * usage: SndConvert <rawResDir> <overlayDir> [prefix]     (prefix = "res/" for LGT)
 */
public class SndConvert {
    public static void main(String[] a) throws Exception {
        File in = new File(a[0], "SND"), out = new File(a[1], (a.length > 2 ? a[2] : "") + "SND");
        out.mkdirs();
        String rp = "/" + (a.length > 2 ? a[2] : "") + "SND/";
        int n = 0, mid = 0, wav = 0, fail = 0;
        StringBuffer list = new StringBuffer();
        long before = 0, after = 0;
        for (File f : in.listFiles()) {
            if (!f.getName().endsWith("_MMF")) continue;
            byte[] d = Files.readAllBytes(f.toPath());
            before += d.length;
            Smaf.Converted c = Smaf.convert(d);
            if (c == null || c.data == null) {
                System.out.println("  cannot convert " + f.getName() + " (kept as SMAF)");
                fail++;
                continue;
            }
            String base = f.getName().substring(0, f.getName().length() - 4);
            String ext = Smaf.MIDI.equals(c.type) ? ".mid" : ".wav";
            if (ext.equals(".mid")) mid++; else wav++;
            Files.write(new File(out, base + ext).toPath(), c.data);
            byte[] stub = ("HL4SND\0" + rp + base + ext).getBytes("ISO-8859-1");
            Files.write(new File(out, f.getName()).toPath(), stub);
            list.append(rp).append(base).append(ext).append('\n');
            after += c.data.length + stub.length;
            n++;
        }
        Files.write(new File(out, "list.txt").toPath(), list.toString().getBytes("ISO-8859-1"));
        System.out.println("sound: " + n + " clips -> " + mid + " .mid + " + wav + " .wav (" + fail + " failed), " + before + " -> " + after + " bytes");
    }
}
