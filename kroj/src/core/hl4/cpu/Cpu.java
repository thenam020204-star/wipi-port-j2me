package hl4.cpu;

/**
 * ARMv5TE interpreter (ARM + Thumb), user mode only.
 * Written for CLDC 1.1 (no generics / collections), so it runs inside a MIDlet.
 *
 * Host calls: a stub is a 4-byte aligned address S with the Thumb instruction
 * SVC #0xFF (0xDFFF) at S and the 32-bit host-call id at S+4. Code reaches it
 * with an interworking branch to (S | 1).
 */
public final class Cpu {
    public final int[] r = new int[16];
    public boolean n, z, c, v, q;
    public boolean t;              // Thumb state
    public int pc;                 // address of next instruction
    public int curPc;              // address of instruction being executed
    public final Memory m;
    public SvcHandler handler;
    public long icount;

    /** id of the host call that ends {@link #call}. */
    public static final int SVC_RETURN = 0xFFFFFFFF;

    private boolean exitLoop;
    private int returnStub;        // address (without thumb bit) of the return stub
    public int callDepth;

    // shifter carry out
    private boolean sc;

    public boolean trace;
    public TraceSink traceSink;

    // statically translated regions
    public Aot aot;
    private int[] aotMap;
    private int aotBase;
    public long aotEntries, aotInstr;
    /** Debug: AOT entry counts per halfword index (null = off). */
    public static int[] aotStat;

    public void setAot(Aot a) {
        aot = a;
        aotMap = a == null ? null : a.map();
        aotBase = a == null ? 0 : a.base();
        applyAotBlocks();
    }

    // code patched at run time (HLE hooks): statically translated entries there must not be used
    private int[] aotBlocks = new int[0];

    public void aotBlock(int addr, int len) {
        int[] b = new int[aotBlocks.length + 2];
        System.arraycopy(aotBlocks, 0, b, 0, aotBlocks.length);
        b[aotBlocks.length] = addr;
        b[aotBlocks.length + 1] = len;
        aotBlocks = b;
        applyAotBlocks();
    }

    private void applyAotBlocks() {
        if (aotMap == null) return;
        for (int i = 0; i < aotBlocks.length; i += 2) {
            for (int a = aotBlocks[i] & ~1; a < aotBlocks[i] + aotBlocks[i + 1]; a += 2) {
                int k = (a - aotBase) >>> 1;
                if (k < aotMap.length) aotMap[k] = 0;
            }
        }
    }

    /** Unaligned-capable LDR (ARMv5 rotation). */
    public static int ldrRot(Memory m, int addr) {
        if ((addr & 3) == 0) return m.read32(addr);
        int w = m.read32(addr & ~3);
        int rot = (addr & 3) << 3;
        return (w >>> rot) | (w << (32 - rot));
    }

    public interface TraceSink {
        void trace(Cpu cpu, int addr, int op, boolean thumb);
    }

    public Cpu(Memory m) {
        this.m = m;
    }

    /** Write a host call stub at addr (must be 4-byte aligned, 8 bytes). Returns addr|1 (Thumb). */
    public int makeStub(int addr, int id) {
        m.write16(addr, 0xDFFF);
        m.write16(addr + 2, 0x4770); // bx lr (never executed)
        m.write32(addr + 4, id);
        return addr | 1;
    }

    public void setReturnStub(int addr) {
        returnStub = addr & ~1;
    }

    // ------------------------------------------------------------------
    // CPSR helpers
    public int getCpsr() {
        int x = 0x10; // user mode
        if (n) x |= 0x80000000;
        if (z) x |= 0x40000000;
        if (c) x |= 0x20000000;
        if (v) x |= 0x10000000;
        if (q) x |= 0x08000000;
        if (t) x |= 0x20;
        return x;
    }

    public void setCpsrFlags(int x) {
        n = (x & 0x80000000) != 0;
        z = (x & 0x40000000) != 0;
        c = (x & 0x20000000) != 0;
        v = (x & 0x10000000) != 0;
        q = (x & 0x08000000) != 0;
    }

    // ------------------------------------------------------------------
    // Calling ARM code from the host

    /** Stack argument helper: returns argument i (0-based) of the current host call. */
    public int arg(int i) {
        if (i < 4) return r[i];
        return m.read32(r[13] + ((i - 4) << 2));
    }

    public int call(int addr) {
        return call(addr, null);
    }

    // argument arrays reused by the fixed-arity helpers: call() copies them into registers before
    // running any guest code, so a nested call may overwrite them safely
    private final int[] args1 = new int[1], args2 = new int[2], args3 = new int[3], args4 = new int[4];

    public int call(int addr, int a0) {
        args1[0] = a0;
        return call(addr, args1);
    }

    public int call(int addr, int a0, int a1) {
        args2[0] = a0;
        args2[1] = a1;
        return call(addr, args2);
    }

    public int call(int addr, int a0, int a1, int a2) {
        args3[0] = a0;
        args3[1] = a1;
        args3[2] = a2;
        return call(addr, args3);
    }

    public int call(int addr, int a0, int a1, int a2, int a3) {
        args4[0] = a0;
        args4[1] = a1;
        args4[2] = a2;
        args4[3] = a3;
        return call(addr, args4);
    }

    public int lastR1;

    /**
     * Call an ARM/Thumb function (bit0 selects Thumb) and run until it returns.
     * Re-entrant: the complete register state is saved and restored.
     */
    private final int[][] savePool = new int[16][]; // register save area per call depth (no allocation per call)

