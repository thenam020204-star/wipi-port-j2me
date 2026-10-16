#!/usr/bin/env python3
"""Field-access census of a struct from the Ghidra pseudo-C (reading aid).  A 'struct pointer' is the first parameter (param_1) of the functions
whose name matches the given regex.  Prints  offset  width/sign  count  example functions.
Usage: fields.py <name regex> [min count]"""
import re, sys, os
P = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'work', 'ghidra', 'decomp_and2011.c')
text = open(P, encoding='utf-8', errors='replace').read()
parts = re.split(r'(?m)^// ==== ', text)
rx = re.compile(sys.argv[1])
minc = int(sys.argv[2]) if len(sys.argv) > 2 else 1
acc = {}
pat = re.compile(r'\*\((\w+(?: \w+)?) \*\)\(param_1 \+ (0x[0-9a-fA-F]+|\d+)\)')
for p in parts[1:]:
    m = re.match(r'(\S+) @', p)
    if not m or not rx.search(m.group(1)):
        continue
    for t, off in pat.findall(p):
        o = int(off, 0)
        acc.setdefault(o, {}).setdefault(t, []).append(m.group(1))
for o in sorted(acc):
    tot = sum(len(v) for v in acc[o].values())
    if tot < minc:
        continue
    ts = ','.join('%s:%d' % (t, len(v)) for t, v in acc[o].items())
    fn = sorted(set(f for v in acc[o].values() for f in v))[:4]
    print('%#06x %-28s %s' % (o, ts, ' '.join(fn)))
