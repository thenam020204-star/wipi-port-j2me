#!/usr/bin/env python3
"""xref.py <hex addr>... : literal-pool words equal to addr (or addr|1) in .text and the
functions (nearest preceding push {.., lr}) whose ldr loads them."""
import sys, os, struct, capstone, bisect
R = os.path.dirname(os.path.abspath(__file__)) + '/../..'
b = open(R + '/kro/jar/binary.mod', 'rb').read()
BASE, OFF, SIZE = 0x1000, 0x34, 0x5dfd7
t = b[OFF:OFF + SIZE]
md = capstone.Cs(capstone.CS_ARCH_ARM, capstone.CS_MODE_THUMB); md.skipdata = True
ins = list(md.disasm(t, BASE))
pushes = [i.address for i in ins if i.mnemonic == 'push' and 'lr' in i.op_str]
loads = {}
for i in ins:
    if i.mnemonic == 'ldr' and '[pc' in i.op_str:
        try:
            imm = int(i.op_str.split('#')[-1].rstrip(']'), 16)
            loads.setdefault(((i.address + 4) & ~3) + imm, []).append(i.address)
        except ValueError: pass
for a in sys.argv[1:]:
    v = int(a, 16)
    lits = [BASE + k for k in range(0, SIZE - 3, 4) if struct.unpack_from('<I', t, k)[0] in (v, v | 1)]
    for l in lits:
        for u in loads.get(l, []):
            f = pushes[bisect.bisect_right(pushes, u) - 1]
            print('%x: literal @%x loaded at %x (func %x)' % (v, l, u, f))
    if not lits: print('%x: no literal' % v)
