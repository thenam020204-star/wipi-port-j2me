"""Minimal JVM classfile writer for CLDC targets (class version 47.0, no StackMap; run ProGuard -microedition afterwards).

Only what the lifter needs: int/ref locals, branches (auto short/long), invoke*, get/putfield, arrays, athrow,
exception tables.  No long/float/double.
"""
import struct

# ---- opcodes used
OP = dict(
    nop=0, aconst_null=1, iconst_m1=2, iconst_0=3, iconst_1=4, iconst_2=5, iconst_3=6, iconst_4=7, iconst_5=8,
    bipush=16, sipush=17, ldc=18, ldc_w=19,
    iload=21, aload=25, istore=54, astore=58,
    iaload=46, aaload=50, baload=51, caload=52, saload=53,
    iastore=79, aastore=83, bastore=84, castore=85, sastore=86,
    pop=87, pop2=88, dup=89, dup_x1=90, dup_x2=91, dup2=92, swap=95,
    iadd=96, isub=100, imul=104, idiv=108, irem=112, ineg=116, ishl=120, ishr=122, iushr=124, iand=126, ior=128,
    ixor=130, iinc=132, i2b=145, i2c=146, i2s=147,
    ifeq=153, ifne=154, iflt=155, ifge=156, ifgt=157, ifle=158,
    if_icmpeq=159, if_icmpne=160, if_icmplt=161, if_icmpge=162, if_icmpgt=163, if_icmple=164,
    if_acmpeq=165, if_acmpne=166, goto=167, tableswitch=170, lookupswitch=171,
    ireturn=172, areturn=176, return_=177,
    getstatic=178, putstatic=179, getfield=180, putfield=181,
    invokevirtual=182, invokespecial=183, invokestatic=184, invokeinterface=185,
    new=187, newarray=188, anewarray=189, arraylength=190, athrow=191, checkcast=192, instanceof=193,
    multianewarray=197, ifnull=198, ifnonnull=199, goto_w=200,
)
INVERT = {'ifeq': 'ifne', 'ifne': 'ifeq', 'iflt': 'ifge', 'ifge': 'iflt', 'ifgt': 'ifle', 'ifle': 'ifgt',
          'if_icmpeq': 'if_icmpne', 'if_icmpne': 'if_icmpeq', 'if_icmplt': 'if_icmpge', 'if_icmpge': 'if_icmplt',
          'if_icmpgt': 'if_icmple', 'if_icmple': 'if_icmpgt', 'if_acmpeq': 'if_acmpne', 'if_acmpne': 'if_acmpeq',
          'ifnull': 'ifnonnull', 'ifnonnull': 'ifnull'}
COND_JUMPS = set(INVERT)
# net stack effect of simple ops (pops - pushes handled by table)
STACK = dict(
    nop=0, aconst_null=1, iconst_m1=1, iconst_0=1, iconst_1=1, iconst_2=1, iconst_3=1, iconst_4=1, iconst_5=1,
    bipush=1, sipush=1, ldc=1, ldc_w=1, iload=1, aload=1, istore=-1, astore=-1,
    iaload=-1, aaload=-1, baload=-1, caload=-1, saload=-1, iastore=-3, aastore=-3, bastore=-3, castore=-3, sastore=-3,
    pop=-1, dup=1, dup_x1=1, dup_x2=1, swap=0,
    iadd=-1, isub=-1, imul=-1, idiv=-1, irem=-1, ineg=0, ishl=-1, ishr=-1, iushr=-1, iand=-1, ior=-1, ixor=-1,
    iinc=0, i2b=0, i2c=0, i2s=0,
    ifeq=-1, ifne=-1, iflt=-1, ifge=-1, ifgt=-1, ifle=-1, if_icmpeq=-2, if_icmpne=-2, if_icmplt=-2, if_icmpge=-2,
    if_icmpgt=-2, if_icmple=-2, if_acmpeq=-2, if_acmpne=-2, goto=0, ireturn=-1, areturn=-1, return_=0,
    new=1, newarray=0, anewarray=0, arraylength=0, athrow=-1, checkcast=0, instanceof=0, ifnull=-1, ifnonnull=-1,
    tableswitch=-1, lookupswitch=-1,
)


