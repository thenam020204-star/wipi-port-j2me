#!/usr/bin/env python3
"""Port coverage: which functions of the AND2011 binary the Java sources cite (by "@0x<addr>" in comments), per module prefix.
Usage: python3 tools/port_cover.py [--list]   (prints totals; --list also prints every cited function)
A cited function is one whose body (or the part reached so far) has a Java counterpart; it does NOT mean every branch is ported:
the open branches are the 'later' throws (counted separately)."""
import os, re, sys, collections
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from andelf import AndElf

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
B = os.path.join(ROOT, 'work', 'orig', 'android', '영웅서기4.apk!', 'res', 'raw', 'jarfile.mp3!', 'binary.mod')
# out of scope of the J2ME port (NOTES.md "Phạm vi port"): network / payment, touch input, IME, platform glue, libc / runtime stubs
OUT = ('NpcUiNet', 'imeLocal', 'touch', 'CUSTOM', 'MX', 'midas', 'Net', 'android')

e = AndElf(B)
funcs = sorted((a, s, n) for n, (a, s, _) in e.fbyname.items())
starts = {a: n for a, s, n in funcs}
cited = set()
later = 0
for dp, _, fs in os.walk(os.path.join(ROOT, 'j2me', 'src')):
    for f in fs:
        if not f.endswith('.java'):
            continue
        t = open(os.path.join(dp, f), encoding='utf-8').read()
        later += len(re.findall(r'later', t))
        for m in re.finditer(r'@0x([0-9a-f]+)', t):
            a = int(m.group(1), 16)
            for cand in (a, a - 0x10000):          # Ghidra addresses = real + 0x10000
                if cand in starts:
                    cited.add(starts[cand])
                    break


def prefix(n):
    return n.split('_')[0] if '_' in n.strip('_') else n


def out_of_scope(n):
    return n.startswith('_') or n.islower() or any(n.startswith(p) or n.startswith(p.lower()) for p in OUT)


tot = collections.Counter()
got = collections.Counter()
for a, s, n in funcs:
    if out_of_scope(n):
        continue
    p = prefix(n)
    tot[p] += 1
    if n in cited:
        got[p] += 1
T, G = sum(tot.values()), sum(got.values())
print('functions in binary: %d; in scope: %d; cited by Java: %d (%.1f%%); "later" markers: %d' % (len(funcs), T, G, 100.0 * G / T, later))
for p, n in tot.most_common(40):
    print('  %-22s %4d / %4d' % (p, got[p], n))
if '--list' in sys.argv:
    for n in sorted(cited):
        print(n)
