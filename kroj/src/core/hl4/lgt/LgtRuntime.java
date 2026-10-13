package hl4.lgt;

import hl4.core.Gfx16;
import hl4.core.Host;
import hl4.core.Runtime;
import hl4.cpu.Cpu;
import hl4.cpu.CpuException;
import java.util.Hashtable;

/**
 * LG Telecom WIPI-C runtime: loads binary.mod (ELF32 ARM), resolves numbered import
 * tables and implements the WIPI-C services the application imports.
 */
public class LgtRuntime extends Runtime {
    static final int CAT_INIT = 0x100;
    static final int CAT_WIPIC = 0x1FB;
    static final int CAT_STDLIB = 0x001;

    int entry;
    int param1, param2;
    int fnInit;

    public LgtRuntime(Host host) {
        super(host);
    }

    protected String resourcePath(String name) {
        // LGT archives keep resources under res/ (the platform maps them transparently)
        if (name.startsWith("/")) name = name.substring(1);
        return "res/" + name;
    }

    protected byte[] resource(String name) {
        byte[] d = host.loadResource(resourcePath(name));
        if (d == null) {
            String n = name.startsWith("/") ? name.substring(1) : name;
            d = host.loadResource(n);
        }
        return d;
    }

    // ------------------------------------------------------------------
    // Loading

    static int u16(byte[] b, int o) {
        return (b[o] & 0xFF) | ((b[o + 1] & 0xFF) << 8);
    }

    static int u32(byte[] b, int o) {
        return (b[o] & 0xFF) | ((b[o + 1] & 0xFF) << 8) | ((b[o + 2] & 0xFF) << 16) | ((b[o + 3] & 0xFF) << 24);
    }

    public void load(byte[] elf) {
        if (elf[0] != 0x7F || elf[1] != 'E' || elf[2] != 'L' || elf[3] != 'F') throw new CpuException("binary.mod is not ELF");
        entry = u32(elf, 0x18);
        int shoff = u32(elf, 0x20);
        int shentsize = u16(elf, 0x2E);
        int shnum = u16(elf, 0x30);
        for (int i = 0; i < shnum; i++) {
            int sh = shoff + i * shentsize;
            int type = u32(elf, sh + 4);
            int addr = u32(elf, sh + 12);
            int off = u32(elf, sh + 16);
            int size = u32(elf, sh + 20);
            if (addr == 0 || size == 0) continue;
            mem.map(addr, size);
            if (type != 8) { // not NOBITS
                int n = Math.min(size, elf.length - off);
                mem.writeBytes(addr, elf, off, n);
            }
            debug("section @" + hex(addr) + " size " + hex(size) + " type " + type);
        }
    }

    /** Run the ELF entry point and the module initializer, then startClet. */
    /** Debug (harness): replace the Thumb function at addr by "return 0" (experiments only). */
    public void stubOut(int addr) {
        hookThumb(addr, 90);
    }

    /** Game specific hooks (text, offline patches); installed after load. */
    public LgtGame game;

    protected void hle(int idx) {
        if (idx == 90) { // debug stub: return from the hooked function
            ret(0);
            return;
        }
        if (game != null && game.hle(idx)) return;
        super.hle(idx);
    }

    public void start() {
        param1 = alloc(0x214 + 4 + 16);
        mem.fill(param1, 0, 0x214 + 4 + 16);
        param2 = allocStruct(new int[]{stub(CAT_INIT << 16), stub((CAT_INIT << 16) | 1), 0, 0});
        cpu.call(entry | 1, new int[]{param1, param2, 0});
        int initStruct = mem.read32(param1 + 0x214);
        fnInit = mem.read32(initStruct + 4);
        log("LGT init struct " + hex(initStruct) + " fn_init " + hex(fnInit));
        cpu.call(fnInit);
        if (fnStartClet == 0) throw new CpuException("clet was not registered");
        log("startClet " + hex(fnStartClet));
        cpu.call(fnStartClet);
    }

    // ------------------------------------------------------------------
    // Host calls

    public int callCount;
    public boolean[] traceSvc;
    public int[] svcStats = new int[0x800];

    public void svc(Cpu cpu, int id) {
        int cat = id >>> 16;
        int idx = id & 0xFFFF;
        callCount++;
        if (cat == CAT_HLE) {
            hle(idx);
        } else if (cat == CAT_WIPIC) {
            if (idx < svcStats.length) svcStats[idx]++;
            if (traceSvc != null && idx < traceSvc.length && traceSvc[idx]) log("svc " + hex(idx) + " lr=" + hex(cpu.r[14]) + " args " + hex(cpu.r[0]) + "," + hex(cpu.r[1]) + "," + hex(cpu.r[2]) + "," + hex(cpu.r[3]));
            wipic(idx);
        } else if (cat == CAT_STDLIB) {
            stdlib(idx);
        } else if (cat == CAT_INIT) {
            init(idx);
        } else {
            throw new CpuException("unknown host call " + hex(id));
        }
    }

    void init(int idx) {
        switch (idx) {
            case 0: // get import table
                ret(cpu.arg(0));
                return;
            case 1: { // get import function
                int table = cpu.arg(0), fn = cpu.arg(1);
                debug("import " + hex(table) + ":" + hex(fn));
                if (table == 0x1FB) ret(stub((CAT_WIPIC << 16) | fn));
                else if (table == 1) ret(stub((CAT_STDLIB << 16) | fn));
                else if (table == 0x1F8 && fn == 0x16) ret(stub((CAT_INIT << 16) | 2));
                else if (table == 0x1F8 && fn == 0x17) ret(stub((CAT_INIT << 16) | 3));
                else {
                    log("unknown import " + hex(table) + ":" + hex(fn));
                    ret(stub((CAT_INIT << 16) | 0x100 | (fn & 0xFF)));
                }
                return;
            }
            case 2: // set display property (ptr_display, property, value, size)
                setDisplayProperty(cpu.arg(1), cpu.arg(2), cpu.arg(3));
                ret(0);
                return;
            case 3: // application jar path
                ret(0);
                return;
            default:
                log("unknown bootstrap call " + hex(idx));
                ret(0);
        }
    }

    // ------------------------------------------------------------------
    // WIPI-C dispatch (LGT numbering)

