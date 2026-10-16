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
    out, skipping, inl, grp = [], False, None, False
    lines = body.split('\n')
    for li, ln in enumerate(lines):
        if inl is not None:                # inlined Touch_setState/TF_setFrame body: print one line, drop the rest of the block
            if ln == inl + '}':
                inl = None
            continue
        m = re.match(r'^(\s*)if \(iRam000fd350 != (.*?)\) \{$', ln)
        if m:
            out.append('%s/* inlined Touch_setState(%s) */' % (m.group(1), m.group(2)))
            inl = m.group(1)
            continue
        if skipping:                       # continuation of a touch_Addrect(...) statement
            skipping = ';' not in ln
            continue
        if re.match(r'^\s*\w+ = m_TouchDirectKeySize;$', ln) and any(l.startswith('LAB_00044378:') for l in lines[li:li + 400]):    # inlined touch_setTouchGroup(): hit-rectangle setup, dropped up to its end label
            out.append(ln.split('=')[0] + '/* inlined touch_setTouchGroup(): pointer hit rectangles omitted */')
            grp = True
            continue
        if grp:
            if ln.startswith('LAB_00044378:') or ln.startswith('LAB_00044360:') and False:
                grp = False
            continue
        if ln.strip().startswith('touch_Addrect(') or ln.strip() == 'touch_rectClear();':
            skipping = ';' not in ln
            continue
        if ln.startswith('/* WARNING') or ln.strip() == '':
            continue
        if DECL.match(ln):
            continue
        ln = re.sub(r'\bundefined[1248]?\b', 'int', ln)
        out.append(ln)
    res, i = [], 0
    while i < len(out):                    # drop empty switch arms ("case N:" immediately followed by "break;")
        if re.match(r'^\s*case [^:]*:$', out[i]) and i + 1 < len(out) and out[i + 1].strip() == 'break;':
            i += 2
            continue
        m = re.match(r'^\s*(\w+(?:\[[^\]]*\])?) = (\w+);$', out[i + 1]) if i + 2 < len(out) else None
        if re.match(r'^\s*case [^:]*:$', out[i]) and m and out[i + 2].strip() == 'break;' and re.match(r'^b[A-Z]\w+$', m.group(2)):
            i += 3                         # "case N: x = bVar5; break;" (jump-table filler that stores an unmodified local back)
            continue
        res.append(out[i])
        i += 1
    return '\n'.join(res)


def shorten(t):
    """Terser reading form: typed loads s8()/u8()/s16()/u16()/i32(); ints stores S(addr) = v (store width: check tools/stores.py); param_N -> pN."""
    t = re.sub(r'\((?:int|uint|short|ushort|byte|char)\)\*\((char|byte|short|ushort|int|uint) \*\)\(([^()]*(?:\([^()]*\)[^()]*)*)\)', lambda m: {'char': 's8', 'byte': 'u8', 'short': 's16', 'ushort': 'u16', 'int': 'i32', 'uint': 'i32'}[m.group(1)] + '(' + m.group(2) + ')', t)
    t = re.sub(r'\*\((char|byte|short|ushort|int|uint) \*\)\(([^()]*(?:\([^()]*\)[^()]*)*)\) =', lambda m: 'S(' + m.group(2) + ') =', t)
    t = re.sub(r'\*\((char|byte|short|ushort|int|uint) \*\)\(([^()]*(?:\([^()]*\)[^()]*)*)\)', lambda m: {'char': 's8', 'byte': 'u8', 'short': 's16', 'ushort': 'u16', 'int': 'i32', 'uint': 'i32'}[m.group(1)] + '(' + m.group(2) + ')', t)
    t = re.sub(r'\bparam_(\d+)\b', r'p\1', t)
    return t


names = [a for a in sys.argv[1:] if not a.startswith('-')]
full = '--full' in sys.argv
for n in names:
    if n in idx:
        b = idx[n].rstrip()
        r = b if full else compact(b)
        print('// ==== ' + (r if '-l' in sys.argv else shorten(r)))
    else:
        print('// ==== %s: not found' % n)
