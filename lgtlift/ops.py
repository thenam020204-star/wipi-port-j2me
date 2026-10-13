"""Instruction semantics (data processing, memory access, flags) for the LGT lifter."""
from absval import Val, U, S, State, TV
from lift import (Unsupported, home_r, sx32, MASK, INV, ACCESS, bss_region, TBL_NAMES)
from armdec import FP, IP, SP, LR, PC

CMP_OPS = ('cmp', 'cmn', 'tst', 'teq')
ARITH = ('mov', 'mvn', 'add', 'sub', 'rsb', 'and', 'orr', 'eor', 'bic', 'lsl', 'lsr', 'asr', 'ror', 'mul', 'mla',
         'movw', 'movt', 'adc', 'sbc', 'rsc', 'rrx')
JOPS = {'add': 'iadd', 'sub': 'isub', 'and': 'iand', 'orr': 'ior', 'eor': 'ixor', 'lsl': 'ishl', 'lsr': 'iushr',
        'asr': 'ishr', 'mul': 'imul'}
SHIFT_J = {'lsl': 'ishl', 'lsr': 'iushr', 'asr': 'ishr'}


def fold(op, a, b):
    a &= MASK
    b &= MASK
    if op == 'add':
        return sx32(a + b)
    if op == 'sub':
        return sx32(a - b)
    if op == 'rsb':
        return sx32(b - a)
    if op == 'and':
        return sx32(a & b)
    if op == 'orr':
        return sx32(a | b)
    if op == 'eor':
        return sx32(a ^ b)
    if op == 'bic':
        return sx32(a & ~b)
    if op == 'mul':
        return sx32(a * b)
    if op == 'lsl':
        return sx32(a << (b & 31)) if (b & 255) < 32 else 0
    if op == 'lsr':
        return sx32(a >> (b & 31)) if (b & 255) < 32 else 0
    if op == 'asr':
        sa = sx32(a)
        return sx32(sa >> min(b & 255, 31))
    raise KeyError(op)