    public int call(int addr, int[] args) {
        int d = callDepth;
        int[] saved = d < savePool.length ? savePool[d] : null;
        if (saved == null) {
            saved = new int[16];
            if (d < savePool.length) savePool[d] = saved;
        }
        System.arraycopy(r, 0, saved, 0, 16);
        boolean sn = n, sz = z, sc2 = c, sv = v, sq = q, st = t;
        int spc = pc, scur = curPc;
        boolean sExit = exitLoop;

        int nargs = args == null ? 0 : args.length;
        int sp = r[13] & ~7;
        if (nargs > 4) {
            int extra = (nargs - 4) * 4;
            sp -= (extra + 7) & ~7;
            for (int i = 4; i < nargs; i++) m.write32(sp + (i - 4) * 4, args[i]);
        }
        r[13] = sp;
        for (int i = 0; i < 4 && i < nargs; i++) r[i] = args[i];
        r[14] = returnStub | 1;
        t = (addr & 1) != 0;
        pc = addr & ~1;
        callDepth++;
        exitLoop = false;
        int result;
        try {
            runLoop();
            result = r[0];
            lastR1 = r[1];
        } finally {
            callDepth--;
            System.arraycopy(saved, 0, r, 0, 16);
            n = sn;
            z = sz;
            c = sc2;
            v = sv;
            q = sq;
            t = st;
            pc = spc;
            curPc = scur;
            exitLoop = sExit;
        }
        return result;
    }

    private void runLoop() {
        final int[] r = this.r;
        final int[][] pages = m.pages;
        final int[] amap = aotMap;
        final int abase = aotBase;
        int a = 0;
        try {
            while (!exitLoop) {
                a = pc;
                curPc = a;
                if (t) {
                    if (amap != null) {
                        int k = (a - abase) >>> 1;
                        if (k < amap.length) {
                            int id = amap[k];
                            if (id != 0) {
                                aotEntries++;
                                if (aotStat != null) aotStat[(a - abase) >>> 1]++;
                                long ic0 = icount;
                                int nx = icount >= dbgFrom && icount <= dbgTo ? aotCheck(id, a) : aot.run(id, this);
                                aotInstr += icount - ic0;
                                if ((nx & 1) != 0) {
                                    pc = nx & ~1;
                                } else { // interworking exit to ARM code (BLX / BX / POP pc)
                                    t = false;
                                    pc = nx & ~3;
                                }
                                continue;
                            }
                        }
                    }
                    int[] p = pages[a >>> 16];
                    int op = (p[(a & 0xFFFF) >> 2] >>> ((a & 2) << 3)) & 0xFFFF;
                    r[15] = a + 4;
                    pc = a + 2;
                    if (trace) traceSink.trace(this, a, op, true);
                    execThumb(op);
                } else {
                    int[] p = pages[a >>> 16];
                    int op = p[(a & 0xFFFF) >> 2];
                    r[15] = a + 8;
                    pc = a + 4;
                    if (trace) traceSink.trace(this, a, op, false);
                    execArm(op);
                }
                icount++;
            }
        } catch (NullPointerException e) {
            throw new CpuException("Fetch/access fault near pc=0x" + Integer.toHexString(a) + (t ? " (thumb)" : " (arm)") + " " + e);
        } catch (CpuException e) {
            throw new CpuException(e.getMessage() + " @pc=0x" + Integer.toHexString(a) + (t ? " (thumb)" : " (arm)"));
        }
    }

    /** Debug: icount window in which every AOT region run is cross-checked against the interpreter. */
    public static long dbgFrom = Long.MAX_VALUE, dbgTo = Long.MIN_VALUE;
    public static int dbgMismatches;

    private static int[] copyOf(int[] a) {
        int[] b = new int[a.length];
        System.arraycopy(a, 0, b, 0, a.length);
        return b;
    }

    private int aotCheck(int id, int entry) {
        int[] r0 = new int[16];
        System.arraycopy(r, 0, r0, 0, 16);
        boolean n0 = n, z0 = z, c0 = c, v0 = v;
        long ic0 = icount;
        int[][] pg0 = new int[m.pages.length][];
        for (int i = 0; i < pg0.length; i++) if (m.pages[i] != null) pg0[i] = copyOf(m.pages[i]);
        int nx = aot.run(id, this);
        int[] rA = new int[16];
        System.arraycopy(r, 0, rA, 0, 16);
        boolean nA = n, zA = z, cA = c, vA = v;
        long icA = icount;
        int[][] pgA = new int[m.pages.length][];
        for (int i = 0; i < pgA.length; i++) if (m.pages[i] != null) pgA[i] = copyOf(m.pages[i]);
        // replay with the interpreter from the saved state
        System.arraycopy(r0, 0, r, 0, 16);
        n = n0; z = z0; c = c0; v = v0; icount = ic0;
        for (int i = 0; i < pg0.length; i++) if (pg0[i] != null) System.arraycopy(pg0[i], 0, m.pages[i], 0, pg0[i].length);
        pc = entry; t = true;
        while (icount < icA) { stepOne(); icount++; }
        StringBuffer sb = new StringBuffer();
        int ipc = pc | (t ? 1 : 0);
        if (ipc != nx) sb.append(" pc aot=" + Integer.toHexString(nx) + " int=" + Integer.toHexString(ipc));
        for (int i = 0; i < 15; i++) if (r[i] != rA[i]) sb.append(" r" + i + " aot=" + Integer.toHexString(rA[i]) + " int=" + Integer.toHexString(r[i]));
        if (n != nA || z != zA || c != cA || v != vA) sb.append(" flags aot=" + nA + zA + cA + vA + " int=" + n + z + c + v);
        for (int i = 0; i < pgA.length; i++) {
            if (pgA[i] == null) continue;
            for (int k = 0; k < pgA[i].length; k++) if (pgA[i][k] != m.pages[i][k]) { sb.append(" mem@" + Integer.toHexString((i << 16) + k * 4) + " aot=" + Integer.toHexString(pgA[i][k]) + " int=" + Integer.toHexString(m.pages[i][k])); break; }
        }
        if (icount != icA) sb.append(" icount");
        if (sb.length() > 0) {
            dbgMismatches++;
            System.err.println("AOTCHECK region " + id + " entry " + Integer.toHexString(entry) + " ic " + ic0 + ":" + sb);
        }
        // continue from the interpreter state
        return ipc;
    }

