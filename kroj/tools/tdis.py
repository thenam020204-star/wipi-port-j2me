#!/usr/bin/env python3
"""Thumb disassembly of the game binary: tdis.py <from hex> <to hex> [profile.txt] [binary.mod]
   .text is mapped at 0x1000 from file offset 0x34; literal pool words / strings are annotated."""
import sys, os, struct, capstone
R = os.path.dirname(os.path.abspath(__file__)) + '/../..'
path = sys.argv[4] if len(sys.argv) > 4 else R + '/kro/jar/binary.mod'
b = open(path, 'rb').read()
BASE, OFF = 0x1000, 0x34
TEXT_END = BASE + 0x5dfd7
def rd32(a):
    o = a - BASE + OFF
    return struct.unpack_from('<I', b, o)[0] if BASE <= a < TEXT_END - 3 else None
def cstr(a):
    if not (BASE <= a < TEXT_END): return None
    o = a - BASE + OFF; e = b.find(b'\0', o, o + 80)
    if e <= o: return None
    s = b[o:e]
    if all(0x20 <= c < 0x7f or c >= 0xa1 or c in (10, 13, 9) for c in s) and len(s) >= 2:
        try: return s.decode('euc-kr')
        except Exception: return None
    return None
prof = {}
if len(sys.argv) > 3 and os.path.exists(sys.argv[3]):
    for l in open(sys.argv[3]):
        a, c = l.split(); prof[int(a, 16)] = int(c)
md = capstone.Cs(capstone.CS_ARCH_ARM, capstone.CS_MODE_THUMB)
md.skipdata = True
a = int(sys.argv[1], 16); e = int(sys.argv[2], 16)
for i in md.disasm(b[a - BASE + OFF:e - BASE + OFF], a):
    note = ''
    if i.mnemonic == 'ldr' and '[pc' in i.op_str:
        try:
            imm = int(i.op_str.split('#')[-1].rstrip(']'), 16)
            la = ((i.address + 4) & ~3) + imm
            v = rd32(la)
            if v is not None:
                note = ' ; =0x%x' % v
                s = cstr(v)
                if s: note += ' "%s"' % s
        except Exception: pass
    print('%x %8s  %s %s%s' % (i.address, prof.get(i.address, ''), i.mnemonic, i.op_str, note))
