"""Compare/flags, conditional jumps and memory access for the LGT lifter."""
from absval import Val, U, S, State, TV
from lift import (Unsupported, home_r, sx32, MASK, INV, ACCESS, bss_region, TBL_NAMES, parse_desc, desc_kind)
from armdec import FP, IP, SP, LR, PC

MIN = -0x80000000
CC_SIGNED = {'lt': 'iflt', 'ge': 'ifge', 'gt': 'ifgt', 'le': 'ifle'}
CC_ICMP = {'eq': 'if_icmpeq', 'ne': 'if_icmpne', 'lt': 'if_icmplt', 'ge': 'if_icmpge', 'gt': 'if_icmpgt',
           'le': 'if_icmple'}
CC_ZERO = {'eq': 'ifeq', 'ne': 'ifne', 'lt': 'iflt', 'ge': 'ifge', 'gt': 'ifgt', 'le': 'ifle'}
UNS = {'lo': 'lt', 'hs': 'ge', 'hi': 'gt', 'ls': 'le'}


def elem_hint(h):
    """Array descriptor -> element hint (class internal name, or array descriptor)."""
    if not h or h[0] != '[' or len(h) < 2:
        return None
    e = h[1:]
    if e[0] == 'L':
        return e[1:-1]
    if e[0] == '[':
        return e
    return None


