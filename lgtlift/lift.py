"""Abstract interpreter / lifter: one ARM function of the LGT binary -> typed register-level IR / JVM bytecode.

Pass structure (per function):
  analyze()  : worklist data-flow over blocks with joined abstract states; collects kind constraints (TVs), call
               signatures, diagnostics.  No emission.
  emit()     : replay from the converged entry states, emitting JVM instructions through `Em`.

Abstract registers hold: ints ('I'), refs ('R'), symbolic values ('S': payload/vtable/table pointers etc. that never
live in a JVM local) or unresolved-kind params ('T').
"""
import struct
from absval import TV, Val, U, S, State, join_hint
from cfg import CFG, uses_defs, is_return
import armdec
from armdec import FP, IP, SP, LR, PC

R0, R1, R2, R3 = 0, 1, 2, 3
CALLER_SAVED = (0, 1, 2, 3, 12)
MASK = 0xffffffff

TBL_NAMES = {0x1500b00: 'finst', 0x1500e9e: 'fstat', 0x1500e40: 'vidx', 0x1500ea0: 'iidx', 0x1500960: 'nvt',
             0x1500000: 'str'}
JI = {0x03: 'unk0', 0x06: 'DestroyCtx', 0x07: 'CreateCtx', 0x09: 'StringLiteral', 0x0a: 'IfaceDispatch',
      0x0b: 'RegisterClass', 0x0c: 'ResolveClass', 0x0d: 'InitClass', 0x0e: 'ArrayType', 0x0f: 'Instantiate',
      0x10: 'NewArray', 0x11: 'NewMultiArray', 0x12: 'IsAssignable', 0x13: 'LinkPublic', 0x14: 'LinkImported',
      0x1f: 'PushExcFrame', 0x20: 'PopExcFrame', 0x21: 'Throw', 0x22: 'NPE', 0x23: 'AIOOBE', 0x25: 'ArithExc',
      0x26: 'ClassCast', 0x54: 'Unk54', 0x55: 'Unk55', 0x56: 'MonEnter', 0x57: 'MonExit', 0x61: 'StoreRefArray',
      0xe1: 'StringClass', 0xe2: 'StringArrayClass', 0xfa: 'StoreRefArrayUnchecked'}
NORETURN = {0x21, 0x22, 0x23, 0x25, 0x26}


class Diag:
    def __init__(self):
        self.items = []     # (category, func, addr, message)

    def add(self, cat, fn, addr, msg=''):
        self.items.append((cat, fn, addr, msg))


class Sig:
    """Calling convention facts for one local function."""

    def __init__(self, addr, owner=None, name=None, desc=None):
        self.addr = addr
        self.owner = owner
        self.name = name
        self.desc = desc
        self.params = None        # list of TV (inferred) when desc is None
        self.nargs = None         # number of register+stack args
        self.ret = TV()
        self.ret_used = False
        self.static = True
        self.virtual_slot = None
        self.used = set()
        self.stack_used = set()
        self.ret_kinds = set()
        self.is_ctor = False

    def params_ext(self, i):
        while len(self.params) <= i:
            tv = TV()
            tv.param_of = (self, len(self.params))
            self.params.append(tv)
        return self.params[i]


def parse_desc(desc):
    """Return (list of param type strings, return type string)."""
    i = 1
    ps = []
    while desc[i] != ')':
        j = i
        while desc[j] == '[':
            j += 1
        if desc[j] == 'L':
            j = desc.index(';', j)
        ps.append(desc[i:j + 1])
        i = j + 1
    return ps, desc[i + 1:]


def desc_kind(t):
    return 'R' if t[0] in 'L[' else 'I'


# --------------------------------------------------------------------------------------------------------------
# Emission back-ends
# --------------------------------------------------------------------------------------------------------------
class NullLabel:
    pass


class NullEm:
    """Analysis-mode emitter: swallows everything."""
    active = False

    def __getattr__(self, name):
        def f(*a, **k):
            return NullLabel() if name == 'label' else None
        return f