    void wipic(int idx) {
        switch (idx) {
            case 0x03: cletRegister(); return;
            case 0x32: fbPointer(); return;
            case 0x33: fbWidth(); return;
            case 0x34: fbHeight(); return;
            case 0x35: fbBpl(); return;
            case 0x36: ret(16); return;
            case 0x64: knlPrintk(); return;
            case 0x65: knlSprintk(); return;
            case 0x6b: knlExit(); return;
            case 0x6f: knlGetProgramName(); return;
            case 0x75: knlAlloc(); return;
            case 0x76: knlCalloc(); return;
            case 0x77: knlFree(); return;
            case 0x78: knlGetTotalMemory(); return;
            case 0x79: knlGetFreeMemory(); return;
            case 0x7a: knlDefTimer(); return;
            case 0x7b: knlSetTimer(); return;
            case 0x7c: knlUnsetTimer(); return;
            case 0x7d: knlCurrentTime(); return;
            case 0x7e: knlGetSystemProperty(); return;
            case 0x7f: knlSetSystemProperty(); return;
            case 0x80: knlGetResourceId(); return;
            case 0x81: knlGetResource(); return;
            case 0x97: { // program info: returns app id string
                byte[] b = ascii(aid);
                int p = alloc(b.length + 1);
                mem.writeCString(p, b);
                ret(p);
                return;
            }
            case 0xc8: grpGetImageProperty(); return;
            case 0xc9: ret(mem.read32(cpu.arg(0) + 4)); return; // MC_grpGetImageFrameBuffer: image->framebuffer
            case 0xca: grpGetScreenFrameBuffer(); return;
            case 0xcb: grpDestroyOffScreenFrameBuffer(); return;
            case 0xcc: grpCreateOffScreenFrameBuffer(); return;
            case 0xcd: grpInitContext(); return;
            case 0xce: grpSetContext(); return;
            case 0xcf: grpGetContext(); return;
            case 0xd0: grpPutPixel(); return;
            case 0xd1: grpDrawLine(); return;
            case 0xd2: grpDrawRect(); return;
            case 0xd3: grpFillRect(); return;
            case 0xd4: grpCopyFrameBuffer(); return;
            case 0xd5: grpDrawImage(); return;
            case 0xd7: grpCopyArea(); return;
            case 0xd8: grpArc(false); return;
            case 0xd9: grpArc(true); return;
            case 0xda: grpDrawString(); return;
            case 0xdc: grpReadPixels(); return;
            case 0xde: grpFlushLcd(); return;
            case 0xdf: ret(Gfx16.toRgb565(cpu.arg(0), cpu.arg(1), cpu.arg(2))); return;
            case 0xe0: grpGetRgbFromPixel(); return;
            case 0xe1: grpGetDisplayInfo(); return;
            case 0xe2: requestRepaint(); return;
            case 0xe3: ret(0); return;                  // MC_grpGetFont (stub, as reference)
            case 0xe4: ret(12); return;
            case 0xe5: ret(10); return;
            case 0xe6: ret(2); return;
            case 0xe7: ret(cpu.arg(2) < 0 ? mem.strlen(cpu.arg(1)) * 6 : cpu.arg(2) * 6); return;
            case 0xe9: grpCreateImage(); return;
            case 0x130: ret(0); return;                 // input method
            case 0x68: // HL5 calls 0x68(0) at every exit point (menu 게임종료, failed auth): terminate
                knlExit();
                return;
            case 0xeb: grpDestroyImage(); return;
            case 0x6a: case 0xee:
            case 0x12c: case 0x12d: case 0x12e: case 0x12f:
                debug("stub wipic " + hex(idx) + "(" + hex(cpu.arg(0)) + ", " + hex(cpu.arg(1)) + ", " + hex(cpu.arg(2)) + ")");
                ret(0);
                return;
            case 0x320: case 0x321: case 0x322: case 0x323: case 0x338:
                timeApi(idx);
                return;
            // database
            case 0x190: dbOpen(); return;
            case 0x191: dbRead(); return;
            case 0x192: dbWrite(); return;
            case 0x193: dbClose(); return;
            case 0x194: dbSeek(); return;
            case 0x195: dbStat(); return;
            case 0x196: dbDelete(); return;
            case 0x197: dbListRecords(); return;
            case 0x19c: ret(1024 * 1024); return;
            case 0x1a0: dbExists(); return;
            case 0x198: // HL5: registers resource directories ("/c", "/c/sp/img0", ...) with flag 1 (mkdir-like)
                debug("wipic 0x198(" + readString(cpu.arg(0)) + ", " + cpu.arg(1) + ")");
                ret(0);
                return;
            // network: always fails like a phone without service
            case 0x258: netConnect(); return;
            case 0x259: log("MC_netClose"); ret(0); return;
            case 0x7d0: log("MC_netSocket(" + cpu.arg(0) + ", " + cpu.arg(1) + ") -> 1"); ret(1); return;
            case 0x25b: netSocketConnect(); return;
            case 0x25c: netSocketWrite(); return;
            case 0x25d: netSocketRead(); return;
            case 0x25e: log("MC_netSocketClose(" + cpu.arg(0) + ")"); netReadCb = 0; netWriteCb = 0; ret(0); return;
            case 0x263: // MC_netGetHostAddr(?, name, cb, param)
                log("MC_netGetHostAddr(" + hex(cpu.arg(0)) + ", " + readString(cpu.arg(1)) + ")");
                ret(-1);
                return;
            case 0x265: // MC_netSetReadCB(fd, cb, param)
                netReadCb = cpu.arg(1);
                netReadParam = cpu.arg(2);
                debug("MC_netSetReadCB " + hex(netReadCb));
                ret(0);
                return;
            case 0x266: // MC_netSetWriteCB(fd, cb, param)
                netWriteCb = cpu.arg(1);
                netWriteParam = cpu.arg(2);
                debug("MC_netSetWriteCB " + hex(netWriteCb));
                ret(0);
                return;
            case 0x384: { // htonl
                int v = cpu.arg(0);
                ret(((v & 0xFF) << 24) | ((v & 0xFF00) << 8) | ((v >>> 8) & 0xFF00) | (v >>> 24));
                return;
            }
            case 0x385: { // htons
                int v = cpu.arg(0);
                ret(((v & 0xFF) << 8) | ((v >> 8) & 0xFF));
                return;
            }
            case 0x388: { // inet_addr(const char*) -> network byte order (little-endian int)
                String h = readString(cpu.arg(0));
                int v = 0, part = 0, n = 0;
                boolean ok = true;
                for (int i = 0; i <= h.length(); i++) {
                    char c = i < h.length() ? h.charAt(i) : '.';
                    if (c >= '0' && c <= '9') part = part * 10 + (c - '0');
                    else if (c == '.') { v |= (part & 255) << (8 * n); n++; part = 0; }
                    else ok = false;
                }
                if (!ok || n != 4) v = 0x0100007f;
                log("MC_netInetAddr(" + h + ") -> " + hex(v));
                ret(v);
                return;
            }
            // media
            case 0x4b0: mdaClipCreate(); return;
            case 0x4b1: mdaClipFree(); return;
            case 0x4b3: mdaClipPutData(); return;
            case 0x4b6: ret(0); return;
            case 0x4b8: ret(clipVolume(cpu.arg(0))); return;
            case 0x4b9: mdaClipSetVolume(); return;
            case 0x4ba: mdaPlay(); return;
            case 0x4bd: mdaStop(); return;
            case 0x4c0: ret(0); return;
            case 0x4c1: host.vibrate(cpu.arg(1), cpu.arg(0)); ret(0); return;
            case 0x4c2: ret(0); return;
            case 0x4c5: ret(0); return;
            case 0x4c6: ret(0); return;
            case 0x4ce: debug("MC_mdaGetVolume(" + readString(cpu.arg(0)) + ")"); ret(100); return;
            case 0x4d1: muted = cpu.arg(1) != 0 && false; ret(0); return;
            case 0x4d2: ret(0); return;
            case 0x514: debug("wipic 0x514(" + hex(cpu.arg(0)) + ")"); ret(0); return;
            case 0x578: ret(0); return;                 // backlight
            case 0x581: ret(0); return;
            default:
                throw new CpuException("unimplemented WIPI-C function " + hex(idx) + " args " + hex(cpu.arg(0)) + "," + hex(cpu.arg(1)) + "," + hex(cpu.arg(2)) + " lr=" + hex(cpu.r[14]));
        }
    }

