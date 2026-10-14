#!/usr/bin/env python3
"""Generate random single-instruction differential tests using Unicorn (ARM926, ARMv5TE).

Output format (one case per line, hex):
  T|A instr seed r0..r15(init, r15=pc) cpsr_flags  ->  r0..r15(final, r15=final pc) cpsr_flags thumb nwrites addr:val ...
Memory: code region 0x0000-0xFFFF and data region 0x10000-0x2FFFF are filled from
lcg(seed) (same generator implemented in the Java checker).
"""
import random, sys, struct
from unicorn import *
from unicorn.arm_const import *

CODE = 0x0
DATA = 0x10000
DATA_SIZE = 0x20000
INSN = 0x8000

def lcg_bytes(seed, n):
    out = bytearray(n)
    x = seed & 0xFFFFFFFF
    for i in range(n):
        x = (x * 1103515245 + 12345) & 0xFFFFFFFF
        out[i] = (x >> 16) & 0xFF
    return out

REGS = [UC_ARM_REG_R0, UC_ARM_REG_R1, UC_ARM_REG_R2, UC_ARM_REG_R3, UC_ARM_REG_R4, UC_ARM_REG_R5,
        UC_ARM_REG_R6, UC_ARM_REG_R7, UC_ARM_REG_R8, UC_ARM_REG_R9, UC_ARM_REG_R10, UC_ARM_REG_R11,
        UC_ARM_REG_R12, UC_ARM_REG_SP, UC_ARM_REG_LR]

def rand_reg(rng):
    k = rng.random()
    if k < 0.6:
        return rng.randrange(DATA + 0x4000, DATA + DATA_SIZE - 0x4000)
    if k < 0.75:
        return rng.randrange(0, 64)
    if k < 0.85:
        return rng.choice([0, 1, 0x7FFFFFFF, 0x80000000, 0xFFFFFFFF, 31, 32, 33])
    return rng.getrandbits(32)

def gen_thumb(rng):
    while True:
        op = rng.getrandbits(16)
        top = op >> 8
        if top in (0xDE, 0xDF, 0xBE):
            continue
        # misc 1011 undefined parts in v5
        if (op >> 12) == 0xB and ((op >> 8) & 0xF) not in (0x0, 0x4, 0x5, 0xC, 0xD):
            continue
        return op

