#!/usr/bin/env python3
"""Print Ghidra pseudo-C of named functions (reading aid only; disassembly wins).  Usage: cfunc.py NAME [NAME...]"""
import re, sys, os
P = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'work', 'ghidra', 'decomp_and2011.c')
text = open(P, encoding='utf-8', errors='replace').read()
parts = re.split(r'(?m)^// ==== ', text)
idx = {}
for p in parts[1:]:
    m = re.match(r'(\S+) @(\w+) size=(\d+)', p)
    if m:
        idx[m.group(1)] = p
for n in sys.argv[1:]:
    if n in idx:
        print('// ==== ' + idx[n].rstrip())
    else:
        print('// ==== %s: not found' % n)