    void cletRegister() {
        int table = cpu.arg(0);
        useAnnunciator = cpu.arg(1);
        fnStartClet = mem.read32(table);
        fnPauseClet = mem.read32(table + 4);
        fnResumeClet = mem.read32(table + 8);
        fnDestroyClet = mem.read32(table + 12);
        fnPaintClet = mem.read32(table + 16);
        fnHandleCletEvent = mem.read32(table + 20);
        log("CletRegister start=" + hex(fnStartClet) + " paint=" + hex(fnPaintClet) + " event=" + hex(fnHandleCletEvent) + " annun=" + useAnnunciator);
    }

    // ------------------------------------------------------------------
    // Graphics (LGT records, see wie's lgt/wipi_c/graphics.rs)
    // backing  = {width, height, bpl, bpp, buf}
    // view     = {ptr_backing, x, y, width, height}
    // fb       = {owned_image, ptr_graphics(view), ptr_image(backing), screen_kind}
    // image    = {ptr_backing, ptr_framebuffer}
    // context  = {clip x1,y1,x2,y2, fg, bg, alpha, pixel_op, pixel_param, font, style, xor, offx, offy}

    int physW = 0, physH = 0;
    int useAnnunciator;
    boolean appViewActive;
    int screenBacking, screenView, screenWrapper, annunciatorView;

    void setDisplayProperty(int prop, int value, int size) {
        if (size != 0) return;
        if (prop == 0x64) physW = value;
        else if (prop == 0x65) physH = value;
        else if (prop == 0x7f) useAnnunciator = value;
        debug("SetDisplayProperty(" + hex(prop) + ", " + value + ")");
    }

    int physWidth() {
        return physW != 0 ? physW : screenWidth;
    }

    int physHeight() {
        return physH != 0 ? physH : screenHeight;
    }

    static int annunciatorHeight(int w) {
        switch (w) {
            case 120: return 14;
            case 176: case 220: return 20;
            case 240: case 320: case 400: return 24;
            default: return 0;
        }
    }

    int appY() {
        return (appViewActive && useAnnunciator != 0) ? annunciatorHeight(physWidth()) : 0;
    }

    int createBacking(int w, int h) {
        int bpl = w * 2;
        int pixels = alloc(bpl * h);
        mem.fill(pixels, 0, bpl * h);
        return allocStruct(new int[]{w, h, bpl, 16, pixels});
    }

    void grpGetScreenFrameBuffer() {
        int kind = cpu.arg(0);
        if (kind != 0) throw new CpuException("unsupported screen kind " + kind);
        if (screenWrapper == 0) {
            screenBacking = createBacking(physWidth(), physHeight());
            screenView = allocStruct(new int[]{screenBacking, 0, 0, physWidth(), physHeight()});
            screenWrapper = allocStruct(new int[]{0, screenView, 0, kind});
            log("screen framebuffer " + physWidth() + "x" + physHeight() + " @" + hex(mem.read32(screenBacking + 16)));
        }
        ret(screenWrapper);
    }

    void activateApplicationView(int handle) {
        if (appViewActive || useAnnunciator == 0 || handle != screenWrapper) return;
        int ay = annunciatorHeight(physWidth());
        mem.write32(screenView + 8, ay);
        mem.write32(screenView + 16, physHeight() - ay);
        annunciatorView = allocStruct(new int[]{screenBacking, 0, 0, physWidth(), ay});
        appViewActive = true;
    }

    void grpCreateOffScreenFrameBuffer() {
        int w = cpu.arg(0), h = cpu.arg(1);
        if (w <= 0 || h <= 0) {
            ret(0);
            return;
        }
        int backing = createBacking(w, h);
        int view = allocStruct(new int[]{backing, 0, 0, w, h});
        ret(allocStruct(new int[]{1, view, backing, 0}));
    }

    void grpDestroyOffScreenFrameBuffer() {
        int h = cpu.arg(0);
        if (h == 0) return;
        int view = mem.read32(h + 4), backing = mem.read32(h + 8);
        if (mem.read32(h) == 0 || view == 0 || backing == 0) return;
        heap.free(view);
        heap.free(mem.read32(backing + 16));
        heap.free(backing);
        heap.free(h);
    }

    int viewOf(int fb) {
        return mem.read32(fb + 4);
    }

    int backingOf(int fb) {
        return mem.read32(viewOf(fb));
    }

    void fbPointer() {
        int h = cpu.arg(0);
        activateApplicationView(h);
        ret(mem.read32(backingOf(h) + 16));
    }

    void fbWidth() {
        ret(mem.read32(viewOf(cpu.arg(0)) + 12));
    }

    void fbHeight() {
        ret(mem.read32(viewOf(cpu.arg(0)) + 16));
    }

    void fbBpl() {
        int h = cpu.arg(0);
        if (mem.read32(h) != 0) ret(0);
        else ret(mem.read32(backingOf(h) + 8));
    }

    void grpInitContext() {
        int p = cpu.arg(0);
        int[] v = {0, 0, 0x7fff, 0x7fff, 0, 0x00ffffff, 255, 0, 255, 0, 0, 0, 0, 0};
        for (int i = 0; i < v.length; i++) mem.write32(p + i * 4, v[i]);
    }

    void grpSetContext() {
        int p = cpu.arg(0), op = cpu.arg(1), value = cpu.arg(2);
        switch (op) {
            case 0:
                if (value != 0) {
                    mem.write32(p, mem.read32(value));
                    mem.write32(p + 4, mem.read32(value + 4));
                    mem.write32(p + 8, mem.read32(value + 8) - 1);
                    mem.write32(p + 12, mem.read32(value + 12) - 1);
                }
                break;
            case 1: mem.write32(p + 16, value); break;
            case 2: mem.write32(p + 20, value); break;
            case 4: if (value >= 0 && value <= 255) mem.write32(p + 24, value); break;
            case 5: mem.write32(p + 28, value); break;
            case 6: mem.write32(p + 32, value); break;
            case 7: mem.write32(p + 36, value); break;
            case 8: mem.write32(p + 40, value); break;
            case 9:
                if (value == 0) {
                    mem.write32(p + 28, 0);
                    mem.write32(p + 44, 0);
                } else {
                    mem.write32(p + 24, 0);
                    mem.write32(p + 28, 1);
                    mem.write32(p + 44, 1);
                }
                break;
            case 10:
                if (value != 0) {
                    mem.write32(p + 48, mem.read32(value));
                    mem.write32(p + 52, mem.read32(value + 4));
                }
                break;
            default:
                break;
        }
    }

