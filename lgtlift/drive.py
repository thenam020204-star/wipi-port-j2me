"""Per-function driver: entry state, block versions, fix-point analysis, bytecode emission, edge fix-ups."""
import os
from collections import deque
from absval import Val, U, S, State, TV
from cfg import is_return
from lift import (Unsupported, Terminated, NullEm, Em, compute_liveness, home_r, sx32, MASK, INV, parse_desc,
                  desc_kind, NORETURN)
from armdec import FP, IP, SP, LR, PC
import jvm

MAXV = 8                 # maximum states ("versions") per basic block before merging lossily
CMP_SET = ('cmp', 'cmn', 'tst', 'teq')
OBJD = 'Ljava/lang/Object;'


class FnCtx:
    def __init__(self, I, addr):
        self.addr = addr
        self.cfg = I.cfg_of(addr)
        self.sig = I.sig_for(addr)
        self.live, self.live_in = compute_liveness(self.cfg)
        self.vers = {}            # block start -> [State]
        self.edgemap = {}         # (block, ver, edge index) -> successor version index
        self.failures = []        # [(addr, reason)]
        self.callees = set()      # function addresses called (all family implementations for virtual calls)
        self.insts = set()        # local classes instantiated
        self.labels = {}
        self.stubs = []


class DriveMixin:
    # ------------------------------------------------------------------ entry state
    def entry_state(self, fn):
        sig = fn.sig
        st = State()
        st.regs[SP] = S('stk', 0)
        st.regs[LR] = S('lr0')
        kinds = self.jvm_kind_list(sig)
        for j, jt in enumerate(kinds):
            if j == 0 and not sig.static:
                v = Val('R', None, home_r(0), self.impl_class(sig))
            elif sig.desc is not None:
                if jt[0] in 'L[':
                    v = Val('R', None, home_r(j) if j < 4 else ('s', 4 * (j - 4)), cname_hint(jt))
                else:
                    v = Val('I', None, home_r(j) if j < 4 else ('s', 4 * (j - 4)))
            else:
                tv = sig.params_ext(j)
                v = Val('T', None, home_r(j) if j < 4 else ('s', 4 * (j - 4)), tv.find().hint, None, tv)
                if jt[0] == 'L' and jt != OBJD:
                    v.hint = jt[1:-1]
            if j < 4:
                st.regs[j] = v
            else:
                st.slots[4 * (j - 4)] = v
        if sig.desc is None:
            for j in range(len(kinds), 4):       # candidate parameters: discover which ones the body reads
                tv = sig.params_ext(j)
                st.regs[j] = Val('T', None, home_r(j), tv.find().hint, None, tv)
        return st

    # ------------------------------------------------------------------ version management
    def merge_into(self, fn, succ, ost):
        live = fn.live_in[succ]
        for r in range(13):
            if not (live >> r) & 1 and ost.regs[r] is not U:
                ost.regs[r] = U
        vers = fn.vers.setdefault(succ, [])
        for i, vs in enumerate(vers):
            j = self.join_state(vs, ost)
            if self.lossless(vs, ost, j, live):
                if j.key() != vs.key():
                    vers[i] = j
                    return i, True
                return i, False
        if len(vers) < MAXV:
            vers.append(ost)
            return len(vers) - 1, True
        i = len(vers) - 1
        self.dg('version-overflow', succ, '')
        j = self.join_state(vers[i], ost)
        if j.key() != vers[i].key():
            vers[i] = j
            return i, True
        return i, False

    def lossless(self, a, b, j, live):
        for r in range(13):
            if not (live >> r) & 1:
                continue
            for x in (a.regs[r], b.regs[r]):
                if x.kind() != 'U' and j.regs[r].kind() == 'U':
                    return False
        for st in (a, b):
            for o, x in st.slots.items():
                if x.kind() != 'U' and o not in j.slots:
                    return False
        return True

    # ------------------------------------------------------------------ analysis
    def analyze_fn(self, fn):
        self.fn = fn
        self.em = NullEm()
        self.final = False
        fn.failures = []
        fn.vers = {}
        fn.edgemap = {}
        try:
            entry = self.entry_state(fn)
        except Unsupported as e:
            fn.failures.append((fn.addr, str(e)))
            return False
        fn.vers[fn.addr] = [entry]
        work = deque([(fn.addr, 0)])
        steps = 0
        while work:
            b, v = work.popleft()
            steps += 1
            if steps > 4000:
                fn.failures.append((b, 'analysis step limit'))
                break
            st = fn.vers[b][v].copy()
            self.edges = []
            self.cur_b, self.cur_v = b, v
            try:
                self.exec_block(fn, b, v, st)
            except Unsupported as e:
                fn.failures.append((self.cur_addr, str(e)))
                fn.fail_state = st
                self.dg('unsupported', self.cur_addr, str(e))
                continue
            except (IndexError, KeyError, TypeError, AttributeError, ValueError, AssertionError) as e:
                if os.environ.get('LIFT_DEBUG'):
                    raise
                fn.failures.append((self.cur_addr, 'internal: %s: %s' % (type(e).__name__, e)))
                continue
            for ei, (succ, ost) in enumerate(self.edges):
                nv, ch = self.merge_into(fn, succ, ost)
                fn.edgemap[(b, v, ei)] = nv
                if ch:
                    work.append((succ, nv))
        return not fn.failures

    # ------------------------------------------------------------------ edges
    def edge(self, target, st, label_only=False):
        """Record (analysis) or emit (final) the control transfer to `target` with state st."""
        fn = self.fn
        if target not in fn.cfg.blocks:
            raise Unsupported('branch out of function to %x' % target)
        ei = len(self.edges)
        ost = st.copy()
        self.edges.append((target, ost))
        if not self.final:
            return None
        nv = fn.edgemap.get((self.cur_b, self.cur_v, ei))
        if nv is None:
            raise Unsupported('internal: no version recorded for edge %x -> %x' % (self.cur_b, target))
        tgt = fn.vers[target][nv]
        L = fn.labels[(target, nv)]
        moves = self.fix_moves(ost, tgt)
        if label_only:
            if not moves:
                return L
            stub = self.em.label()
            fn.stubs.append((stub, moves, L))
            return stub
        self.emit_moves(moves)
        return L

    def goto_edge(self, target, st, tail=True):
        L = self.edge(target, st)
        if self.final:
            nxt = self.layout_next
            if tail and nxt is not None and fn_label_is(self.fn, nxt, L):
                return
            self.em.op('goto', L)

    def label_edge(self, target, st):
        if not self.final:
            self.edge(target, st, True)
            return self.em.label()
        return self.edge(target, st, True)

    def fix_moves(self, st, tgt):
        moves = []
        for r in range(13):
            w = tgt.regs[r]
            wk = w.kind()
            if wk in ('U', 'S'):
                continue
            if w.h is None:
                continue
            h = st.regs[r]
            hk = h.kind()
            if hk == 'U':
                continue
            if wk == 'T':
                wk = 'I'
            if hk == 'T':
                hk = wk
            if hk != wk:
                raise Unsupported('edge kind mismatch r%d %s vs %s' % (r, hk, wk))
            if h.h == w.h:
                continue
            moves.append((wk, ('c', h.c) if h.h is None else ('l', h.h), w.h))
        for o, w in tgt.slots.items():
            wk = w.kind()
            if wk in ('U', 'S') or w.h is None:
                continue
            h = st.slots.get(o)
            if h is None or h.kind() == 'U':
                continue
            hk = h.kind()
            if wk == 'T':
                wk = 'I'
            if hk == 'T':
                hk = wk
            if hk != wk:
                raise Unsupported('edge kind mismatch slot %d' % o)
            if h.h == w.h:
                continue
            moves.append((wk, ('c', h.c) if h.h is None else ('l', h.h), w.h))
        return moves

    def emit_moves(self, moves):
        em = self.em
        for kind, src, dst in moves:
            if src[0] == 'c':
                if kind == 'R':
                    em.op('aconst_null')
                else:
                    em.iconst(src[1])
            else:
                em.op('aload' if kind == 'R' else 'iload', em.loc(kind, src[1]))
        for kind, src, dst in reversed(moves):
            em.op('astore' if kind == 'R' else 'istore', em.loc(kind, dst))

    # ------------------------------------------------------------------ block execution
    def exec_block(self, fn, b, v, st):
        cfg = fn.cfg
        blk = cfg.blocks[b]
        addrs = blk.insns
        insns = cfg.insns
        n = len(addrs)
        i = 0
        self.cur_addr = b
        try:
            while i < n:
                a = addrs[i]
                ins = insns[a]
                self.cur = ins
                self.cur_addr = a
                self.cur_live = fn.live[a]
                if ins.cc != 'al' and not (ins.op == 'ldr' and ins.ops[0] == ('r', PC)):
                    used, term = self.step_pred(fn, st, addrs, i)
                    i += used
                    if term:
                        return
                    continue
                term = self.step(st, ins)
                self.kill_dead(st, a)
                i += 1
                if term:
                    return
            nxt = blk.end
            if nxt in cfg.blocks:
                self.goto_edge(nxt, st)
        except Terminated:
            return

    def step(self, st, ins):
        """Execute one unconditional instruction. Returns True when the block is terminated."""
        op = ins.op
        if op == 'b':
            self.goto_edge(self.branch_target(ins), st)
            return True
        if op == 'bl':
            t = ins.ops[0][1]
            if t in self.text_thunks:
                th = self.text_thunks[t]
                self.exec_call(st, ins, Val('S', sym=('thunk', th[0], th[1])))
            else:
                self.exec_call(st, ins, Val('S', sym=('fn', t)))
            return False
        if op == 'bx':
            if ins.ops[0] == ('r', LR):
                self.do_return(st)
                return True
            if st.regs[LR].k != 'S' or st.regs[LR].sym[0] != 'retaddr':
                raise Unsupported('bx r%d without return address' % ins.ops[0][1])
            tgt = st.regs[ins.ops[0][1]]
            self.exec_call(st, ins, tgt)
            return False
        if op.startswith('ldm') and ins.ops[1][0] == 'rl' and PC in ins.ops[1][1]:
            self.exec_block_transfer(st, ins)
            self.do_return(st)
            return True
        if op == 'ldr' and ins.ops[0] == ('r', PC):
            return self.do_switch(st, ins)
        if op == 'mov' and ins.ops[0] == ('r', LR) and ins.ops[1] == ('r', PC):
            st.regs[LR] = S('retaddr')
            return False
        self.exec_insn(st, ins)
        return False

    def branch_target(self, ins):
        return ins.ops[0][1]

    # ------------------------------------------------------------------ predicated instructions
    def step_pred(self, fn, st, addrs, i):
        """Run of consecutive instructions with the same condition.  Returns (consumed, terminated)."""
        insns = fn.cfg.insns
        ins0 = insns[addrs[i]]
        cc = ins0.cc
        if ins0.op == 'b':
            self.branch_cond(st, ins0)
            self.kill_dead(st, ins0.addr)
            return 1, False
        if is_return(ins0) or (ins0.op.startswith('ldm') and ins0.ops[1][0] == 'rl' and PC in ins0.ops[1][1]):
            em = self.em
            F = em.label()
            self.cond_jump(st, cc, F, want=False)
            st2 = st.copy()
            if ins0.op != 'bx':
                self.exec_block_transfer(st2, ins0)
            self.do_return(st2)
            em.mark(F)
            return 1, False
        run = []
        j = i
        n = len(addrs)
        while j < n:
            x = insns[addrs[j]]
            if x.cc != cc:
                break
            if x.op in ('bl', 'bx', 'b') or (x.op == 'ldr' and x.ops[0] == ('r', PC)):
                if x.op == 'b' and run is not None:
                    pass
                break
            run.append(x)
            j += 1
            if x.s or x.op in CMP_SET:
                break
        ends_with_branch = (j < n and insns[addrs[j]].op == 'b' and insns[addrs[j]].cc == cc
                            and not (run and (run[-1].s or run[-1].op in CMP_SET)))
        if not run and not ends_with_branch:
            raise Unsupported('predicated %s' % ins0.op)
        em = self.em
        flags0 = st.flags
        F = em.label()
        self.cond_jump(st, cc, F, want=False)
        st2 = st.copy()
        for x in run:
            self.cur = x
            self.cur_addr = x.addr
            self.cur_live = fn.live[x.addr]
            self.exec_insn(st2, Insn_al(x))
            self.kill_dead(st2, x.addr)
        if ends_with_branch:
            br = insns[addrs[j]]
            self.cur = br
            self.cur_addr = br.addr
            self.cur_live = fn.live[br.addr]
            self.goto_edge(self.branch_target(br), st2, tail=False)
            em.mark(F)
            self.kill_dead(st, br.addr)
            return (j - i) + 1, False
        last = run[-1]
        if last.s or last.op in CMP_SET:
            f2 = st2.flags
            st2.flags = ('sel', cc, flags0, f2, flags0) if flags0 is not None else None
        J = self.join_state(st, st2)
        if st2.flags is not None and J.flags is None:
            J.flags = st2.flags if (last.s or last.op in CMP_SET) else None
        if self.final:
            m2 = self.fix_moves(st2, J)
            m1 = self.fix_moves(st, J)
            self.emit_moves(m2)
            if m1:
                E = em.label()
                em.op('goto', E)
                em.mark(F)
                self.emit_moves(m1)
                em.mark(E)
            else:
                em.mark(F)
        else:
            em.mark(F)
        self.kill_dead(J, last.addr)
        st.regs = J.regs
        st.slots = J.slots
        st.flags = J.flags
        st.frames = J.frames
        self.cur = last
        self.cur_addr = last.addr
        return j - i, False

    def branch_cond(self, st, ins):
        """Conditional branch: taken edge via label, fall-through handled by the caller."""
        T = self.branch_target(ins)
        L = self.label_edge(T, st)
        self.cond_jump(st, ins.cc, L, want=True)

    # ------------------------------------------------------------------ return / switch
    def do_return(self, st):
        em = self.em
        sig = self.fn.sig
        r = 'V' if self.fn.addr in self.ctor_fns else self.sig_ret(sig)
        v = st.regs[0]
        k = v.kind()
        if not self.final:
            if sig.desc is None and self.fn.addr not in self.ctor_fns:
                if k in ('I', 'R'):
                    if k not in sig.ret_kinds:
                        sig.ret_kinds.add(k)
                        self.changed = True
                elif k == 'T':
                    kk = v.tv.kind()
                    if kk:
                        if kk not in sig.ret_kinds:
                            sig.ret_kinds.add(kk)
                            self.changed = True
                    elif v.tv.find() is not sig.ret.find():
                        sig.ret.unify(v.tv)
                        self.changed = True
        if r == 'V':
            em.op('return')
            return
        if r == 'R' or r[0] in 'L[':
            jt = OBJD if r == 'R' else r
            self.push_typed(v, jt, 'return')
            em.op('areturn')
            return
        if r in ('J', 'D', 'F'):
            raise Unsupported('wide return type')
        self.push_typed(v, 'I', 'return')
        em.op('ireturn')

    def do_switch(self, st, ins):
        em = self.em
        a = ins.addr
        tg = self.fn.cfg.jump_tables.get(a)
        if tg is None:
            raise Unsupported('computed jump without table')
        m = ins.ops[1]
        idx = self.reg_val(st, m[2])
        self.want_kind(idx, 'I')
        labels = [self.label_edge(t, st) for t in tg]
        dflt = self.label_edge(ins.addr + 4, st) if (ins.addr + 4) in self.fn.cfg.blocks else None
        if dflt is None:
            raise Unsupported('switch without default')
        self.push(idx, 'I')
        em.op('tableswitch', dflt, 0, labels)
        return True

    # ------------------------------------------------------------------ emission
    def emit_fn(self, fn, method):
        """Re-run the converged analysis with a real emitter.  `method` is a jvm.Method (code is filled)."""
        self.fn = fn
        self.final = True
        code = method.code
        sig = fn.sig
        nparam = len(self.jvm_kind_list(sig))
        self.em = Em(code, nparam)
        em = self.em
        fn.labels = {}
        fn.stubs = []
        layout = []
        for b in sorted(fn.vers):
            for v in range(len(fn.vers[b])):
                layout.append((b, v))
                fn.labels[(b, v)] = em.label()
        entry = fn.vers[fn.addr][0]
        # prologue: parameters -> register homes
        kinds = self.jvm_kind_list(sig)
        for j, jt in enumerate(kinds):
            home = home_r(j) if j < 4 else ('s', 4 * (j - 4))
            v = entry.regs[j] if j < 4 else entry.slots.get(4 * (j - 4))
            if v is None or v.kind() == 'U' or v.h is None:
                continue
            k = v.kind()
            k = 'R' if (k == 'R' or (k == 'T' and v.tv.kind() == 'R')) else 'I'
            em.op('aload' if k == 'R' else 'iload', j)
            em.op('astore' if k == 'R' else 'istore', em.loc(k, v.h))
        self.ntemps = getattr(self, 'ntemps', 0)
        for li, (b, v) in enumerate(layout):
            self.layout_next = layout[li + 1] if li + 1 < len(layout) else None
            em.mark(fn.labels[(b, v)])
            st = fn.vers[b][v].copy()
            self.edges = []
            self.cur_b, self.cur_v = b, v
            self.exec_block(fn, b, v, st)
        self.layout_next = None
        for stub, moves, L in fn.stubs:
            em.mark(stub)
            self.emit_moves(moves)
            em.op('goto', L)
        # initialise every allocated local up front (verifier-safe regardless of control flow)
        init = []
        for (kind, home), n in em.locmap.items():
            init.append((kind, n))
        for kind, n in em.reg_locals:
            init.append((kind, n))
        pro = []
        for kind, n in sorted(set(init), key=lambda x: x[1]):
            if kind == 'R':
                pro.append(('aconst_null',))
                pro.append(('astore', n))
            else:
                pro.append(('iconst', 0))
                pro.append(('istore', n))
        code.ins[0:0] = pro
        return code


def fn_label_is(fn, key, L):
    return fn.labels.get(key) is L


def cname_hint(jt):
    return jt[1:-1] if jt[0] == 'L' else jt


def Insn_al(x):
    """Copy of instruction x without its condition (executed inside a conditional region)."""
    from armdec import Insn
    y = Insn(x.addr, x.op, 'al', x.s, x.ops, x.text, x.wb)
    return y
