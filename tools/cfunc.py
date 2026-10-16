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
DECL = re.compile(r'^\s+(undefined\d?|u?int|u?short|u?char|byte|bool|code|longlong|ulonglong|float|double|uint|ushort)\s*[\*]*\s*[\w\[\]\s,\*]+;\s*$')


def compact(body):
    out, inhead = [], False
    for ln in body.split('\n'):
        if ln.startswith('/* WARNING') or ln.strip() == '':
            continue
        if DECL.match(ln):
            continue
        ln = re.sub(r'\bundefined[1248]?\b', 'int', ln)
        out.append(ln)
    return '\n'.join(out)


names = [a for a in sys.argv[1:] if not a.startswith('-')]
full = '--full' in sys.argv
for n in names:
    if n in idx:
        b = idx[n].rstrip()
        print('// ==== ' + (b if full else compact(b)))
    else:
        print('// ==== %s: not found' % n)
