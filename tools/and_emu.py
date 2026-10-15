#!/usr/bin/env python3
"""Reference-only ARM emulator for AND2011 binary.mod (Unicorn). NOT part of the product.

Loads every allocated section at its ELF vaddr (load base 0, so R_ARM_RELATIVE
needs no adjustment), zero-fills .bss, gives a heap/stack, and lets Python handle
PLT imports by name. Used to execute original routines (DES, decoders, RNG...)
to produce golden outputs for the Java re-implementation.

    emu = Emu(path)
    emu.imports['dlib_memcpy'] = lambda emu, a: ...   # return value in r0
    r0 = emu.call('startDes', 0, nblocks, buf_addr)
"""
import os
import struct
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from unicorn import Uc, UC_ARCH_ARM, UC_MODE_ARM, UC_HOOK_CODE, UcError
from unicorn.arm_const import (UC_ARM_REG_R0, UC_ARM_REG_R1, UC_ARM_REG_R2, UC_ARM_REG_R3,
                               UC_ARM_REG_SP, UC_ARM_REG_LR, UC_ARM_REG_PC)
from andelf import AndElf

STACK_TOP = 0x08000000
STACK_SIZE = 0x00100000
HEAP_BASE = 0x10000000
HEAP_SIZE = 0x04000000
RET_MAGIC = 0x0F000000   # return address sentinel (mapped, executes nothing)
REGS = [UC_ARM_REG_R0, UC_ARM_REG_R1, UC_ARM_REG_R2, UC_ARM_REG_R3]


def _page(x):
    return x & ~0xfff


class Emu:
    def __init__(self, path):
        self.elf = AndElf(path)
        e = self.elf
        self.uc = Uc(UC_ARCH_ARM, UC_MODE_ARM)
        lo = min(s['sh_addr'] for s in e.secs if s['sh_addr'])
        hi = max(s['sh_addr'] + s['sh_size'] for s in e.secs if s['sh_addr'])
        self.uc.mem_map(_page(lo), _page(hi + 0xfff) - _page(lo) + 0x1000)
        for s in e.secs:
            if s['sh_addr'] and s['sh_type'] != 'SHT_NOBITS':
                self.uc.mem_write(s['sh_addr'], s.data())
        self.uc.mem_map(STACK_TOP - STACK_SIZE, STACK_SIZE)
        self.uc.mem_map(HEAP_BASE, HEAP_SIZE)
        self.uc.mem_map(RET_MAGIC, 0x1000)
        self.heap_ptr = HEAP_BASE
        self.imports = {}           # name -> fn(emu, args) -> int
        self.calls = []             # trace of import calls (name, args)
        self.icount = 0
        self.uc.hook_add(UC_HOOK_CODE, self._hook_plt, begin=min(e.plt), end=max(e.plt) + 12)

    # --- memory helpers ---
    def alloc(self, n, align=8):
        a = (self.heap_ptr + align - 1) & ~(align - 1)
        self.heap_ptr = a + n
        self.uc.mem_write(a, b'\0' * n)
        return a

    def read(self, a, n):
        return bytes(self.uc.mem_read(a, n))

    def write(self, a, b):
        self.uc.mem_write(a, bytes(b))

    def u32(self, a):
        return struct.unpack('<I', self.read(a, 4))[0]

    def cstr(self, a, n=4096):
        out = b''
        while len(out) < n:                 # read in small chunks so we never run past a mapping
            chunk = self.read(a + len(out), 16)
            z = chunk.find(b'\0')
            if z >= 0:
                return out + chunk[:z]
            out += chunk
        return out

    def arg(self, i):
        if i < 4:
            return self.uc.reg_read(REGS[i])
        return self.u32(self.uc.reg_read(UC_ARM_REG_SP) + 4 * (i - 4))

    # --- imports ---
    def _hook_plt(self, uc, addr, size, _):
        name = self.elf.plt.get(addr)
        if name is None:
            return
        fn = self.imports.get(name)
        if fn is None:
            raise RuntimeError('unhandled import %s @%#x (lr=%#x)' % (name, addr, uc.reg_read(UC_ARM_REG_LR)))
        self.calls.append(name)
        r = fn(self, [self.arg(i) for i in range(6)])
        if isinstance(r, tuple):                       # multi-register returns (r0, r1[, r2, r3])
            for reg, v in zip(REGS, r):
                uc.reg_write(reg, v & 0xffffffff)
        else:
            uc.reg_write(UC_ARM_REG_R0, (r or 0) & 0xffffffff)
        uc.reg_write(UC_ARM_REG_PC, uc.reg_read(UC_ARM_REG_LR))

    # --- call ---
    def call(self, fname, *args, limit=200_000_000):
        a, sz, thumb = self.elf.fbyname[fname]
        sp = STACK_TOP - 0x100
        extra = list(args[4:])
        sp -= 4 * len(extra)
        for i, v in enumerate(extra):
            self.write(sp + 4 * i, struct.pack('<I', v & 0xffffffff))
        self.uc.reg_write(UC_ARM_REG_SP, sp)
        for i, v in enumerate(args[:4]):
            self.uc.reg_write(REGS[i], v & 0xffffffff)
        self.uc.reg_write(UC_ARM_REG_LR, RET_MAGIC)
        try:
            self.uc.emu_start(a | thumb, RET_MAGIC, count=limit)
        except UcError as ex:
            raise RuntimeError('%s at pc=%#x' % (ex, self.uc.reg_read(UC_ARM_REG_PC)))
        return self.uc.reg_read(UC_ARM_REG_R0)