    /** MC_grpGetContext(ctx, index, out): clip = {x1, y1, x2, y2} (x2/y2 exclusive), others one word. */
    void grpGetContext() {
        int p = cpu.arg(0), op = cpu.arg(1), out = cpu.arg(2);
        int v = 0;
        switch (op) {
            case 0:
                if (out != 0) {
                    mem.write32(out, mem.read32(p));
                    mem.write32(out + 4, mem.read32(p + 4));
                    mem.write32(out + 8, mem.read32(p + 8) + 1);
                    mem.write32(out + 12, mem.read32(p + 12) + 1);
                }
                ret(out);
                return;
            case 1: v = mem.read32(p + 16); break;
            case 2: v = mem.read32(p + 20); break;
            case 4: v = mem.read32(p + 24); break;
            case 5: v = mem.read32(p + 28); break;
            case 6: v = mem.read32(p + 32); break;
            case 7: v = mem.read32(p + 36); break;
            case 8: v = mem.read32(p + 40); break;
            case 9: v = mem.read32(p + 44); break;
            case 10:
                if (out != 0) {
                    mem.write32(out, mem.read32(p + 48));
                    mem.write32(out + 4, mem.read32(p + 52));
                }
                ret(out);
                return;
            default:
                break;
        }
        if (out != 0) mem.write32(out, v);
        ret(v);
    }

    /** Build a drawing surface for framebuffer handle fb with graphics context ctx. */
    // reused surfaces (game thread only): no allocation per drawing call
    private final Gfx16.Surface surfDst = new Gfx16.Surface(), surfSrc = new Gfx16.Surface(), surfImg = new Gfx16.Surface();

    Gfx16.Surface surface(int fb, int ctx) {
        return surface(fb, ctx, surfDst);
    }

    Gfx16.Surface surface(int fb, int ctx, Gfx16.Surface s) {
        int view = viewOf(fb);
        int backing = mem.read32(view);
        s.width = mem.read32(backing);
        s.height = mem.read32(backing + 4);
        s.bpl = mem.read32(backing + 8);
        s.buf = mem.read32(backing + 16);
        int vx = mem.read32(view + 4), vy = mem.read32(view + 8), vw = mem.read32(view + 12), vh = mem.read32(view + 16);
        int adjY = (mem.read32(fb) == 0 && mem.read32(fb + 12) != 1) ? appY() : 0;
        int cx1 = vx, cy1 = vy, cx2 = vx + vw, cy2 = vy + vh;
        if (ctx != 0) {
            int x1 = mem.read32(ctx), y1 = mem.read32(ctx + 4) + adjY;
            int x2 = mem.read32(ctx + 8) + 1, y2 = mem.read32(ctx + 12) + 1 + adjY;
            cx1 = Math.max(cx1, x1);
            cy1 = Math.max(cy1, y1);
            cx2 = Math.min(cx2, x2);
            cy2 = Math.min(cy2, y2);
            s.tx = mem.read32(ctx + 48);
            s.ty = mem.read32(ctx + 52) + adjY;
        } else {
            s.ty = adjY;
        }
        s.clipX1 = Math.max(0, cx1);
        s.clipY1 = Math.max(0, cy1);
        s.clipX2 = Math.min(s.width, cx2);
        s.clipY2 = Math.min(s.height, cy2);
        return s;
    }

    int fg(int ctx) {
        return ctx == 0 ? 0 : mem.read32(ctx + 16) & 0xFFFF;
    }

    /** 0xdc (fb, x, y, w, h, buf, ?): read RGB565 pixels of the frame buffer into buf, one int each (Zenonia 2). */
    void grpReadPixels() {
        int fb = cpu.arg(0), x = cpu.arg(1), y = cpu.arg(2), w = cpu.arg(3), h = cpu.arg(4), buf = cpu.arg(5);
        Gfx16.Surface s = surface(fb, 0);
        int k = 0;
        for (int j = 0; j < h; j++)
            for (int i = 0; i < w; i++, k++) {
                int px = x + i + s.tx, py = y + j + s.ty;
                int v = (px >= 0 && py >= 0 && px < s.width && py < s.height) ? mem.read16(s.buf + py * s.bpl + px * 2) : 0;
                mem.write32(buf + 4 * k, v);
            }
        ret(0);
    }

    void grpPutPixel() {
        int fb = cpu.arg(0), x = cpu.arg(1), y = cpu.arg(2), ctx = cpu.arg(3);
        Gfx16.Surface s = surface(fb, ctx);
        Gfx16.putPixel(mem, s, x + s.tx, y + s.ty, fg(ctx));
    }

    void grpDrawLine() {
        int fb = cpu.arg(0), x1 = cpu.arg(1), y1 = cpu.arg(2), x2 = cpu.arg(3), y2 = cpu.arg(4), ctx = cpu.arg(5);
        Gfx16.Surface s = surface(fb, ctx);
        Gfx16.drawLine(mem, s, x1 + s.tx, y1 + s.ty, x2 + s.tx, y2 + s.ty, fg(ctx));
    }

    void grpDrawRect() {
        int fb = cpu.arg(0), x = cpu.arg(1), y = cpu.arg(2), w = cpu.arg(3), h = cpu.arg(4), ctx = cpu.arg(5);
        if (w <= 0 || h <= 0) return;
        Gfx16.Surface s = surface(fb, ctx);
        Gfx16.drawRect(mem, s, x + s.tx, y + s.ty, w - 1, h - 1, fg(ctx));
    }

    void grpFillRect() {
        int fb = cpu.arg(0), x = cpu.arg(1), y = cpu.arg(2), w = cpu.arg(3), h = cpu.arg(4), ctx = cpu.arg(5);
        if (w <= 0 || h <= 0) return;
        Gfx16.Surface s = surface(fb, ctx);
        Gfx16.fillRect(mem, s, x + s.tx, y + s.ty, w, h, fg(ctx));
    }

    void grpArc(boolean fill) {
        int fb = cpu.arg(0), x = cpu.arg(1), y = cpu.arg(2), w = cpu.arg(3), h = cpu.arg(4);
        int start = cpu.arg(5), end = cpu.arg(6), ctx = cpu.arg(7);
        if (w <= 0 || h <= 0) return;
        Gfx16.Surface s = surface(fb, ctx);
        Gfx16.arc(mem, s, x + s.tx, y + s.ty, fill ? w : w - 1, fill ? h : h - 1, start, end - start, fg(ctx), fill);
    }

    void grpCopyFrameBuffer() {
        int dst = cpu.arg(0), x = cpu.arg(1), y = cpu.arg(2), w = cpu.arg(3), h = cpu.arg(4);
        int src = cpu.arg(5), sx = cpu.arg(6), sy = cpu.arg(7), ctx = cpu.arg(8);
        if (w <= 0 || h <= 0) return;
        Gfx16.Surface d = surface(dst, ctx);
        Gfx16.Surface s = surface(src, 0, surfSrc);
        s.clipX1 = 0;
        s.clipY1 = 0;
        s.clipX2 = s.width;
        s.clipY2 = s.height;
        Gfx16.copy(mem, d, x + d.tx, y + d.ty, w, h, s, sx, sy);
    }

    void grpCopyArea() {
        int dst = cpu.arg(0), x = cpu.arg(1), y = cpu.arg(2), w = cpu.arg(3), h = cpu.arg(4);
        int sx = cpu.arg(5), sy = cpu.arg(6), ctx = cpu.arg(7);
        if (w <= 0 || h <= 0) return;
        Gfx16.Surface d = surface(dst, ctx);
        Gfx16.copy(mem, d, x + d.tx, y + d.ty, w, h, d, sx + d.tx, sy + d.ty);
    }

