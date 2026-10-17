#!/usr/bin/env python3
"""Count the entries of the ORIGINAL functions whose names start with the given prefixes in a tick window (T0.4 acceptance: does a scenario reach its target
module?).  Usage: e_count.py <binary.mod> <snapshot> <from_tick> <to_tick> <prefix,prefix..> <full scenario tokens..>   (fast: one code hook per function)"""
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from collections import Counter
from unicorn import UC_HOOK_CODE
import e_snap, ref_game
binp, snap, t0, t1, prefixes = sys.argv[1], sys.argv[2], int(sys.argv[3]), int(sys.argv[4]), sys.argv[5].split(',')
script = ref_game.parse_script(sys.argv[6:])
g, first = e_snap.load(binp, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'work', 'res'), snap)
cnt = Counter(); cur = [0]
targets = {a: n for n, (a, s, th) in g.elf.fbyname.items() if any(n.startswith(p) for p in prefixes)}
def hook(uc, addr, size, ud):
    if cur[0] >= t0: cnt[targets[addr]] += 1
for a in targets: g.uc.hook_add(UC_HOOK_CODE, hook, begin=a, end=a)
for i in range(first, t1):
    cur[0] = i; ref_game.tick_script(g, script, i)
print(len(cnt), 'functions:', ' '.join('%s:%d' % x for x in sorted(cnt.items(), key=lambda x: -x[1])))