def std_imports(emu):
    """Minimal libc-like imports (dlib_*, memcpy...) sufficient for pure routines."""
    def memcpy(e, a):
        e.write(a[0], e.read(a[1], a[2])) if a[2] else None
        return a[0]
    def memset(e, a):
        e.write(a[0], bytes([a[1] & 0xff]) * a[2]) if a[2] else None
        return a[0]
    def strlen(e, a):
        return len(e.cstr(a[0], 4096))
    def calloc(e, a):                   # MC_knlCalloc(size) / MC_knlAlloc(size): one argument
        return e.alloc(a[0])
    for n in ('dlib_memcpy', 'memcpy', 'dlib_memmove', 'memmove'):
        emu.imports[n] = memcpy
    for n in ('dlib_memset', 'memset'):
        emu.imports[n] = memset
    emu.imports['dlib_strlen'] = strlen
    def strncpy(e, a):
        src = e.cstr(a[1], a[2])
        e.write(a[0], (src + b'\0' * a[2])[:a[2]]) if a[2] else None
        return a[0]
    def strcpy(e, a):
        e.write(a[0], e.cstr(a[1]) + b'\0')
        return a[0]
    emu.imports['dlib_strncpy'] = strncpy
    emu.imports['dlib_strcpy'] = strcpy
    emu.imports['MC_knlCalloc'] = calloc
    emu.imports['MC_knlAlloc'] = calloc
    emu.imports['MC_knlFree'] = lambda e, a: 0

    # ARM EABI run-time helpers (C semantics: truncate toward zero). Division by zero is not
    # expected in the routines exercised here; raise so it is noticed instead of guessed.
    def s32(v): return v - (1 << 32) if v & 0x80000000 else v
    def tdiv(n, d):
        if d == 0: raise ZeroDivisionError('__aeabi division by zero')
        q = abs(n) // abs(d)
        return q if (n < 0) == (d < 0) else -q
    def idivmod(e, a):
        n, d = s32(a[0]), s32(a[1]); q = tdiv(n, d); return (q, n - q * d)
    def uidivmod(e, a):
        if a[1] == 0: raise ZeroDivisionError('__aeabi_uidivmod')
        return (a[0] // a[1], a[0] % a[1])
    def ldivmod(e, a):
        n = a[0] | (a[1] << 32); d = a[2] | (a[3] << 32)
        n = n - (1 << 64) if n >> 63 else n; d = d - (1 << 64) if d >> 63 else d
        q = tdiv(n, d); r = n - q * d
        return (q & 0xffffffff, (q >> 32) & 0xffffffff, r & 0xffffffff, (r >> 32) & 0xffffffff)
    emu.imports['__aeabi_idiv'] = lambda e, a: idivmod(e, a)[0]
    emu.imports['__aeabi_idivmod'] = idivmod
    emu.imports['__aeabi_uidivmod'] = uidivmod
    emu.imports['__aeabi_ldivmod'] = ldivmod
    return emu