    void grpDrawString() {
        log("MC_grpDrawString(" + readString(cpu.arg(3)) + ") not rendered");
    }

    void grpGetRgbFromPixel() {
        int px = cpu.arg(0) & 0xFFFF;
        int argb = Gfx16.table()[px];
        mem.write32(cpu.arg(1), (argb >> 16) & 0xFF);
        mem.write32(cpu.arg(2), (argb >> 8) & 0xFF);
        mem.write32(cpu.arg(3), argb & 0xFF);
        ret(cpu.arg(0));
    }

    void grpGetDisplayInfo() {
        int out = cpu.arg(1);
        int[] v = {16, 16, physWidth(), physHeight() - appY(), physWidth() * 2, 1, 0xf800, 0x07e0, 0x001f};
        for (int i = 0; i < v.length; i++) mem.write32(out + i * 4, v[i]);
        ret(1);
    }

    void grpGetImageProperty() {
        int img = cpu.arg(0), prop = cpu.arg(1);
        int backing = mem.read32(img);
        if (prop == 4) ret(mem.read32(backing));
        else if (prop == 5) ret(mem.read32(backing + 4));
        else ret(0);
    }

    // ---- decoded images (MC_grpCreateImage) ----
    // image = {ptr_backing, ptr_framebuffer}; backing = {w, h, bpl, 16, pixels} in RGB565 (wie lgt/graphics.rs).
    // Transparent PNG pixels (alpha < 128) are returned as IMG_KEY = 0xFFF7, the value the LGT handsets give:
    // Kroisen hard-codes it (display init 0x3a184 stores 0xfff7 at 0x15108e4 and maps it to its transparent
    // palette colour 0xf81f before converting the image to palette indices, loader 0x26590). An opaque
    // pixel of that value is left as is (same ambiguity as on the handset). MC_grpDrawImage skips IMG_KEY.
    public static final int IMG_KEY = 0xFFF7;
    final Hashtable keyedBackings = new Hashtable(); // backing ptr -> Boolean (has transparent pixels)
    public int imagesLive, imagePixelsLive, imagePixelsPeak;
    private final int[] imgSize = new int[2];

    /** MC_grpCreateImage(out, buf, offset, len): decode, *out = image, returns 1 (0 on error). */
    void grpCreateImage() {
        int out = cpu.arg(0), buf = cpu.arg(1), off = cpu.arg(2), len = cpu.arg(3);
        byte[] data = mem.readBytes(buf + off, len);
        int[] argb = host.decodeImage(data, 0, len, imgSize);
        data = null;
        if (argb == null) {
            log("MC_grpCreateImage: cannot decode " + len + " bytes @" + hex(buf + off));
            if (out != 0) mem.write32(out, 0);
            ret(0);
            return;
        }
        int w = imgSize[0], h = imgSize[1];
        int bpl = w * 2;
        int pixels = alloc(Math.max(4, bpl * h));
        boolean keyed = false;
        for (int y = 0, k = 0; y < h; y++) {
            int a = pixels + y * bpl;
            for (int x = 0; x < w; x++, k++, a += 2) {
                int c = argb[k];
                int p;
                if ((c >>> 24) < 0x80) {
                    p = IMG_KEY;
                    keyed = true;
                } else {
                    p = ((c >> 8) & 0xF800) | ((c >> 5) & 0x07E0) | ((c >> 3) & 0x001F);
                }
                mem.write16(a, p);
            }
        }
        argb = null;
        int backing = allocStruct(new int[]{w, h, bpl, 16, pixels});
        int view = allocStruct(new int[]{backing, 0, 0, w, h});
        int fb = allocStruct(new int[]{1, view, backing, 0});
        int img = allocStruct(new int[]{backing, fb});
        if (keyed) keyedBackings.put(new Integer(backing), Boolean.TRUE);
        imagesLive++;
        imagePixelsLive += w * h;
        if (imagePixelsLive > imagePixelsPeak) imagePixelsPeak = imagePixelsLive;
        debug("MC_grpCreateImage " + w + "x" + h + (keyed ? " keyed" : "") + " -> " + hex(img));
        if (out != 0) mem.write32(out, img);
        ret(1);
    }

    /** MC_grpDestroyImage(image) (0xeb; Kroisen calls it after copying an image into its own sprite format). */
    void grpDestroyImage() {
        int img = cpu.arg(0);
        if (img == 0) return;
        int backing = mem.read32(img), fb = mem.read32(img + 4);
        if (backing == 0 || fb == 0 || mem.read32(fb) != 1) {
            log("MC_grpDestroyImage: not an image " + hex(img));
            return;
        }
        int w = mem.read32(backing), h = mem.read32(backing + 4);
        keyedBackings.remove(new Integer(backing));
        heap.free(mem.read32(fb + 4)); // view
        heap.free(fb);
        heap.free(mem.read32(backing + 16)); // pixels
        heap.free(backing);
        mem.write32(img, 0);
        heap.free(img);
        imagesLive--;
        imagePixelsLive -= w * h;
    }

    /** MC_grpDrawImage(dst, x, y, w, h, image, sx, sy, ctx) */
    void grpDrawImage() {
        int dst = cpu.arg(0), x = cpu.arg(1), y = cpu.arg(2), w = cpu.arg(3), h = cpu.arg(4);
        int img = cpu.arg(5), sx = cpu.arg(6), sy = cpu.arg(7), ctx = cpu.arg(8);
        if (w <= 0 || h <= 0 || img == 0) return;
        int backing = mem.read32(img);
        if (backing == 0) return;
        Gfx16.Surface d = surface(dst, ctx);
        Gfx16.Surface s = surfImg;
        s.width = mem.read32(backing);
        s.height = mem.read32(backing + 4);
        s.bpl = mem.read32(backing + 8);
        s.buf = mem.read32(backing + 16);
        int alpha = ctx == 0 ? 255 : mem.read32(ctx + 24);
        int key = keyedBackings.get(new Integer(backing)) != null ? IMG_KEY : -1;
        Gfx16.drawKeyed(mem, d, x + d.tx, y + d.ty, w, h, s, sx, sy, key, alpha);
    }

    int[] frame;
    public int flushCount;

    void grpFlushLcd() {
        int fb = cpu.arg(1);
        int w = physWidth(), h = physHeight();
        if (frame == null || frame.length != w * h) frame = new int[w * h];
        int view = viewOf(fb);
        int backing = mem.read32(view);
        int vx = mem.read32(view + 4), vy = mem.read32(view + 8), vw = mem.read32(view + 12), vh = mem.read32(view + 16);
        int dx = (w - vw) / 2, dy = (h - vh) / 2;
        if (vw != w || vh != h) for (int i = 0; i < frame.length; i++) frame[i] = 0xFF000000;
        Gfx16.toArgb(mem, mem.read32(backing + 16), mem.read32(backing + 8), vx, vy, Math.min(vw, w), Math.min(vh, h), frame, dy * w + dx, w);
        flushCount++;
        if (ui != null) ui.compose(frame, w, h);
        host.present(frame, w, h);
    }