def desc_slots(desc):
    """Return (argslots, retslots) for a method descriptor (no long/double support: all 1 slot)."""
    i = 1
    n = 0
    while desc[i] != ')':
        c = desc[i]
        while c == '[':
            i += 1
            c = desc[i]
        if c == 'L':
            i = desc.index(';', i)
        i += 1
        n += 1
    r = desc[i + 1:]
    return n, (0 if r == 'V' else 1)


class ConstPool:
    def __init__(self):
        self.items = []     # list of bytes
        self.map = {}
        self.count = 1

    def _add(self, key, data):
        if key in self.map:
            return self.map[key]
        idx = self.count
        self.items.append(data)
        self.map[key] = idx
        self.count += 1
        return idx

    def utf8(self, s):
        b = s.encode('utf-8')
        return self._add(('u', s), b'\x01' + struct.pack('>H', len(b)) + b)

    def cls(self, name):
        n = self.utf8(name)
        return self._add(('c', name), b'\x07' + struct.pack('>H', n))

    def string(self, s):
        n = self.utf8(s)
        return self._add(('s', s), b'\x08' + struct.pack('>H', n))

    def integer(self, v):
        return self._add(('i', v), b'\x03' + struct.pack('>i', v))

    def nat(self, name, desc):
        a, b = self.utf8(name), self.utf8(desc)
        return self._add(('nt', name, desc), b'\x0c' + struct.pack('>HH', a, b))

    def field(self, owner, name, desc):
        c, n = self.cls(owner), self.nat(name, desc)
        return self._add(('f', owner, name, desc), b'\x09' + struct.pack('>HH', c, n))

    def method(self, owner, name, desc):
        c, n = self.cls(owner), self.nat(name, desc)
        return self._add(('m', owner, name, desc), b'\x0a' + struct.pack('>HH', c, n))

    def imethod(self, owner, name, desc):
        c, n = self.cls(owner), self.nat(name, desc)
        return self._add(('im', owner, name, desc), b'\x0b' + struct.pack('>HH', c, n))

    def write(self):
        return struct.pack('>H', self.count) + b''.join(self.items)


class Label:
    __slots__ = ('pos', 'depth')

    def __init__(self):
        self.pos = None
        self.depth = None


