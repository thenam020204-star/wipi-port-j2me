import hl4.core.Smaf;
import java.io.File;
import java.nio.file.Files;

/**
 * Build-time audio conversion: every SMAF clip (content "MMMD", any extension) of the sound dir becomes a plain J2ME file
 * (.mid for score tracks, .wav for PCM tracks) plus a stub under the original name
 * ("HL4SND\0" + resource path of the converted file) that the MIDlet resolves at play time.
 *
 * usage: SndConvert5 <srcSndDir> <outSndDir> <resourcePathPrefix e.g. /res/c/snd/>
 */
public class SndConvert5 {
    public static void main(String[] a) throws Exception {
        File in = new File(a[0]), out = new File(a[1]);
        out.mkdirs();
        String rp = a[2];
        int n = 0, mid = 0, wav = 0, fail = 0;
        StringBuffer list = new StringBuffer();
        long before = 0, after = 0;
        File[] fs = in.listFiles();
        java.util.Arrays.sort(fs);
        for (File f : fs) {
            if (!f.isFile()) continue;
            byte[] d = Files.readAllBytes(f.toPath());
            // SMAF clips by content ("MMMD"): Kroisen keeps them as sound/NNN.dat
            if (d.length < 4 || d[0] != 'M' || d[1] != 'M' || d[2] != 'M' || d[3] != 'D') continue;
            before += d.length;
            Smaf.Converted c = Smaf.convert(d);
            if (c == null || c.data == null) {
                System.out.println("  cannot convert " + f.getName() + " (kept as SMAF)");
                fail++;
                continue;
            }
            String base = f.getName().lastIndexOf('.') > 0 ? f.getName().substring(0, f.getName().lastIndexOf('.')) : f.getName();
            String ext = Smaf.MIDI.equals(c.type) ? ".mid" : ".wav";
            if (ext.equals(".mid")) mid++; else wav++;
            Files.write(new File(out, base + ext).toPath(), c.data);
            String ref = rp + base + ext;
            if (c.extraWav != null) { // score + PCM voice: second file played together with the MIDI
                Files.write(new File(out, base + "_v.wav").toPath(), c.extraWav);
                ref += "\0" + rp + base + "_v.wav";
                list.append(rp).append(base).append("_v.wav").append('\n');
                after += c.extraWav.length;
            }
            byte[] stub = ("HL4SND\0" + ref).getBytes("ISO-8859-1");
            Files.write(new File(out, f.getName()).toPath(), stub);
            list.append(rp).append(base).append(ext).append('\n');
            after += c.data.length + stub.length;
            n++;
        }
        Files.write(new File(out, "list.txt").toPath(), list.toString().getBytes("ISO-8859-1"));
        System.out.println("sound: " + n + " clips -> " + mid + " .mid + " + wav + " .wav (" + fail + " failed), " + before + " -> " + after + " bytes");
    }
}
