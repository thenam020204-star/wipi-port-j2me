import java.nio.file.*;
public class Dump {
    public static void main(String[] a) throws Exception {
        for (int i = 1; i < a.length; i++) {
            Path p = Paths.get(a[i]);
            hl4.core.Smaf.Converted c = hl4.core.Smaf.convert(Files.readAllBytes(p));
            String n = String.format("%03d", i) + "_" + p.getFileName();
            if (c.data == null) { System.out.println(n + " null"); continue; }
            Files.write(Paths.get(a[0], n + (c.type.equals(hl4.core.Smaf.MIDI) ? ".mid" : ".wav")), c.data);
            System.out.println(n + " " + c.type + " " + c.durationMs + " " + c.data.length);
        }
    }
}