    /** Execute exactly one instruction (for tests). */
    public void stepOne() {
        int a = pc;
        curPc = a;
        if (t) {
            int op = m.read16(a);
            r[15] = a + 4;
            pc = a + 2;
            execThumb(op);
        } else {
            int op = m.read32(a);
            r[15] = a + 8;
            pc = a + 4;
            execArm(op);
        }
    }

    /**
     * Host-call id of a resolved LGT import thunk at ARM address t
     * ("ldr ip, [pc, #4]; bx ip; .word table; .word stub|1" with a SVC stub), else 0. Used by AOT code
     * to call imports directly (same effect and instruction count as the interpreted path).
     */
    public final int thunkHost(int t) {
        if ((t & 3) != 0) return 0;
        int[] p = m.pages[t >>> 16];
        if (p == null) return 0;
        int i = (t & 0xFFFF) >> 2;
        if (i + 3 >= p.length || p[i] != 0xe59fc004 || p[i + 1] != 0xe12fff1c) return 0;
        int s = p[i + 3];
        if ((s & 1) == 0) return 0;
        s &= ~1;
        int[] q = m.pages[s >>> 16];
        if (q == null || ((q[(s & 0xFFFF) >> 2] >>> ((s & 2) << 3)) & 0xFFFF) != 0xDFFF) return 0;
        int id = m.read32(s + 4);
        return id == SVC_RETURN ? 0 : id;
    }

    /** Direct host call from AOT code (registers already stored in r[]). */
    public final void host(int id) {
        handler.svc(this, id);
    }

    private void hostCall(int stubAddr) {
        int id = m.read32(stubAddr + 4);
        if (id == SVC_RETURN) {
            exitLoop = true;
            return;
        }
        handler.svc(this, id);
        int target = r[14];
        t = (target & 1) != 0;
        pc = target & ~1;
    }

    /** Branch with interworking. */
    private void bx(int target) {
        t = (target & 1) != 0;
        pc = t ? (target & ~1) : (target & ~3);
    }

    // ------------------------------------------------------------------
    // Memory access with ARMv5 semantics

    private int ldr(int addr) {
        if ((addr & 3) == 0) return m.read32(addr);
        int w = m.read32(addr & ~3);
        int rot = (addr & 3) << 3;
        return (w >>> rot) | (w << (32 - rot));
    }

    private int ldrh(int addr) {
        return m.read16(addr & ~1);
    }

    // ------------------------------------------------------------------
    // Flag helpers

    private int add(int a, int b) {
        int res = a + b;
        n = res < 0;
        z = res == 0;
        c = (res ^ 0x80000000) < (a ^ 0x80000000);
        v = ((a ^ res) & (b ^ res)) < 0;
        return res;
    }

    private int sub(int a, int b) {
        int res = a - b;
        n = res < 0;
        z = res == 0;
        c = (a ^ 0x80000000) >= (b ^ 0x80000000);
        v = ((a ^ b) & (a ^ res)) < 0;
        return res;
    }

    private int adc(int a, int b) {
        long ures = (a & 0xFFFFFFFFL) + (b & 0xFFFFFFFFL) + (c ? 1 : 0);
        int res = (int) ures;
        n = res < 0;
        z = res == 0;
        c = (ures >>> 32) != 0;
        v = ((a ^ res) & (b ^ res)) < 0;
        return res;
    }

    private int sbc(int a, int b) {
        // a - b - !c  == a + ~b + c
        long ures = (a & 0xFFFFFFFFL) + ((~b) & 0xFFFFFFFFL) + (c ? 1 : 0);
        int res = (int) ures;
        n = res < 0;
        z = res == 0;
        c = (ures >>> 32) != 0;
        v = ((a ^ b) & (a ^ res)) < 0;
        return res;
    }

    private void nz(int res) {
        n = res < 0;
        z = res == 0;
    }

    // register-specified shifts (amount = low byte), set sc
    private int lslReg(int val, int amt) {
        amt &= 0xFF;
        if (amt == 0) {
            sc = c;
            return val;
        }
        if (amt < 32) {
            sc = ((val >>> (32 - amt)) & 1) != 0;
            return val << amt;
        }
        if (amt == 32) {
            sc = (val & 1) != 0;
            return 0;
        }
        sc = false;
        return 0;
    }

    private int lsrReg(int val, int amt) {
        amt &= 0xFF;
        if (amt == 0) {
            sc = c;
            return val;
        }
        if (amt < 32) {
            sc = ((val >>> (amt - 1)) & 1) != 0;
            return val >>> amt;
        }
        if (amt == 32) {
            sc = val < 0;
            return 0;
        }
        sc = false;
        return 0;
    }

    private int asrReg(int val, int amt) {
        amt &= 0xFF;
        if (amt == 0) {
            sc = c;
            return val;
        }
        if (amt < 32) {
            sc = ((val >> (amt - 1)) & 1) != 0;
            return val >> amt;
        }
        sc = val < 0;
        return val >> 31;
    }

    private int rorReg(int val, int amt) {
        amt &= 0xFF;
        if (amt == 0) {
            sc = c;
            return val;
        }
        amt &= 31;
        if (amt == 0) {
            sc = val < 0;
            return val;
        }
        int res = (val >>> amt) | (val << (32 - amt));
        sc = res < 0;
        return res;
    }

