#!/usr/bin/env python3
"""Compact store map of functions: 'b0x123 h0x44 w0x8 ...' (width + immediate offset, in program order).  Usage: stw.py NAME [NAME...]
Non-immediate offsets show as 'b[reg]'.  Loads: add -r."""
import os, re, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from andelf import AndElf
B = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'work', 'orig', 'android', '영웅서기4.apk!', 'res', 'raw', 'jarfile.mp3!', 'binary.mod')
e = AndElf(B)
args = [a for a in sys.argv[1:] if a != '-r']
ld = '-r' in sys.argv
for n in args:
    out = []
    for i, note in e.annotate(n):
        m = i.mnemonic
        isw = m.startswith('str')
        isr = m.startswith('ldr') or m.startswith('ldrs')
        if not ((isw and not ld) or (isr and ld)) or '[' not in i.op_str:
            continue
        if i.op_str.split(',')[1].strip().startswith('[pc') or '[sp' in i.op_str:
            continue
        w = 'b' if m.startswith(('strb', 'ldrb', 'ldrsb')) else 'h' if m.startswith(('strh', 'ldrh', 'ldrsh')) else 'w'
        mm = re.search(r'\[(\w+)(?:, #(-?0x[0-9a-f]+))?\]', i.op_str)
        if not mm:
            out.append(w + '[reg]')
            continue
        off = mm.group(2) or '0x0'
        out.append('%s%s%s' % (w, off, ('@' + mm.group(1)) if mm.group(1) not in ('r0', 'r1', 'r2', 'r3', 'r4', 'r5', 'r6', 'r7', 'r8', 'sb', 'sl', 'fp', 'ip') else ''))
    print('== %s: %s' % (n, ' '.join(out)))