    // ------------------------------------------------------------------
    // Time API (LGT specific)
    int timeValue;

    void timeApi(int idx) {
        switch (idx) {
            case 0x320: { // timeNow(component) -> handle to u32 seconds
                if (timeValue == 0) timeValue = alloc(4);
                mem.write32(timeValue, (int) (host.currentTimeMillis() / 1000));
                ret(timeValue);
                return;
            }
            case 0x321:
                ret(cpu.arg(0));
                return;
            case 0x322: {
                int t = mem.read32(cpu.arg(0));
                if (timeValue == 0) timeValue = alloc(4);
                mem.write32(timeValue, t);
                ret(timeValue);
                return;
            }
            default: { // to tm
                long ts = mem.read32(cpu.arg(0)) & 0xFFFFFFFFL;
                int out = cpu.arg(1);
                long days = ts / 86400;
                int sod = (int) (ts % 86400);
                int[] ymd = civil(days);
                mem.write32(out, sod % 60);
                mem.write32(out + 4, (sod / 60) % 60);
                mem.write32(out + 8, sod / 3600);
                mem.write32(out + 12, ymd[2]);
                mem.write32(out + 16, ymd[1] - 1);
                mem.write32(out + 20, ymd[0] - 1900);
                ret(0);
            }
        }
    }

    static int[] civil(long days) {
        days += 719468;
        long era = (days >= 0 ? days : days - 146096) / 146097;
        long doe = days - era * 146097;
        long yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365;
        long y = yoe + era * 400;
        long doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
        long mp = (5 * doy + 2) / 153;
        long d = doy - (153 * mp + 2) / 5 + 1;
        long m = mp + (mp < 10 ? 3 : -9);
        if (m <= 2) y++;
        return new int[]{(int) y, (int) m, (int) d};
    }

    // ------------------------------------------------------------------
    // Database = files (open/read/write/close/seek/stat/delete).
    // Packaged resources ("/c/...", read with mode 1) are served read-only straight from the jar and
    // are never copied into storage. Files the game creates or writes (save "kickass", ...) share one
    // in-memory buffer per name and are written to host storage as one piece when closed
    // (and on pause/exit via flushStorage).
    // Open modes: 1 = read, 2 = create, 4 = create empty (truncate), 8 = read/write.

    static final class FileBuf {
        String name;
        byte[] data;
        int len;
        boolean dirty;
        boolean readOnly; // packaged resource
    }

    static final class DbFile {
        FileBuf f;
        int pos;
        int mode;
    }

    final Hashtable dbHandles = new Hashtable();
    final Hashtable files = new Hashtable(); // name -> FileBuf (storage files only)
    int dbNextHandle = 0x100;
    public int saveCount;
    public boolean saveDebug;

    /** Storage backed file (cached), or null when it does not exist in storage. */
    FileBuf storageFile(String name) {
        FileBuf fb = (FileBuf) files.get(name);
        if (fb != null) return fb;
        byte[] ex = host.storageRead(name);
        if (ex == null) return null;
        fb = new FileBuf();
        fb.name = name;
        fb.data = new byte[Math.max(64, ex.length)];
        System.arraycopy(ex, 0, fb.data, 0, ex.length);
        fb.len = ex.length;
        files.put(name, fb);
        return fb;
    }

    static boolean resourcePathLike(String name) {
        return name.startsWith("/c/") || name.startsWith("c/");
    }

    void persist(FileBuf fb) {
        if (!fb.dirty || fb.readOnly) return;
        byte[] snap = new byte[fb.len];
        System.arraycopy(fb.data, 0, snap, 0, fb.len);
        host.storageWrite(fb.name, snap);
        fb.dirty = false;
        saveCount++;
        if (saveDebug && ui != null) ui.note("GHI#" + saveCount + " " + fb.name + " " + fb.len + "B");
        debug("db persist " + fb.name + " " + fb.len + " bytes");
    }

    public void flushStorage() {
        synchronized (files) {
            java.util.Enumeration e = files.elements();
            while (e.hasMoreElements()) persist((FileBuf) e.nextElement());
        }
    }

    void dbOpen() {
        String name = readString(cpu.arg(0));
        int mode = cpu.arg(1), type = cpu.arg(2);
        FileBuf fb = null;
        // resources: the jar copy, unless the game has written a file of that name
        if (mode == 1 && resourcePathLike(name) && files.get(name) == null) {
            byte[] packaged = resource(name);
            if (packaged != null) {
                fb = new FileBuf();
                fb.name = name;
                fb.data = packaged;
                fb.len = packaged.length;
                fb.readOnly = true;
            }
        }
        if (fb == null) {
            synchronized (files) {
                fb = storageFile(name);
                if (fb == null) {
                    byte[] packaged = resource(name);
                    if (packaged == null && mode == 1) {
                        debug("MC_dbOpen(" + name + ", " + mode + ") -> missing");
                        ret(-12);
                        return;
                    }
                    if (packaged != null && mode == 1) {
                        fb = new FileBuf();
                        fb.name = name;
                        fb.data = packaged;
                        fb.len = packaged.length;
                        fb.readOnly = true;
                    } else {
                        fb = new FileBuf();
                        fb.name = name;
                        fb.data = new byte[Math.max(64, packaged == null ? 0 : packaged.length)];
                        if (packaged != null) {
                            System.arraycopy(packaged, 0, fb.data, 0, packaged.length);
                            fb.len = packaged.length;
                        }
                        fb.dirty = true;
                        files.put(name, fb);
                    }
                }
                if (!fb.readOnly && (mode == 4 || mode == 2)) {
                    fb.len = 0;
                    fb.dirty = true;
                }
                if (fb.dirty) persist(fb); // created / truncated files exist from now on
            }
        }
        if (mode != 1 && resourcePathLike(name)) log("WARNING: write open of resource path " + name);
        if (!fb.readOnly || !resourcePathLike(name)) log("MC_dbOpen(" + name + ", mode=" + mode + ", type=" + type + ") len=" + fb.len + (fb.readOnly ? " packaged" : ""));
        DbFile f = new DbFile();
        f.f = fb;
        f.mode = mode;
        int h = dbNextHandle++;
        dbHandles.put(new Integer(h), f);
        ret(h);
    }

    DbFile db(int h) {
        return (DbFile) dbHandles.get(new Integer(h));
    }

    void dbRead() {
        DbFile f = db(cpu.arg(0));
        int buf = cpu.arg(1), len = cpu.arg(2);
        if (f == null) {
            ret(-25);
            return;
        }
        FileBuf fb = f.f;
        if (f.pos >= fb.len) {
            ret(-23);
            return;
        }
        int take = Math.min(len, fb.len - f.pos);
        if (take > 0) mem.writeBytes(buf, fb.data, f.pos, take);
        if (resHook != null && take > 0) resHook.loaded(fb.name, buf, take);
        f.pos += take;
        ret(take);
    }

