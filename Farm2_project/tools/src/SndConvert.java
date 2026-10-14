import java.io.File;
import java.nio.file.Files;

/**
 * Build-time audio conversion for The Farm 2: every /Sound/*.mmf (SMAF)
 * becomes /snd/<name>.mid (score) or /snd/<name>.wav (PCM). The original
 * name keeps a stub "WSND/snd/<name>.<ext>" read by com.skt.m.AudioClip.
 *
 * usage: SndConvert <origSoundDir> <outRoot>
 */
public class SndConvert {
    public static void main(String[] a) throws Exception {
        File in = new File(a[0]);
        File outSound = new File(a[1], "Sound"), outSnd = new File(a[1], "snd");
        outSound.mkdirs();
        outSnd.mkdirs();
        int n = 0, mid = 0, wav = 0, fail = 0;
        long before = 0, after = 0;
        File[] fs = in.listFiles();
        java.util.Arrays.sort(fs);
        for (File f : fs) {
            if (!f.getName().endsWith(".mmf")) continue;
            byte[] d = Files.readAllBytes(f.toPath());
            before += d.length;
            Smaf.Converted c = Smaf.convert(d);
            String base = f.getName().substring(0, f.getName().length() - 4);
            if (c == null || c.data == null) {
                System.out.println("  cannot convert " + f.getName());
                fail++;
                continue;
            }
            String ext = Smaf.MIDI.equals(c.type) ? ".mid" : ".wav";
            if (ext.equals(".mid")) mid++; else wav++;
            Files.write(new File(outSnd, base + ext).toPath(), c.data);
            byte[] stub = ("WSND/snd/" + base + ext).getBytes("ISO-8859-1");
            Files.write(new File(outSound, f.getName()).toPath(), stub);
            System.out.println("  " + f.getName() + " -> " + base + ext + " (" + c.data.length + " B)");
            after += c.data.length + stub.length;
            n++;
        }
        System.out.println("sound: " + n + " clips -> " + mid + " .mid + " + wav + " .wav (" + fail + " failed), " + before + " -> " + after + " bytes");
        if (fail > 0) System.exit(2);
    }
}
