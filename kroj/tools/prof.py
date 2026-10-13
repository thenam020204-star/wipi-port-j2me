#!/usr/bin/env python3
"""aggregate a HL5 profile by function: prof.py profile.txt [top]"""
import sys, struct, bisect
sys.path.insert(0, '/home/claude/work/hl5/tools')
import img
prof = {}
for l in open(sys.argv[1]):
    a, c = l.split(); prof[int(a, 16)] = int(c)
tot = sum(prof.values())
a0, blob = img.text_bytes()
starts = []
for o in range(0, len(blob) - 1, 2):
    h = struct.unpack_from('<H', blob, o)[0]
    if (h & 0xFF00) == 0xB500: starts.append(o + a0)
fn = {}
for a, c in prof.items():
    i = bisect.bisect_right(starts, a) - 1
    f = starts[i] if i >= 0 else 0
    if a >= 0x1400000 or a < a0: f = a & ~0xF
    fn.setdefault(f, [0, set()]); fn[f][0] += c; fn[f][1].add(a)
print('total', tot)
acc = 0
for f, (c, addrs) in sorted(fn.items(), key=lambda x: -x[1][0])[:int(sys.argv[2]) if len(sys.argv) > 2 else 40]:
    acc += c
    print('%x %6.2f%% cum %5.1f%% span %x-%x' % (f, 100 * c / tot, 100 * acc / tot, min(addrs), max(addrs)))
