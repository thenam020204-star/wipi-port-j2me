#!/usr/bin/env python3
"""Struct field census from the DISASSEMBLY (exact load/store widths and signedness - Ghidra's pseudo-C often types byte fields as int).
Tracks one 'struct pointer' through registers (mov / add #imm) inside each function:
  - root A: the first argument (r0) of functions whose name matches <argrx>
  - root B: the value returned by a getter function (e.g. MP_getHero) after `bl <getter>`
Reports offset -> {width/sign: count}, plus the functions that touch it.
Usage: fields2.py <binary.mod> <argrx or -> <getter name or -> [min count]"""
import os, re, sys, collections
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import capstone
from andelf import AndElf

e = AndElf(sys.argv[1])
argrx = re.compile(sys.argv[2]) if sys.argv[2] != '-' else None
getter = sys.argv[3] if sys.argv[3] != '-' else None
minc = int(sys.argv[4]) if len(sys.argv) > 4 else 1
gaddr = e.fbyname[getter][0] if getter else None
W = {'ldr': 'w32', 'str': 'w32', 'ldrb': 'u8', 'strb': 'w8', 'ldrsb': 's8', 'ldrh': 'u16', 'strh': 'w16', 'ldrsh': 's16'}
census = collections.defaultdict(lambda: collections.defaultdict(list))
for a, sz, n, t in e.funcs:
    if t or sz == 0:
        continue
    ins = e.insns(n)
    regs = {}
    if argrx and argrx.search(n):
        regs['r0'] = 0
    for i in ins:
        m = i.mnemonic
        ops = i.operands
        try:
            if m in W and ops[-1].type == capstone.arm.ARM_OP_MEM:
                mem = ops[-1].mem
                base = i.reg_name(mem.base) if mem.base else None
                if base in regs and not mem.index:
                    off = regs[base] + mem.disp
                    kind = ('R' if m.startswith('ldr') else 'W') + W[m]
                    census[off][kind].append(n)
                if m.startswith('ldr'):
                    d = i.reg_name(ops[0].reg)
                    regs.pop(d, None)
                if len(ops) == 2 and i.writeback and base in regs:
                    pass
            elif m == 'mov' and len(ops) == 2 and ops[1].type == capstone.arm.ARM_OP_REG:
                d, s = i.reg_name(ops[0].reg), i.reg_name(ops[1].reg)
                if s in regs:
                    regs[d] = regs[s]
                else:
                    regs.pop(d, None)
            elif m == 'add' and len(ops) == 3 and ops[1].type == capstone.arm.ARM_OP_REG and ops[2].type == capstone.arm.ARM_OP_IMM:
                d, s = i.reg_name(ops[0].reg), i.reg_name(ops[1].reg)
                if s in regs:
                    regs[d] = regs[s] + ops[2].imm
                else:
                    regs.pop(d, None)
            elif m in ('bl', 'blx') and i.op_str.startswith('#'):
                t2 = int(i.op_str[1:], 16)
                for r in ('r0', 'r1', 'r2', 'r3', 'r12', 'lr'):
                    regs.pop(r, None)
                if gaddr and t2 == gaddr:
                    regs['r0'] = 0
            elif ops and ops[0].type == capstone.arm.ARM_OP_REG and m not in ('cmp', 'cmn', 'tst', 'teq', 'str', 'strb', 'strh', 'push', 'stm', 'stmib'):
                regs.pop(i.reg_name(ops[0].reg), None)
        except Exception:
            pass
for off in sorted(census):
    cnt = sum(len(v) for v in census[off].values())
    if cnt < minc:
        continue
    kinds = ' '.join('%s:%d' % (k, len(v)) for k, v in sorted(census[off].items()))
    fn = sorted(set(f for v in census[off].values() for f in v))
    print('%#06x  %-34s %2d fn  %s' % (off, kinds, len(fn), ' '.join(fn[:5])))
