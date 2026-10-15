#!/usr/bin/env python3
"""Annotated disassembly of named AND2011 functions.
Usage: python3 tools/and_dump.py <binary.mod> <out.txt|-> func [func...]"""
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from andelf import AndElf
e = AndElf(sys.argv[1])
txt = []
for n in sys.argv[3:]:
    a, sz, _ = e.fbyname[n]
    txt.append('== %s @%#x size %d\n%s\n' % (n, a, sz, e.dump(n)))
out = '\n'.join(txt)
if sys.argv[2] == '-': print(out)
else: open(sys.argv[2], 'w', encoding='utf-8').write(out)
