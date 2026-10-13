"""Call lowering for the LGT lifter: local functions, imported (framework/core) methods, runtime thunks."""
from absval import Val, U, S, TV
from lift import (Unsupported, Terminated, home_r, sx32, MASK, parse_desc, desc_kind, JI, NORETURN)
from armdec import FP, IP, SP, LR, PC

RT = 'Rt'                      # synthesized runtime-helper class (static helpers)
OBJ = 'java/lang/Object'
OBJD = 'Ljava/lang/Object;'
PRIM_ATYPE = {'Z': 4, 'C': 5, 'F': 6, 'D': 7, 'B': 8, 'S': 9, 'I': 10, 'J': 11}
ATYPE_DESC = {4: 'Z', 5: 'C', 6: 'F', 7: 'D', 8: 'B', 9: 'S', 10: 'I', 11: 'J'}
# Thumb runtime arithmetic helpers (signed, truncating; division by zero -> ArithmeticException == JVM idiv/irem)
ARITH = {0xf49cc: 'idiv', 0xf4a60: 'irem'}
THUNK_PENDING = (1, 0x32)


def cname_of(desc):
    """Field/param descriptor -> internal class name usable in checkcast/anewarray (arrays keep descriptor form)."""
    if desc[0] == 'L':
        return desc[1:-1]
    return desc


