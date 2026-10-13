"""Abstract values / state for the ARM->JVM lifter."""


class TV:
    """Kind variable (union-find) for values whose int/ref kind is only known from use (descriptor-less params)."""
    __slots__ = ('p', 'k', 'hint', 'conflict', 'param_of')

    def __init__(self):
        self.p = None
        self.k = None        # 'I' or 'R'
        self.hint = None
        self.conflict = False
        self.param_of = None

    def find(self):
        t = self
        while t.p is not None:
            t = t.p
        return t

    def kind(self):
        return self.find().k

    def set(self, k):
        t = self.find()
        if t.k is None:
            t.k = k
            return True
        if t.k != k:
            t.conflict = True
        return False

    def unify(self, o):
        a, b = self.find(), o.find()
        if a is b:
            return False
        b.p = a
        if a.k is None:
            a.k = b.k
        elif b.k is not None and a.k != b.k:
            a.conflict = True
        a.hint = join_hint(a.hint, b.hint)
        a.conflict = a.conflict or b.conflict
        return True

    def add_hint(self, h):
        t = self.find()
        nh = join_hint(t.hint, h) if t.hint else h
        if nh != t.hint:
            t.hint = nh
            return True
        return False


def join_hint(a, b):
    if a == b:
        return a
    if a is None:
        return b
    if b is None:
        return a
    return 'java/lang/Object' if a != b else a


# Val kinds:  'U' undefined/dead, 'I' int, 'R' ref, 'S' symbolic (never materialised in a JVM local), 'T' kind-var
class Val:
    __slots__ = ('k', 'c', 'h', 'hint', 'sym', 'tv')

    def __init__(self, k, c=None, h=None, hint=None, sym=None, tv=None):
        self.k, self.c, self.h, self.hint, self.sym, self.tv = k, c, h, hint, sym, tv

    def kind(self):
        if self.k == 'T':
            return self.tv.kind() or 'T'
        return self.k

    def key(self):
        """Equality key for fix-point comparison (ignores hints)."""
        k = self.kind()
        if k == 'S':
            return ('S', sym_key(self.sym))
        if k == 'I':
            return ('I', self.c, self.h)
        if k == 'R':
            return ('R', self.c, self.h)
        if k == 'T':
            return ('T', id(self.tv.find()), self.h)
        return (k,)

    def __repr__(self):
        k = self.kind()
        if k == 'S':
            return 'S%s' % (self.sym,)
        if k == 'I':
            return 'I(%s@%s)' % (self.c, self.h)
        if k == 'R':
            return 'R(%s@%s %s)' % (self.c, self.h, self.hint)
        return k


U = Val('U')


def sym_key(s):
    out = []
    for x in s:
        if isinstance(x, tuple):
            out.append(sym_key(x))
        elif isinstance(x, Val):
            out.append(x.key())
        else:
            out.append(x)
    return tuple(out)


def S(*sym):
    return Val('S', sym=tuple(sym))


class State:
    __slots__ = ('regs', 'slots', 'flags', 'frames')

    def __init__(self):
        self.regs = [U] * 16
        self.slots = {}
        self.flags = None
        self.frames = ()

    def copy(self):
        s = State()
        s.regs = list(self.regs)
        s.slots = dict(self.slots)
        s.flags = self.flags
        s.frames = self.frames
        return s

    def key(self):
        return (tuple(v.key() for v in self.regs), tuple(sorted((o, v.key()) for o, v in self.slots.items())),
                flags_key(self.flags), self.frames)


def flags_key(f):
    if f is None:
        return None
    out = []
    for x in f:
        if isinstance(x, Val):
            out.append(x.key())
        elif isinstance(x, tuple):
            out.append(flags_key(x))
        else:
            out.append(x)
    return tuple(out)