class Code:
    """Instruction list assembler.  Instructions: tuples (opname, args...).  Labels are Label objects placed with mark()."""

    def __init__(self, cp, nargs_slots, is_static):
        self.cp = cp
        self.ins = []          # ('op', ...), or Label
        self.max_locals = nargs_slots
        self.handlers = []     # (start Label, end Label, handler Label, catch class or None)
        self.depth = 0
        self.max_stack = 0
        self.dead = False
        self.size_estimate = 0

    # ---- emission helpers
    def emit(self, op, *args):
        self.ins.append((op,) + args)
        d = self._effect(op, args)
        self.depth += d
        if self.depth > self.max_stack:
            self.max_stack = self.depth
        if self.depth < 0:
            raise ValueError('stack underflow at %s %s' % (op, args))

    def _effect(self, op, args):
        if op in STACK:
            return STACK[op]
        if op in ('getstatic',):
            return 1
        if op == 'putstatic':
            return -1
        if op == 'getfield':
            return 0
        if op == 'putfield':
            return -2
        if op in ('invokevirtual', 'invokespecial', 'invokeinterface', 'invokestatic'):
            n, r = desc_slots(args[2])
            return -n - (0 if op == 'invokestatic' else 1) + r
        if op == 'multianewarray':
            return 1 - args[1]
        raise KeyError(op)

    def mark(self, label):
        self.ins.append(label)

    def local(self, n):
        if n + 1 > self.max_locals:
            self.max_locals = n + 1

    # ---- assembly
    def assemble(self, long_jumps=False):
        """Two-pass.  Returns bytes."""
        cp = self.cp
        # pass 1: sizes
        pos = 0
        sized = []
        for it in self.ins:
            if isinstance(it, Label):
                it.pos = pos
                sized.append((it, 0))
                continue
            sz = self._size(it, pos, long_jumps)
            sized.append((it, sz))
            pos += sz
        total = pos
        # branch offsets may change sizes for switch padding; recompute once more
        pos = 0
        out = bytearray()
        for it, _ in sized:
            if isinstance(it, Label):
                it.pos = pos
                continue
            pos += self._size(it, pos, long_jumps)
        pos = 0
        for it, _ in sized:
            if isinstance(it, Label):
                continue
            b = self._encode(it, pos, long_jumps)
            out += b
            pos += len(b)
        if len(out) > 65535:
            raise OverflowError('method code too large: %d' % len(out))
        return bytes(out)

    def _size(self, it, pos, lj):
        op = it[0]
        if op in COND_JUMPS:
            return 8 if lj else 3
        if op == 'goto':
            return 5 if lj else 3
        if op in ('iload', 'aload', 'istore', 'astore'):
            n = it[1]
            return 1 if n <= 3 else (2 if n < 256 else 4)
        if op == 'iconst':
            v = it[1]
            if -1 <= v <= 5:
                return 1
            if -128 <= v <= 127:
                return 2
            if -32768 <= v <= 32767:
                return 3
            idx = self.cp.integer(v)
            return 2 if idx < 256 else 3
        if op == 'iinc':
            return 3 if (-128 <= it[2] <= 127 and it[1] < 256) else 6
        if op == 'tableswitch':
            pad = (4 - ((pos + 1) % 4)) % 4
            return 1 + pad + 12 + 4 * len(it[3])
        if op == 'lookupswitch':
            pad = (4 - ((pos + 1) % 4)) % 4
            return 1 + pad + 8 + 8 * len(it[2])
        if op in ('getfield', 'putfield', 'getstatic', 'putstatic', 'invokevirtual', 'invokespecial', 'invokestatic',
                  'new', 'anewarray', 'checkcast', 'instanceof'):
            return 3
        if op == 'invokeinterface':
            return 5
        if op == 'newarray':
            return 2
        if op == 'ldc_str':
            idx = self.cp.string(it[1])
            return 2 if idx < 256 else 3
        if op == 'bipush':
            return 2
        if op == 'sipush':
            return 3
        if op == 'multianewarray':
            return 4
        return 1

    def _encode(self, it, pos, lj):
        op = it[0]
        cp = self.cp
        if op in COND_JUMPS:
            tgt = it[1].pos
            if lj:
                inv = INVERT[op]
                return bytes([OP[inv]]) + struct.pack('>h', 8) + bytes([OP['goto_w']]) + struct.pack('>i', tgt - (pos + 3))
            off = tgt - pos
            if not -32768 <= off <= 32767:
                raise OverflowError('branch')
            return bytes([OP[op]]) + struct.pack('>h', off)
        if op == 'goto':
            tgt = it[1].pos
            if lj:
                return bytes([OP['goto_w']]) + struct.pack('>i', tgt - pos)
            off = tgt - pos
            if not -32768 <= off <= 32767:
                raise OverflowError('branch')
            return bytes([OP['goto']]) + struct.pack('>h', off)
        if op in ('iload', 'aload', 'istore', 'astore'):
            n = it[1]
            base = OP[op]
            if n <= 3:
                short = {'iload': 26, 'aload': 42, 'istore': 59, 'astore': 75}[op] + n
                return bytes([short])
            if n < 256:
                return bytes([base, n])
            return bytes([196, base]) + struct.pack('>H', n)
        if op == 'iconst':
            v = it[1]
            if -1 <= v <= 5:
                return bytes([3 + v])
            if -128 <= v <= 127:
                return bytes([16, v & 255])
            if -32768 <= v <= 32767:
                return bytes([17]) + struct.pack('>h', v)
            idx = cp.integer(v)
            return bytes([18, idx]) if idx < 256 else bytes([19]) + struct.pack('>H', idx)
        if op == 'ldc_str':
            idx = cp.string(it[1])
            return bytes([18, idx]) if idx < 256 else bytes([19]) + struct.pack('>H', idx)
        if op == 'iinc':
            n, v = it[1], it[2]
            if -128 <= v <= 127 and n < 256:
                return bytes([132, n, v & 255])
            return bytes([196, 132]) + struct.pack('>Hh', n, v)
        if op in ('getfield', 'putfield', 'getstatic', 'putstatic'):
            return bytes([OP[op]]) + struct.pack('>H', cp.field(it[1], it[2], it[3]))
        if op in ('invokevirtual', 'invokespecial', 'invokestatic'):
            return bytes([OP[op]]) + struct.pack('>H', cp.method(it[1], it[2], it[3]))
        if op == 'invokeinterface':
            n, _ = desc_slots(it[3])
            return bytes([OP[op]]) + struct.pack('>HBB', cp.imethod(it[1], it[2], it[3]), n + 1, 0)
        if op in ('new', 'anewarray', 'checkcast', 'instanceof'):
            return bytes([OP[op]]) + struct.pack('>H', cp.cls(it[1]))
        if op == 'newarray':
            return bytes([OP[op], it[1]])
        if op == 'multianewarray':
            return bytes([OP[op]]) + struct.pack('>HB', cp.cls(it[1]), it[2])
        if op == 'bipush':
            return bytes([16, it[1] & 255])
        if op == 'sipush':
            return bytes([17]) + struct.pack('>h', it[1])
        if op == 'tableswitch':
            # ('tableswitch', default Label, low, [labels])
            pad = (4 - ((pos + 1) % 4)) % 4
            b = bytes([170]) + b'\0' * pad
            b += struct.pack('>iii', it[1].pos - pos, it[2], it[2] + len(it[3]) - 1)
            for l in it[3]:
                b += struct.pack('>i', l.pos - pos)
            return b
        if op == 'lookupswitch':
            # ('lookupswitch', default Label, [(key, Label)...])
            pad = (4 - ((pos + 1) % 4)) % 4
            b = bytes([171]) + b'\0' * pad
            pairs = sorted(it[2], key=lambda p: p[0])
            b += struct.pack('>ii', it[1].pos - pos, len(pairs))
            for k, l in pairs:
                b += struct.pack('>ii', k, l.pos - pos)
            return b
        return bytes([OP[op]])