class MemMixin:
    # ------------------------------------------------------------------ compare
    def exec_cmp(self, st, ins):
        op, o = ins.op, ins.ops
        a = self.reg_val(st, o[0][1])
        bd = self.opnd(st, o[1])
        b = self.od_val(bd)
        self.use(b)
        if a.kind() == 'T' or b.kind() == 'T':
            ka, kb = a.kind(), b.kind()
            za = ka == 'I' and a.c == 0 and a.h is None
            zb = kb == 'I' and b.c == 0 and b.h is None
            if ka == 'T' and kb in ('I', 'R') and not zb:
                self.want_kind(a, kb)
            elif kb == 'T' and ka in ('I', 'R') and not za:
                self.want_kind(b, ka)
            elif ka == 'T' and kb == 'T':
                if a.tv.unify(b.tv):
                    self.changed = True
        if op == 'cmn':
            cb = self.const_of(b)
            if cb is not None and cb not in (0, MIN):
                st.flags = ('cmp', a, Val('I', sx32(-cb), None))
                return st
        st.flags = (op, a, b)
        return st

    # ------------------------------------------------------------------ conditional jumps
    def push_cmp_operand(self, v, want):
        k = v.kind()
        if k == 'S':
            raise Unsupported('compare of symbolic value %s' % (v.sym,))
        self.push(v, want)

    def cond_jump(self, st, cc, label, want=True):
        """Emit a jump to `label` taken iff (cc holds) == want."""
        if st.flags is None:
            raise Unsupported('flags undefined at conditional use (cc=%s)' % cc)
        if not want:
            cc = INV[cc]
        self.flag_jump(st.flags, cc, label)

    def flag_jump(self, f, cc, label):
        em = self.em
        kind = f[0]
        if kind == 'sel':
            _, pcc, pflags, newf, oldf = f
            Lold, Ldone = em.label(), em.label()
            self.flag_jump(pflags, INV[pcc], Lold)
            self.flag_jump(newf, cc, label)
            em.op('goto', Ldone)
            em.mark(Lold)
            self.flag_jump(oldf, cc, label)
            em.mark(Ldone)
            return
        if kind == 'res':
            a, b = f[1], Val('I', 0, None)
            kind = 'cmp'
        else:
            a, b = f[1], f[2]
        ka, kb = a.kind(), b.kind()
        if kind == 'cmp':
            if cc in ('eq', 'ne'):
                refish = ka == 'R' or kb == 'R' or (ka == 'T' and kb == 'T')
                if ka == 'T' and kb == 'I' and b.c == 0 and b.h is None:
                    k0 = a.tv.kind()
                    if k0 is None:
                        if not self.final:
                            return          # null/zero test does not decide int-vs-ref
                        k0 = 'I'
                    ka = k0
                if ka == 'R' or kb == 'R':
                    if kb == 'I' and b.c == 0 or (kb == 'R' and b.c == 0 and b.h is None):
                        self.push(a, 'R')
                        em.op('ifnull' if cc == 'eq' else 'ifnonnull', label)
                        return
                    if ka == 'R' and ka == kb and a.c == 0 and a.h is None:
                        self.push(b, 'R')
                        em.op('ifnull' if cc == 'eq' else 'ifnonnull', label)
                        return
                    self.push(a, 'R')
                    self.push(b, 'R')
                    em.op('if_acmpeq' if cc == 'eq' else 'if_acmpne', label)
                    return
                if kb == 'I' and b.c == 0 and b.h is None:
                    self.push_cmp_operand(a, 'I')
                    em.op(CC_ZERO[cc], label)
                    return
                self.push_cmp_operand(a, 'I')
                self.push_cmp_operand(b, 'I')
                em.op(CC_ICMP[cc], label)
                return
            if cc in CC_SIGNED:
                if kb == 'I' and b.c == 0 and b.h is None:
                    self.push_cmp_operand(a, 'I')
                    em.op(CC_ZERO[cc], label)
                else:
                    self.push_cmp_operand(a, 'I')
                    self.push_cmp_operand(b, 'I')
                    em.op(CC_ICMP[cc], label)
                return
            if cc in UNS:
                sc = UNS[cc]
                cb = self.const_of(b)
                ca = self.const_of(a)
                if cb is not None and cb >= 0 and False:
                    pass
                self.push_cmp_operand(a, 'I')
                if ca is None:
                    em.iconst(MIN)
                    em.op('ixor')
                self.push_cmp_operand(b, 'I')
                if cb is None:
                    em.iconst(MIN)
                    em.op('ixor')
                em.op(CC_ICMP[sc], label)
                return
            if cc in ('mi', 'pl'):
                self.push_cmp_operand(a, 'I')
                if not (kb == 'I' and b.c == 0 and b.h is None):
                    self.push_cmp_operand(b, 'I')
                    em.op('isub')
                em.op('iflt' if cc == 'mi' else 'ifge', label)
                return
            raise Unsupported('condition %s on cmp' % cc)
        if kind == 'cmn':
            self.push_cmp_operand(a, 'I')
            self.push_cmp_operand(b, 'I')
            em.op('iadd')
            if cc in ('eq', 'ne', 'lt', 'ge', 'gt', 'le', 'mi', 'pl'):
                em.op({'mi': 'iflt', 'pl': 'ifge'}.get(cc) or CC_ZERO[cc], label)
                return
            raise Unsupported('condition %s on cmn' % cc)
        if kind in ('tst', 'teq'):
            self.push_cmp_operand(a, 'I')
            self.push_cmp_operand(b, 'I')
            em.op('iand' if kind == 'tst' else 'ixor')
            if cc in ('eq', 'ne'):
                em.op(CC_ZERO[cc], label)
                return
            if cc in ('mi', 'pl'):
                em.op('iflt' if cc == 'mi' else 'ifge', label)
                return
            raise Unsupported('condition %s on %s' % (cc, kind))
        raise Unsupported('flags %s' % (kind,))

    # ------------------------------------------------------------------ pool / constant loads
    def pool_value(self, addr_of_word):
        B = self.B
        w = B.word(addr_of_word)
        if addr_of_word not in B.reloc_words:
            return Val('I', sx32(w), None)
        if w in B.thunks:
            t, f = B.thunks[w]
            return S('thunk', t, f)
        if w in self.text_thunks:
            t, f = self.text_thunks[w]
            return S('thunk', t, f)
        if B.tb <= w < B.tend:
            if w & ~1 in (0xf4a60, 0xf49cc):
                return S('fn', w & ~1)
            if w in B.func_starts or w in self.W.classrec_fns:
                return S('fn', w)
            return S('addr', 'text', w)
        if w >= 0x1500000:
            return S('addr', 'bss', w)
        if w in self.M.by_rec:
            return S('classrec', self.M.by_rec[w].name)
        if w >= 0x1400000:
            return S('addr', 'data', w)
        return Val('I', sx32(w), None)

    # ------------------------------------------------------------------ memory access
    def exec_mem(self, st, ins):
        op = ins.op
        width, signed = ACCESS[op]
        is_load = op.startswith('ldr')
        rt = ins.ops[0][1]
        m = ins.ops[1]
        _, base_r, idx_r, sk, sa, disp, sub, wb, post = m
        if wb or post:
            raise Unsupported('write-back addressing')
        if is_load and base_r == PC:
            if idx_r is not None:
                raise Unsupported('pc-relative indexed load')
            st.regs[rt] = self.pool_value(ins.addr + 8 + disp)
            return st
        base = self.reg_val(st, base_r)
        idx = None
        if idx_r is not None:
            iv = self.reg_val(st, idx_r)
            if sub:
                raise Unsupported('negative register offset')
            idx = (iv, sk, sa)
        kb = base.kind()
        if kb == 'T':
            self.want_kind(base, 'R')
            kb = 'R'
        if kb == 'U':
            raise Unsupported('memory access through undefined register r%d' % base_r)
        if kb == 'I':
            raise Unsupported('memory access through plain int base r%d' % base_r)
        if kb == 'R':
            return self.mem_object(st, ins, base, idx, disp, width, signed, is_load, rt)
        tag = base.sym[0]
        if tag == 'stk':
            return self.mem_stack(st, ins, base, idx, disp, width, signed, is_load, rt)
        if tag == 'addr':
            return self.mem_global(st, ins, base, idx, disp, width, signed, is_load, rt)
        if tag == 'payload':
            return self.mem_payload(st, ins, base, idx, disp, width, signed, is_load, rt)
        if tag == 'ptr':
            return self.mem_ptr(st, ins, base, idx, disp, width, signed, is_load, rt)
        if tag == 'vtable':
            return self.mem_vtable(st, ins, base, idx, disp, width, signed, is_load, rt)
        if tag == 'vslot':
            if not is_load or disp != 4 - base.sym[5] and False:
                raise Unsupported('vslot store')
            _, h, hint, tbl, k, extra = base.sym
            if disp + extra != 4:
                raise Unsupported('vslot disp %d+%d' % (disp, extra))
            st.regs[rt] = S('vtarget', h, hint, tbl, k)
            return st
        if tag == 'classrec':
            if is_load and disp == 8 and idx is None:
                st.regs[rt] = S('classdesc', base.sym[1])
                return st
        if tag == 'classdesc':
            if is_load and disp == 8 and idx is None:
                st.regs[rt] = S('classname', base.sym[1])
                return st
            if idx is None and width == 4 and disp >= 20 and disp % 4 == 0:
                return self.mem_static(st, base.sym[1], (disp - 20) // 4, is_load, rt)
            raise Unsupported('classdesc %s +%d w%d %s' % (base.sym[1], disp, width, 'ld' if is_load else 'st'))
        raise Unsupported('memory access via %s' % (base.sym,))

    # ---- static fields: word i of class C lives at [classrec+8] + 0x14 + 4*i (offsets seen in the game code)
    def static_tv(self, cls, i):
        tv = self.static_tvs.get((cls, i))
        if tv is None:
            tv = self.static_tvs[(cls, i)] = TV()
        return tv

    def static_desc(self, cls, i):
        k = self.static_tvs[(cls, i)].kind()
        return 'Ljava/lang/Object;' if k == 'R' else 'I'

    def mem_static(self, st, cls, i, is_load, rt):
        em = self.em
        sc = self.W.cls.get(cls)
        if sc is None or i >= max(sc.static_words, 1) and sc.static_words:
            self.dg('static-out-of-range', self.cur_addr, '%s %d' % (cls, i))
        tv = self.static_tv(cls, i)
        if is_load:
            if self.is_dead(rt):
                return st
            k = tv.kind()
            if not self.final:
                st.regs[rt] = Val('T', None, home_r(rt), None, None, tv) if k is None else Val(k, None, home_r(rt))
                return st
            k = k or 'I'
            em.op('getstatic', cls, 's%d' % i, 'Ljava/lang/Object;' if k == 'R' else 'I')
            if k == 'R':
                self.wr_ref(st, rt, None)
            else:
                self.wr_int(st, rt)
            return st
        v = self.reg_val(st, rt)
        vk = v.kind()
        if vk in ('I', 'R') and not (v.h is None and v.c == 0):
            if tv.set(vk):
                self.changed = True
        elif vk == 'T':
            if v.tv.kind() and tv.set(v.tv.kind()):
                self.changed = True
            elif tv.kind() and v.tv.set(tv.kind()):
                self.changed = True
            elif v.tv.find() is not tv.find() and tv.kind() is None and v.tv.kind() is None:
                tv.unify(v.tv)
                self.changed = True
        if not self.final:
            return st
        k = tv.kind() or 'I'
        self.push_typed(v, 'Ljava/lang/Object;' if k == 'R' else 'I', 'static store')
        em.op('putstatic', cls, 's%d' % i, 'Ljava/lang/Object;' if k == 'R' else 'I')
        return st

    # ---- stack slots
    def mem_stack(self, st, ins, base, idx, disp, width, signed, is_load, rt):
        if idx is not None:
            raise Unsupported('indexed stack access')
        if width != 4:
            raise Unsupported('sub-word stack access')
        off = base.sym[1] + disp
        if is_load:
            v = self.slot_get(st, off)
            if v.k == 'S' or v.kind() == 'U':
                st.regs[rt] = v
                return st
            if v.h is None and v.c is not None:
                st.regs[rt] = v
                return st
            k = v.kind()
            if k == 'T':
                kk = v.tv.kind()
                if kk is None:
                    st.regs[rt] = Val('T', None, home_r(rt), None, None, v.tv)
                    return st
                k = kk
            if self.is_dead(rt):
                return st
            em = self.em
            if k == 'I':
                em.iload(em.loc('I', v.h))
                self.wr_int(st, rt)
            else:
                em.aload(em.loc('R', v.h))
                self.wr_ref(st, rt, v.hint)
            if v.k == 'T':
                st.regs[rt] = Val('T', None, home_r(rt), v.hint, None, v.tv)
            return st
        v = self.reg_val(st, rt)
        self.slot_put(st, off, v)
        return st

    def slot_get(self, st, off):
        v = st.slots.get(off)
        if v is not None:
            return v
        if off >= 0:
            # incoming stack argument
            idx = off // 4
            tv = self.fn.sig.params_ext(idx + 4)
            self.fn.sig.stack_used.add(idx)
            tv.param_of = (self.fn.sig, 4 + idx)
            self.fn.sig.used.add(4 + idx)
            v = Val('T', None, ('s', off), None, None, tv)
            st.slots[off] = v
            return v
        return U

    def slot_put(self, st, off, v):
        k = v.kind()
        if k in ('U',):
            st.slots.pop(off, None)
            return
        if k == 'S' or (v.h is None and v.c is not None):
            st.slots[off] = v
            return
        em = self.em
        if k == 'T':
            kk = v.tv.kind()
            if kk is None:
                st.slots[off] = Val('T', None, ('s', off), None, None, v.tv)
                return
            k = kk
        home = ('s', off)
        self.protect(st, k, home)
        if k == 'I':
            em.iload(em.loc('I', v.h))
            em.istore(em.loc('I', home))
            nv = Val('I', None, home)
        else:
            em.aload(em.loc('R', v.h))
            em.astore(em.loc('R', home))
            nv = Val('R', None, home, v.hint)
        if v.k == 'T':
            nv = Val('T', None, home, v.hint, None, v.tv)
        st.slots[off] = nv

    # ---- globals / tables in .bss
    def mem_global(self, st, ins, base, idx, disp, width, signed, is_load, rt):
        kind, a = base.sym[1], base.sym[2]
        if idx is not None:
            c = self.const_of(idx[0])
            if c is None or idx[1] not in (None, 'lsl'):
                raise Unsupported('variable index into global table')
            a += (c << idx[2]) if idx[1] == 'lsl' else c
        a += disp
        if kind == 'bss':
            r = bss_region(a)
            if r and is_load:
                nm, k, w = r
                if nm == 'nvt':
                    st.regs[rt] = S('nvt', k)
                elif nm == 'str':
                    st.regs[rt] = S('lit', k)
                else:
                    st.regs[rt] = S('tabidx', nm, k)
                return st
            raise Unsupported('bss access %x' % a)
        if kind == 'text' and is_load:
            # read-only data in the text segment (link tables); value semantics unknown
            st.regs[rt] = S('opaque', 'text', a)
            return st
        if kind == 'data':
            if is_load:
                st.regs[rt] = S('opaque', 'data', a)
                return st
        raise Unsupported('global access %s %x' % (kind, a))

    # ---- object header
    def mem_object(self, st, ins, base, idx, disp, width, signed, is_load, rt):
        if idx is not None or not is_load or width != 4:
            raise Unsupported('unsupported object-header access')
        h = ('h', 'R', base.h)
        if base.h is None:
            raise Unsupported('object header of constant reference')
        if disp == 0:
            st.regs[rt] = S('vtable', h, base.hint)
            return st
        if disp == 8:
            st.regs[rt] = S('payload', h, base.hint)
            return st
        raise Unsupported('object header offset %d' % disp)

    def mem_vtable(self, st, ins, base, idx, disp, width, signed, is_load, rt):
        if not is_load or width != 4:
            raise Unsupported('vtable store')
        _, h, hint = base.sym
        if idx is not None:
            raise Unsupported('indexed vtable access')
        if disp == 0:
            st.regs[rt] = S('classof', h, hint)
        else:
            st.regs[rt] = S('ftarget', h, hint, disp // 4)
        return st

    # ---- payload (fields / array header)
    def home_of(self, hh):
        assert hh[0] == 'h'
        return hh[2]

    def push_obj(self, hh, hint, cast=None):
        """Push the object referenced by embedded ('h','R',home); optionally checkcast."""
        em = self.em
        em.aload(em.loc('R', hh[2]))
        if cast and cast != 'java/lang/Object':
            em.op('checkcast', cast)

    def mem_payload(self, st, ins, base, idx, disp, width, signed, is_load, rt):
        _, hh, hint = base.sym
        if idx is not None:
            iv, sk, sa = idx
            if iv.k == 'S' and iv.sym[0] == 'tabscaled' and iv.sym[1] == 'finst' and sk is None and sa == 0 and disp == 0:
                return self.field_access(st, ins, hh, hint, imp=self.M.inst_imp[iv.sym[2]], tab_k=iv.sym[2],
                                         width=width, is_load=is_load, rt=rt)
            if iv.k == 'S' and iv.sym[0] == 'tabidx16' and iv.sym[1] == 'finst' and sk == 'asr' and sa == 14 and disp == 0:
                return self.field_access(st, ins, hh, hint, imp=self.M.inst_imp[iv.sym[2]], tab_k=iv.sym[2],
                                         width=width, is_load=is_load, rt=rt)
            if iv.k == 'S' and iv.sym[0] == 'tabidx':
                if iv.sym[1] == 'finst' and sk == 'lsl' and sa == 2 and disp == 0:
                    return self.field_access(st, ins, hh, hint, imp=self.M.inst_imp[iv.sym[2]], tab_k=iv.sym[2],
                                             width=width, is_load=is_load, rt=rt)
                raise Unsupported('unscaled table index into payload (%s)' % (iv.sym,))
            # register-indexed element access
            sc = (1 << sa) if sk == 'lsl' else 1
            self.want_kind(iv, 'I')
            ptr = S('ptr', hh, hint, ('h', 'I', iv.h) if self.const_of(iv) is None else None, sc,
                    0 if self.const_of(iv) is None else self.const_of(iv) * sc).sym
            return self.mem_ptr(st, ins, Val('S', sym=ptr), None, disp, width, signed, is_load, rt)
        if hint and hint[0] == '[' and disp >= 4:
            ptr = ('ptr', hh, hint, None, 1, 0)
            return self.mem_ptr(st, ins, Val('S', sym=ptr), None, disp, width, signed, is_load, rt)
        if width == 4:
            if disp == 0 and self.looks_like_length(ins, rt, hint):
                return self.array_length(st, hh, hint, rt)
            return self.field_access(st, ins, hh, hint, word=disp // 4, width=width, is_load=is_load, rt=rt, disp=disp)
        # sub-word access on payload without index -> element at constant index
        ptr = ('ptr', hh, hint, None, 1, 0)
        return self.mem_ptr(st, ins, Val('S', sym=ptr), None, disp, width, signed, is_load, rt)

    def looks_like_length(self, ins, rt, hint):
        if hint and hint[0] == '[':
            return True
        if hint and hint in self.W.cls:
            return False
        # lookahead: cmp x, rt ; b{lo,hs,hi,ls}
        a = ins.addr
        for i in range(1, 6):
            nxt = self.fn.cfg.insns.get(a + 4 * i)
            if nxt is None:
                break
            if nxt.op == 'cmp' and nxt.ops[1] == ('r', rt):
                return True
            if nxt.ops and nxt.ops[0] == ('r', rt) and nxt.op in ('mov', 'ldr'):
                break
        return False

    def refine_hint(self, st, home, newhint):
        """The object living in JVM local `home` is now known to be of type newhint (e.g. an array)."""
        for i, v in enumerate(st.regs):
            nv = self.refine_val(v, home, newhint)
            if nv is not v:
                st.regs[i] = nv
        for o, v in list(st.slots.items()):
            nv = self.refine_val(v, home, newhint)
            if nv is not v:
                st.slots[o] = nv

    def refine_val(self, v, home, newhint):
        if v.k == 'R' and v.h == home and (not v.hint or v.hint == 'java/lang/Object'):
            return Val('R', v.c, v.h, newhint)
        if v.k == 'S' and v.sym[0] in ('payload', 'vtable', 'ptr') and v.sym[1][2] == home:
            old = v.sym[2]
            if old and old[0] == '[' or old and old != 'java/lang/Object':
                return v
            return Val('S', sym=(v.sym[0], v.sym[1], newhint) + tuple(v.sym[3:]))
        return v

    def array_length(self, st, hh, hint, rt):
        em = self.em
        if not (hint and hint[0] == '['):
            self.refine_hint(st, hh[2], '[')
        if self.is_dead(rt):
            return st
        self.push_obj(hh, hint)
        em.op('arraylength')
        self.wr_int(st, rt)
        return st

    # ---- field resolution
    def resolve_word_field(self, hint, word):
        """(owner, name, desc, isref, wide, decl) for instance word of class `hint`; None if unresolved."""
        W = self.W
        if hint in W.cls:
            decl, fld = W.field_at(hint, word)
            if decl is None:
                return None
            if fld:
                return (decl.name, fld.name, fld.desc, fld.isref, fld.wide)
            isref = W.word_is_ref(hint, word)
            return (decl.name, 'w%d' % word, 'Ljava/lang/Object;' if isref else 'I', bool(isref), False)
        return None

    def field_access(self, st, ins, hh, hint, imp=None, tab_k=None, word=None, width=4, is_load=True, rt=None, disp=None):
        em = self.em
        if imp is not None:
            owner, name, desc, _pub = imp
            isref = desc[0] in 'L['
            wide = desc in ('J', 'D')
            if owner in self.W.cls:
                # prefer the declaring class for the field word (owner is a public link class)
                pass
            if owner == 'org/kwis/msp/lwc/TextComponent':
                pass
        else:
            r = self.resolve_word_field(hint, word)
            if r is None:
                cands = self.field_candidates(word, is_load)
                self.dg('field-recv-unknown', ins.addr, 'hint=%s word=%d cands=%s' % (hint, word, cands))
                raise Unsupported('field receiver class unknown (hint=%s word=%d)' % (hint, word))
            owner, name, desc, isref, wide = r
        if wide:
            raise Unsupported('long/double field %s.%s' % (owner, name))
        if is_load:
            if self.is_dead(rt):
                return st
            self.push_obj(hh, hint, owner)
            em.op('getfield', owner, name, desc)
            if isref:
                self.wr_ref(st, rt, desc if desc[0] == '[' else desc[1:-1])
            else:
                self.wr_int(st, rt)
            return st
        v = self.reg_val(st, rt)
        self.want_kind(v, 'R' if isref else 'I')
        self.push_obj(hh, hint, owner)
        self.push(v, 'R' if isref else 'I')
        em.op('putfield', owner, name, desc)
        if isref and imp is None:
            self.note_field_hint(owner, word, v.hint)
        return st

    def note_field_hint(self, owner, word, hint):
        pass

    def field_candidates(self, word, isload):
        out = []
        for cm in self.W.cls.values():
            if cm.own_start <= word < cm.iw:
                out.append(cm.name)
        return out

    # ---- arrays
    def site_tv(self, key):
        tv = self.site_tvs.get(key)
        if tv is None:
            tv = self.site_tvs[key] = TV()
        return tv

    def mem_ptr(self, st, ins, base, idx, disp, width, signed, is_load, rt):
        """Element access  payload + [variable byte offset] + const : array element load/store."""
        _, hh, hint, ixs, sc, off = base.sym
        tot = off + disp
        em = self.em
        dyn = None
        if idx is not None:
            if ixs is not None:
                raise Unsupported('indexed access via ptr with variable base index')
            iv, sk, sa = idx
            self.want_kind(iv, 'I')
            if iv.kind() == 'S':
                raise Unsupported('symbolic array offset %s' % (iv.sym,))
            dyn = ('b', iv, sk, sa)
        elif ixs is not None:
            dyn = ('x', ixs, sc)
        rel = tot - 4
        if rel % width or rel < 0 and dyn is None:
            raise Unsupported('array element offset %d width %d' % (tot, width))
        if dyn is None:
            eidx = ('c', rel // width)
        else:
            if rel % width or rel < 0:
                raise Unsupported('array element offset %d width %d (indexed)' % (tot, width))
            eidx = dyn + (rel // width,)
        ah = hint if hint and hint[0] == '[' and len(hint) > 1 else None
        lg = {1: 0, 2: 1, 4: 2}[width]
        if is_load:
            if self.is_dead(rt):
                return st
            site = None
            if width == 4 and (ah is None):
                site = self.site_tv(('elem', self.fn.addr, ins.addr))
                k = site.kind()
                if not self.final:
                    st.regs[rt] = (Val('T', None, home_r(rt), None, None, site) if k is None
                                   else Val(k, None, home_r(rt)))
                    return st
                k = k or 'I'
                self.push_obj(hh, hint, '[Ljava/lang/Object;' if k == 'R' else '[I')
                self.push_eidx(eidx, lg)
                if k == 'R':
                    em.op('aaload')
                    self.wr_ref(st, rt, None)
                else:
                    em.op('iaload')
                    self.wr_int(st, rt)
                return st
            cast = self.arr_cast(ah, width, signed)
            self.push_obj(hh, hint, cast)
            self.push_eidx(eidx, lg)
            if width == 1:
                em.op('baload')
                self.wr_int(st, rt)
            elif width == 2:
                em.op('caload' if cast == '[C' else 'saload')
                self.wr_int(st, rt)
            else:
                if ah[:2] == '[I' or ah in ('[F',):
                    em.op('iaload')
                    self.wr_int(st, rt)
                else:
                    em.op('aaload')
                    self.wr_ref(st, rt, elem_hint(ah))
            return st
        v = self.reg_val(st, rt)
        if width == 4 and ah is None:
            site = self.site_tv(('elem', self.fn.addr, ins.addr))
            vk = v.kind()
            if vk in ('I', 'R') and not (v.h is None and v.c == 0):
                if site.set(vk):
                    self.changed = True
            elif vk == 'T':
                if v.tv.kind() and site.set(v.tv.kind()):
                    self.changed = True
                elif site.kind() and v.tv.set(site.kind()):
                    self.changed = True
            if not self.final:
                return st
            k = site.kind() or 'I'
            self.push_obj(hh, hint, '[Ljava/lang/Object;' if k == 'R' else '[I')
            self.push_eidx(eidx, lg)
            if k == 'R':
                self.push_typed(v, 'Ljava/lang/Object;', 'array element')
                em.op('aastore')
            else:
                self.push_typed(v, 'I', 'array element')
                em.op('iastore')
            return st
        cast = self.arr_cast(ah, width, signed)
        self.push_obj(hh, hint, cast)
        self.push_eidx(eidx, lg)
        if width == 1:
            self.want_kind(v, 'I')
            self.push_typed(v, 'I', 'array element')
            em.op('bastore')
        elif width == 2:
            self.want_kind(v, 'I')
            self.push_typed(v, 'I', 'array element')
            em.op('castore' if cast == '[C' else 'sastore')
        else:
            if ah[:2] == '[I' or ah in ('[F',):
                self.push_typed(v, 'I', 'array element')
                em.op('iastore')
            else:
                self.push_typed(v, 'Ljava/lang/Object;' if ah[1] != '[' else ah[1:], 'array element')
                em.op('aastore')
        return st

    def push_eidx(self, eidx, lg):
        """Push the element index.  eidx = ('c', n) | ('x', ixs, scale, extra) | ('b', Val, shift kind, amt, extra)."""
        em = self.em
        if eidx[0] == 'c':
            em.iconst(eidx[1])
            return
        if eidx[0] == 'x':
            _, ixs, sc, extra = eidx
            em.iload(em.loc('I', ixs[2]))
            sclg = {1: 0, 2: 1, 4: 2, 8: 3, 16: 4}.get(sc)
            if sclg is None:
                raise Unsupported('array index scale %d' % sc)
            if sclg > lg:
                em.iconst(sclg - lg)
                em.op('ishl')
            elif sclg < lg:
                em.iconst(lg - sclg)
                em.op('iushr')
        else:
            _, iv, sk, sa, extra = eidx
            self.push(iv, 'I')
            if sk and sa:
                em.iconst(sa)
                em.op({'lsl': 'ishl', 'lsr': 'iushr', 'asr': 'ishr'}[sk])
            elif sk:
                raise Unsupported('shift by zero encoding')
            if lg:
                em.iconst(lg)
                em.op('iushr')
        if extra:
            em.iconst(extra)
            em.op('iadd')

    def arr_cast(self, ah, width, signed):
        if ah:
            return ah
        return {1: '[B', 2: ('[S' if signed else '[C')}.get(width)

    # ------------------------------------------------------------------ block transfers
    def exec_block_transfer(self, st, ins):
        op, o = ins.op, ins.ops
        if op in ('push',):
            n = len(o[0][1])
            sp = st.regs[SP]
            if sp.k != 'S' or sp.sym[0] != 'stk':
                raise Unsupported('push with non-stack sp')
            st.regs[SP] = S('stk', sp.sym[1] - 4 * n)
            return st
        if op == 'pop':
            sp = st.regs[SP]
            n = len(o[0][1])
            st.regs[SP] = S('stk', sp.sym[1] + 4 * n)
            for r in o[0][1]:
                if r == PC:
                    raise Unsupported('pop {pc}')
                st.regs[r] = U
            return st
        base_r, rl = o[0][1], o[1][1]
        base = st.regs[base_r]
        mode = op[3:] or 'ia'
        if op.startswith('ldm') and base_r == SP and (FP in rl or PC in rl) and mode in ('ia',):
            # function epilogue: restore frame; callee-saved registers become unknown
            for r in rl:
                if r != PC:
                    st.regs[r] = U
            st.regs[SP] = S('stk', 0)
            st.regs[LR] = S('lr0')
            return st
        if base.k != 'S' or base.sym[0] != 'stk':
            raise Unsupported('block transfer through non-stack base')
        n = len(rl)
        b = base.sym[1]
        start = {'ia': b, 'ib': b + 4, 'da': b - 4 * (n - 1), 'db': b - 4 * n}[mode]
        newb = {'ia': b + 4 * n, 'ib': b + 4 * n, 'da': b - 4 * n, 'db': b - 4 * n}[mode]
        for i, r in enumerate(sorted(rl)):
            off = start + 4 * i
            if op.startswith('stm'):
                self.slot_put(st, off, self.reg_val(st, r))
            else:
                v = self.slot_get(st, off)
                st.regs[r] = Val(v.k, v.c, None if v.k == 'S' else v.h, v.hint, v.sym, v.tv)
                if v.k in ('I', 'R', 'T') and v.h is not None:
                    # materialise a copy into the register local
                    k = v.kind()
                    if k == 'I':
                        self.em.iload(self.em.loc('I', v.h)); self.wr_int(st, r)
                    elif k == 'R':
                        self.em.aload(self.em.loc('R', v.h)); self.wr_ref(st, r, v.hint)
        if ins.wb:
            st.regs[base_r] = S('stk', newb)
        return st
