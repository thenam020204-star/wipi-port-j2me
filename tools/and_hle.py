#!/usr/bin/env python3
"""Shared reference-harness HLE for AND2011 (test tooling only, never shipped).

Builds on and_emu.Emu + std_imports and adds:
- frame buffers (MC_grpGetScreenFrameBuffer / MC_grpCreateOffScreenFrameBuffer / wipic_get_frame_*)
- resources from the original jar res/ directory (MC_knlGetResourceID / MC_knlGetResource)
- WIPI-C pixel conversion and no-op context/drawing calls needed during DM_Initialize
- boot_dm(): runs the ORIGINAL DM_Initialize so every DM_* global/function pointer is real

HLE semantics marked INFERRED are platform behaviour (WIPI-C), not game logic; they only have
to be good enough for the routines under test, and each test states which ones it depends on.
"""
import os
import struct
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from and_emu import Emu, std_imports

M_E_NOENT = -12      # value UM_LoadRes treats as "not found" (cmn r0,#0xc @0xca220)


class Harness(Emu):
    def __init__(self, binp, resdir, fb_w=240, fb_h=400):
        super().__init__(binp)
        std_imports(self)
        self.resdir = resdir
        self.frames = {}
        self.res_ids = {}
        self.screen = self.new_frame(fb_w, fb_h)
        self.fb = self.frames[self.screen][0]
        im = self.imports
        im['MC_grpGetScreenFrameBuffer'] = lambda e, a: self.screen
        im['MC_grpCreateOffScreenFrameBuffer'] = lambda e, a: self.new_frame(a[0], a[1])
        im['MC_grpDestroyOffScreenFrameBuffer'] = lambda e, a: 0
        im['MC_grpInitContext'] = lambda e, a: 0
        im['MC_grpGetDisplayInfo'] = lambda e, a: 0
        # INFERRED WIPI-C semantics for an RGB565 frame
        im['MC_grpGetPixelFromRGB'] = lambda e, a: ((a[0] >> 3) << 11) | ((a[1] >> 2) << 5) | (a[2] >> 3)
        im['MC_grpGetRGBFromPixel'] = lambda e, a: (((a[0] >> 11) & 31) << 19) | (((a[0] >> 5) & 63) << 10) | ((a[0] & 31) << 3)
        for n in ('MC_grpSetContext', 'MC_grpFillRect', 'MC_grpFlushLcd', 'MC_grpDrawRect', 'MC_grpDrawLine'):
            im[n] = lambda e, a: 0
        im['wipic_get_frame_pointer'] = lambda e, a: self.frames.get(a[0], self.frames[self.screen])[0]
        im['wipic_get_frame_width'] = lambda e, a: self.frames.get(a[0], self.frames[self.screen])[1]
        im['wipic_get_frame_height'] = lambda e, a: self.frames.get(a[0], self.frames[self.screen])[2]
        im['MC_knlGetResourceID'] = self._res_id
        im['MC_knlGetResource'] = self._res_get
        im['MC_knlPrintk'] = lambda e, a: 0
        im['MC_knlSprintk'] = self._sprintk

    # --- frames ---
    def new_frame(self, w, h):
        ptr = self.alloc(w * h * 2)
        hnd = self.alloc(0x40)
        self.frames[hnd] = (ptr, w, h)
        return hnd

    def frame_pixels(self, hnd):
        ptr, w, h = self.frames[hnd]
        return w, h, struct.unpack('<%dH' % (w * h), self.read(ptr, w * h * 2))

    # --- resources ---
    def _res_id(self, e, a):
        # the game passes 'res/<path>' (tempLocalGetExactFilename adds the jar's res/ prefix, CONFIRMED)
        name = self.cstr(a[0]).decode('latin-1').lstrip('/')
        if name.startswith('res/'):
            name = name[4:]
        p = os.path.join(self.resdir, name)
        if not os.path.isfile(p):
            return M_E_NOENT
        rid = len(self.res_ids) + 1
        data = open(p, 'rb').read()
        self.res_ids[rid] = data
        if a[1]:
            self.write(a[1], struct.pack('<i', len(data)))   # size out-parameter
        return rid

    def _res_get(self, e, a):
        data = self.res_ids.get(a[0])
        if data is None:
            return M_E_NOENT
        n = min(a[2], len(data))
        self.write(a[1], data[:n])
        return n

    def _sprintk(self, e, a):
        """%d %s %c %x %02d %04d %03d only (enough for path building)."""
        import re
        fmt = self.cstr(a[1]).decode('latin-1')
        args = [self.arg(i) for i in range(2, 12)]
        out, k = '', 0
        for m in re.finditer(r'%(0?\d*)([dsxcu%])|[^%]+', fmt):
            if not m.group(0).startswith('%'):
                out += m.group(0); continue
            spec, conv = m.group(1), m.group(2)
            if conv == '%':
                out += '%'; continue
            v = args[k]; k += 1
            if conv == 's':
                out += self.cstr(v).decode('latin-1')
            elif conv == 'c':
                out += chr(v & 0xff)
            elif conv == 'x':
                out += ('%' + spec + 'x') % v
            else:
                sv = v - (1 << 32) if v & 0x80000000 else v
                out += ('%' + spec + 'd') % sv
        b = out.encode('latin-1') + b'\0'
        self.write(a[0], b)
        return len(b) - 1

    # --- boot pieces ---
    def boot_dm(self):
        self.call('DM_Initialize')
        obj = {o[2]: o[0] for o in self.elf.objs}
        self.obj = obj
        return self

    def objaddr(self, name):
        return {o[2]: o[0] for o in self.elf.objs}[name]
