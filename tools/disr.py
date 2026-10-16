#!/usr/bin/env python3
"""Linear disassembly of an address range of the AND2011 binary with skip-data (continues past literal pools / jump tables that stop disf.py).
Usage: disr.py <start hex> <end hex>   (real addresses, i.e. Ghidra - 0x10000)"""
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from andelf import AndElf
B = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'work', 'orig', 'android', '영웅서기4.apk!', 'res', 'raw', 'jarfile.mp3!', 'binary.mod')
e = AndElf(B)
a, b = int(sys.argv[1], 16), int(sys.argv[2], 16)
e.cs.skipdata = True
o = e.off(a)
for i in e.cs.disasm(e.data[o:o + (b - a)], a):
    print('  %08x  %-8s %s' % (i.address, i.mnemonic, i.op_str))
