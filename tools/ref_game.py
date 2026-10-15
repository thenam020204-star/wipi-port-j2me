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

IMPL = {
    'clock': 'MC_knlCurrentTime = virtual ms, advanced by tick_ms (default 50) per tick',
    'timers': 'MC_knlDefTimer/SetTimer only record the callback; the runner calls timerCB itself once per tick',
    'props': 'PHONEMODEL="LG-LU9400"? no: "HL4-REF"; PHONENUMBER fixed 01000000000',
    'fs': 'MC_fs* backed by an in-memory dict (save files); not persisted',
    'audio': 'MC_mda* no-ops (clip handles are fake integers)',
}

PROPS = {'PHONEMODEL': b'HL4-REF', 'PHONENUMBER': b'01000000000', 'MIN': b'01000000000'}


class Game(Harness):
    def __init__(self, binp, resdir, tick_ms=50, strict=True):
        super().__init__(binp, resdir)
        self.tick_ms = tick_ms
        self.now = 1000
        self.strict = strict
        self.missing = {}
        self.files = {}
        self.fds = {}
        self.timers = {}
        self.ticks = 0
        self.flushes = 0
        self.last_flush = None
        self._imports()

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
        if self.strict:
            self._fallback_missing()

    def _fallback_missing(self):
        """Any import without an HLE entry returns 0 and is counted (so a trace can say what it did not model)."""
        for name in set(self.elf.plt.values()):
            if name not in self.imports:
                def mk(n):
                    def f(e, a):
                        self.missing[n] = self.missing.get(n, 0) + 1
                        return 0
                    return f
                self.imports[name] = mk(name)

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
    def start(self, game_h=320):
        """init + startClet.  game_h: height of the game area.  The stock binary lays the Logo screens out for the full 400-row
        frame and only switches to 240x320 (+ 80-row virtual pad) in TitleFrame_Initialize (0xc8bfc); the J2ME target is 240x320
        throughout, so the reference is poked to the same height (_lcdG+0x58) before the first tick."""
        self.call('init')
        self.call('startClet')
        if game_h:
            self.write(self.objaddr('_lcdG') + 0x58, struct.pack('<i', game_h))
        return self

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
        """Frame handed to the last MC_grpFlushLcd (what the player would see); falls back to the screen buffer."""
        hnd = self.last_flush[1] if self.last_flush and self.last_flush[1] in self.frames else self.screen
        return self.frame_pixels(hnd)

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