class CallsMixin:
    # ------------------------------------------------------------------ result / clobber
    def clobber(self, st):
        for r in (0, 1, 2, 3, 12, LR):
            st.regs[r] = U

    def set_ret(self, st, rtype):
        """Value of the call is on the JVM stack (unless rtype == 'V'); bind it to r0."""
        em = self.em
        if rtype == 'V':
            st.regs[0] = U
            return
        if rtype in ('J', 'D'):
            raise Unsupported('wide return value')
        live0 = (self.cur_live & 1)
        if rtype == 'R':
            if live0:
                self.wr_ref(st, 0, None)
            else:
                em.op('pop')
                st.regs[0] = U
        elif rtype[0] in 'L[':
            if live0:
                self.wr_ref(st, 0, cname_of(rtype))
            else:
                em.op('pop')
                st.regs[0] = U
        else:
            if live0:
                self.wr_int(st, 0)
            else:
                em.op('pop')
                st.regs[0] = U

    # ------------------------------------------------------------------ argument handling
    def arm_arg(self, st, i):
        """ARM argument word i: r0..r3 then stack words at [sp + 4*(i-4)]."""
        if i < 4:
            return st.regs[i]
        sp = st.regs[SP]
        if sp.k != 'S' or sp.sym[0] != 'stk':
            raise Unsupported('stack argument with non-stack sp')
        v = st.slots.get(sp.sym[1] + 4 * (i - 4))
        return v if v is not None else U

    def push_typed(self, v, jt, what='arg'):
        """Push v converted for a JVM parameter of type jt ('I'-like prim, class, array)."""
        em = self.em
        self.use(v)
        if jt[0] in 'L[':
            if v.kind() == 'U':
                self.dg('undef-arg', 0, what)
                em.op('aconst_null')
                return
            if v.kind() == 'I':
                if v.c == 0 and v.h is None:
                    em.op('aconst_null')
                    return
                raise Unsupported('int value passed as reference (%s)' % what)
            self.push(v, 'R')
            if jt != OBJD and v.kind() != 'S':
                if not (v.c == 0 and v.h is None):
                    em.op('checkcast', cname_of(jt))
            return
        if jt in ('J', 'D', 'F'):
            raise Unsupported('wide/float parameter %s' % jt)
        if v.kind() == 'U':
            self.dg('undef-arg', 0, what)
            em.iconst(0)
            return
        if v.kind() == 'R':
            if v.c == 0 and v.h is None:
                em.iconst(0)
                return
            raise Unsupported('reference passed as int (%s)' % what)
        self.push(v, 'I')

    def desc_words(self, desc, has_recv):
        """ARM word layout of a typed call: list of JVM types, receiver first."""
        ps, ret = parse_desc(desc)
        for p in ps:
            if p in ('J', 'D', 'F'):
                raise Unsupported('wide/float parameter in %s' % desc)
        return ps, ret

    # ------------------------------------------------------------------ entry point
    def exec_call(self, st, ins, tgt):
        if tgt.kind() != 'S':
            raise Unsupported('indirect call through %s' % tgt)
        tag = tgt.sym[0]
        if tag == 'thunk':
            return self.call_thunk(st, ins, tgt.sym[1], tgt.sym[2])
        if tag == 'fn':
            return self.call_fn(st, ins, tgt.sym[1])
        if tag == 'nvt':
            return self.call_nvt(st, ins, tgt.sym[1])
        if tag == 'vtarget':
            return self.call_vtarget(st, ins, tgt.sym)
        if tag == 'ftarget':
            return self.call_ftarget(st, ins, tgt.sym)
        raise Unsupported('call through %s' % (tgt.sym,))

    # ------------------------------------------------------------------ imported methods with known descriptors
    def invoke_import(self, st, ins, owner, name, desc, static, op=None):
        em = self.em
        ps, ret = self.desc_words(desc, not static)
        words = ([] if static else [None]) + ps
        pend = None
        recv = None
        if not static:
            recv = st.regs[0]
            if recv.k == 'S' and recv.sym[0] == 'newobj':
                pend = recv
        if pend is not None:
            return self.invoke_ctor(st, ins, owner, desc, pend)
        for i, jt in enumerate(words):
            v = self.arm_arg(st, i)
            if jt is None:
                if v.kind() in ('U',):
                    raise Unsupported('undefined receiver for %s.%s' % (owner, name))
                self.use(v)
                if v.kind() == 'I' and v.c != 0:
                    raise Unsupported('int receiver for %s.%s' % (owner, name))
                self.push(v, 'R')
                if owner != OBJ and not (v.hint and v.hint == owner):
                    em.op('checkcast', owner)
            else:
                self.push_typed(v, jt, '%s.%s arg%d' % (owner, name, i))
        if op is None:
            op = 'invokestatic' if static else ('invokespecial' if name == '<init>' else 'invokevirtual')
        em.op(op, owner, name, desc)
        self.clobber(st)
        if name == '<init>':
            return st
        self.set_ret(st, ret)
        return st

    def invoke_ctor(self, st, ins, owner, desc, pend):
        """`new owner; dup; args; invokespecial <init>` for a pending Instantiate result."""
        em = self.em
        cls = pend.sym[1]
        if cls != owner and not self.W.subtype(cls, owner):
            raise Unsupported('constructor %s on instance of %s' % (owner, cls))
        ps, _ = self.desc_words(desc, True)
        self.pending_reg_homes = None
        em.op('new', cls)
        em.op('dup')
        for i, jt in enumerate(ps):
            self.push_typed(self.arm_arg(st, i + 1), jt, 'ctor arg%d' % i)
        em.op('invokespecial', owner, '<init>', desc)
        self.bind_new(st, pend, cls)
        self.clobber(st)
        return st

    def bind_new(self, st, pend, cls):
        """The initialised object is on the JVM stack: store it and rebind every alias of `pend`."""
        home = ('n', pend.sym[2])
        self.protect(st, 'R', home)
        em = self.em
        em.astore(em.loc('R', home))
        nv = Val('R', None, home, cls)
        for r in range(16):
            v = st.regs[r]
            if v.k == 'S' and v.sym == pend.sym:
                st.regs[r] = nv
        for o, v in list(st.slots.items()):
            if v.k == 'S' and v.sym == pend.sym:
                st.slots[o] = nv
        # keep the object in r0-compatible form: constructors return nothing, aliases rebound above
        # (r0 itself is clobbered by the caller of bind_new)
        self._rebound = nv

    # ------------------------------------------------------------------ nonvirtual imports (via .bss nvt table)
    def call_nvt(self, st, ins, k):
        M = self.M
        e = M.nv_imp.get(k)
        if e is None:
            raise Unsupported('nvt index %d unknown' % k)
        owner, name, desc, _pub = e
        if name in ('<clinitget>', '<classget>'):
            self.clobber(st)
            st.regs[0] = S('classrec', owner)
            return st
        if name == '<init>':
            return self.invoke_import(st, ins, owner, name, desc, False)
        static = (owner, name) in self.static_set
        return self.invoke_import(st, ins, owner, name, desc, static)

    # ------------------------------------------------------------------ virtual imports (vidx/iidx tables)
    def call_vtarget(self, st, ins, sym):
        _, h, hint, tbl, k = sym
        M = self.M
        if tbl == 'vidx':
            e = M.virt_imp.get(k)
            if e is None:
                raise Unsupported('vidx %d unknown' % k)
            owner, name, desc, _p = e
            return self.invoke_import(st, ins, owner, name, desc, False, 'invokevirtual')
        if tbl == 'iidx':
            e = M.iface_imp.get(k)
            if e is None:
                raise Unsupported('iidx %d unknown' % k)
            owner, name, desc, _p = e
            return self.invoke_interface(st, ins, owner, name, desc)
        raise Unsupported('vtarget table %s' % tbl)

    def invoke_interface(self, st, ins, owner, name, desc):
        em = self.em
        ps, ret = self.desc_words(desc, True)
        recv = self.arm_arg(st, 0)
        self.push(recv, 'R')
        em.op('checkcast', owner)
        for i, jt in enumerate(ps):
            self.push_typed(self.arm_arg(st, i + 1), jt, '%s.%s arg%d' % (owner, name, i))
        em.op('invokeinterface', owner, name, desc, 1 + sum(1 for p in ps))
        self.clobber(st)
        self.set_ret(st, ret)
        return st

    # ------------------------------------------------------------------ fixed vtable slots
    def resolve_fixed(self, hint, widx):
        """(kind, data) for a call through fixed vtable word widx on a receiver of static class `hint`.
        kind 'local' -> (root ClassModel, slot); kind 'core' -> (owner, name, desc); None -> unresolved."""
        slot = widx - 1
        W = self.W
        if hint in W.cls:
            root = W.vslot_root(hint, slot)
            if root is not None and root.vcount > slot:
                # slot defined by the local hierarchy only if the topmost local class's vtable reaches it and the
                # slot is beyond the (framework/Object) base vtable of that topmost class
                base = self.base_vcount(root)
                if slot >= base:
                    return ('local', (root, slot))
            fs = W.framework_super(hint)
            e = W.core_method(fs, slot) if fs else None
            if e:
                return ('core', (fs, e[0], e[1]))
            return None
        if hint:
            e = W.core_method(hint, slot)
            if e and (hint in __import__('coreabi').CORE or slot < 10):
                owner = hint if slot >= 10 or hint in __import__('coreabi').CORE else OBJ
                return ('core', (owner, e[0], e[1]))
        return None

    def base_vcount(self, cm):
        """vtable size contributed by the non-local base of a local root class (Object = 10, else unknown)."""
        if cm.jsuper == OBJ:
            return 10
        import coreabi
        e = coreabi.CORE.get(cm.jsuper)
        if e and e.get('vtable_size'):
            return e['vtable_size']
        return 10 ** 6

    def call_ftarget(self, st, ins, sym):
        _, h, hint, widx = sym
        slot = widx - 1
        r = self.resolve_fixed(hint, widx) if hint else None
        if r is None:
            cands = self.fixed_candidates(widx)
            self.fixed_sites.setdefault((hint, slot), []).append((self.fn.addr, ins.addr))
            for c in cands:
                if c[2][0] == 'local':
                    for cm in self.W.subclasses(c[2][1][0].name):
                        if slot in cm.vslots:
                            self.fn.callees.add(cm.vslots[slot])
            self.dg('fixed-slot-unresolved', ins.addr, 'hint=%s slot=%d cands=%s' % (hint, slot, cands))
            uniq = set(c[:2] for c in cands) if cands else set()
            if len(cands) == 1:
                r = cands[0][2]
            else:
                raise Unsupported('fixed slot %d on %s unresolved (%d candidates)' % (slot, hint, len(cands)))
        kind, data = r
        if kind == 'core':
            owner, name, desc = data
            return self.invoke_import(st, ins, owner, name, desc, False, 'invokevirtual')
        root, s = data
        return self.invoke_local_virtual(st, ins, root.name, s)

    def fixed_candidates(self, widx):
        """All (kind,..) resolutions of slot widx-1 over every local class hierarchy and core class."""
        slot = widx - 1
        out = []
        W = self.W
        seen = set()
        for cm in W.cls.values():
            root = W.vslot_root(cm.name, slot)
            if root is None or root.name in seen:
                continue
            if slot >= self.base_vcount(root):
                seen.add(root.name)
                out.append((root.name, slot, ('local', (root, slot))))
        import coreabi
        for cn, e in coreabi.CORE.items():
            if slot in e['vtable'] and cn != OBJ:
                out.append((cn, slot, ('core', (cn, e['vtable'][slot][0], e['vtable'][slot][1]))))
        if slot < 10:
            e = W.core_method(OBJ, slot)
            if e:
                out.append((OBJ, slot, ('core', (OBJ, e[0], e[1]))))
        return out

    def invoke_local_virtual(self, st, ins, root_name, slot):
        """invokevirtual Root.v<slot> with the (inferred) family signature."""
        fam = (root_name, slot)
        sig = self.famsigs.get(fam)
        if sig is None:
            impl = None
            for cm in self.W.subclasses(root_name):
                if slot in cm.vslots:
                    impl = cm.vslots[slot]
                    break
            if impl is None:
                raise Unsupported('abstract local virtual %s.v%d' % (root_name, slot))
            sig = self.sig_for(impl)
        for cm in self.W.subclasses(root_name):
            if slot in cm.vslots:
                self.fn.callees.add(cm.vslots[slot])
        return self.invoke_sig(st, ins, sig, 'invokevirtual')

    # ------------------------------------------------------------------ string literal pool getter
    def string_getters(self):
        """Functions of the form `String lit(int idx)`: load the idx-th entry of the literal table through the
        StringLiteral runtime thunk (table 0x64, fn 9) and return it.  Evidence: tiny, branch-free, one StringLiteral
        thunk reference (fn 0x16ac in the sample binary)."""
        r = getattr(self, '_strfns', None)
        if r is None:
            r = self._strfns = set()
            B = self.B
            for a in B.func_starts:
                if a >= B.game_end:
                    break
                if B.func_end(a) - a > 0x80:
                    continue
                insns, data, order = B.decode_function(a)
                if any(B.thunks.get(w) == (100, 9) for w in data.values()) and not any(
                        i.op in ('b', 'bl') for i in insns.values()):
                    r.add(a)
        return r

    def call_string_getter(self, st, ins):
        v = self.reg_val(st, 0)
        c = self.const_of(v)
        self.clobber(st)
        if c is not None and 0 <= c < len(self.M.strings):
            st.regs[0] = S('lit', c)
            return st
        self.want_kind(v, 'I')
        self.push(v, 'I')
        self.em.op('invokestatic', 'Rt', 'str', '(I)Ljava/lang/String;')
        if self.cur_live & 1:
            self.wr_ref(st, 0, 'java/lang/String')
        else:
            self.em.op('pop')
        return st

    # ------------------------------------------------------------------ local (lifted) functions
    def call_fn(self, st, ins, addr):
        W = self.W
        if addr in W.classrec_fns:
            self.clobber(st)
            st.regs[0] = S('classrec', W.classrec_fns[addr])
            return st
        if addr in self.string_getters():
            return self.call_string_getter(st, ins)
        if addr in ARITH:
            a, b = self.reg_val(st, 0), self.reg_val(st, 1)
            self.want_kind(a, 'I')
            self.want_kind(b, 'I')
            self.push(a, 'I')
            self.push(b, 'I')
            self.em.op(ARITH[addr])
            self.clobber(st)
            if self.cur_live & 1:
                self.wr_int(st, 0)
            else:
                self.em.op('pop')
            return st
        if addr in W.skip_fns:
            raise Unsupported('call to runtime link function %x' % addr)
        if addr not in self.B._fidx:
            raise Unsupported('call to non-function address %x' % addr)
        sig = self.sig_for(addr)
        return self.invoke_sig(st, ins, sig, None)

    def jvm_kind_list(self, sig):
        """Per ARM word: JVM type ('I', object descriptor) for each parameter incl. receiver."""
        out = []
        if sig.desc is not None:
            ps, _ = self.desc_words(sig.desc, not sig.static)
            if not sig.static:
                out.append('L%s;' % sig.owner)
            out.extend(ps)
            return out
        n = self.sig_nwords(sig)
        for i in range(n):
            if i == 0 and not sig.static:
                out.append('L%s;' % sig.owner)
                continue
            k = sig.params[i].kind() if i < len(sig.params) else None
            out.append(OBJD if k == 'R' else 'I')
        return out

    def sig_nwords(self, sig):
        if sig.desc is not None:
            ps, _ = parse_desc(sig.desc)
            return len(ps) + (0 if sig.static else 1)
        if not sig.used:
            return 0 if sig.static else 1
        return 1 + max(sig.used)

    def sig_ret(self, sig):
        """'V' | 'I' | 'R' | descriptor-ish for typed methods."""
        if sig.desc is not None:
            return parse_desc(sig.desc)[1]
        ks = getattr(sig, 'ret_kinds', set())
        if 'R' in ks:
            return 'R'
        if 'I' in ks or sig.ret_used:
            return 'I'
        return 'V'

    def jvm_ret(self, sig):
        r = self.sig_ret(sig)
        return OBJD if r == 'R' else r

    def jvm_desc(self, sig):
        ks = self.jvm_kind_list(sig)
        if not sig.static:
            ks = ks[1:]
        return '(%s)%s' % (''.join(ks), self.jvm_ret(sig))

    def invoke_sig(self, st, ins, sig, op):
        em = self.em
        self.fn.callees.add(sig.addr)
        if sig.desc is None and self.cur_live & 1:
            if not sig.ret_used:
                sig.ret_used = True
                self.changed = True
        kinds = self.jvm_kind_list(sig)
        # constructor call on a pending Instantiate result?
        recv0 = st.regs[0]
        if not sig.static and recv0.k == 'S' and recv0.sym[0] == 'newobj':
            return self.invoke_local_ctor(st, ins, sig, kinds, recv0)
        for i, jt in enumerate(kinds):
            v = self.arm_arg(st, i)
            self.use(v)
            if i < 4:
                v = st.regs[i]
            if sig.desc is None and sig.params[i].kind() is None and v.kind() in ('I', 'R'):
                sig.params[i].set(v.kind())
                self.changed = True
            if sig.desc is None and v.kind() == 'T' and i < len(sig.params):
                if v.tv.find() is not sig.params[i].find():
                    v.tv.unify(sig.params[i])
                    self.changed = True
            if sig.desc is None and i < len(sig.params) and v.kind() == 'R' and v.hint:
                if sig.params[i].add_hint(v.hint):
                    self.changed = True
            if i == 0 and not sig.static:
                if v.kind() == 'U':
                    raise Unsupported('undefined receiver for %s' % sig.name)
                if v.kind() == 'I' and v.c != 0:
                    raise Unsupported('int receiver for %s' % sig.name)
                self.push(v, 'R')
                em.op('checkcast', sig.owner)
            elif jt == 'I':
                self.push_typed(v, 'I', 'fn arg%d' % i)
            else:
                self.push_typed(v, jt, 'fn arg%d' % i)
        name, desc = self.jvm_name(sig), self.jvm_desc(sig)
        owner = sig.owner
        if op is None:
            op = 'invokestatic' if sig.static else self.method_op(sig)
        em.op(op, owner, name, desc)
        self.clobber(st)
        r = self.sig_ret(sig)
        self.set_ret(st, 'V' if r == 'V' else ('R' if r == 'R' else r))
        if sig.desc is None and r == 'R':
            rv = st.regs[0]
        return st

    def invoke_local_ctor(self, st, ins, sig, kinds, pend):
        em = self.em
        cls = pend.sym[1]
        self.ctor_fns[sig.addr] = cls
        sig.is_ctor = True
        em.op('new', cls)
        em.op('dup')
        for i, jt in enumerate(kinds):
            if i == 0:
                continue
            v = self.arm_arg(st, i)
            self.use(v)
            if sig.desc is None and sig.params[i].kind() is None and v.kind() in ('I', 'R'):
                sig.params[i].set(v.kind())
                self.changed = True
            self.push_typed(v, jt, 'ctor arg%d' % i)
        em.op('invokespecial', cls, '<init>', '(%s)V' % ''.join(kinds[1:]))
        self.bind_new(st, pend, cls)
        self.clobber(st)
        return st

    def jvm_name(self, sig):
        if getattr(sig, 'is_ctor', False) or sig.addr in self.ctor_fns:
            return '<init>'
        return sig.name

    def method_op(self, sig):
        if getattr(sig, 'is_ctor', False) or sig.addr in self.ctor_fns:
            return 'invokespecial'
        m = self.W.mth_by_addr.get(sig.addr)
        if m is not None and (m.acc & 2):
            return 'invokespecial'
        return 'invokevirtual'

    # ------------------------------------------------------------------ runtime thunks
    def call_thunk(self, st, ins, t, f):
        em = self.em
        if (t, f) == THUNK_PENDING:
            return self.thunk_pending(st, ins)
        if t != 0x64:
            raise Unsupported('thunk table %x fn %x' % (t, f))
        name = JI.get(f, 'fn%x' % f)
        h = getattr(self, 'th_' + name, None)
        if h is None:
            raise Unsupported('thunk %x (%s)' % (f, name))
        return h(st, ins)

    def th_Unk54(self, st, ins):
        self.clobber(st)
        return st

    th_Unk55 = th_Unk54

    def throw_helper(self, name, desc='()Ljava/lang/Throwable;', arg=None):
        em = self.em
        if arg is not None:
            self.push(arg, 'I')
        em.op('invokestatic', RT, name, desc)
        em.op('athrow')

    def th_NPE(self, st, ins):
        self.throw_helper('npe')
        raise Terminated()

    def th_ArithExc(self, st, ins):
        self.throw_helper('arith')
        raise Terminated()

    def th_AIOOBE(self, st, ins):
        v = self.reg_val(st, 0)
        if v.kind() in ('U',):
            self.em.iconst(0)
            self.em.op('invokestatic', RT, 'aioobe', '(I)Ljava/lang/Throwable;')
            self.em.op('athrow')
        else:
            self.want_kind(v, 'I')
            self.throw_helper('aioobe', '(I)Ljava/lang/Throwable;', v)
        raise Terminated()

    def th_ClassCast(self, st, ins):
        self.throw_helper('cce')
        raise Terminated()

    def th_Throw(self, st, ins):
        v = self.reg_val(st, 0)
        self.push_typed(v, 'Ljava/lang/Throwable;', 'throw')
        self.em.op('athrow')
        raise Terminated()

    def th_StringClass(self, st, ins):
        self.clobber(st)
        st.regs[0] = S('classrec', 'java/lang/String')
        return st

    def th_StringArrayClass(self, st, ins):
        self.clobber(st)
        st.regs[0] = S('arrtype', '[Ljava/lang/String;')
        return st

    def th_ArrayType(self, st, ins):
        rank = self.const_of(self.reg_val(st, 0))
        comp = self.reg_val(st, 1)
        prim = self.const_of(self.reg_val(st, 2))
        if rank is None:
            raise Unsupported('array rank not constant')
        if comp.k == 'S' and comp.sym[0] == 'classname':
            c = 'L%s;' % comp.sym[1]
        elif comp.k == 'S' and comp.sym[0] == 'addr' and comp.sym[1] == 'text':
            s = self.im.cstr(comp.sym[2])
            s = s.decode('latin1') if s else ''
            if not s:
                raise Unsupported('array component string empty at %x' % comp.sym[2])
            c = s if s.startswith('[') else 'L%s;' % s
        elif self.const_of(comp) == 0:
            if prim not in ATYPE_DESC:
                raise Unsupported('primitive array type %r' % prim)
            c = ATYPE_DESC[prim]
        else:
            raise Unsupported('array component %s' % (comp,))
        self.clobber(st)
        st.regs[0] = S('arrtype', '[' * rank + c)
        return st

    def th_NewArray(self, st, ins):
        em = self.em
        at = self.reg_val(st, 0)
        ln = self.reg_val(st, 1)
        if at.k != 'S' or at.sym[0] != 'arrtype':
            raise Unsupported('NewArray with non-constant array class %s' % (at,))
        d = at.sym[1]
        self.want_kind(ln, 'I')
        self.push(ln, 'I')
        e = d[1:]
        if e[0] in PRIM_ATYPE and len(e) == 1:
            if e in ('F', 'D', 'J'):
                raise Unsupported('wide primitive array')
            em.op('newarray', PRIM_ATYPE[e])
        else:
            em.op('anewarray', cname_of(e))
        self.clobber(st)
        if self.cur_live & 1:
            self.wr_ref(st, 0, d)
        else:
            em.op('pop')
        return st

    def th_NewMultiArray(self, st, ins):
        em = self.em
        at = self.reg_val(st, 0)
        dims = self.reg_val(st, 1)
        rank = self.const_of(self.reg_val(st, 2))
        if at.k != 'S' or at.sym[0] != 'arrtype' or dims.k != 'S' or dims.sym[0] != 'stk' or rank is None:
            raise Unsupported('NewMultiArray operands %s %s' % (at, dims))
        for i in range(rank):
            v = st.slots.get(dims.sym[1] + 4 * i)
            if v is None:
                raise Unsupported('multi-array dimension %d undefined' % i)
            self.want_kind(v, 'I')
            self.push(v, 'I')
        em.op('multianewarray', at.sym[1], rank)
        self.clobber(st)
        if self.cur_live & 1:
            self.wr_ref(st, 0, at.sym[1])
        else:
            em.op('pop')
        return st

    def th_Instantiate(self, st, ins):
        c = self.reg_val(st, 0)
        if c.k != 'S' or c.sym[0] != 'classrec':
            raise Unsupported('Instantiate of %s' % (c,))
        self.clobber(st)
        self.fn.insts.add(c.sym[1])
        st.regs[0] = S('newobj', c.sym[1], ins.addr)
        return st

    def th_IsAssignable(self, st, ins):
        em = self.em
        a, b = self.reg_val(st, 0), self.reg_val(st, 1)
        if a.k != 'S' or a.sym[0] != 'classof' or b.k != 'S' or b.sym[0] != 'classname':
            raise Unsupported('IsAssignable operands %s %s' % (a, b))
        _, hh, hint = a.sym
        cname = b.sym[1]
        em.aload(em.loc('R', hh[2]))
        em.op('instanceof', cname)
        self.clobber(st)
        if self.cur_live & 1:
            self.wr_int(st, 0)
        else:
            em.op('pop')
        return st

    def th_StoreRefArray(self, st, ins):
        em = self.em
        arr, idx, val = self.reg_val(st, 0), self.reg_val(st, 1), self.reg_val(st, 2)
        self.want_kind(idx, 'I')
        self.push_typed(arr, '[Ljava/lang/Object;', 'array store')
        self.push(idx, 'I')
        self.push_typed(val, OBJD, 'array store value')
        em.op('aastore')
        self.clobber(st)
        return st

    th_StoreRefArrayUnchecked = th_StoreRefArray

    def th_MonEnter(self, st, ins):
        self.dg('monitor', ins.addr)
        self.clobber(st)
        return st

    th_MonExit = th_MonEnter

    def thunk_pending(self, st, ins):
        raise Unsupported('PendingException outside frame idiom')
