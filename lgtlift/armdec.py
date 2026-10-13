"""ARM (A32) decoder for the subset used by LGT's Java->ARM compiler, built on capstone details.

Normalised instruction: Insn(addr, op, cc, s, ops, ...). Operands are tuples:
  ('r', reg)                          register
  ('i', value)                        immediate (signed 32)
  ('sh', reg, kind, amt)              register shifted by immediate (kind lsl/lsr/asr/ror), amt int
  ('shr', reg, kind, reg2)            register shifted by register
  ('m', base, index, shift_kind, shift_amt, disp, sub, wb, post)   memory operand
  ('rl', [regs])                      register list (ldm/stm/push/pop)
  ('t', target)                       branch target address
"""
import struct
from capstone import *
from capstone.arm import *

CC = {ARM_CC_EQ: 'eq', ARM_CC_NE: 'ne', ARM_CC_HS: 'hs', ARM_CC_LO: 'lo', ARM_CC_MI: 'mi', ARM_CC_PL: 'pl',
      ARM_CC_VS: 'vs', ARM_CC_VC: 'vc', ARM_CC_HI: 'hi', ARM_CC_LS: 'ls', ARM_CC_GE: 'ge', ARM_CC_LT: 'lt',
      ARM_CC_GT: 'gt', ARM_CC_LE: 'le', ARM_CC_AL: 'al'}
INV = {'eq': 'ne', 'ne': 'eq', 'hs': 'lo', 'lo': 'hs', 'mi': 'pl', 'pl': 'mi', 'vs': 'vc', 'vc': 'vs',
       'hi': 'ls', 'ls': 'hi', 'ge': 'lt', 'lt': 'ge', 'gt': 'le', 'le': 'gt'}
SH = {ARM_SFT_LSL: 'lsl', ARM_SFT_LSR: 'lsr', ARM_SFT_ASR: 'asr', ARM_SFT_ROR: 'ror', ARM_SFT_RRX: 'rrx',
      ARM_SFT_LSL_REG: 'lsl', ARM_SFT_LSR_REG: 'lsr', ARM_SFT_ASR_REG: 'asr', ARM_SFT_ROR_REG: 'ror'}
REGN = {}
for n, v in (('r0', ARM_REG_R0), ('r1', ARM_REG_R1), ('r2', ARM_REG_R2), ('r3', ARM_REG_R3), ('r4', ARM_REG_R4),
             ('r5', ARM_REG_R5), ('r6', ARM_REG_R6), ('r7', ARM_REG_R7), ('r8', ARM_REG_R8), ('r9', ARM_REG_R9),
             ('r10', ARM_REG_R10), ('r11', ARM_REG_R11), ('r12', ARM_REG_R12), ('sp', ARM_REG_SP),
             ('lr', ARM_REG_LR), ('pc', ARM_REG_PC)):
    REGN[v] = ['r0', 'r1', 'r2', 'r3', 'r4', 'r5', 'r6', 'r7', 'r8', 'r9', 'r10', 'r11', 'r12', 'sp', 'lr', 'pc'].index(n)
REGN[ARM_REG_SB] = 9; REGN[ARM_REG_SL] = 10; REGN[ARM_REG_FP] = 11; REGN[ARM_REG_IP] = 12
FP, IP, SP, LR, PC = 11, 12, 13, 14, 15

_md = Cs(CS_ARCH_ARM, CS_MODE_ARM)
_md.detail = True


class Insn:
    __slots__ = ('addr', 'op', 'cc', 's', 'ops', 'text', 'size', 'wb')

    def __init__(self, addr, op, cc, s, ops, text, wb=False):
        self.addr = addr; self.op = op; self.cc = cc; self.s = s; self.ops = ops; self.text = text; self.size = 4; self.wb = wb

    def __repr__(self):
        return '%x: %s%s%s %s' % (self.addr, self.op, '' if self.cc == 'al' else self.cc, 's' if self.s else '', self.text)


def _imm(v):
    v &= 0xffffffff
    return v - (1 << 32) if v & 0x80000000 else v


BASE_OPS = ('mov', 'mvn', 'add', 'sub', 'rsb', 'adc', 'sbc', 'rsc', 'and', 'orr', 'eor', 'bic', 'cmp', 'cmn', 'tst', 'teq',
            'mul', 'mla', 'umull', 'smull', 'umlal', 'smlal', 'ldrsb', 'ldrsh', 'ldrb', 'ldrh', 'ldrd', 'ldr', 'strb',
            'strh', 'strd', 'str', 'ldm', 'stm', 'push', 'pop', 'bl', 'bx', 'blx', 'b', 'lsl', 'lsr', 'asr', 'ror', 'rrx',
            'nop', 'swp', 'swi', 'svc', 'clz')


def _split(mnem, cc):
    """Split capstone mnemonic into (base op, set-flags, writeback-suffix variants)."""
    m = mnem
    if cc != 'al' and m.endswith(cc):
        m = m[:-len(cc)]
    # stm/ldm addressing-mode suffixes
    return m


def decode(word, addr):
    """Decode one A32 word. Returns Insn or None (undecodable/data)."""
    d = list(_md.disasm(struct.pack('<I', word), addr))
    if not d:
        return None
    i = d[0]
    cc = CC.get(i.cc, 'al')
    m = i.mnemonic
    s = bool(i.update_flags)
    base = _split(m, cc)
    if s and base.endswith('s') and base[:-1] in BASE_OPS and base not in BASE_OPS:
        base = base[:-1]
    ops = []
    for op in i.operands:
        if op.type == ARM_OP_REG:
            r = REGN.get(op.reg)
            if r is None:
                return Insn(addr, 'unk', cc, s, [], i.op_str)
            if op.shift.type != ARM_SFT_INVALID:
                kind = SH[op.shift.type]
                if op.shift.type in (ARM_SFT_LSL_REG, ARM_SFT_LSR_REG, ARM_SFT_ASR_REG, ARM_SFT_ROR_REG):
                    ops.append(('shr', r, kind, REGN[op.shift.value]))
                else:
                    ops.append(('sh', r, kind, op.shift.value))
            else:
                ops.append(('r', r))
        elif op.type == ARM_OP_IMM:
            if base in ('b', 'bl', 'blx'):
                ops.append(('t', op.imm & 0xffffffff))
            else:
                ops.append(('i', _imm(op.imm)))
        elif op.type == ARM_OP_MEM:
            idx = REGN[op.mem.index] if op.mem.index else None
            sk = SH.get(op.shift.type) if op.shift.type != ARM_SFT_INVALID else None
            ops.append(('m', REGN[op.mem.base], idx, sk, op.shift.value if sk else 0, op.mem.disp,
                        bool(op.subtracted), bool(i.writeback), bool(getattr(i, 'post_index', False))))
        else:
            return Insn(addr, 'unk', cc, s, [], i.op_str)
    # register lists (ldm/stm/push/pop): capstone puts them as consecutive REG operands
    if base in ('ldm', 'stm', 'push', 'pop') or base.startswith(('ldm', 'stm')):
        regs = [o[1] for o in ops if o[0] == 'r']
        if base.startswith(('ldm', 'stm')) or base in ('push', 'pop'):
            if base in ('push', 'pop'):
                ops = [('rl', regs)]
            else:
                ops = [ops[0], ('rl', regs[1:])]
    return Insn(addr, base, cc, s, ops, i.op_str, bool(i.writeback))