    // immediate shifts (ARM encoding), set sc
    private int shiftImm(int type, int val, int amt) {
        switch (type) {
            case 0: // LSL
                if (amt == 0) {
                    sc = c;
                    return val;
                }
                sc = ((val >>> (32 - amt)) & 1) != 0;
                return val << amt;
            case 1: // LSR
                if (amt == 0) {
                    sc = val < 0;
                    return 0;
                }
                sc = ((val >>> (amt - 1)) & 1) != 0;
                return val >>> amt;
            case 2: // ASR
                if (amt == 0) {
                    sc = val < 0;
                    return val >> 31;
                }
                sc = ((val >> (amt - 1)) & 1) != 0;
                return val >> amt;
            default: // ROR / RRX
                if (amt == 0) {
                    boolean nc = (val & 1) != 0;
                    int res = (val >>> 1) | (c ? 0x80000000 : 0);
                    sc = nc;
                    return res;
                }
                int res = (val >>> amt) | (val << (32 - amt));
                sc = ((val >>> (amt - 1)) & 1) != 0;
                return res;
        }
    }

    private int shiftReg(int type, int val, int amt) {
        switch (type) {
            case 0:
                return lslReg(val, amt);
            case 1:
                return lsrReg(val, amt);
            case 2:
                return asrReg(val, amt);
            default:
                return rorReg(val, amt);
        }
    }

    private boolean cond(int cc) {
        switch (cc) {
            case 0x0: return z;
            case 0x1: return !z;
            case 0x2: return c;
            case 0x3: return !c;
            case 0x4: return n;
            case 0x5: return !n;
            case 0x6: return v;
            case 0x7: return !v;
            case 0x8: return c && !z;
            case 0x9: return !c || z;
            case 0xA: return n == v;
            case 0xB: return n != v;
            case 0xC: return !z && (n == v);
            case 0xD: return z || (n != v);
            case 0xE: return true;
            default: return true;
        }
    }

    // ==================================================================
    // THUMB

    private void execThumb(int op) {
        final int[] r = this.r;
        switch (op >>> 11) {
            case 0: { // LSL imm
                int rd = op & 7, rm = (op >> 3) & 7, imm = (op >> 6) & 31;
                int val = r[rm];
                if (imm != 0) {
                    c = ((val >>> (32 - imm)) & 1) != 0;
                    val <<= imm;
                }
                r[rd] = val;
                n = val < 0;
                z = val == 0;
                return;
            }
            case 1: { // LSR imm
                int rd = op & 7, rm = (op >> 3) & 7, imm = (op >> 6) & 31;
                int val = r[rm];
                if (imm == 0) {
                    c = val < 0;
                    val = 0;
                } else {
                    c = ((val >>> (imm - 1)) & 1) != 0;
                    val >>>= imm;
                }
                r[rd] = val;
                n = val < 0;
                z = val == 0;
                return;
            }
            case 2: { // ASR imm
                int rd = op & 7, rm = (op >> 3) & 7, imm = (op >> 6) & 31;
                int val = r[rm];
                if (imm == 0) {
                    c = val < 0;
                    val >>= 31;
                } else {
                    c = ((val >> (imm - 1)) & 1) != 0;
                    val >>= imm;
                }
                r[rd] = val;
                n = val < 0;
                z = val == 0;
                return;
            }
            case 3: { // ADD/SUB reg/imm3
                int rd = op & 7, rn = (op >> 3) & 7, x = (op >> 6) & 7;
                int b = (op & 0x400) != 0 ? x : r[x];
                if ((op & 0x200) == 0) r[rd] = add(r[rn], b);
                else r[rd] = sub(r[rn], b);
                return;
            }
            case 4: { // MOV imm8
                int rd = (op >> 8) & 7, imm = op & 0xFF;
                r[rd] = imm;
                n = false;
                z = imm == 0;
                return;
            }
            case 5: // CMP imm8
                sub(r[(op >> 8) & 7], op & 0xFF);
                return;
            case 6: { // ADD imm8
                int rd = (op >> 8) & 7;
                r[rd] = add(r[rd], op & 0xFF);
                return;
            }
            case 7: { // SUB imm8
                int rd = (op >> 8) & 7;
                r[rd] = sub(r[rd], op & 0xFF);
                return;
            }
            case 8:
                if ((op & 0x400) == 0) thumbAlu(op);
                else thumbHiReg(op);
                return;
            case 9: { // LDR literal
                int rd = (op >> 8) & 7;
                r[rd] = m.read32((r[15] & ~3) + ((op & 0xFF) << 2));
                return;
            }
            case 10:
            case 11:
                thumbLoadStoreReg(op);
                return;
            case 12: { // STR imm5
                int rd = op & 7, rn = (op >> 3) & 7;
                m.write32((r[rn] + (((op >> 6) & 31) << 2)) & ~3, r[rd]);
                return;
            }
            case 13: { // LDR imm5
                int rd = op & 7, rn = (op >> 3) & 7;
                r[rd] = ldr(r[rn] + (((op >> 6) & 31) << 2));
                return;
            }
            case 14: { // STRB imm5
                int rd = op & 7, rn = (op >> 3) & 7;
                m.write8(r[rn] + ((op >> 6) & 31), r[rd]);
                return;
            }
            case 15: { // LDRB imm5
                int rd = op & 7, rn = (op >> 3) & 7;
                r[rd] = m.read8(r[rn] + ((op >> 6) & 31));
                return;
            }
            case 16: { // STRH imm5
                int rd = op & 7, rn = (op >> 3) & 7;
                m.write16((r[rn] + (((op >> 6) & 31) << 1)) & ~1, r[rd]);
                return;
            }
            case 17: { // LDRH imm5
                int rd = op & 7, rn = (op >> 3) & 7;
                r[rd] = ldrh(r[rn] + (((op >> 6) & 31) << 1));
                return;
            }
            case 18: { // STR sp-rel
                int rd = (op >> 8) & 7;
                m.write32((r[13] + ((op & 0xFF) << 2)) & ~3, r[rd]);
                return;
            }
            case 19: { // LDR sp-rel
                int rd = (op >> 8) & 7;
                r[rd] = ldr(r[13] + ((op & 0xFF) << 2));
                return;
            }
            case 20: // ADD rd, pc, imm
                r[(op >> 8) & 7] = (r[15] & ~3) + ((op & 0xFF) << 2);
                return;
            case 21: // ADD rd, sp, imm
                r[(op >> 8) & 7] = r[13] + ((op & 0xFF) << 2);
                return;
            case 22:
            case 23:
                thumbMisc(op);
                return;
            case 24: { // STMIA
                int rn = (op >> 8) & 7;
                int addr = r[rn] & ~3;
                int list = op & 0xFF;
                for (int i = 0; i < 8; i++) {
                    if ((list & (1 << i)) != 0) {
                        m.write32(addr, r[i]);
                        addr += 4;
                    }
                }
                r[rn] = r[rn] + (bitCount(list) << 2);
                return;
            }
            case 25: { // LDMIA
                int rn = (op >> 8) & 7;
                int base = r[rn];
                int addr = base & ~3;
                int list = op & 0xFF;
                for (int i = 0; i < 8; i++) {
                    if ((list & (1 << i)) != 0) {
                        r[i] = m.read32(addr);
                        addr += 4;
                    }
                }
                if ((list & (1 << rn)) == 0) r[rn] = base + (bitCount(list) << 2);
                return;
            }
            case 26:
            case 27: { // conditional branch / SWI
                int cc = (op >> 8) & 0xF;
                if (cc == 0xF) {
                    if ((op & 0xFF) == 0xFF) {
                        hostCall(curPc);
                        return;
                    }
                    throw new CpuException("Thumb SWI 0x" + Integer.toHexString(op & 0xFF));
                }
                if (cc == 0xE) throw new CpuException("Thumb undefined 0x" + Integer.toHexString(op));
                if (cond(cc)) pc = r[15] + (((byte) op) << 1);
                return;
            }
            case 28: // B
                pc = r[15] + ((op << 21) >> 20);
                return;
            case 29: { // BLX suffix (to ARM)
                int next = curPc + 2;
                int target = (r[14] + ((op & 0x7FF) << 1)) & ~3;
                r[14] = next | 1;
                t = false;
                pc = target;
                return;
            }
            case 30: // BL prefix
                r[14] = r[15] + ((op << 21) >> 9);
                return;
            case 31: { // BL suffix
                int next = curPc + 2;
                int target = r[14] + ((op & 0x7FF) << 1);
                r[14] = next | 1;
                pc = target & ~1;
                return;
            }
        }
    }

