"""Annotated ARM disassembly of a function in the LGT binary."""
import sys, struct
from capstone import *
from capstone.arm import *
from elfimg import Img
from funcs import load
im, tb, td, rels = load()
relset = {o for o,i in rels}
# thunk table: code at X (str lr; bl) followed by pair (table,fn) at X+8
THUNK = {}
for a in range(0x1400000, 0x1400000+len(im.mem['.data'][1])-12, 4):
    try:
        if im.u32(a) == 0xe52de004 and (im.u32(a+4) >> 24) == 0xeb:
            THUNK[a] = (im.u32(a+8), im.u32(a+12))
    except KeyError: pass
JI = {0x03:'unk0',0x06:'DestroyCtx',0x07:'CreateCtx',0x09:'StringLiteral',0x0a:'IfaceDispatch',0x0b:'RegisterClass',0x0c:'ResolveClass',0x0d:'InitClass',0x0e:'ArrayType',0x0f:'Instantiate',0x10:'NewArray',0x11:'NewMultiArray',0x12:'IsAssignable',0x13:'LinkPublic',0x14:'LinkImported',0x1f:'PushExcFrame',0x20:'PopExcFrame',0x21:'Throw',0x22:'NPE',0x23:'AIOOBE',0x25:'ArithExc',0x54:'Unk54',0x55:'Unk55',0x56:'MonEnter',0x57:'MonExit',0x61:'StoreRefArray',0xe1:'StringClass',0xe2:'StringArrayClass',0xfa:'StoreRefArrayUnchecked'}
def tname(a):
    t = THUNK.get(a)
    if not t: return None
    return 'T[%x:%x %s]' % (t[0], t[1], JI.get(t[1],'?') if t[0]==0x64 else '')
def sym(v):
    n = tname(v)
    if n: return n
    f = im.find(v)
    if f: return '%s@%x' % (f[0], v)
    return hex(v)
md = Cs(CS_ARCH_ARM, CS_MODE_ARM); md.detail = True
def ann(start, end):
    pool = set()
    # first pass: find pool words via ldr rX,[pc,#imm]
    code = bytes(td[start-tb:end-tb])
    ins = list(md.disasm(code, start))
    for i in ins:
        if i.mnemonic.startswith('ldr') and i.op_str.find('[pc') >= 0:
            for op in i.operands:
                if op.type == ARM_OP_MEM and op.mem.base == ARM_REG_PC:
                    pool.add(i.address + 8 + op.mem.disp)
    a = start
    while a < end:
        if a in pool or a in relset:
            v = struct.unpack_from('<I', td, a-tb)[0]
            print('%x:\t.word %#x\t; %s' % (a, v, sym(v) if a in relset else ''))
            a += 4; continue
        w = struct.unpack_from('<I', td, a-tb)[0]
        d = list(md.disasm(td[a-tb:a-tb+4], a))
        if not d:
            print('%x:\t.word %#x' % (a, w)); a += 4; continue
        i = d[0]
        note = ''
        if i.mnemonic.startswith('ldr') and '[pc' in i.op_str:
            for op in i.operands:
                if op.type == ARM_OP_MEM and op.mem.base == ARM_REG_PC:
                    p = i.address + 8 + op.mem.disp
                    v = struct.unpack_from('<I', td, p-tb)[0]
                    note = '; =' + (sym(v) if p in relset else hex(v))
        if i.mnemonic.startswith('bl') and i.op_str.startswith('#'):
            t = int(i.op_str[1:], 16); note = '; -> ' + (sym(t))
        print('%x:\t%s\t%s\t%s' % (a, i.mnemonic, i.op_str, note))
        a += 4
if __name__ == '__main__':
    ann(int(sys.argv[1],16), int(sys.argv[2],16))
