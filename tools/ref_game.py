#!/usr/bin/env python3
"""Whole-game reference runner for AND2011 (verification tool only - NEVER the product runtime).

Runs the ORIGINAL binary.mod in the Unicorn ARM emulator with a small HLE of the WIPI-C imports so that a scripted key
sequence yields per-tick frame hashes and RNG/state probes (golden traces for the Java port).

    g = Game(binary.mod, resdir)
    g.start()                 # init + startClet
    g.tick(key=None)          # one timerCB (one game frame); key = WIPI key code delivered as a press event before the tick
    g.frame()                 # (w, h, RGB565 tuple) of the screen buffer after the last tick

Platform behaviour (timers, clock, property strings, file system) is HLE and therefore INFERRED; each choice is listed in
IMPL below so a trace states exactly what it depended on.
"""
import os
import struct
import sys
import zlib

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from and_hle import Harness
from and_emu import HEAP_BASE, HEAP_SIZE

CLOCK_BASE_MS = 1303268400000        # 2011-04-20T03:00:00Z (= 12:00 KST); epoch milliseconds (MX_knlTime = ms / 1000, CONFIRMED 0x2701c)

IMPL = {
    'clock': 'MC_knlCurrentTime = CLOCK_BASE_MS + virtual ms, advanced by tick_ms (default 50) per tick',
    'timers': 'MC_knlDefTimer/SetTimer only record the callback; the runner calls timerCB itself once per tick',
    'props': 'PHONEMODEL="HL4-REF" (not LG-LU9400, so the 9400 timer path is off); PHONENUMBER fixed; TIMEZONE="GMT+09:00" '
             '(format read at 0x26c28-0x26ca0: "GMT" sign hh ":" mm)',
    'fs': 'MC_fs* backed by an in-memory dict (save files); not persisted',
    'audio': 'MC_mda* no-ops (clip handles are fake integers)',
    'orientation': 'AND2011 defaults _menuOptions+4 = 1 (OptionInit 0xbd624) = 320x240 landscape game rotated onto the 240x400 '
                   'frame (touch_setUiChange 0x35d50, nTouchRotate=1).  The port targets 240x320 portrait (approved parameter): '
                   'the reference forces _menuOptions+4 = 0 right after OptionInit returns (portrait path 0x35e18, the legacy '
                   'layout), so game area = 240x320 + 80 rows of virtual pad that are never part of a hash.',
    'logo_h': 'until TitleFrame_Initialize the stock game area is 240x400 (Logo/notice screens laid out for 400 rows); the reference pokes '
              '_lcdG+0x58 = 320 after startClet and re-runs Touch_Stand_Initialize + UM_SetMenuXY so the J2ME target (240x320 throughout) sees the same layout',
    'prims': 'MC_grpFillRect(f,x,y,w,h,pgc) fills w x h; MC_grpDrawRect(f,x,y,w,h,pgc) outlines the INCLUSIVE extent x..x+w, y..y+h '
             '(the game passes w-1,h-1 and pairs it with an inner fill of w-2, so the pair is consistent); MC_grpDrawLine = Bresenham, '
             'both ends inclusive; foreground pixel = pgc context index 1; clipping to the frame buffer only',
    'touch': 'only touch_drawVirtualPad is stubbed (it draws below the 240x320 game area).  The TOUCH sprite layer (TOUCH/CIF skins) '
             'draws dialog/menu backgrounds inside the game area and stays active; pointer input is simply never generated',
    'consent': 'Load_smsAgree is stubbed to "agreed" (_gameMenu[0x21]=1): the real answer path waits for the carrier server forever offline; the port stores the answer and continues',
    'frame': 'platform frame 240x400; hashes cover only the game area (_lcdG+0x54 x +0x58 of the offscreen buffer _lcdG+0x10)',
    'heap': 'MC_knlAlloc/Calloc zero-filled; MC_knlFree recycles same-size blocks (pointer values are not observable)',
}

PROPS = {'PHONEMODEL': b'HL4-REF', 'PHONENUMBER': b'01000000000', 'MIN': b'01000000000', 'TIMEZONE': b'GMT+09:00'}


