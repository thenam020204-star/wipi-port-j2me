#!/usr/bin/env python3
"""Who calls a function of the ORIGINAL (LR at entry) per tick.  Usage: e_callers.py <binary.mod> <res> <from_tick> <to_tick> <func>[,func..] [tick:key ...]"""
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from unicorn import UC_HOOK_CODE, arm_const
from ref_game import Game
binp, res, t0, t1, fns = sys.argv[1], sys.argv[2], int(sys.argv[3]), int(sys.argv[4]), sys.argv[5].split(',')
script = {}
for kv in sys.argv[6:]:
    t, k = kv.split(':'); script[int(t)] = int(k, 0)
g = Game(binp, res)
cur = [-1]
cnt = {}
def mk(fn):
    a = g.elf.fbyname[fn][0]
    def hook(uc, addr, size, ud):
        if cur[0] < t0:
            return
        lr = uc.reg_read(arm_const.UC_ARM_REG_LR)
        k = (cur[0], fn, g.elf.name(lr))
        cnt[k] = cnt.get(k, 0) + 1
    g.uc.hook_add(UC_HOOK_CODE, hook, begin=a, end=a)
for f in fns:
    mk(f)
g.start()
for i in range(t1 + 1):
    cur[0] = i
    k = script.get(i)
    g.tick(press=k, release=k) if k is not None else g.tick()
for k in sorted(cnt):
    print('tick %d %s <- %s x%d' % (k + (cnt[k],)))
