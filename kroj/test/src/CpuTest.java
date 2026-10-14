import hl4.cpu.*;
import java.io.*;
import java.util.*;

public class CpuTest {
    static byte[] lcg(int seed, int n) {
        byte[] out = new byte[n];
        long x = seed & 0xFFFFFFFFL;
        for (int i = 0; i < n; i++) {
            x = (x * 1103515245L + 12345L) & 0xFFFFFFFFL;
            out[i] = (byte) ((x >> 16) & 0xFF);
        }
        return out;
    }

    static int hex(String s) {
        return (int) Long.parseLong(s, 16);
    }

    public static void main(String[] args) throws Exception {
        BufferedReader br = new BufferedReader(new FileReader(args[0]));
        String line;
        int total = 0, fail = 0;
        Map<String, Integer> failKinds = new TreeMap<>();
        while ((line = br.readLine()) != null) {
            String[] f = line.split(" ");
            boolean thumb = f[0].equals("T");
            int instr = hex(f[1]);
            int seed = hex(f[2]);
            Memory m = new Memory();
            m.map(0, 0x10000);
            m.map(0x10000, 0x20000);
            byte[] code = lcg(seed, 0x10000);
            if (thumb) {
                code[0x8000] = (byte) instr;
                code[0x8001] = (byte) (instr >> 8);
            } else {
                code[0x8000] = (byte) instr;
                code[0x8001] = (byte) (instr >> 8);
                code[0x8002] = (byte) (instr >> 16);
                code[0x8003] = (byte) (instr >> 24);
            }
            byte[] data = lcg(seed ^ 0x5A5A5A5A, 0x20000);
            m.writeBytes(0, code, 0, code.length);
            m.writeBytes(0x10000, data, 0, data.length);
            Cpu cpu = new Cpu(m);
            for (int i = 0; i < 15; i++) cpu.r[i] = hex(f[3 + i]);
            cpu.pc = hex(f[18]);
            cpu.t = thumb;
            cpu.setCpsrFlags(hex(f[19]) << 28);
            int k = 21;
            int[] exp = new int[15];
            for (int i = 0; i < 15; i++) exp[i] = hex(f[k + i]);
            int expPc = hex(f[k + 15]);
            int expFlags = hex(f[k + 16]);
            int expThumb = Integer.parseInt(f[k + 17]);
            int nw = Integer.parseInt(f[k + 18]);
            Map<Integer, Integer> writes = new HashMap<>();
            for (int i = 0; i < nw; i++) {
                String[] w = f[k + 19 + i].split(":");
                writes.put(hex(w[0]), hex(w[1]));
            }
            total++;
            String err = null;
            try {
                cpu.stepOne();
                for (int i = 0; i < 15; i++) {
                    if (cpu.r[i] != exp[i]) {
                        err = "r" + i + " got " + Integer.toHexString(cpu.r[i]) + " exp " + Integer.toHexString(exp[i]);
                        break;
                    }
                }
                if (err == null && cpu.pc != expPc) err = "pc got " + Integer.toHexString(cpu.pc) + " exp " + Integer.toHexString(expPc);
                int fl = (cpu.getCpsr() >>> 27) & 0x1F;
                if (err == null && fl != expFlags) err = "flags got " + Integer.toBinaryString(fl) + " exp " + Integer.toBinaryString(expFlags);
                if (err == null && (cpu.t ? 1 : 0) != expThumb) err = "thumb got " + cpu.t;
                if (err == null) {
                    // memory compare
                    for (int a = 0; a < 0x30000 && err == null; a += 4) {
                        int init = a < 0x10000 ? le(code, a) : le(data, a - 0x10000);
                        int cur = m.read32(a);
                        Integer e = writes.get(a);
                        int ev = e == null ? init : e;
                        if (cur != ev) err = "mem " + Integer.toHexString(a) + " got " + Integer.toHexString(cur) + " exp " + Integer.toHexString(ev);
                    }
                }
            } catch (Exception e) {
                err = "exception " + e;
            }
            if (err != null) {
                fail++;
                String kind = (thumb ? "T:" + Integer.toHexString(instr >>> 11) : "A:" + Integer.toHexString((instr >>> 20) & 0xFF));
                failKinds.merge(kind, 1, Integer::sum);
                if (fail <= 40) System.out.println("FAIL " + (thumb ? "T " : "A ") + Integer.toHexString(instr) + ": " + err);
            }
        }
        System.out.println("total " + total + " fail " + fail);
        System.out.println(failKinds);
    }

    static int le(byte[] b, int i) {
        return (b[i] & 0xFF) | ((b[i + 1] & 0xFF) << 8) | ((b[i + 2] & 0xFF) << 16) | ((b[i + 3] & 0xFF) << 24);
    }
}
