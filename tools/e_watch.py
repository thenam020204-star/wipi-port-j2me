#!/usr/bin/env python3
"""Write-watch on an address range of the ORIGINAL: prints (tick, pc, function, value) for each write.
Usage: e_watch.py <binary.mod> <res> <from_tick> <to_tick> <global>[+off][:size] [tick:key ...]"""
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from unicorn import UC_HOOK_MEM_WRITE
from ref_game import Game
binp, res, t0, t1, spec = sys.argv[1], sys.argv[2], int(sys.argv[3]), int(sys.argv[4]), sys.argv[5]
script = {}
for kv in sys.argv[6:]:
    t, k = kv.split(':'); script[int(t)] = int(k, 0)
nm, _, rest = spec.partition('+')
off, _, sz = rest.partition(':')
g = Game(binp, res).start()
a = g.objaddr(nm) + (int(off, 0) if off else 0)
n = int(sz, 0) if sz else 1
cur = [0]
def hook(uc, access, address, size, value, ud):
    pc = uc.reg_read(__import__('unicorn').arm_const.UC_ARM_REG_PC)
    print('tick %d pc %#x (%s) write %#x size %d value %#x' % (cur[0], pc, g.elf.name(pc) if hasattr(g, 'elf') else '?', address, size, value & 0xffffffff))
g.uc.hook_add(UC_HOOK_MEM_WRITE, hook, begin=a, end=a + n - 1)
for i in range(t1 + 1):
    cur[0] = i
    k = script.get(i)
    g.tick(press=k, release=k) if k is not None else g.tick()
