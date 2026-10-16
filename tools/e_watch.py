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
g = Game(binp, res)
cur = [-1]
ranges = []
_w = g.write
def wr(addr, data, *a, **k):
    for lo, hi, sp in ranges:
        if addr < hi and addr + len(data) > lo:
            lr = g.uc.reg_read(arm_const.UC_ARM_REG_LR)
            print('tick %d %s HLE-write %#x len %d (caller lr %#x %s) first %s' % (cur[0], sp, addr, len(data), lr, g.elf.name(lr), data[:8].hex()))
    return _w(addr, data, *a, **k)
g.write = wr
from unicorn import arm_const
def mk(spec):
    nm, _, rest = spec.partition('+')
    off, _, sz = rest.partition(':')
    a = g.objaddr(nm) + (int(off, 0) if off else 0)
    n = int(sz, 0) if sz else 1
    def hook(uc, access, address, size, value, ud):
        pc = uc.reg_read(arm_const.UC_ARM_REG_PC)
        lr = uc.reg_read(arm_const.UC_ARM_REG_LR); print('tick %d %s pc %#x (%s) lr %#x (%s) write %#x size %d value %#x' % (cur[0], spec, pc, g.elf.name(pc), lr, g.elf.name(lr), address, size, value & 0xffffffff))
    g.uc.hook_add(UC_HOOK_MEM_WRITE, hook, begin=a, end=a + n - 1)
    ranges.append((a, a + n, spec))
for sp in spec.split(','):
    mk(sp)
g.start()
for i in range(t1 + 1):
    cur[0] = i
    k = script.get(i)
    g.tick(press=k, release=k) if k is not None else g.tick()
