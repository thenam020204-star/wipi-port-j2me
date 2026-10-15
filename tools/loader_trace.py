#!/usr/bin/env python3
"""Run an ORIGINAL AND2011 resource loader in the reference emulator and record, for every store into the
destination structure, the file bytes that were read since the previous store.  This yields a measured
(file offset -> struct offset, width, value) map: the loader's real field layout, without trusting a decompiler.

    t = Tracer(binary, resdir)
    ev = t.run(lambda tr: ...)   # builds args, calls the loader through tr.emu.call(...)
    ev -> [ (dst_off, size, value, src_lo, src_hi) ... ]   src_* are file offsets (None if the store used no file byte)

The file bytes are given as `tr.watch(buf_addr, length)` and the destination as `tr.watch_dst(addr, length)`.
Library only (used by the table analyses); run `python3 tools/loader_trace.py demo <binary.mod> <res dir>` for a demo on
MONSTER_dataLoad_d.
"""
import os
import struct
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from unicorn import UC_HOOK_MEM_READ, UC_HOOK_MEM_WRITE


class Tracer:
    def __init__(self, binp, resdir):
        from and_hle import Harness
        self.h = Harness(binp, resdir).boot_dm()
        self.h.imports['MC_knlFree'] = lambda e, a: 0
        self.src = None
        self.dst = None
        self.ev = []
        self.reads = []
        self._hooks = []

    def watch_src(self, addr, n):
        self.src = (addr, n)

    def watch_dst(self, addr, n):
        self.dst = (addr, n)

    def _on_read(self, uc, access, address, size, value, _):
        if self.src and self.src[0] <= address < self.src[0] + self.src[1]:
            self.reads.append(address - self.src[0])

    def _on_write(self, uc, access, address, size, value, _):
        if self.dst and self.dst[0] <= address < self.dst[0] + self.dst[1]:
            lo = min(self.reads) if self.reads else None
            hi = max(self.reads) if self.reads else None
            self.ev.append((address - self.dst[0], size, value & ((1 << (8 * size)) - 1), lo, hi))
            self.reads = []

    def run(self, fn):
        uc = self.h.uc
        self.ev, self.reads = [], []
        hs = [uc.hook_add(UC_HOOK_MEM_READ, self._on_read), uc.hook_add(UC_HOOK_MEM_WRITE, self._on_write)]
        try:
            fn(self)
        finally:
            for k in hs:
                uc.hook_del(k)
        return self.ev


def summarize(ev):
    """Collapse events to one line per distinct destination range (last write wins), printing the source file range."""
    last = {}
    for d, sz, v, lo, hi in ev:
        last[(d, sz)] = (v, lo, hi)
    return sorted(last.items())


def stub_function(h, name, ret=0):
    """Replace an ORIGINAL internal function by `return ret` (used to skip in-place decryption while tracing)."""
    from unicorn import UC_HOOK_CODE
    from unicorn.arm_const import UC_ARM_REG_LR, UC_ARM_REG_PC, UC_ARM_REG_R0
    a = h.elf.fbyname[name][0]

    def cb(uc, addr, size, _):
        uc.reg_write(UC_ARM_REG_R0, ret)
        uc.reg_write(UC_ARM_REG_PC, uc.reg_read(UC_ARM_REG_LR))
    h.uc.hook_add(UC_HOOK_CODE, cb, begin=a, end=a)


def trace_with_resource(t, rel, loader, *args, des=False):
    """Run `loader(*args)` (name of an original function); the first resource it loads is pre-decrypted in Python when
    `des` is set (MX_desDecrypt is stubbed) and watched as the SOURCE.  Returns (events, resource_bytes, buf_addr)."""
    import hl4des
    h = t.h
    stub_function(h, 'MX_desDecrypt')
    orig = h.imports['MC_knlGetResource']
    cap = {}

    def getres(e, a):
        r = orig(e, a)
        if 'buf' not in cap:
            data = h.read(a[1], r)
            if des:
                data = hl4des.decrypt(data)
                h.write(a[1], data)
            cap['buf'], cap['data'] = a[1], data
            t.watch_src(a[1], r)
        return r
    h.imports['MC_knlGetResource'] = getres
    ev = t.run(lambda tr: h.call(loader, *args))
    h.imports['MC_knlGetResource'] = orig
    return ev, cap.get('data'), cap.get('buf')


if __name__ == '__main__':
    if sys.argv[1] == 'demo':
        t = Tracer(sys.argv[2], sys.argv[3])
        h = t.h
        mon = h.alloc(0x400)
        h.write(h.objaddr('_game') + 1, bytes([0]))
        t.watch_dst(mon, 0x400)
        for idx in (0, 1):
            h.write(mon, b'\0' * 0x400)
            ev, data, buf = trace_with_resource(t, 'E/_ESDAT_0', 'MONSTER_dataLoad_d', mon, idx, des=True)
            print('--- monster index', idx, 'file bytes', len(data), 'events', len(ev))
            for (d, sz), (v, lo, hi) in summarize(ev)[:80]:
                print('dst+%#05x w%d = %#x   src %s..%s' % (d, sz, v, lo, hi))
