import struct, sys, re
from collections import Counter
from capstone import *
from capstone.arm import *
import inv
from inv import starts, im, tb, td, relset, THUNK, JI
md = Cs(CS_ARCH_ARM, CS_MODE_ARM); md.detail = True
fs = sorted(starts)
end_game = 0xf4a00  # helper/data tail starts around here
bounds = fs + [end_game]
mn = Counter(); shapes = Counter(); calls = Counter(); total = 0
badwords = 0
pool_total = 0
for fi in range(len(fs)):
    a, e = fs[fi], bounds[fi+1]
    # collect pool words: reloc'd words + pc-relative ldr targets
    pool = set()
    for x in range(a, e, 4):
        if x in relset: pool.add(x)
    w = {}
    x = a
    while x < e:
        if x in pool: x += 4; continue
        word = struct.unpack_from('<I', td, x-tb)[0]
        d = list(md.disasm(td[x-tb:x-tb+4], x))
        if not d:
            badwords += 1; x += 4; continue
        i = d[0]
        # pc-relative ldr: literal pool (possibly non-reloc constant)
        if i.mnemonic.startswith('ldr') and not i.mnemonic.startswith(('ldrb','ldrh','ldrs','ldrd')):
            for op in i.operands:
                if op.type == ARM_OP_MEM and op.mem.base == ARM_REG_PC and op.mem.index == 0:
                    pool.add(i.address + 8 + op.mem.disp)
        mn[i.mnemonic] += 1; total += 1
        x += 4
print('total instr', total, 'bad words', badwords)
for m,c in mn.most_common(120): print('%6d %s' % (c, m))