def gen_arm(rng):
    cond = 0xE if rng.random() < 0.7 else rng.randrange(0, 15)
    k = rng.random()
    if k < 0.30:  # data processing
        op = rng.getrandbits(28) & 0x03FFFFFF
        if rng.random() < 0.5:
            op |= 0x02000000  # immediate
            op &= ~0x00000000
        else:
            op &= ~0x02000000
            if op & 0x10:
                op &= ~0x80  # register shift: bit7=0
        # avoid misc space (TST..CMN without S)
        opc = (op >> 21) & 0xF
        if 8 <= opc <= 11:
            op |= 0x00100000
        # avoid Rd=PC with S (exception return) mostly
        if ((op >> 12) & 0xF) == 15 and rng.random() < 0.8:
            op = (op & ~0xF000) | (rng.randrange(0, 15) << 12)
        if ((op >> 12) & 0xF) == 15:
            op &= ~0x00100000
            if 8 <= opc <= 11:
                op = (op & ~0xF000)
    elif k < 0.45:  # single data transfer
        op = 0x04000000 | (rng.getrandbits(26) & 0x03FFFFFF)
        if op & 0x02000000:
            op &= ~0x10
        if ((op >> 16) & 0xF) == ((op >> 12) & 0xF) and (op & 0x00200000 or not op & 0x01000000):
            op ^= 0x1000
    elif k < 0.55:  # extra load/store
        op = rng.getrandbits(25) & 0x01FFFFFF
        op &= ~0x0E000000
        op |= 0x90
        sh = rng.randrange(1, 4)
        op = (op & ~0x60) | (sh << 5)
        if op & 0x00400000:
            pass
        else:
            op &= ~0xF00
        if not (op & 0x00100000) and sh >= 2:
            op &= ~0x1000  # LDRD/STRD need even rd
            if ((op >> 12) & 0xF) == 14:
                op = (op & ~0xF000) | 0x2000
        rn = (op >> 16) & 0xF; rd = (op >> 12) & 0xF
        if rn == rd or rn == rd + 1:
            op = (op & ~0xF0000) | (((rd + 3) & 0xF) << 16)
    elif k < 0.65:  # block
        op = 0x08000000 | (rng.getrandbits(25) & 0x01FFFFFF)
        op &= ~0x00400000
        if op & 0xFFFF == 0:
            op |= 1
        rn = (op >> 16) & 0xF
        if op & 0x00200000 and (op >> rn) & 1:
            op &= ~(1 << rn)
            if op & 0xFFFF == 0:
                op |= 1 << ((rn + 1) & 7)
    elif k < 0.72:  # branch
        op = 0x0A000000 | (rng.getrandbits(25) & 0x01FFFFFF)
        op = (op & ~0x00FFFFFF) | (rng.randrange(-0x100, 0x100) & 0x00FFFFFF)
    elif k < 0.82:  # multiply
        sub = rng.randrange(0, 6)
        rd = rng.randrange(0, 15); rn = rng.randrange(0, 15); rs = rng.randrange(0, 15); rm = rng.randrange(0, 15)
        s = rng.getrandbits(1)
        if sub < 2:
            op = (sub << 21) | (s << 20) | (rd << 16) | (rn << 12) | (rs << 8) | 0x90 | rm
        else:
            while rn == rd:
                rn = rng.randrange(0, 15)
            op = 0x00800000 | ((sub - 2) << 21) | (s << 20) | (rd << 16) | (rn << 12) | (rs << 8) | 0x90 | rm
    elif k < 0.92:  # misc: bx, blx, clz, qadd, smulxy etc
        sub = rng.randrange(0, 5)
        rd = rng.randrange(0, 15); rn = rng.randrange(0, 15); rs = rng.randrange(0, 15); rm = rng.randrange(0, 15)
        if sub == 0:
            op = 0x012FFF10 | rm
        elif sub == 1:
            op = 0x012FFF30 | rm
        elif sub == 2:
            op = 0x016F0F10 | (rd << 12) | rm
        elif sub == 3:
            op = 0x01000050 | (rng.randrange(0, 4) << 21) | (rn << 16) | (rd << 12) | rm
        else:
            op1 = rng.randrange(0, 4)
            xy = rng.randrange(0, 4)
            if op1 == 2:
                while rn == rd:
                    rn = rng.randrange(0, 15)
            op = 0x01000080 | (op1 << 21) | (rd << 16) | (rn << 12) | (rs << 8) | (xy << 5) | rm
    else:  # swp / mrs / msr flags
        sub = rng.randrange(0, 3)
        rd = rng.randrange(0, 15); rn = rng.randrange(0, 15); rm = rng.randrange(0, 15)
        if sub == 0:
            op = 0x01000090 | (rng.getrandbits(1) << 22) | (rn << 16) | (rd << 12) | rm
        elif sub == 1:
            op = 0x0128F000 | rm
        else:
            op = 0x0128F000 | rm
    return (cond << 28) | (op & 0x0FFFFFFF)

