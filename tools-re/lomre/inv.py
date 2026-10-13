import struct, sys
from collections import Counter, defaultdict
from capstone import *
from capstone.arm import *
from funcs import load
import ann
from ann import im, tb, td, rels, relset, THUNK, JI
from classes import scan
from meth import methods

# function starts
starts = set()
for a in range(tb, tb+len(td)-8, 4):
    w = struct.unpack_from('<I', td, a-tb)[0]
    if w == 0xe1a0c00d:
        w2 = struct.unpack_from('<I', td, a-tb+4)[0]
        if (w2 & 0xffff0000) == 0xe92d0000: starts.add(a)
classes = scan(im)
mstarts = set()
for r,c in classes.items():
    for m in methods(im,c):
        mstarts.add(m['code'])
if __name__ == "__main__": print('apcs funcs', len(starts), 'method-table funcs', len(mstarts), 'method funcs not apcs', [hex(x) for x in sorted(mstarts-starts)][:10])
# bl targets and relocated text pointers
bltargets = set()
md = Cs(CS_ARCH_ARM, CS_MODE_ARM)
for a in sorted(starts):
    pass
relptr = set()
for o in relset:
    v = struct.unpack_from('<I', td, o-tb)[0]
    if tb <= v < tb+len(td): relptr.add(v & ~1)
if __name__ == "__main__": print('reloc pointers into text', len(relptr), 'of which apcs starts', len(relptr & starts))
nonapcs = sorted(relptr - starts)
if __name__ == "__main__": print('non-apcs reloc targets', len(nonapcs), [hex(x) for x in nonapcs[:40]])
