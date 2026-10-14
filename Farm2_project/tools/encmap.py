#!/usr/bin/env python3
"""Build /enc/map.bin: every CP949 double-byte code found in the game's text
files (+ optional extra table of localisation code points).
usage: encmap.py <resource_root> <out_map.bin> [extra.tsv(code_hex<TAB>char)]"""
import os, struct, sys
root, out = sys.argv[1], sys.argv[2]
TEXT_DIRS = ('Data', 'Map')
m = {}
for d in TEXT_DIRS:
    for r, _, fs in os.walk(os.path.join(root, d)):
        for f in fs:
            b = open(os.path.join(r, f), 'rb').read()
            i = 0
            while i < len(b) - 1:
                a, t = b[i], b[i + 1]
                # KS X 1001 symbol rows A1-AC and Hangul rows B0-C8 (CP949 extension codes are
                # only taken from the exact text list given as argv[3])
                if (0xA1 <= a <= 0xAC or 0xB0 <= a <= 0xC8) and 0xA1 <= t <= 0xFE:
                    try:
                        ch = bytes([a, t]).decode('cp949')
                        m[(a << 8) | t] = ord(ch)
                    except UnicodeDecodeError:
                        pass
                    i += 2
                else:
                    i += 1
if len(sys.argv) > 3:
    for line in open(sys.argv[3], encoding='utf-8'):
        line = line.rstrip('\n')
        if not line or line.startswith('#'):
            continue
        c, ch = line.split('\t')
        m[int(c, 16)] = ord(ch)
codes = sorted(m)
with open(out, 'wb') as o:
    o.write(struct.pack('>H', len(codes)))
    for c in codes:
        o.write(struct.pack('>HH', c, m[c]))
print('encmap: %d codes -> %s (%d bytes)' % (len(codes), out, 2 + 4 * len(codes)))
