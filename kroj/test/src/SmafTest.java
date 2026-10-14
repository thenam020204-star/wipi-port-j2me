import hl4.core.Smaf;
import java.io.*;
import java.nio.file.*;
public class SmafTest {
    public static void main(String[] a) throws Exception {
        File out = new File(a[1]); out.mkdirs();
        for (File f : new File(a[0]).listFiles()) {
            byte[] d = Files.readAllBytes(f.toPath());
            Smaf.Converted c = Smaf.convert(d);
            String ext = c.type == null ? "none" : (c.type.equals(Smaf.MIDI) ? "mid" : "wav");
            if (c.data != null) Files.write(new File(out, f.getName() + "." + ext).toPath(), c.data);
            String info = "";
            try {
                if (ext.equals("mid")) { javax.sound.midi.Sequence s = javax.sound.midi.MidiSystem.getSequence(new ByteArrayInputStream(c.data)); info = "len " + s.getMicrosecondLength()/1000 + "ms events " + s.getTracks()[0].size(); }
                if (ext.equals("wav")) { javax.sound.sampled.AudioInputStream s = javax.sound.sampled.AudioSystem.getAudioInputStream(new ByteArrayInputStream(c.data)); info = s.getFormat() + " frames " + s.getFrameLength(); }
            } catch (Exception e) { info = "PARSE ERROR " + e; }
            System.out.println(f.getName() + " -> " + ext + " " + (c.data == null ? 0 : c.data.length) + " dur " + c.durationMs + " " + info);
        }
    }
}