    private static int bitCount(int x) {
        x = x - ((x >>> 1) & 0x55555555);
        x = (x & 0x33333333) + ((x >>> 2) & 0x33333333);
        x = (x + (x >>> 4)) & 0x0F0F0F0F;
        x = x + (x >>> 8);
        x = x + (x >>> 16);
        return x & 0x3F;
    }

    private void thumbAlu(int op) {
        final int[] r = this.r;
        int rd = op & 7, rm = (op >> 3) & 7;
        int a = r[rd], b = r[rm];
        switch ((op >> 6) & 15) {
            case 0: nz(r[rd] = a & b); return;
            case 1: nz(r[rd] = a ^ b); return;
            case 2: { int res = lslReg(a, b); c = sc; nz(r[rd] = res); return; }
            case 3: { int res = lsrReg(a, b); c = sc; nz(r[rd] = res); return; }
            case 4: { int res = asrReg(a, b); c = sc; nz(r[rd] = res); return; }
            case 5: r[rd] = adc(a, b); return;
            case 6: r[rd] = sbc(a, b); return;
            case 7: { int res = rorReg(a, b); c = sc; nz(r[rd] = res); return; }
            case 8: nz(a & b); return;
            case 9: r[rd] = sub(0, b); return;
            case 10: sub(a, b); return;
            case 11: add(a, b); return;
            case 12: nz(r[rd] = a | b); return;
            case 13: nz(r[rd] = a * b); return;
            case 14: nz(r[rd] = a & ~b); return;
            default: nz(r[rd] = ~b); return;
        }
    }

    private void thumbHiReg(int op) {
        final int[] r = this.r;
        int rd = (op & 7) | ((op >> 4) & 8);
        int rm = (op >> 3) & 15;
        switch ((op >> 8) & 3) {
            case 0: { // ADD
                int res = r[rd] + r[rm];
                if (rd == 15) pc = res & ~1;
                else r[rd] = res;
                return;
            }
            case 1: // CMP
                sub(r[rd], r[rm]);
                return;
            case 2: { // MOV
                int res = r[rm];
                if (rd == 15) pc = res & ~1;
                else r[rd] = res;
                return;
            }
            default: { // BX / BLX
                int target = r[rm];
                if ((op & 0x80) != 0) r[14] = (curPc + 2) | 1;
                bx(target);
                return;
            }
        }
    }

