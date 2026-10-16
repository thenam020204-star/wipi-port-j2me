#!/usr/bin/env python3
"""Argument trace of the ORIGINAL: for each call of the given functions in the tick range prints r0-r3 and the first 10 stack words.
Usage: e_args.py <binary.mod> <res> <from_tick> <to_tick> <func>[,func..] [tick:key ...]"""
import os, struct, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from unicorn import UC_HOOK_CODE, arm_const
from ref_game import Game
binp, res, t0, t1, fns = sys.argv[1], sys.argv[2], int(sys.argv[3]), int(sys.argv[4]), sys.argv[5].split(',')
script = {}
for kv in sys.argv[6:]:
    t, k = kv.split(':'); script[int(t)] = int(k, 0)
g = Game(binp, res)
cur = [-1]
def s32(v):
    return v - (1 << 32) if v & 0x80000000 else v
def mk(fn):
    a = g.elf.fbyname[fn][0]
    def hook(uc, addr, size, ud):
        if cur[0] < t0:
            return
        r = [s32(uc.reg_read(x)) for x in (arm_const.UC_ARM_REG_R0, arm_const.UC_ARM_REG_R1, arm_const.UC_ARM_REG_R2, arm_const.UC_ARM_REG_R3)]
        sp = uc.reg_read(arm_const.UC_ARM_REG_SP)
        st = [s32(w) for w in struct.unpack('<10I', bytes(uc.mem_read(sp, 40)))]
        lr = uc.reg_read(arm_const.UC_ARM_REG_LR)
        print('tick %d %s <- %s args %s stack %s' % (cur[0], fn, g.elf.name(lr), r, st))
    g.uc.hook_add(UC_HOOK_CODE, hook, begin=a, end=a)
for f in fns:
    mk(f)
g.start()
for i in range(t1 + 1):
    cur[0] = i
    k = script.get(i)
    g.tick(press=k, release=k) if k is not None else g.tick()