    void dbWrite() {
        DbFile f = db(cpu.arg(0));
        int buf = cpu.arg(1), len = cpu.arg(2);
        if (f == null) {
            ret(-25);
            return;
        }
        FileBuf fb = f.f;
        if (fb.readOnly) {
            log("MC_dbWrite on read-only " + fb.name);
            ret(-1);
            return;
        }
        synchronized (files) {
            int end = f.pos + len;
            if (end > fb.data.length) {
                byte[] nd = new byte[Math.max(end, fb.data.length * 2)];
                System.arraycopy(fb.data, 0, nd, 0, fb.len);
                fb.data = nd;
            }
            if (f.pos > fb.len) for (int i = fb.len; i < f.pos; i++) fb.data[i] = 0;
            mem.readBytes(buf, fb.data, f.pos, len);
            f.pos = end;
            if (end > fb.len) fb.len = end;
            fb.dirty = true;
        }
        debug("MC_dbWrite(" + fb.name + ", " + len + ")");
        ret(len);
    }

    void dbClose() {
        DbFile f = (DbFile) dbHandles.remove(new Integer(cpu.arg(0)));
        if (f == null) {
            ret(-25);
            return;
        }
        synchronized (files) {
            persist(f.f);
        }
        ret(0);
    }

    void dbSeek() {
        DbFile f = db(cpu.arg(0));
        int off = cpu.arg(1), origin = cpu.arg(2);
        if (f == null) {
            ret(-25);
            return;
        }
        int base;
        if (origin == 0) base = 0;
        else if (origin == 1) base = f.pos;
        else if (origin == 2) base = f.f.len;
        else {
            ret(-1);
            return;
        }
        int pos = Math.max(0, Math.min(f.f.len, base + off));
        f.pos = pos;
        ret(pos);
    }

    /** stat(name, int[3] out, count) -> 0 and {1, 0, size} */
    void dbStat() {
        String name = readString(cpu.arg(0));
        int buf = cpu.arg(1), cap = cpu.arg(2);
        int size = -1;
        FileBuf fb = null;
        if (!(resourcePathLike(name) && files.get(name) == null)) {
            synchronized (files) {
                fb = storageFile(name);
            }
        }
        if (fb != null) size = fb.len;
        else {
            byte[] d = resource(name);
            if (d != null) size = d.length;
        }
        debug("MC_dbStat(" + name + ") -> " + size);
        if (size < 0) {
            ret(-12);
            return;
        }
        if (cap > 0) {
            mem.write32(buf, 1);
            mem.write32(buf + 4, 0);
            mem.write32(buf + 8, size);
        }
        ret(0);
    }

    void dbDelete() {
        String name = readString(cpu.arg(0));
        log("MC_dbDelete(" + name + ", " + cpu.arg(1) + ")");
        synchronized (files) {
            files.remove(name);
            host.storageDelete(name);
        }
        if (saveDebug && ui != null) ui.note("XOA " + name);
        ret(0);
    }

    void dbListRecords() {
        DbFile f = db(cpu.arg(0));
        if (f == null) {
            ret(-25);
            return;
        }
        mem.write32(cpu.arg(1), 1);
        ret(1);
    }

    void dbExists() {
        String name = readString(cpu.arg(0));
        boolean ex;
        synchronized (files) {
            ex = files.get(name) != null || (!resourcePathLike(name) && storageFile(name) != null) || resource(name) != null;
        }
        ret(ex ? 0 : -12);
    }

    // ------------------------------------------------------------------
    // Network: no service

    void netConnect() {
        int cb = cpu.arg(0), param = cpu.arg(1);
        log("MC_netConnect -> fail (offline)");
        if (game != null) game.netAttempt();
        scheduleCall(cb, new int[]{-1, param}, 1);
        ret(0);
    }

    int netReadCb, netReadParam, netWriteCb, netWriteParam;

    void netSocketConnect() { // (fd, addr, port, cb, param)
        int fd = cpu.arg(0), addr = cpu.arg(1), port = cpu.arg(2) & 0xFFFF, cb = cpu.arg(3), param = cpu.arg(4);
        log("MC_netSocketConnect(" + fd + ", " + ((addr) & 255) + "." + ((addr >> 8) & 255) + "." + ((addr >> 16) & 255) + "." + ((addr >>> 24) & 255) + ", " + (((port & 255) << 8) | (port >> 8)) + ") -> refused (offline)");
        if (game != null) game.netAttempt();
        scheduleCall(cb, new int[]{fd, -1, param}, 5);
        ret(-19);
    }

    void netSocketWrite() { // (fd, buf, len)
        log("MC_netSocketWrite " + cpu.arg(2) + " (offline)");
        ret(-1);
    }

    void netSocketRead() { // (fd, buf, len)
        ret(-1);
    }

    // ------------------------------------------------------------------
    // Media
    static final int CLIP_SIZE = 180;
    final Hashtable clips = new Hashtable(); // clip ptr -> Object sound
    final Hashtable clipVolumes = new Hashtable();
    boolean muted;

    void mdaClipCreate() {
        int p = alloc(CLIP_SIZE);
        mem.fill(p, 0, CLIP_SIZE);
        debug("MC_mdaClipCreate(" + hex(cpu.arg(0)) + ", " + hex(cpu.arg(1)) + ", " + hex(cpu.arg(2)) + ") -> " + hex(p));
        if (cpu.arg(2) != 0) clipCb.put(new Integer(p), new int[]{cpu.arg(2), 0, 0, 0}); // {callback, state, start ms, token}
        ret(p);
    }

    void mdaClipFree() {
        int p = cpu.arg(0);
        if (p == 0) {
            ret(0);
            return;
        }
        Object s = clips.remove(new Integer(p));
        if (s != null) host.soundFree(s);
        clipCb.remove(new Integer(p));
        heap.free(p);
        ret(0);
    }

    void mdaClipPutData() {
        int p = cpu.arg(0), buf = cpu.arg(1), size = cpu.arg(2);
        if (p == 0) {
            ret(-1);
            return;
        }
        byte[] data = mem.readBytes(buf, size);
        Object old = clips.remove(new Integer(p));
        if (old != null) host.soundFree(old);
        Object s = host.soundCreate(data, 0);
        if (s != null) {
            clips.put(new Integer(p), s);
            Integer vol = (Integer) clipVolumes.get(new Integer(p));
            if (vol != null) host.soundVolume(s, vol.intValue());
        }
        ret(size);
    }

    int clipVolume(int p) {
        Integer v = (Integer) clipVolumes.get(new Integer(p));
        return v == null ? 0 : v.intValue();
    }

    void mdaClipSetVolume() {
        int p = cpu.arg(0), vol = cpu.arg(1);
        clipVolumes.put(new Integer(p), new Integer(vol));
        Object s = clips.get(new Integer(p));
        if (s != null) host.soundVolume(s, vol);
        ret(0);
    }

    // The game (Xenonia) frees a clip only after the platform reports "stopped" (status 3) through the
    // callback given to MC_mdaClipCreate; without it the music/effect channel stays busy forever.
    final Hashtable clipCb = new Hashtable();
    static final int CLIP_STOPPED = 3;