    private void thumbLoadStoreReg(int op) {
        final int[] r = this.r;
        int rd = op & 7;
        int addr = r[(op >> 3) & 7] + r[(op >> 6) & 7];
        switch ((op >> 9) & 7) {
            case 0: m.write32(addr & ~3, r[rd]); return;             // STR
            case 1: m.write16(addr & ~1, r[rd]); return;             // STRH
            case 2: m.write8(addr, r[rd]); return;                   // STRB
            case 3: r[rd] = (byte) m.read8(addr); return;            // LDRSB
            case 4: r[rd] = ldr(addr); return;                       // LDR
            case 5: r[rd] = ldrh(addr); return;                      // LDRH
            case 6: r[rd] = m.read8(addr); return;                   // LDRB
            default: r[rd] = (short) ldrh(addr); return;             // LDRSH
        }
    }

    private void thumbMisc(int op) {
        final int[] r = this.r;
        switch ((op >> 8) & 15) {
            case 0x0: // ADD/SUB SP
                if ((op & 0x80) == 0) r[13] += (op & 0x7F) << 2;
                else r[13] -= (op & 0x7F) << 2;
                return;
            case 0x4:
            case 0x5: { // PUSH
                int list = op & 0xFF;
                int cnt = bitCount(list) + ((op & 0x100) != 0 ? 1 : 0);
                int addr = r[13] - (cnt << 2);
                r[13] = addr;
                addr &= ~3;
                for (int i = 0; i < 8; i++) {
                    if ((list & (1 << i)) != 0) {
                        m.write32(addr, r[i]);
                        addr += 4;
                    }
                }
                if ((op & 0x100) != 0) m.write32(addr, r[14]);
                return;
            }
            case 0xC:
            case 0xD: { // POP
                int list = op & 0xFF;
                int addr = r[13] & ~3;
                for (int i = 0; i < 8; i++) {
                    if ((list & (1 << i)) != 0) {
                        r[i] = m.read32(addr);
                        addr += 4;
                    }
                }
                if ((op & 0x100) != 0) {
                    int target = m.read32(addr);
                    addr += 4;
                    r[13] = addr;
                    bx(target);
                } else {
                    r[13] = addr;
                }
                return;
            }
            case 0xE: // BKPT
                throw new CpuException("BKPT");
            default:
                throw new CpuException("Thumb undefined misc 0x" + Integer.toHexString(op));
        }
    }

    // ==================================================================
    // ARM

    private void execArm(int op) {
        int cc = op >>> 28;
        if (cc != 0xE) {
            if (cc == 0xF) {
                armUnconditional(op);
                return;
            }
            if (!cond(cc)) return;
        }
        switch ((op >>> 25) & 7) {
            case 0:
                if ((op & 0x90) == 0x90) {
                    if ((op & 0x60) == 0) armMultiplySwap(op);
                    else armExtraLoadStore(op);
                } else if ((op & 0x01900000) == 0x01000000) {
                    armMisc(op);
                } else {
                    armDataProc(op);
                }
                return;
            case 1:
                if ((op & 0x01900000) == 0x01000000) {
                    if ((op & 0x00200000) != 0) {
                        armMsr(op, rorImm(op));
                        return;
                    }
                    throw new CpuException("ARM undefined 0x" + Integer.toHexString(op));
                }
                armDataProc(op);
                return;
            case 2:
                armSingleTransfer(op, op & 0xFFF);
                return;
            case 3:
                if ((op & 0x10) != 0) throw new CpuException("ARM undefined 0x" + Integer.toHexString(op));
                armSingleTransfer(op, shiftImm((op >> 5) & 3, r[op & 15], (op >> 7) & 31));
                return;
            case 4:
                armBlock(op);
                return;
            case 5: {
                int off = (op << 8) >> 6;
                if ((op & 0x01000000) != 0) r[14] = curPc + 4;
                pc = r[15] + off;
                return;
            }
            case 6:
                throw new CpuException("ARM coprocessor transfer 0x" + Integer.toHexString(op));
            default:
                if ((op & 0x01000000) != 0) throw new CpuException("ARM SWI 0x" + Integer.toHexString(op & 0xFFFFFF));
                if ((op & 0x10) != 0) {
                    if ((op & 0x00100000) != 0) { // MRC
                        int rd = (op >> 12) & 15;
                        if (rd == 15) {
                            n = z = c = v = false;
                        } else r[rd] = 0;
                    }
                    // MCR: cache maintenance etc -> no-op
                }
                return;
        }
    }

    private int rorImm(int op) {
        int imm = op & 0xFF;
        int rot = (op >> 7) & 30;
        if (rot == 0) {
            sc = c;
            return imm;
        }
        int res = (imm >>> rot) | (imm << (32 - rot));
        sc = res < 0;
        return res;
    }

    private void armUnconditional(int op) {
        if ((op & 0x0E000000) == 0x0A000000) { // BLX imm
            int off = ((op << 8) >> 6) | ((op >> 23) & 2);
            r[14] = curPc + 4;
            t = true;
            pc = r[15] + off;
            return;
        }
        if ((op & 0x0D70F000) == 0x0550F000) return; // PLD
        throw new CpuException("ARM unconditional undefined 0x" + Integer.toHexString(op));
    }