class Em:
    """Bytecode emitter bound to a jvm.Code; maps (kind, home) -> JVM local numbers."""
    active = True

    def __init__(self, code, nparam_slots):
        import jvm
        self.jvm = jvm
        self.code = code
        self.np = nparam_slots
        self.locmap = {}
        self.reg_locals = set()
        self.next = nparam_slots + 32
        code.local(self.next)

    def loc(self, kind, home):
        if home[0] == 'r':
            n = self.np + home[1] + (0 if kind == 'I' else 16)
            self.code.local(n)
            self.reg_locals.add((kind, n))
            return n
        key = (kind, home)
        n = self.locmap.get(key)
        if n is None:
            n = self.next
            self.next += 1
            self.locmap[key] = n
            self.code.local(n)
        return n

    def label(self):
        return self.jvm.Label()

    def mark(self, l):
        self.code.mark(l)

    def op(self, name, *args):
        self.code.emit('return_' if name == 'return' else name, *args)

    def iconst(self, v):
        v = ((v + 0x80000000) & MASK) - 0x80000000
        self.code.ins.append(('iconst', v))
        self.code.depth += 1
        self.code.max_stack = max(self.code.max_stack, self.code.depth)

    def ldc_str(self, s):
        self.code.ins.append(('ldc_str', s))
        self.code.depth += 1
        self.code.max_stack = max(self.code.max_stack, self.code.depth)

    def iload(self, n):
        self.op('iload', n)

    def aload(self, n):
        self.op('aload', n)

    def istore(self, n):
        self.op('istore', n)

    def astore(self, n):
        self.op('astore', n)


# --------------------------------------------------------------------------------------------------------------
# Liveness
# --------------------------------------------------------------------------------------------------------------
def compute_liveness(cfg):
    """Backward register liveness; returns dict addr -> bitmask live *after* the instruction.
    Calls conservatively use r0-r3 and the target register and define r0-r3, r12, lr."""
    insns = cfg.insns
    gen, kill = {}, {}

    def ud(ins):
        u, d = uses_defs(ins)
        cond = ins.cc != 'al'
        if ins.op == 'bl' or (ins.op == 'bx' and ins.ops[0][1] != LR):
            u = set(u) | {0, 1, 2, 3}
            d = set(d) | {0, 1, 2, 3, 12, 14}
            cond = False if ins.cc == 'al' else True
        elif is_return(ins):
            u = set(u) | {0, 1}
        elif ins.op.startswith('ldm') and PC in ins.ops[1][1]:
            u = set(u) | {0, 1}
        mu = 0
        for r in u:
            mu |= 1 << r
        md = 0
        if not cond:
            for r in d:
                md |= 1 << r
        return mu, md
    ud_cache = {a: ud(insns[a]) for a in cfg.order}
    live_in = {b: 0 for b in cfg.blocks}
    changed = True
    order = list(reversed(cfg.rpo()))
    after = {}
    while changed:
        changed = False
        for b in order:
            blk = cfg.blocks[b]
            live = 0
            for s in blk.succ:
                live |= live_in[s]
            for a in reversed(blk.insns):
                after[a] = live
                mu, md = ud_cache[a]
                live = (live & ~md) | mu
            if live != live_in[b]:
                live_in[b] = live
                changed = True
    return after, live_in


def sx32(v):
    v &= MASK
    return v - (1 << 32) if v & 0x80000000 else v


def home_r(n):
    return ('r', n)


class Unsupported(Exception):
    pass


class Terminated(Exception):
    """Raised by no-return runtime thunks (throw): the rest of the basic block is unreachable."""
    pass