class OpsMixin:
    # ------------------------------------------------------------------ register writes
    def wr_int(self, st, rd, hint=None):
        """Value is on the JVM stack: store into the int local of rd."""
        self.protect(st, 'I', home_r(rd))
        em = self.em
        em.istore(em.loc('I', home_r(rd)))
        st.regs[rd] = Val('I', None, home_r(rd))

    def wr_ref(self, st, rd, hint=None):
        self.protect(st, 'R', home_r(rd))
        em = self.em
        em.astore(em.loc('R', home_r(rd)))
        st.regs[rd] = Val('R', None, home_r(rd), hint)

    def wr_const(self, st, rd, c):
        st.regs[rd] = Val('I', c & MASK if False else sx32(c), None)

    def wr_sym(self, st, rd, *sym):
        st.regs[rd] = Val('S', sym=tuple(sym))

    def flush_reg(self, st, r):
        """Materialise a lazy constant register into its JVM local."""
        v = st.regs[r]
        if v.h is None and v.kind() in ('I', 'R') and (v.c is not None):
            if v.kind() == 'I':
                self.em.iconst(v.c)
                self.wr_int(st, r)
            else:
                self.em.op('aconst_null')
                self.wr_ref(st, r, v.hint)

    def is_dead(self, rd):
        return rd < 13 and not ((self.cur_live >> rd) & 1)

    # ------------------------------------------------------------------ operand 2
    def opnd(self, st, o):
        t = o[0]
        if t == 'i':
            return ('c', sx32(o[1]))
        if t == 'r':
            return ('v', self.reg_val(st, o[1]))
        if t == 'sh':
            return ('sh', self.reg_val(st, o[1]), o[2], o[3])
        if t == 'shr':
            return ('shr', self.reg_val(st, o[1]), o[2], self.reg_val(st, o[3]))
        raise Unsupported('operand %s' % (o,))

    def opnd_const(self, od):
        if od[0] == 'c':
            return od[1]
        if od[0] == 'v':
            return self.const_of(od[1])
        if od[0] == 'sh':
            c = self.const_of(od[1])
            if c is not None:
                return fold(od[2], c, od[3])
        return None

    def push_opnd(self, od, want='I'):
        em = self.em
        t = od[0]
        if t == 'c':
            em.iconst(od[1])
        elif t == 'v':
            self.push(od[1], want)
        elif t == 'sh':
            kind, amt = od[2], od[3]
            self.push(od[1], 'I')
            if kind == 'ror':
                if amt == 0:
                    raise Unsupported('rrx')
                em.op('dup')
                em.iconst(amt)
                em.op('iushr')
                em.op('swap')
                em.iconst(32 - amt)
                em.op('ishl')
                em.op('ior')
            else:
                em.iconst(amt if amt else (31 if kind == 'asr' and False else 0))
                if kind in ('lsr', 'asr') and amt == 0:
                    raise Unsupported('shift by 32 encoding')
                em.op(SHIFT_J[kind])
        elif t == 'shr':
            self.push(od[1], 'I')
            self.push(od[3], 'I')
            if od[2] == 'ror':
                raise Unsupported('ror by register')
            em.op(SHIFT_J[od[2]])
        else:
            raise Unsupported('push_opnd %s' % (od,))

    # ------------------------------------------------------------------ dispatch
    def exec_insn(self, st, ins):
        op = ins.op
        if op in ARITH:
            return self.exec_arith(st, ins)
        if op in CMP_OPS:
            return self.exec_cmp(st, ins)
        if op in ACCESS:
            return self.exec_mem(st, ins)
        if op in ('push', 'stm', 'stmib', 'stmda', 'stmdb', 'ldm', 'ldmib', 'ldmda', 'ldmdb', 'pop', 'ldmia', 'stmia'):
            return self.exec_block_transfer(st, ins)
        if op == 'nop':
            return st
        raise Unsupported('opcode %s' % ins.op)

    # ------------------------------------------------------------------ arithmetic
    def exec_arith(self, st, ins):
        op, o = ins.op, ins.ops
        rd = o[0][1]
        if rd == PC:
            raise Unsupported('write to pc')
        if op in ('adc', 'sbc', 'rsc', 'rrx'):
            raise Unsupported('carry arithmetic %s' % op)
        if op in ('movw', 'movt'):
            if op == 'movw':
                st.regs[rd] = Val('I', sx32(o[1][1]), None)
                return st
            raise Unsupported('movt')
        if op == 'mov':
            return self.exec_mov(st, ins)
        if op == 'mvn':
            od = self.opnd(st, o[1])
            c = self.opnd_const(od)
            if c is not None:
                st.regs[rd] = Val('I', sx32(~c), None)
                return self.set_res_flags(st, ins, rd)
            if self.is_dead(rd) and not ins.s:
                return st
            self.push_opnd(od)
            self.em.iconst(-1)
            self.em.op('ixor')
            self.wr_int(st, rd)
            return self.set_res_flags(st, ins, rd)
        if op in ('lsl', 'lsr', 'asr', 'ror'):
            return self.exec_shift(st, ins)
        if op == 'mul':
            a, b = self.reg_val(st, o[1][1]), self.reg_val(st, o[2][1])
            self.want_kind(a, 'I'); self.want_kind(b, 'I')
            ca, cb = self.const_of(a), self.const_of(b)
            if ca is not None and cb is not None:
                st.regs[rd] = Val('I', fold('mul', ca, cb), None)
                return st
            if self.is_dead(rd) and not ins.s:
                return st
            self.push(a, 'I'); self.push(b, 'I'); self.em.op('imul')
            self.wr_int(st, rd)
            return self.set_res_flags(st, ins, rd)
        if op == 'mla':
            a, b, c = self.reg_val(st, o[1][1]), self.reg_val(st, o[2][1]), self.reg_val(st, o[3][1])
            for v in (a, b, c):
                self.want_kind(v, 'I')
            if self.is_dead(rd) and not ins.s:
                return st
            self.push(a, 'I'); self.push(b, 'I'); self.em.op('imul'); self.push(c, 'I'); self.em.op('iadd')
            self.wr_int(st, rd)
            return self.set_res_flags(st, ins, rd)
        # three-operand: add sub rsb and orr eor bic
        rn = o[1][1]
        a = self.reg_val(st, rn)
        bd = self.opnd(st, o[2])
        sym = self.sym_arith(st, op, rd, a, bd)
        if sym is not None:
            if ins.s:
                raise Unsupported('flag-setting symbolic arithmetic')
            st.regs[rd] = sym
            return st
        # copy-like: x +/- 0 keeps kind (refs for null-check idiom `subs r0,r4,#0`)
        cb = self.opnd_const(bd)
        if op in ('add', 'sub', 'orr', 'eor') and cb == 0 and bd[0] in ('c', 'v'):
            if ins.s and op == 'sub':
                st = self.copy_reg(st, rd, rn)
                st.flags = ('cmp', st.regs[rd] if False else a, Val('I', 0, None))
                return st
            return self.copy_reg(st, rd, rn)
        # integer arithmetic
        self.want_kind(a, 'I')
        if bd[0] == 'v':
            self.want_kind(bd[1], 'I')
        ca = self.const_of(a)
        if ca is not None and cb is not None and op in ('add', 'sub', 'rsb', 'and', 'orr', 'eor', 'bic'):
            st.regs[rd] = Val('I', fold(op, ca, cb), None)
            if ins.s:
                st.flags = ('cmp', a, self.const_val(cb)) if op == 'sub' else ('res', st.regs[rd])
            return st
        if self.is_dead(rd) and not ins.s:
            return st
        em = self.em
        if op == 'rsb':
            self.push_opnd(bd); self.push(a, 'I'); em.op('isub')
        elif op == 'bic':
            self.push(a, 'I'); self.push_opnd(bd); em.iconst(-1); em.op('ixor'); em.op('iand')
        else:
            self.push(a, 'I'); self.push_opnd(bd); em.op(JOPS[op])
        self.wr_int(st, rd)
        if ins.s:
            if op == 'sub':
                st.flags = ('cmp', a, self.od_val(bd))
            elif op == 'add':
                st.flags = ('cmn', a, self.od_val(bd))
            elif op == 'rsb':
                st.flags = ('cmp', self.od_val(bd), a)
            else:
                st.flags = ('res', st.regs[rd])
        return st

    def const_val(self, c):
        return Val('I', sx32(c), None)

    def od_val(self, od):
        """Operand descriptor -> Val usable in flags (only simple operands)."""
        if od[0] == 'c':
            return Val('I', od[1], None)
        if od[0] == 'v':
            return od[1]
        raise Unsupported('shifted operand in flag-setting instruction')

    def set_res_flags(self, st, ins, rd):
        if ins.s:
            st.flags = ('res', st.regs[rd])
        return st

    def exec_mov(self, st, ins):
        o = ins.ops
        rd = o[0][1]
        src = o[1]
        if src[0] == 'i':
            if ins.s:
                raise Unsupported('movs imm')
            st.regs[rd] = Val('I', sx32(src[1]), None)
            return st
        if src[0] == 'r':
            if src[1] == PC and rd == LR:
                st.regs[LR] = S('retaddr')
                return st
            if rd == SP or rd == FP or rd == IP:
                st.regs[rd] = st.regs[src[1]]
                return st
            st = self.copy_reg(st, rd, src[1])
            if ins.s:
                st.flags = ('res', st.regs[rd])
            return st
        # shifted register as mov
        od = self.opnd(st, src)
        c = self.opnd_const(od)
        if c is not None:
            st.regs[rd] = Val('I', c, None)
            return self.set_res_flags(st, ins, rd)
        v = od[1]
        if v.k == 'S' and v.sym[0] == 'tabidx' and od[0] == 'sh' and od[2] == 'lsl':
            st.regs[rd] = S('tabscaled', v.sym[1], v.sym[2], od[3])
            return st
        self.want_kind(v, 'I')
        if self.is_dead(rd) and not ins.s:
            return st
        self.push_opnd(od)
        self.wr_int(st, rd)
        return self.set_res_flags(st, ins, rd)

    def exec_shift(self, st, ins):
        op, o = ins.op, ins.ops
        rd = o[0][1]
        if len(o) == 2 and o[1][0] == 'sh':
            a = self.reg_val(st, o[1][1])
            if o[1][2] != op:
                raise Unsupported('shift kind mismatch')
            sh = ('i', o[1][3])
        elif len(o) == 3:
            a = self.reg_val(st, o[1][1])
            sh = o[2]
        else:
            raise Unsupported('shift operands %s' % (o,))
        if sh[0] == 'i':
            amt = sh[1]
            if a.k == 'S' and a.sym[0] == 'tabidx' and op == 'lsl':
                if amt == 16:
                    st.regs[rd] = S('tabidx16', a.sym[1], a.sym[2])
                else:
                    st.regs[rd] = S('tabscaled', a.sym[1], a.sym[2], amt)
                return st
            od = ('sh', a, op, amt)
        else:
            od = ('shr', a, op, self.reg_val(st, sh[1]))
        c = self.opnd_const(od) if od[0] == 'sh' else None
        if c is not None:
            st.regs[rd] = Val('I', c, None)
            return self.set_res_flags(st, ins, rd)
        self.want_kind(a, 'I')
        if self.is_dead(rd) and not ins.s:
            return st
        self.push_opnd(od)
        self.wr_int(st, rd)
        return self.set_res_flags(st, ins, rd)

    def copy_reg(self, st, rd, rn):
        v = st.regs[rn]
        self.use(v)
        k = v.kind()
        if k == 'U':
            st.regs[rd] = U
            return st
        if k == 'S':
            st.regs[rd] = v
            return st
        if v.h is None and v.c is not None:        # lazy constant
            st.regs[rd] = v
            return st
        if self.is_dead(rd):
            return st
        if k == 'T':
            kk = v.tv.kind()
            if kk is None:
                # unresolved: keep alias semantic via fresh Val sharing the TV; emission happens after resolution
                st.regs[rd] = Val('T', None, home_r(rd), None, None, v.tv)
                self.pending_copy = True
                return st
            k = kk
        em = self.em
        if k == 'I':
            em.iload(em.loc('I', v.h))
            self.wr_int(st, rd)
            if v.tv is not None:
                st.regs[rd] = Val('T', None, home_r(rd), None, None, v.tv) if v.k == 'T' else st.regs[rd]
        else:
            em.aload(em.loc('R', v.h))
            self.wr_ref(st, rd, v.hint)
            if v.k == 'T':
                st.regs[rd] = Val('T', None, home_r(rd), v.hint, None, v.tv)
        return st

    # ------------------------------------------------------------------ symbolic arithmetic
    def sym_arith(self, st, op, rd, a, bd):
        """add/sub involving symbolic values; returns Val or None."""
        if op not in ('add', 'sub'):
            return None
        b = bd[1] if bd[0] == 'v' else None
        if a.k != 'S' and not (b is not None and b.k == 'S'):
            return None
        cb = self.opnd_const(bd)
        sign = 1 if op == 'add' else -1
        if a.k == 'S':
            t = a.sym[0]
            if cb is not None:
                c = sign * cb
                if t == 'stk':
                    return S('stk', a.sym[1] + c)
                if t == 'addr':
                    return S('addr', a.sym[1], a.sym[2] + c)
                if t == 'ptr':
                    return S('ptr', a.sym[1], a.sym[2], a.sym[3], a.sym[4], a.sym[5] + c)
                if t == 'payload':
                    return S('ptr', a.sym[1], a.sym[2], None, 1, c)
                if t == 'vtable':
                    return S('vtoff', a.sym[1], a.sym[2], c)
                if t == 'vslot':
                    return S('vslot', a.sym[1], a.sym[2], a.sym[3], a.sym[4], a.sym[5] + c)
            if op == 'add' and bd[0] == 'sh' and bd[1].k == 'S':
                bs = bd[1].sym
                if t == 'vtable' and bs[0] == 'tabidx' and bd[2] == 'lsl' and bd[3] == 2:
                    return S('vslot', a.sym[1], a.sym[2], bs[1], bs[2], 0)
            if op == 'add' and b is not None and b.k == 'S' and b.sym[0] == 'tabscaled' and t == 'vtable':
                return S('vslot', a.sym[1], a.sym[2], b.sym[1], b.sym[2], 0)
            if op == 'add' and t == 'payload' and bd[0] in ('v', 'sh'):
                idx = bd[1]
                sc = 1 if bd[0] == 'v' else (1 << bd[3])
                if bd[0] == 'sh' and bd[2] != 'lsl':
                    raise Unsupported('non-lsl scaled index')
                self.want_kind(idx, 'I')
                ci = self.const_of(idx)
                if ci is not None:
                    return S('ptr', a.sym[1], a.sym[2], None, 1, ci * sc)
                return S('ptr', a.sym[1], a.sym[2], ('h', 'I', idx.h), sc, 0)
            raise Unsupported('symbolic %s %s with %s' % (op, a.sym[0], bd[0]))
        # a is plain, b symbolic (commutative add)
        if op == 'add' and bd[0] == 'v' and b.k == 'S' and b.sym[0] == 'payload':
            self.want_kind(a, 'I')
            ci = self.const_of(a)
            if ci is not None:
                return S('ptr', b.sym[1], b.sym[2], None, 1, ci)
            return S('ptr', b.sym[1], b.sym[2], ('h', 'I', a.h), 1, 0)
        raise Unsupported('symbolic operand %s' % (b.sym,))
