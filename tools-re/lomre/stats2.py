import struct, sys, re
from collections import Counter
from capstone import *
from capstone.arm import *
import inv
from inv import starts, im, tb, td, relset, THUNK, JI
import ann
md = Cs(CS_ARCH_ARM, CS_MODE_ARM); md.detail = True
fs = sorted(starts)
bounds = fs + [0xf4a00]
tgt = Counter(); unk = 0
for fi in range(len(fs)):
    a, e = fs[fi], bounds[fi+1]
    pool = set(x for x in range(a, e, 4) if x in relset)
    # first sweep for pc-rel pools
    x = a
    ins = []
    while x < e:
        if x in pool: x += 4; continue
        d = list(md.disasm(td[x-tb:x-tb+4], x))
        if not d: x += 4; continue
        i = d[0]
        if i.mnemonic.startswith('ldr') and not i.mnemonic.startswith(('ldrb','ldrh','ldrs','ldrd')):
            for op in i.operands:
                if op.type == ARM_OP_MEM and op.mem.base == ARM_REG_PC and op.mem.index == 0:
                    pool.add(i.address + 8 + op.mem.disp)
        ins.append(i); x += 4
    regval = {}
    for i in ins:
        mnem = i.mnemonic
        if mnem.startswith('ldr') and '[pc' in i.op_str and not mnem.startswith(('ldrb','ldrh','ldrs')):
            p = i.address + 8 + [op for op in i.operands if op.type==ARM_OP_MEM][0].mem.disp
            v = struct.unpack_from('<I', td, p-tb)[0]
            regval[i.operands[0].reg] = ('lit', v, p in relset)
        elif mnem in ('bx','bxne','bxeq','bxhs') :
            r = i.operands[0].reg
            if r == ARM_REG_LR:
                tgt['RET'] += 1
            else:
                rv = regval.get(r)
                if rv and rv[0]=='lit':
                    v = rv[1]
                    if v in THUNK:
                        t = THUNK[v]
                        tgt['T:%x:%x %s' % (t[0], t[1], JI.get(t[1],'?') if t[0]==100 else '')] += 1
                    elif tb <= (v&~1) < tb+len(td):
                        tgt['TEXT:%x' % v] += 1
                    else:
                        tgt['LIT:%x' % v] += 1
                else:
                    tgt['REG:%s' % i.reg_name(r)] += 1
        if i.mnemonic.startswith(('b','ldm','ldr')) and False: pass
        # writes invalidate
        for op in i.operands:
            if op.type == ARM_OP_REG and op.access & CS_AC_WRITE and not (mnem.startswith('ldr') and '[pc' in i.op_str):
                regval.pop(op.reg, None)
for k,c in tgt.most_common(100): print(c, k)