def _op_alias(name):
    return 'return_' if name == 'return' else name


class Method:
    def __init__(self, cf, acc, name, desc, nargs_slots, is_static):
        self.cf = cf
        self.acc = acc
        self.name = name
        self.desc = desc
        self.code = Code(cf.cp, nargs_slots, is_static)

    def write(self, cp):
        code = self.code
        try:
            body = code.assemble(False)
        except OverflowError as e:
            if 'branch' not in str(e):
                raise
            body = code.assemble(True)
        # exception table
        exc = b''
        for s, e, h, c in code.handlers:
            exc += struct.pack('>HHHH', s.pos, e.pos, h.pos, cp.cls(c) if c else 0)
        attr = struct.pack('>HHI', code.max_stack + 2, code.max_locals, len(body)) + body
        attr += struct.pack('>H', len(code.handlers)) + exc + struct.pack('>H', 0)
        out = struct.pack('>HHH', self.acc, cp.utf8(self.name), cp.utf8(self.desc))
        out += struct.pack('>H', 1) + struct.pack('>HI', cp.utf8('Code'), len(attr)) + attr
        return out


class ClassFile:
    def __init__(self, name, super_name, acc=0x21, interfaces=()):
        self.name = name
        self.super_name = super_name
        self.acc = acc
        self.interfaces = list(interfaces)
        self.cp = ConstPool()
        self.fields = []     # (acc, name, desc)
        self.methods = []
        self.abstract_methods = []

    def add_field(self, acc, name, desc):
        self.fields.append((acc, name, desc))

    def add_method(self, acc, name, desc, is_static):
        n, _ = desc_slots(desc)
        m = Method(self, acc, name, desc, n + (0 if is_static else 1), is_static)
        self.methods.append(m)
        return m

    def add_abstract(self, acc, name, desc):
        self.abstract_methods.append((acc, name, desc))

    def to_bytes(self):
        cp = self.cp
        this = cp.cls(self.name)
        sup = cp.cls(self.super_name)
        ifs = [cp.cls(i) for i in self.interfaces]
        mbytes = [m.write(cp) for m in self.methods]
        abytes = []
        for acc, name, desc in self.abstract_methods:
            abytes.append(struct.pack('>HHHH', acc, cp.utf8(name), cp.utf8(desc), 0))
        fbytes = []
        for acc, name, desc in self.fields:
            fbytes.append(struct.pack('>HHHH', acc, cp.utf8(name), cp.utf8(desc), 0))
        out = struct.pack('>IHH', 0xCAFEBABE, 0, 47)
        out += cp.write()
        out += struct.pack('>HHH', self.acc, this, sup)
        out += struct.pack('>H', len(ifs)) + b''.join(struct.pack('>H', i) for i in ifs)
        out += struct.pack('>H', len(fbytes)) + b''.join(fbytes)
        out += struct.pack('>H', len(mbytes) + len(abytes)) + b''.join(mbytes) + b''.join(abytes)
        out += struct.pack('>H', 0)
        return out