class Game(Harness):
    def __init__(self, binp, resdir, tick_ms=50, raise_on_missing=False, portrait=True, no_touch=False, consent=1):
        super().__init__(binp, resdir)
        self.tick_ms = tick_ms
        self.now = CLOCK_BASE_MS
        self.raise_on_missing = raise_on_missing
        self.portrait = portrait
        self.consent = consent
        self.no_touch = no_touch
        self.stubbed = []
        self.rng_calls = 0
        self.blocks = {}
        self.freelist = {}
        self.missing = {}
        self.files = {}
        self.fds = {}
        self.timers = {}
        self.ticks = 0
        self.flushes = 0
        self.ctx = {}
        self.last_flush = None
        self._imports()
        self._softfloat()

    # --- imports ---------------------------------------------------------------------------------------
    def _imports(self):
        im = self.imports

        def prop(e, a):
            k = e.cstr(a[0]).decode('latin-1')
            v = PROPS.get(k)
            if v is None:
                self.missing.setdefault('prop:' + k, 0)
                self.missing['prop:' + k] += 1
                return -1
            if a[1] and a[2]:
                e.write(a[1], (v + b'\0')[:a[2]])
            return len(v)
        im['MC_knlGetSystemProperty'] = prop

        def cmp(a, b):
            return (a > b) - (a < b)
        im['dlib_strcmp'] = lambda e, a: cmp(e.cstr(a[0]), e.cstr(a[1])) & 0xffffffff
        im['dlib_strncmp'] = lambda e, a: cmp(e.cstr(a[0])[:a[2]], e.cstr(a[1])[:a[2]]) & 0xffffffff
        im['dlib_memcmp'] = lambda e, a: cmp(e.read(a[0], a[2]), e.read(a[1], a[2])) & 0xffffffff
        im['dlib_strlen'] = lambda e, a: len(e.cstr(a[0]))

        def strstr(e, a):
            i = e.cstr(a[0]).find(e.cstr(a[1]))
            return 0 if i < 0 else a[0] + i
        im['dlib_strstr'] = strstr
        im['MC_knlCurrentTime'] = lambda e, a: (self.now & 0xffffffff, (self.now >> 32) & 0xffffffff)
        im['MC_knlDefTimer'] = lambda e, a: self._deftimer(a)
        im['MC_knlSetTimer'] = lambda e, a: 0
        im['MC_knlUnsetTimer'] = lambda e, a: 0
        im['MC_miscBackLight'] = lambda e, a: 0
        im['MC_mdaSetMuteState'] = lambda e, a: 0
        for n in ('MC_mdaClipAllocPlayer', 'MC_mdaClipCreate', 'MC_mdaClipFree', 'MC_mdaClipPutData', 'MC_mdaClipSetVolume',
                  'MC_mdaClipClearData', 'MC_mdaPlay', 'MC_mdaStop', 'MC_mdaVibrator'):
            im[n] = lambda e, a: 1
        im['MC_knlGetFreeMemory'] = lambda e, a: 0x400000
        im['MC_knlGetTotalMemory'] = lambda e, a: 0x800000
        im['dlet_set_property'] = lambda e, a: 0
        im['wipic_event_handler'] = lambda e, a: 0
        im['MC_grpFlushLcd'] = self._flush
        # WIPI-C 2D primitives (platform behaviour, INFERRED - see IMPL['prims'])
        im['MC_grpSetContext'] = self._setctx
        im['MC_grpFillRect'] = self._fillrect
        im['MC_grpDrawRect'] = self._drawrect
        im['MC_grpDrawLine'] = self._drawline
        im['MC_knlExit'] = lambda e, a: self._die('MC_knlExit')
        im['MC_knlGetCurProgramID'] = lambda e, a: 0
        im['LGTC_knlGetProgramExecName'] = lambda e, a: 0
        # file system: logical files live in a dict, handles are small ints
        im['MC_fsFileAttribute'] = lambda e, a: self._fsattr(a)
        im['MC_fsOpen'] = lambda e, a: self._fsopen(a)
        im['MC_fsClose'] = lambda e, a: 0
        im['MC_fsRead'] = lambda e, a: self._fsread(a)
        im['MC_fsWrite'] = lambda e, a: self._fswrite(a)
        im['MC_fsSeek'] = lambda e, a: self._fsseek(a)
        im['MC_fsRemove'] = lambda e, a: 0
        self._heap_imports()
        self._fallback_missing()


    # --- ARM EABI soft-float helpers (libgcc): IEEE-754 binary32/binary64, round-to-nearest-even -------------
    # Doubles travel in (r0=low, r1=high) / (r2, r3); floats in r0.  Python floats are binary64; binary32 results are rounded through
    # struct 'f' (+,-,*,/ of two binary32 values computed in binary64 and rounded once is exact: 53 >= 2*24+2).
    def _softfloat(self):
        import math
        D = lambda lo, hi: struct.unpack('<d', struct.pack('<II', lo & 0xffffffff, hi & 0xffffffff))[0]
        F = lambda w: struct.unpack('<f', struct.pack('<I', w & 0xffffffff))[0]

        def pd(v):
            lo, hi = struct.unpack('<II', struct.pack('<d', v))
            return (lo, hi)

        def pf(v):
            try:
                return struct.unpack('<I', struct.pack('<f', v))[0]
            except OverflowError:
                return 0xff800000 if v < 0 else 0x7f800000

        def div(a, b):
            if b == 0.0:
                if a == 0.0 or a != a:
                    return math.nan
                neg = (math.copysign(1.0, a) < 0) != (math.copysign(1.0, b) < 0)
                return -math.inf if neg else math.inf
            return a / b

        def s32(v):
            return v - (1 << 32) if v & 0x80000000 else v

        def d2iz(v):
            if v != v:
                return 0
            v = int(v) if abs(v) != math.inf else (1 << 40) * (1 if v > 0 else -1)
            return max(-(1 << 31), min((1 << 31) - 1, v)) & 0xffffffff
        im = self.imports
        im['__aeabi_i2d'] = lambda e, a: pd(float(s32(a[0])))
        im['__aeabi_i2f'] = lambda e, a: pf(float(s32(a[0])))
        im['__aeabi_f2d'] = lambda e, a: pd(F(a[0]))
        im['__aeabi_d2f'] = lambda e, a: pf(D(a[0], a[1]))
        im['__aeabi_d2iz'] = lambda e, a: d2iz(D(a[0], a[1]))
        im['__aeabi_f2iz'] = lambda e, a: d2iz(F(a[0]))
        im['__aeabi_dadd'] = lambda e, a: pd(D(a[0], a[1]) + D(a[2], a[3]))
        im['__aeabi_dsub'] = lambda e, a: pd(D(a[0], a[1]) - D(a[2], a[3]))
        im['__aeabi_dmul'] = lambda e, a: pd(D(a[0], a[1]) * D(a[2], a[3]))
        im['__aeabi_ddiv'] = lambda e, a: pd(div(D(a[0], a[1]), D(a[2], a[3])))
        im['__aeabi_fadd'] = lambda e, a: pf(F(a[0]) + F(a[1]))
        im['__aeabi_fsub'] = lambda e, a: pf(F(a[0]) - F(a[1]))
        im['__aeabi_fmul'] = lambda e, a: pf(F(a[0]) * F(a[1]))
        im['__aeabi_fdiv'] = lambda e, a: pf(div(F(a[0]), F(a[1])))
        for n, f in (('eq', lambda x, y: x == y), ('lt', lambda x, y: x < y), ('le', lambda x, y: x <= y),
                     ('ge', lambda x, y: x >= y), ('gt', lambda x, y: x > y)):
            im['__aeabi_dcmp' + n] = (lambda f: lambda e, a: 1 if f(D(a[0], a[1]), D(a[2], a[3])) else 0)(f)
            im['__aeabi_fcmp' + n] = (lambda f: lambda e, a: 1 if f(F(a[0]), F(a[1])) else 0)(f)

    def _heap_imports(self):
        def alloc(e, a):
            n = (a[0] + 7) & ~7
            fl = self.freelist.get(n)
            if fl:
                p = fl.pop()
                e.write(p, b'\0' * n)
            else:
                if e.heap_ptr + n >= HEAP_BASE + HEAP_SIZE:
                    raise MemoryError('reference heap exhausted (%d bytes live)' % sum(self.blocks.values()))
                p = e.alloc(n)
            self.blocks[p] = n
            return p

        def free(e, a):
            n = self.blocks.pop(a[0], None)
            if n is not None:
                self.freelist.setdefault(n, []).append(a[0])
            return 0
        self.imports['MC_knlCalloc'] = alloc
        self.imports['MC_knlAlloc'] = alloc
        self.imports['MC_knlFree'] = free

    def _fallback_missing(self):
        """Any import without an HLE entry returns 0 and is counted (so a trace can say what it did not model)."""
        for name in set(self.elf.plt.values()):
            if name not in self.imports:
                def mk(n):
                    def f(e, a):
                        if self.raise_on_missing:
                            raise RuntimeError('unmodelled import ' + n)
                        self.missing[n] = self.missing.get(n, 0) + 1
                        return 0
                    return f
                self.imports[name] = mk(name)

    # --- WIPI-C primitives -------------------------------------------------------------------------------
    # MC_grpSetContext(pgc, idx, value): idx 1 = foreground pixel, 2 = background pixel (the game passes exactly these, 0x50ce4/0x50cb0).
    # The platform clips only to the frame buffer; the game's own clip rectangle (OutR) is NOT known to the platform.
    def _setctx(self, e, a):
        self.ctx.setdefault(a[0], {})[a[1]] = a[2] & 0xffff
        return 0

    def _fg(self, pgc):
        return self.ctx.get(pgc, {}).get(1, 0)

    def _fillrect(self, e, a):
        frame, x, y, w, h = a[0], a[1], a[2], a[3], a[4]
        pgc = self.arg(5)
        s32 = lambda v: v - (1 << 32) if v & 0x80000000 else v
        x, y, w, h = s32(x), s32(y), s32(w), s32(h)
        self.fill(frame, x, y, x + w, y + h, self._fg(pgc))
        return 0

    def fill(self, frame, x0, y0, x1, y1, px):
        ptr, fw, fh = self.frames.get(frame, self.frames[self.screen])
        x0, y0, x1, y1 = max(x0, 0), max(y0, 0), min(x1, fw), min(y1, fh)
        if x1 <= x0 or y1 <= y0:
            return
        row = struct.pack('<H', px) * (x1 - x0)
        for yy in range(y0, y1):
            self.write(ptr + (yy * fw + x0) * 2, row)

    def _drawrect(self, e, a):
        frame, x, y, w, h = a[0], a[1], a[2], a[3], a[4]
        pgc = self.arg(5)
        s32 = lambda v: v - (1 << 32) if v & 0x80000000 else v
        x, y, w, h = s32(x), s32(y), s32(w), s32(h)
        if w < 0 or h < 0:
            return 0
        px = self._fg(pgc)
        self.fill(frame, x, y, x + w + 1, y + 1, px)          # top
        self.fill(frame, x, y + h, x + w + 1, y + h + 1, px)  # bottom
        self.fill(frame, x, y, x + 1, y + h + 1, px)          # left
        self.fill(frame, x + w, y, x + w + 1, y + h + 1, px)  # right
        return 0

    def _drawline(self, e, a):
        frame, x0, y0, x1, y1 = a[0], a[1], a[2], a[3], a[4]
        pgc = self.arg(5)
        s32 = lambda v: v - (1 << 32) if v & 0x80000000 else v
        x0, y0, x1, y1 = s32(x0), s32(y0), s32(x1), s32(y1)
        px = self._fg(pgc)
        dx, dy = abs(x1 - x0), -abs(y1 - y0)
        sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
        err = dx + dy
        while True:
            self.fill(frame, x0, y0, x0 + 1, y0 + 1, px)
            if x0 == x1 and y0 == y1:
                break
            e2 = 2 * err
            if e2 >= dy:
                err += dy; x0 += sx
            if e2 <= dx:
                err += dx; y0 += sy
        return 0

    def _flush(self, e, a):
        self.last_flush = list(a[:6])
        self.flushes += 1
        return 0

    def _die(self, why):
        raise RuntimeError('game requested exit: ' + why)

    def _deftimer(self, a):
        self.timers[a[0]] = a[1]
        return 0

    def _fsattr(self, a):
        name = self.cstr(a[0]).decode('latin-1')
        return 0 if name in self.files else -1

    def _fsopen(self, a):
        name = self.cstr(a[0]).decode('latin-1')
        fd = len(self.fds) + 3
        self.fds[fd] = [name, 0]
        self.files.setdefault(name, bytearray())
        return fd

    def _fsread(self, a):
        f = self.fds.get(a[0])
        if not f:
            return -1
        d = bytes(self.files[f[0]][f[1]:f[1] + a[2]])
        self.write(a[1], d)
        f[1] += len(d)
        return len(d)

    def _fswrite(self, a):
        f = self.fds.get(a[0])
        if not f:
            return -1
        buf = self.files[f[0]]
        d = self.read(a[1], a[2])
        buf[f[1]:f[1] + len(d)] = d
        f[1] += len(d)
        return len(d)

    def _fsseek(self, a):
        f = self.fds.get(a[0])
        if f:
            f[1] = a[1]
        return 0

    # --- lifecycle -------------------------------------------------------------------------------------
    def start(self, logo_h=320):
        """init + startClet, with the reference configuration of IMPL (portrait hook, RNG counter, logo-phase game height)."""
        from unicorn import UC_HOOK_CODE
        a, sz, th = self.elf.fbyname['OptionInit']
        # OptionInit is a leaf: its final `bx lr` is the last 4-byte instruction before the literal pool
        ret = self._leaf_ret('OptionInit')
        opt = self.objaddr('_menuOptions')
        rng = self.elf.fbyname['UM_Random'][0]

        def portrait(uc, addr, size, _):
            if self.portrait:
                self.write(opt + 4, b'\x00')

        def count(uc, addr, size, _):
            self.rng_calls += 1
        self.uc.hook_add(UC_HOOK_CODE, portrait, begin=ret, end=ret)
        self.uc.hook_add(UC_HOOK_CODE, count, begin=rng, end=rng)
        self.stub_touch_draw(only=('touch_drawVirtualPad',) if not self.no_touch else None)
        if self.consent is not None:
            self.stub_consent()
        self.call('init')
        self.call('startClet')
        if logo_h:
            self.write(self.objaddr('_lcdG') + 0x58, struct.pack('<i', logo_h))
            self.write(self.objaddr('m_TouchDirectKeySize'), struct.pack('<i', 400 - logo_h))   # 80 (TitleFrame_Initialize sets it only at the title)
            self.call('Touch_Stand_Initialize')     # centre/size of the touch skin layer and menu positions follow the poked height
            self.call('UM_SetMenuXY')
        return self

    def stub_consent(self):
        """Load_smsAgree (@0xceda0) is replaced by 'answer already stored': _gameMenu[0x21] = consent (1 = agreed), [0x22] = 0.
        The real answer path (NpcUi 5/10 'server response pending...') needs the carrier server and never completes offline."""
        from unicorn import UC_HOOK_CODE
        from unicorn.arm_const import UC_ARM_REG_LR, UC_ARM_REG_PC
        a = self.elf.fbyname['Load_smsAgree'][0]
        gm = self.objaddr('_gameMenu')

        def cb(uc, addr, size, _):
            self.write(gm + 0x21, bytes([self.consent & 0xff, 0]))
            uc.reg_write(UC_ARM_REG_PC, uc.reg_read(UC_ARM_REG_LR))
        self.uc.hook_add(UC_HOOK_CODE, cb, begin=a, end=a)

    def stub_touch_draw(self, only=None):
        """Neutralise touch drawing: `only` = exact names (default config: the virtual pad, which is drawn below the game area),
        or None = every Touch_draw*/touch_draw*/draw_local routine (no_touch=True, diagnostic only: the TOUCH sprite layer also draws
        the dialog/menu skins of the AND2011 UI, so the product keeps it).  Input-state bookkeeping keeps running."""
        import re
        from unicorn import UC_HOOK_CODE
        from unicorn.arm_const import UC_ARM_REG_LR, UC_ARM_REG_PC, UC_ARM_REG_R0
        rx = re.compile(r'^(Touch_draw|touch_draw|draw_local)')

        def ret(uc, addr, size, _):
            uc.reg_write(UC_ARM_REG_R0, 0)
            uc.reg_write(UC_ARM_REG_PC, uc.reg_read(UC_ARM_REG_LR))
        for n, (a, sz, th) in self.elf.fbyname.items():
            if (n in only) if only is not None else rx.match(n):
                self.uc.hook_add(UC_HOOK_CODE, ret, begin=a, end=a)
                self.stubbed.append(n)

    def _leaf_ret(self, name):
        """address of the last `bx lr` of a leaf function (found by decoding, not guessed)."""
        a, sz, th = self.elf.fbyname[name]
        code = self.read(a, sz)
        last = None
        for i in range(0, sz - 3, 4):
            if struct.unpack_from('<I', code, i)[0] == 0xe12fff1e:
                last = a + i
        assert last is not None
        return last

    def game_area(self):
        """(w, h, pixels) of the game area: the offscreen buffer _lcdG+0x10 cropped to _lcdG+0x54 x _lcdG+0x58."""
        L = self.objaddr('_lcdG')
        hnd = struct.unpack('<I', self.read(L + 0x10, 4))[0]
        w, h = struct.unpack('<ii', self.read(L + 0x54, 8))
        ptr, fw, fh = self.frames[hnd]
        rows = [self.read(ptr + y * fw * 2, w * 2) for y in range(h)]
        return w, h, struct.unpack('<%dH' % (w * h), b''.join(rows))

    def back_area(self):
        """(w, h, pixels) of the prepared background buffer _lcdG+0x14 (what DM_CopyOffScreen copies into the game buffer)"""
        L = self.objaddr('_lcdG')
        hnd = struct.unpack('<I', self.read(L + 0x14, 4))[0]
        w, h = struct.unpack('<ii', self.read(L + 0x54, 8))
        ptr, fw, fh = self.frames[hnd]
        rows = [self.read(ptr + y * fw * 2, w * 2) for y in range(h)]
        return w, h, struct.unpack('<%dH' % (w * h), b''.join(rows))

    # --- function coverage (slow: one Python callback per basic block) ----------------------------------
    def enable_cover(self):
        from unicorn import UC_HOOK_BLOCK
        self.entries = {a: n for n, (a, sz, th) in self.elf.fbyname.items()}
        self.cover = {}

        def blk(uc, addr, size, _):
            n = self.entries.get(addr)
            if n is not None:
                self.cover[n] = self.cover.get(n, 0) + 1
        self.uc.hook_add(UC_HOOK_BLOCK, blk)

    def event(self, typ, p1=0, p2=0):
        return self.call('handleCletEvent', typ, p1 & 0xffffffff, p2 & 0xffffffff)

    def key(self, code):
        """Deliver a press then a release (WIPI events 0x1f6/0x1f7) BEFORE the next tick, like a real fast tap
        would after one frame.  The game keeps only the last event per tick (CONFIRMED, docs/phaseB_flows.md section 4)."""
        self.event(0x1f6, code)
        self.event(0x1f7, code)

    def tick(self, press=None, release=None):
        if press is not None:
            self.event(0x1f6, press)
        if release is not None:
            self.event(0x1f7, release)
        self.now += self.tick_ms
        self.call('timerCB')
        self.ticks += 1

    def frame(self):
        return self.game_area()

    def png(self, path):
        w, h, px = self.frame()
        raw = bytearray()
        for y in range(h):
            raw.append(0)
            for x in range(w):
                v = px[y * w + x]
                raw += bytes((((v >> 11) & 31) * 255 // 31, ((v >> 5) & 63) * 255 // 63, (v & 31) * 255 // 31))

        def chunk(t, d):
            c = struct.pack('>I', len(d)) + t + d
            return c + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
        open(path, 'wb').write(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 2, 0, 0, 0))
                              + chunk(b'IDAT', zlib.compress(bytes(raw), 6)) + chunk(b'IEND', b''))

    def fhash(self):
        w, h, px = self.frame()
        return zlib.crc32(struct.pack('<%dH' % len(px), *px)) & 0xffffffff


if __name__ == '__main__':
    binp, resdir = sys.argv[1], sys.argv[2]
    g = Game(binp, resdir).start()
    n = int(sys.argv[3]) if len(sys.argv) > 3 else 30
    for i in range(n):
        g.tick()
        print(i, '%08x' % g.fhash(), 'icount?')
    print('missing imports:', g.missing)


def parse_script(tokens):
    """Scenario tokens 'tick:key' (tap = press+release in that tick), 'tick:pKEY' (press only), 'tick:rKEY' (release only) -> {tick: [press, release]}"""
    sc = {}
    for kv in tokens:
        t, k = kv.split(':')
        e = sc.setdefault(int(t), [None, None])
        if k[:1] == 'p':
            e[0] = int(k[1:], 0)
        elif k[:1] == 'r':
            e[1] = int(k[1:], 0)
        else:
            e[0] = e[1] = int(k, 0)
    return sc


def tick_script(g, sc, i):
    e = sc.get(i)
    if e is None:
        g.tick()
    else:
        g.tick(press=e[0], release=e[1])
