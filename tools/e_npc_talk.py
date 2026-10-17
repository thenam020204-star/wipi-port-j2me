#!/usr/bin/env python3
"""Find which NPC of a scene opens an NpcUi (shop, ...) in the ORIGINAL: warp the hero next to each NPC (4 sides), face it, press OK, report
NpcUi_Initialize(mode, kind) calls.  Usage: e_npc_talk.py <binary.mod> <snapshot (free play, e.g. M_4800)> <scene id>"""
import os
import sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from unicorn import UC_HOOK_CODE, arm_const
import e_snap
binp, snap = sys.argv[1], sys.argv[2]
g, t0 = e_snap.load(binp, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'work', 'res'), snap)
d = e_snap.read(snap)
scene = int(sys.argv[3])
g.call('Event_moveNextSceneWithWeatherTime', scene, 7, 15, 0, 0xffffffff)
for k in range(40):
    g.tick()
npcbase = 0x155e40 - 0x10000
npcs = []
for i in range(20):
    b = g.read(npcbase + i * 0x1cc, 0x1cc)
    if b[0x15] == 0:
        x = int.from_bytes(b[8:12], 'little', signed=True); y = int.from_bytes(b[12:16], 'little', signed=True)
        npcs.append((i, x, y))
print('npcs', npcs)
calls = []
a = g.elf.fbyname['NpcUi_Initialize'][0]
def hk(uc, addr, size, ud):
    calls.append((uc.reg_read(arm_const.UC_ARM_REG_R0), uc.reg_read(arm_const.UC_ARM_REG_R1)))
g.uc.hook_add(UC_HOOK_CODE, hk, begin=a, end=a)
for i, x, y in npcs:
    tx, ty = x // 16, (y - 1) // 16
    for dx, dy, key in ((0, 1, -1), (0, -1, -2), (1, 0, -3), (-1, 0, -4)):
        e_snap.restore(g, d); del calls[:]
        g.call('Event_moveNextSceneWithWeatherTime', scene, tx + dx, ty + dy, 0, 0xffffffff)
        for k in range(40):
            g.tick()
        g.tick(press=key, release=key)          # face the NPC
        for k in range(3):
            g.tick()
        for k in range(30):
            kk = -5 if k % 5 == 0 else None
            g.tick(press=kk, release=kk)
        if calls:
            print('npc', i, 'tile', tx + dx, ty + dy, 'face key', key, 'NpcUi_Initialize', calls, flush=True)
