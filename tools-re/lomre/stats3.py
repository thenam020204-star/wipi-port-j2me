import struct, re
from collections import Counter
from capstone import *
from capstone.arm import *
import inv
from inv import starts, im, tb, td, relset
md = Cs(CS_ARCH_ARM, CS_MODE_ARM); md.detail = True
fs = sorted(starts); bounds = fs + [0xf4a00]
want = Counter(); ex = {}
for fi in range(len(fs)):
    a, e = fs[fi], bounds[fi+1]
    pool = set(x for x in range(a, e, 4) if x in relset)
    x = a
    while x < e:
        if x in pool: x += 4; continue
        d = list(md.disasm(td[x-tb:x-tb+4], x))
        if not d: x += 4; continue
        i = d[0]; m = i.mnemonic
        if i.mnemonic.startswith('ldr') and not i.mnemonic.startswith(('ldrb','ldrh','ldrs','ldrd')):
            for op in i.operands:
                if op.type == ARM_OP_MEM and op.mem.base == ARM_REG_PC and op.mem.index == 0:
                    pool.add(i.address + 8 + op.mem.disp)
        base = re.sub(r'(eq|ne|hs|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le)$', '', m)
        if re.match(r'^(adc|sbc|umull|smull|umlal|smlal|rsc|swp|eor|orr|and|bic|tst|teq|ror|rrx|lsr|mvn|cmn|str[dh]|ldrd|ldm|stm|mla|mul)', m) or ('pc' in i.op_str and ('ldr' in m or 'add' in m or 'mov' in m) and '[pc' not in i.op_str):
            want[m] += 1; ex.setdefault(m, []).append(x)
        x += 4
for k,v in sorted(want.items(), key=lambda kv: -kv[1])[:60]:
    print(v, k, [hex(a) for a in ex[k][:3]])