    void clipNotify(final int p, final int token) {
        final int[] st = (int[]) clipCb.get(new Integer(p));
        if (st == null || st[1] != 1 || st[3] != token) return;
        st[1] = 0;
        // deliver later, but only if the game has not freed the clip in the meantime (a stale
        // "stopped" on a freed clip makes the game free its sound buffer twice -> heap corruption)
        final int cb = st[0];
        scheduleHost(new Runnable() {
            public void run() {
                if (clipCb.get(new Integer(p)) != st || st[3] != token || st[1] != 0) return;
                st[1] = 2; // notified
                cpu.call(cb, new int[]{p, CLIP_STOPPED, 0});
            }
        }, 1);
    }

    void clipWatch(final int p, final int token) {
        scheduleHost(new Runnable() {
            public void run() {
                int[] st = (int[]) clipCb.get(new Integer(p));
                Object s = clips.get(new Integer(p));
                if (st == null || st[1] != 1 || st[3] != token) return;
                if (s != null && host.soundIsPlaying(s)) st[2] = (int) host.currentTimeMillis() - 400; // still going
                if (s == null || (!host.soundIsPlaying(s) && host.currentTimeMillis() - st[2] > 400)) clipNotify(p, token);
                else clipWatch(p, token);
            }
        }, 120);
    }

    void mdaPlay() {
        int p = cpu.arg(0), repeat = cpu.arg(1);
        Object s = clips.get(new Integer(p));
        debug("MC_mdaPlay(" + hex(p) + ", " + repeat + ")");
        if (s != null) host.soundPlay(s, repeat != 0);
        int[] st = (int[]) clipCb.get(new Integer(p));
        if (st != null) {
            st[1] = 1;
            st[2] = (int) host.currentTimeMillis();
            st[3]++;
            if (repeat == 0) clipWatch(p, st[3]);
        }
        ret(0);
    }

    void mdaStop() {
        int p = cpu.arg(0);
        Object s = clips.get(new Integer(p));
        if (s != null) host.soundStop(s);
        clipNotify(p, ((int[]) (clipCb.containsKey(new Integer(p)) ? clipCb.get(new Integer(p)) : new int[4]))[3]);
        ret(0);
    }

    // ------------------------------------------------------------------
    // C standard library (import table 1)
    int randState, tmBuf;

    void stdlib(int idx) {
        switch (idx) {
            case 0x3f6: // abort-like
                log("stdlib 0x3f6(" + hex(cpu.arg(0)) + ")");
                return;
            case 0x3f7: { // sprintf
                int dst = cpu.arg(0);
                byte[] s = sprintf(cpu.arg(1), 2);
                mem.writeCString(dst, s);
                ret(s.length);
                return;
            }
            case 0x3fb: // atoi
                ret(atoi(mem.readCString(cpu.arg(0))));
                return;
            case 0x403: // rand
                randState = randState * 1103515245 + 12345;
                ret((randState >>> 16) & 0x7fff);
                return;
            case 0x404: // srand
                randState = cpu.arg(0);
                return;
            case 0x405: { // strcpy
                int d = cpu.arg(0), s = cpu.arg(1);
                int n = mem.strlen(s);
                mem.copy(d, s, n + 1);
                ret(d);
                return;
            }
            case 0x406: { // strncpy
                int d = cpu.arg(0), s = cpu.arg(1), n = cpu.arg(2);
                int len = mem.strlen(s);
                int c = Math.min(n, len);
                mem.copy(d, s, c);
                if (c < n) mem.fill(d + c, 0, n - c);
                ret(d);
                return;
            }
            case 0x407: { // strcat
                int d = cpu.arg(0), s = cpu.arg(1);
                int dl = mem.strlen(d);
                mem.copy(d + dl, s, mem.strlen(s) + 1);
                ret(d);
                return;
            }
            case 0x409: // strcmp
                ret(strncmp(cpu.arg(0), cpu.arg(1), Integer.MAX_VALUE));
                return;
            case 0x40a: // strncmp
                ret(strncmp(cpu.arg(0), cpu.arg(1), cpu.arg(2)));
                return;
            case 0x410: { // strstr
                int h = cpu.arg(0), n = cpu.arg(1);
                byte[] hs = mem.readCString(h), ns = mem.readCString(n);
                int pos = indexOf(hs, ns);
                ret(pos < 0 ? 0 : h + pos);
                return;
            }
            case 0x411: // strlen
                ret(mem.strlen(cpu.arg(0)));
                return;
            case 0x414: { // memcpy
                int d = cpu.arg(0);
                mem.copy(d, cpu.arg(1), cpu.arg(2));
                ret(d);
                return;
            }
            case 0x415: { // memmove
                int d = cpu.arg(0);
                mem.copy(d, cpu.arg(1), cpu.arg(2));
                ret(d);
                return;
            }
            case 0x418: { // memset
                int d = cpu.arg(0);
                mem.fill(d, cpu.arg(1), cpu.arg(2));
                ret(d);
                return;
            }
            case 0x41a: { // time
                int t = (int) (host.currentTimeMillis() / 1000);
                if (cpu.arg(0) != 0) mem.write32(cpu.arg(0), t);
                ret(t);
                return;
            }
            case 0x420: { // localtime(time_t*) -> static struct tm
                if (tmBuf == 0) tmBuf = alloc(48);
                java.util.Calendar c = java.util.Calendar.getInstance();
                c.setTime(new java.util.Date(mem.read32(cpu.arg(0)) * 1000L));
                int[] f = {c.get(java.util.Calendar.SECOND), c.get(java.util.Calendar.MINUTE), c.get(java.util.Calendar.HOUR_OF_DAY),
                        c.get(java.util.Calendar.DAY_OF_MONTH), c.get(java.util.Calendar.MONTH), c.get(java.util.Calendar.YEAR) - 1900,
                        c.get(java.util.Calendar.DAY_OF_WEEK) - 1, 0, 0};
                for (int i = 0; i < 9; i++) mem.write32(tmBuf + 4 * i, f[i]);
                ret(tmBuf);
                return;
            }
            default:
                throw new CpuException("unimplemented stdlib function " + hex(idx) + " args " + hex(cpu.arg(0)) + "," + hex(cpu.arg(1)) + "," + hex(cpu.arg(2)) + " lr=" + hex(cpu.r[14]));
        }
    }

    int strncmp(int a, int b, int n) {
        for (int i = 0; i < n; i++) {
            int x = mem.read8(a + i), y = mem.read8(b + i);
            if (x != y) return x - y;
            if (x == 0) return 0;
        }
        return 0;
    }

    static int indexOf(byte[] h, byte[] n) {
        outer:
        for (int i = 0; i + n.length <= h.length; i++) {
            for (int j = 0; j < n.length; j++) if (h[i + j] != n[j]) continue outer;
            return i;
        }
        return -1;
    }

    static int atoi(byte[] s) {
        int i = 0;
        while (i < s.length && (s[i] == ' ' || s[i] == '\t' || s[i] == '\n')) i++;
        boolean neg = false;
        if (i < s.length && (s[i] == '-' || s[i] == '+')) {
            neg = s[i] == '-';
            i++;
        }
        int v = 0;
        while (i < s.length && s[i] >= '0' && s[i] <= '9') {
            v = v * 10 + (s[i] - '0');
            i++;
        }
        return neg ? -v : v;
    }
}