def run_case(rng, thumb):
    seed = rng.getrandbits(32)
    instr = gen_thumb(rng) if thumb else gen_arm(rng)
    code = lcg_bytes(seed, 0x10000)
    data = lcg_bytes(seed ^ 0x5A5A5A5A, DATA_SIZE)
    if thumb:
        code[INSN:INSN + 2] = struct.pack('<H', instr)
    else:
        code[INSN:INSN + 4] = struct.pack('<I', instr)
    regs = [rand_reg(rng) for _ in range(15)]
    regs[13] = rng.randrange(DATA + 0x8000, DATA + 0x18000) & ~3
    flags = rng.getrandbits(4) << 28
    mu = Uc(UC_ARCH_ARM, UC_MODE_THUMB if thumb else UC_MODE_ARM)
    mu.ctl_set_cpu_model(UC_CPU_ARM_926)
    mu.mem_map(CODE, 0x10000)
    mu.mem_map(DATA, DATA_SIZE)
    mu.mem_write(CODE, bytes(code))
    mu.mem_write(DATA, bytes(data))
    for i, rr in enumerate(REGS):
        mu.reg_write(rr, regs[i])
    cpsr = mu.reg_read(UC_ARM_REG_CPSR)
    cpsr = (cpsr & 0x0FFFFFFF) | flags
    mu.reg_write(UC_ARM_REG_CPSR, cpsr)
    bad = []
    def hook(uc, access, address, size, value, user):
        if size > 1 and address % size != 0:
            bad.append(1)
    mu.hook_add(UC_HOOK_MEM_READ | UC_HOOK_MEM_WRITE, hook)
    if thumb and (instr >> 11) == 0x1F:
        mu.reg_write(UC_ARM_REG_LR, regs[14] & ~1)
        regs[14] &= ~1
    try:
        mu.emu_start(INSN | (1 if thumb else 0), 0xFFFFFFFF, count=1)
    except UcError as e:
        return None
    if bad:
        return None
    out = [mu.reg_read(rr) for rr in REGS]
    fpc = mu.reg_read(UC_ARM_REG_PC)
    fcpsr = mu.reg_read(UC_ARM_REG_CPSR)
    fthumb = (fcpsr >> 5) & 1
    if not fthumb and (fpc & 3):
        return None
    if not thumb:
        cls = (instr >> 25) & 7
        rn = (instr >> 16) & 15
        # unpredictable: writeback/post-index with Rn=PC, reg-shift with PC, STM with PC
        if cls in (2, 3) and rn == 15 and ((instr & 0x00200000) or not (instr & 0x01000000)):
            return None
        if cls == 0 and (instr & 0x90) == 0x90 and (instr & 0x60) and rn == 15 and ((instr & 0x00200000) or not (instr & 0x01000000)):
            return None
        if cls == 0 and (instr & 0x90) == 0x10 and ((instr & 15) == 15 or rn == 15 or ((instr >> 8) & 15) == 15):
            return None
        if cls == 4 and not (instr & 0x00100000) and (instr & 0x8000):
            return None
        if cls in (2, 3) and not (instr & 0x00100000) and ((instr >> 12) & 15) == 15:
            return None
    code2 = mu.mem_read(CODE, 0x10000)
    data2 = mu.mem_read(DATA, DATA_SIZE)
    writes = []
    for base, a, b in ((CODE, code, code2), (DATA, data, data2)):
        for i in range(0, len(a), 4):
            if a[i:i + 4] != b[i:i + 4]:
                writes.append((base + i, struct.unpack('<I', bytes(b[i:i + 4]))[0]))
    if len(writes) > 40:
        return None
    line = ['T' if thumb else 'A', '%x' % instr, '%x' % seed]
    line += ['%x' % x for x in regs] + ['%x' % INSN, '%x' % (flags >> 28)]
    line += ['->'] + ['%x' % x for x in out] + ['%x' % fpc, '%x' % ((fcpsr >> 27) & 0x1F), '%d' % fthumb, '%d' % len(writes)]
    line += ['%x:%x' % w for w in writes]
    return ' '.join(line)

def main():
    n = int(sys.argv[1])
    seed = int(sys.argv[2]) if len(sys.argv) > 2 else 1
    mode = sys.argv[3] if len(sys.argv) > 3 else 'both'
    rng = random.Random(seed)
    cnt = 0
    while cnt < n:
        thumb = mode == 'thumb' or (mode == 'both' and rng.random() < 0.5)
        line = run_case(rng, thumb)
        if line:
            print(line)
            cnt += 1

main()