class Core:
    def __init__(self, W):
        self.W = W
        self.B = W.B
        self.M = W.M
        self.im = W.im
        self.diag = Diag()
        self.cfgs = {}
        self.sigs = {}
        self.famsigs = {}
        self.fixed_sites = {}       # (hint, idx) -> list of sites
        self.changed = False        # set when global facts (sigs/TVs/hints) change during a round
        self.field_hints = {}       # (declaring class, word) -> hint
        self.ctor_fns = {}          # function address -> class (functions called as constructors of a Instantiate'd object)
        self.text_thunks = self._text_thunks()
        self.fn = None

    def _text_thunks(self):
        out = {}
        for a in range(0xf4000, 0xf4a00, 4):
            if self.B.word(a) == 0xe52de004 and (self.B.word(a + 4) >> 24) == 0xeb:
                out[a] = (self.B.word(a + 8), self.B.word(a + 12))
        return out

    # ------------------------------------------------------------------ signatures
    def sig_for(self, addr):
        s = self.sigs.get(addr)
        if s:
            return s
        W = self.W
        m = W.mth_by_addr.get(addr)
        if m:
            s = Sig(addr, m.owner, m.name, m.desc)
            s.static = bool(m.acc & 8)
            s.params = []
            s.params_ext(3)
        else:
            fam = None
            for cm in W.cls.values():
                for slot, a in cm.vslots.items():
                    if a == addr:
                        root = W.vslot_root(cm.name, slot)
                        fam = (root.name if root else cm.name, slot)
                        break
                if fam:
                    break
            if fam:
                s = self.famsigs.get(fam)
                if s is None:
                    s = Sig(addr, fam[0], 'v%d' % fam[1], None)
                    s.static = False
                    s.virtual_slot = fam[1]
                    s.params = []
                    s.params_ext(3)
                    self.famsigs[fam] = s
            else:
                owner = W.owner_of_code(addr)
                s = Sig(addr, owner, 'fn_%x' % addr, None)
                s.static = True
                s.params = []
                s.params_ext(3)
        self.sigs[addr] = s
        return s

    def cfg_of(self, addr):
        c = self.cfgs.get(addr)
        if c is None:
            c = self.cfgs[addr] = CFG(self.B, addr)
        return c

    # ------------------------------------------------------------------ diagnostics
    def dg(self, cat, addr, msg=''):
        self.diag.add(cat, self.fn.addr if self.fn else None, addr, msg)

    # ------------------------------------------------------------------ state helpers
    def kill_dead(self, st, addr):
        live = self.fn.live[addr]
        regs = st.regs
        for r in range(13):
            if not (live >> r) & 1 and regs[r] is not U:
                regs[r] = U

    def refs_loc(self, x, kind, home):
        """Does symbolic element x embed ('h', kind, home)?"""
        if isinstance(x, Val):
            return x.k == 'S' and self.refs_loc(x.sym, kind, home)
        if isinstance(x, tuple):
            if len(x) == 3 and x[0] == 'h':
                return x[1] == kind and x[2] == home
            return any(self.refs_loc(y, kind, home) for y in x)
        return False

    def flag_refs(self, f, kind, home):
        for x in f:
            if isinstance(x, Val):
                if x.h == home and x.h is not None and x.kind() == kind:
                    return True
                if x.k == 'S' and self.refs_loc(x.sym, kind, home):
                    return True
            elif isinstance(x, tuple):
                if self.flag_refs(x, kind, home):
                    return True
        return False

    def protect(self, st, kind, home):
        """Before JVM local (kind, home) is overwritten, snapshot values that symbolically depend on it."""
        need = False
        for v in st.regs:
            if v.k == 'S' and self.refs_loc(v.sym, kind, home):
                need = True
                break
        if not need:
            for v in st.slots.values():
                if v.k == 'S' and self.refs_loc(v.sym, kind, home):
                    need = True
                    break
        if not need and st.flags is not None and self.flag_refs(st.flags, kind, home):
            need = True
        if not need:
            return
        self.ntemps = getattr(self, 'ntemps', 0) + 1
        th = ('t', self.ntemps)
        em = self.em
        if em.active:
            em.op('aload' if kind == 'R' else 'iload', em.loc(kind, home))
            em.op('astore' if kind == 'R' else 'istore', em.loc(kind, th))
        for i, v in enumerate(st.regs):
            if v.k == 'S' and self.refs_loc(v.sym, kind, home):
                st.regs[i] = Val('S', sym=self.subst(v.sym, kind, home, th))
        for o, v in list(st.slots.items()):
            if v.k == 'S' and self.refs_loc(v.sym, kind, home):
                st.slots[o] = Val('S', sym=self.subst(v.sym, kind, home, th))
        if st.flags is not None:
            st.flags = self.flag_subst(st.flags, kind, home, th)

    def subst(self, x, kind, home, new):
        if isinstance(x, tuple):
            if len(x) == 3 and x[0] == 'h' and x[1] == kind and x[2] == home:
                return ('h', kind, new)
            return tuple(self.subst(y, kind, home, new) for y in x)
        return x

    def flag_subst(self, f, kind, home, new):
        out = []
        for x in f:
            if isinstance(x, Val):
                if x.h == home and x.h is not None and x.kind() == kind:
                    x = Val(x.k, x.c, new, x.hint, x.sym, x.tv)
                elif x.k == 'S' and self.refs_loc(x.sym, kind, home):
                    x = Val('S', sym=self.subst(x.sym, kind, home, new))
            elif isinstance(x, tuple):
                x = self.flag_subst(x, kind, home, new)
            out.append(x)
        return tuple(out)

    # ------------------------------------------------------------------ joins
    def lub(self, a, b):
        if a == b:
            return a
        if a is None:
            return b
        if b is None:
            return a
        W = self.W
        if a in W.cls and b in W.cls:
            ca = [c.name for c in W.chain(a)]
            for c in W.chain(b):
                if c.name in ca:
                    return c.name
            fa, fb = W.framework_super(a), W.framework_super(b)
            return fa if fa == fb else 'java/lang/Object'
        return 'java/lang/Object'

    def join_val(self, a, b, home=None):
        if a is b:
            return a
        ka, kb = a.kind(), b.kind()
        if ka == 'U':
            return b
        if kb == 'U':
            return a
        if ka == 'T' and kb in ('I', 'R'):
            a.tv.set(kb)
            ka = kb
        elif kb == 'T' and ka in ('I', 'R'):
            b.tv.set(ka)
            kb = ka
        elif ka == 'T' and kb == 'T':
            if a.tv.find() is not b.tv.find():
                if a.tv.unify(b.tv):
                    self.changed = True
            return Val('T', h=a.h if a.h == b.h else home, tv=a.tv)
        if ka != kb:
            return U
        if ka == 'S':
            return a if a.key() == b.key() else U
        h = a.h if a.h == b.h else None
        if a.h != b.h or a.c != b.c:
            h = home if home is not None else (a.h or b.h)
        c = a.c if (a.c == b.c and a.h == b.h) else None
        hint = self.lub(a.hint, b.hint) if ka == 'R' else None
        if c is not None and h is None:
            return Val(ka, c, None, hint)
        if h is None:
            h = home
        return Val(ka, None, h, hint)

    def join_state(self, a, b):
        s = State()
        for r in range(16):
            s.regs[r] = self.join_val(a.regs[r], b.regs[r], home_r(r))
        for o in set(a.slots) | set(b.slots):
            va, vb = a.slots.get(o), b.slots.get(o)
            if va is None or vb is None:
                v = va or vb
                v = v if v.k != 'S' else U
            else:
                v = self.join_val(va, vb, ('s', o))
            if v is not U:
                s.slots[o] = v
        s.flags = a.flags if flags_eq(a.flags, b.flags) else None
        if a.frames != b.frames:
            s.frames = a.frames if len(a.frames) >= len(b.frames) else b.frames
            self.dg('frame-mismatch', 0, '%s vs %s' % (a.frames, b.frames))
        else:
            s.frames = a.frames
        return s

    # ------------------------------------------------------------------ push helpers
    def use(self, v):
        if v.k == 'T' and v.tv is not None:
            p = getattr(v.tv, 'param_of', None)
            if p is not None:
                p[0].used.add(p[1])
        return v

    def want_kind(self, v, k):
        """Constrain a possibly-unresolved value to kind k ('I'/'R'); returns True if consistent."""
        self.use(v)
        kk = v.kind()
        if kk == 'T':
            if v.tv.set(k):
                self.changed = True
            return True
        return kk == k

    def push(self, v, want=None):
        """Emit code pushing value v on the JVM stack."""
        em = self.em
        self.use(v)
        k = v.kind()
        if k == 'T':
            if want:
                self.want_kind(v, want)
                k = want
            else:
                k = 'I'
        if k == 'I':
            if v.c is not None and v.h is None:
                em.iconst(v.c)
            else:
                em.iload(em.loc('I', v.h)) if em.active else None
        elif k == 'R':
            if v.c == 0 and v.h is None:
                em.op('aconst_null')
            else:
                em.aload(em.loc('R', v.h)) if em.active else None
        elif k == 'S':
            sym = v.sym
            if sym[0] == 'lit':
                s = self.M.strings[sym[1]]
                em.ldc_str(s)
            else:
                raise Unsupported('push of symbolic %s' % (sym,))
        else:
            raise Unsupported('push of undefined value')
        return k

    def reg_val(self, st, r):
        v = st.regs[r]
        return self.use(v)

    def const_of(self, v):
        if v.kind() == 'I' and v.c is not None and v.h is None:
            return v.c
        return None


def flags_eq(a, b):
    from absval import flags_key
    return flags_key(a) == flags_key(b)


INV = armdec.INV
ACCESS = {'ldr': (4, False), 'ldrb': (1, False), 'ldrh': (2, False), 'ldrsb': (1, True), 'ldrsh': (2, True),
          'str': (4, False), 'strb': (1, False), 'strh': (2, False)}
BSS_REGIONS = [(0x1500000, 0x1500954, 'str', 4), (0x1500960, 0x1500b00, 'nvt', 4), (0x1500b00, 0x1500e40, 'finst', 2),
               (0x1500e40, 0x1500e9e, 'vidx', 2), (0x1500e9e, 0x1500ea0, 'fstat', 2), (0x1500ea0, 0x1500ea8, 'iidx', 2)]


def bss_region(a):
    for lo, hi, nm, w in BSS_REGIONS:
        if lo <= a < hi:
            return nm, (a - lo) // w, w
    return None

