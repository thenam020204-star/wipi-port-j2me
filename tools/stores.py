#!/usr/bin/env python3
"""List the memory accesses of a function with their exact widths: stores.py <func> [r|w|rw]  (disassembly facts, for cross-checking Ghidra's pseudo-C widths)"""
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from andelf import AndElf
B = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'work', 'orig', 'android', '영웅서기4.apk!', 'res', 'raw', 'jarfile.mp3!', 'binary.mod')
e = AndElf(B)
mode = sys.argv[2] if len(sys.argv) > 2 else 'w'
for i, note in e.annotate(sys.argv[1]):
    m = i.mnemonic
    if m.startswith(('str', 'ldr')) and '[' in i.op_str and not i.op_str.split(',')[1].strip().startswith('[pc'):
        isw = m.startswith('str')
        if (isw and 'w' in mode) or (not isw and 'r' in mode):
            print('%08x %-7s %-26s %s' % (i.address, m, i.op_str, note))