    private void armDataProc(int op) {
        final int[] r = this.r;
        int b;
        int rn = (op >> 16) & 15;
        int a;
        if ((op & 0x02000000) != 0) {
            b = rorImm(op);
            a = r[rn];
        } else {
            int rm = op & 15;
            int type = (op >> 5) & 3;
            if ((op & 0x10) == 0) {
                b = shiftImm(type, r[rm], (op >> 7) & 31);
                a = r[rn];
            } else {
                int rs = (op >> 8) & 15;
                int vm = r[rm];
                if (rm == 15) vm += 4;
                b = shiftReg(type, vm, r[rs]);
                a = r[rn];
                if (rn == 15) a += 4;
            }
        }
        int rd = (op >> 12) & 15;
        boolean s = (op & 0x00100000) != 0;
        int res;
        switch ((op >> 21) & 15) {
            case 0x0: res = a & b; if (s) { nz(res); c = sc; } break;           // AND
            case 0x1: res = a ^ b; if (s) { nz(res); c = sc; } break;           // EOR
            case 0x2: if (s) res = sub(a, b); else res = a - b; break;          // SUB
            case 0x3: if (s) res = sub(b, a); else res = b - a; break;          // RSB
            case 0x4: if (s) res = add(a, b); else res = a + b; break;          // ADD
            case 0x5: if (s) res = adc(a, b); else res = a + b + (c ? 1 : 0); break; // ADC
            case 0x6: if (s) res = sbc(a, b); else res = a - b - (c ? 0 : 1); break; // SBC
            case 0x7: if (s) res = sbc(b, a); else res = b - a - (c ? 0 : 1); break; // RSC
            case 0x8: nz(a & b); c = sc; return;                                  // TST
            case 0x9: nz(a ^ b); c = sc; return;                                  // TEQ
            case 0xA: sub(a, b); return;                                          // CMP
            case 0xB: add(a, b); return;                                          // CMN
            case 0xC: res = a | b; if (s) { nz(res); c = sc; } break;           // ORR
            case 0xD: res = b; if (s) { nz(res); c = sc; } break;               // MOV
            case 0xE: res = a & ~b; if (s) { nz(res); c = sc; } break;          // BIC
            default: res = ~b; if (s) { nz(res); c = sc; } break;               // MVN
        }
        if (rd == 15) {
            if (s) throw new CpuException("ARM exception return (S with Rd=PC)");
            pc = res & ~3;
        } else {
            r[rd] = res;
        }
    }

    private void armMultiplySwap(int op) {
        final int[] r = this.r;
        int bits = (op >> 23) & 3;
        if (((op >> 24) & 1) == 1) {
            // SWP / SWPB
            if ((op & 0x0FB00FF0) != 0x01000090) throw new CpuException("ARM undefined 0x" + Integer.toHexString(op));
            int rn = (op >> 16) & 15, rd = (op >> 12) & 15, rm = op & 15;
            int addr = r[rn];
            if ((op & 0x00400000) != 0) {
                int tmp = m.read8(addr);
                m.write8(addr, r[rm]);
                r[rd] = tmp;
            } else {
                int tmp = ldr(addr);
                m.write32(addr & ~3, r[rm]);
                r[rd] = tmp;
            }
            return;
        }
        boolean s = (op & 0x00100000) != 0;
        int rdHi = (op >> 16) & 15, rdLo = (op >> 12) & 15, rs = (op >> 8) & 15, rm = op & 15;
        if (bits == 0) {
            int res = r[rm] * r[rs];
            if ((op & 0x00200000) != 0) res += r[rdLo];
            r[rdHi] = res;
            if (s) nz(res);
            return;
        }
        // long multiply
        long res;
        if ((op & 0x00400000) != 0) res = (long) r[rm] * (long) r[rs];
        else res = (r[rm] & 0xFFFFFFFFL) * (r[rs] & 0xFFFFFFFFL);
        if ((op & 0x00200000) != 0) res += ((long) r[rdHi] << 32) | (r[rdLo] & 0xFFFFFFFFL);
        r[rdLo] = (int) res;
        r[rdHi] = (int) (res >>> 32);
        if (s) {
            n = res < 0;
            z = res == 0;
        }
    }

    private void armExtraLoadStore(int op) {
        final int[] r = this.r;
        int rn = (op >> 16) & 15, rd = (op >> 12) & 15;
        int off = (op & 0x00400000) != 0 ? (((op >> 4) & 0xF0) | (op & 0xF)) : r[op & 15];
        boolean p = (op & 0x01000000) != 0, u = (op & 0x00800000) != 0, w = (op & 0x00200000) != 0;
        boolean l = (op & 0x00100000) != 0;
        int base = r[rn];
        int addr = p ? (u ? base + off : base - off) : base;
        int sh = (op >> 5) & 3;
        if (l) {
            int val;
            if (sh == 1) val = ldrh(addr);
            else if (sh == 2) val = (byte) m.read8(addr);
            else val = (short) ldrh(addr);
            if (!p) r[rn] = u ? base + off : base - off;
            else if (w) r[rn] = addr;
            if (rd == 15) bx(val);
            else r[rd] = val;
        } else {
            if (sh == 1) {
                int val = rd == 15 ? r[15] + 4 : r[rd];
                m.write16(addr & ~1, val);
            } else if (sh == 2) { // LDRD
                int v0 = m.read32(addr & ~3);
                int v1 = m.read32((addr & ~3) + 4);
                if (!p) r[rn] = u ? base + off : base - off;
                else if (w) r[rn] = addr;
                r[rd] = v0;
                r[rd + 1] = v1;
                return;
            } else { // STRD
                m.write32(addr & ~3, r[rd]);
                m.write32((addr & ~3) + 4, r[rd + 1]);
            }
            if (!p) r[rn] = u ? base + off : base - off;
            else if (w) r[rn] = addr;
        }
    }

