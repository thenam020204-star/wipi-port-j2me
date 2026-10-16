import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.*;
import ghidra.program.model.listing.*;
import java.io.*;

public class DecompAll extends GhidraScript {
    @Override
    public void run() throws Exception {
        String[] args = getScriptArgs();
        String out = args.length > 0 ? args[0] : "/tmp/decomp.c";
        DecompInterface di = new DecompInterface();
        DecompileOptions opt = new DecompileOptions();
        di.setOptions(opt);
        di.openProgram(currentProgram);
        PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(out)));
        FunctionIterator it = currentProgram.getFunctionManager().getFunctions(true);
        int n = 0, fail = 0;
        while (it.hasNext() && !monitor.isCancelled()) {
            Function f = it.next();
            if (f.isThunk()) continue;
            DecompileResults r = di.decompileFunction(f, 60, monitor);
            pw.println("// ==== " + f.getName() + " @" + f.getEntryPoint() + " size=" + f.getBody().getNumAddresses());
            if (r.decompileCompleted()) {
                pw.println(r.getDecompiledFunction().getC());
            } else {
                pw.println("// DECOMPILE FAILED: " + r.getErrorMessage());
                fail++;
            }
            n++;
        }
        pw.close();
        println("decompiled " + n + " failed " + fail);
    }
}
