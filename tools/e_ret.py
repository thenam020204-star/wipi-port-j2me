#!/usr/bin/env python3
"""Entry args + return value (r0) of the ORIGINAL for the given functions in a tick range.
Usage: e_ret.py <binary.mod> <res> <from_tick> <to_tick> <func>[,func..] [tick:key ...]"""
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from unicorn import UC_HOOK_CODE, arm_const
import ref_game
binp, res, t0, t1, fns = sys.argv[1], sys.argv[2], int(sys.argv[3]), int(sys.argv[4]), sys.argv[5].split(',')
script = ref_game.parse_script(sys.argv[6:])
import e_snap
g, first = e_snap.boot(binp, res)        # env SNAPLOAD: tools/e_snap.py (T0.3)
cur = [-1]
pend = []
retaddrs = {}
def s32(v):
    return v - (1 << 32) if v & 0x80000000 else v
R = (arm_const.UC_ARM_REG_R0, arm_const.UC_ARM_REG_R1, arm_const.UC_ARM_REG_R2, arm_const.UC_ARM_REG_R3)
def onret(uc, addr, size, ud):
    for i in range(len(pend) - 1, -1, -1):
        p = pend[i]
        if p[1] == addr and uc.reg_read(arm_const.UC_ARM_REG_SP) == p[3]:
            print('tick %d %s%s args %s -> %d' % (p[4], '  ' * p[5], p[0], p[2], s32(uc.reg_read(arm_const.UC_ARM_REG_R0))))
            del pend[i]
            return
def mk(fn):
    a = g.elf.fbyname[fn][0]
    def hook(uc, addr, size, ud):
        if cur[0] < t0:
            return
        lr = uc.reg_read(arm_const.UC_ARM_REG_LR) & ~1
        sp = uc.reg_read(arm_const.UC_ARM_REG_SP)
        pend.append((fn, lr, [s32(uc.reg_read(x)) for x in R], sp, cur[0], len(pend)))
        if lr not in retaddrs:
            retaddrs[lr] = 1
            uc.hook_add(UC_HOOK_CODE, onret, begin=lr, end=lr)
    g.uc.hook_add(UC_HOOK_CODE, hook, begin=a, end=a)
for f in fns:
    mk(f)
for i in range(first, t1 + 1):
    cur[0] = i
    ref_game.tick_script(g, script, i)