    private void armMisc(int op) {
        final int[] r = this.r;
        int op2 = (op >> 4) & 15;
        int op1 = (op >> 21) & 3;
        switch (op2) {
            case 0:
                if (op1 == 0 || op1 == 2) { // MRS
                    r[(op >> 12) & 15] = getCpsr();
                } else { // MSR reg
                    armMsr(op, r[op & 15]);
                }
                return;
            case 1:
                if (op1 == 1) { // BX
                    bx(r[op & 15]);
                    return;
                }
                if (op1 == 3) { // CLZ
                    int x = r[op & 15];
                    int cnt = 0;
                    if (x == 0) cnt = 32;
                    else {
                        while ((x & 0x80000000) == 0) {
                            x <<= 1;
                            cnt++;
                        }
                    }
                    r[(op >> 12) & 15] = cnt;
                    return;
                }
                break;
            case 3:
                if (op1 == 1) { // BLX reg
                    int target = r[op & 15];
                    r[14] = curPc + 4;
                    bx(target);
                    return;
                }
                break;
            case 5: { // QADD/QSUB/QDADD/QDSUB
                int rd = (op >> 12) & 15, rn = (op >> 16) & 15, rm = op & 15;
                int a = r[rm], b = r[rn];
                if ((op1 & 2) != 0) b = sat((long) b * 2);
                long res = (op1 & 1) == 0 ? (long) a + b : (long) a - b;
                r[rd] = sat(res);
                return;
            }
            case 7:
                throw new CpuException("BKPT (arm)");
            default:
                if ((op2 & 9) == 8) {
                    armDspMultiply(op, op1, op2);
                    return;
                }
                break;
        }
        throw new CpuException("ARM undefined misc 0x" + Integer.toHexString(op));
    }

    private int sat(long x) {
        if (x > Integer.MAX_VALUE) {
            q = true;
            return Integer.MAX_VALUE;
        }
        if (x < Integer.MIN_VALUE) {
            q = true;
            return Integer.MIN_VALUE;
        }
        return (int) x;
    }

    private void armDspMultiply(int op, int op1, int op2) {
        final int[] r = this.r;
        int rd = (op >> 16) & 15, rn = (op >> 12) & 15, rs = (op >> 8) & 15, rm = op & 15;
        boolean xTop = (op2 & 2) != 0, yTop = (op2 & 4) != 0;
        int rsv = yTop ? (r[rs] >> 16) : (short) r[rs];
        switch (op1) {
            case 0: { // SMLAxy
                int rmv = xTop ? (r[rm] >> 16) : (short) r[rm];
                int prod = rmv * rsv;
                long sum = (long) prod + r[rn];
                int res = prod + r[rn];
                if (sum != res) q = true;
                r[rd] = res;
                return;
            }
            case 1: { // SMLAWy / SMULWy
                long prod = ((long) r[rm] * rsv) >> 16;
                if (!xTop) { // SMLAW
                    long sum = prod + r[rn];
                    int res = (int) sum;
                    if (sum != res) q = true;
                    r[rd] = res;
                } else {
                    r[rd] = (int) prod;
                }
                return;
            }
            case 2: { // SMLALxy
                int rmv = xTop ? (r[rm] >> 16) : (short) r[rm];
                long acc = ((long) r[rd] << 32) | (r[rn] & 0xFFFFFFFFL);
                acc += (long) rmv * rsv;
                r[rn] = (int) acc;
                r[rd] = (int) (acc >>> 32);
                return;
            }
            default: { // SMULxy
                int rmv = xTop ? (r[rm] >> 16) : (short) r[rm];
                r[rd] = rmv * rsv;
                return;
            }
        }
    }

    private void armMsr(int op, int val) {
        if ((op & 0x00400000) != 0) return; // SPSR: ignore
        if ((op & 0x00080000) != 0) setCpsrFlags(val); // flags field
        // control field ignored (user mode)
    }

    private void armSingleTransfer(int op, int off) {
        final int[] r = this.r;
        int rn = (op >> 16) & 15, rd = (op >> 12) & 15;
        boolean p = (op & 0x01000000) != 0, u = (op & 0x00800000) != 0;
        boolean b = (op & 0x00400000) != 0, w = (op & 0x00200000) != 0;
        boolean l = (op & 0x00100000) != 0;
        int base = r[rn];
        int addr = p ? (u ? base + off : base - off) : base;
        if (l) {
            int val = b ? m.read8(addr) : ldr(addr);
            if (!p) r[rn] = u ? base + off : base - off;
            else if (w) r[rn] = addr;
            if (rd == 15) bx(val);
            else r[rd] = val;
        } else {
            int val = rd == 15 ? r[15] + 4 : r[rd];
            if (b) m.write8(addr, val);
            else m.write32(addr & ~3, val);
            if (!p) r[rn] = u ? base + off : base - off;
            else if (w) r[rn] = addr;
        }
    }

    private void armBlock(int op) {
        final int[] r = this.r;
        int rn = (op >> 16) & 15;
        int list = op & 0xFFFF;
        boolean p = (op & 0x01000000) != 0, u = (op & 0x00800000) != 0;
        boolean w = (op & 0x00200000) != 0, l = (op & 0x00100000) != 0;
        if ((op & 0x00400000) != 0 && !(l && (list & 0x8000) != 0)) {
            // user bank transfer: identical in user mode
        }
        int cnt = bitCount(list);
        int base = r[rn];
        int start;
        if (u) start = p ? base + 4 : base;
        else start = p ? base - (cnt << 2) : base - (cnt << 2) + 4;
        int newBase = u ? base + (cnt << 2) : base - (cnt << 2);
        int addr = start & ~3;
        if (l) {
            if (w) r[rn] = newBase;
            int newPc = 0;
            boolean loadPc = false;
            for (int i = 0; i < 16; i++) {
                if ((list & (1 << i)) != 0) {
                    int val = m.read32(addr);
                    addr += 4;
                    if (i == 15) {
                        newPc = val;
                        loadPc = true;
                    } else r[i] = val;
                }
            }
            if (loadPc) bx(newPc);
        } else {
            for (int i = 0; i < 16; i++) {
                if ((list & (1 << i)) != 0) {
                    int val = i == 15 ? r[15] + 4 : r[i];
                    m.write32(addr, val);
                    addr += 4;
                }
            }
            if (w) r[rn] = newBase;
        }
    }
}
