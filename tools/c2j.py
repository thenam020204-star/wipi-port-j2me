#!/usr/bin/env python3
"""Semi-automatic translator Ghidra pseudo-C -> Java in the style of j2me/src (T0.2, docs/PLAN_NEXT.md).  A DRAFT generator: every
translated function must still pass the per-function check (tools/e_calls.py + tools/jcr.sh + tools/callcmp.py) before it counts as ported.

Usage: python3 tools/c2j.py NAME [NAME..]          -> Java method text on stdout (one per function)
       python3 tools/c2j.py --stats NAME..         -> only the C2J-CHECK counts

What it does:
  * parses the raw Ghidra text (ghidra/decomp_and2011.c) with pycparser (Ghidra types declared as typedefs);
  * memory accesses '*(T *)(p + off)' become Bytes.s8/u8/s16/u16/s32(p.m, off) / Bytes.w8/w16/w32 by the width of T; the base p is typed Obj
    (Char when the Java callee/caller says so); globals _game/_event/_map/... become G.game/G.event/...; '_g._O_N_' = N bytes at offset O;
  * locals are Java ints; an assignment to a char/byte/short/ushort local is truncated like C ((byte), & 0xff, (short), & 0xffff);
    uint operands use >>> and unsigned compares;
  * calls go to the Java method whose javadoc names the original (`/** NAME .. @0x..`): same index as tools/callrec_inst.py; an already-ported
    function keeps its Java signature (drop-in body); unknown callees become 'throw new IllegalStateException("NAME: callee: later")';
  * every construct Ghidra often gets wrong is marked '// C2J-CHECK <why>': loops, goto, comma expressions, argument-count mismatches (Ghidra
    drops pass-through registers), pointer arithmetic, stack arrays (C.x.y tables), unsigned division, unknown globals.  Read the disassembly
    (tools/disf.py) at each mark.
"""
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from pycparser import c_parser, c_ast

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
GH = os.path.join(ROOT, 'ghidra', 'decomp_and2011.c')
SRC = os.path.join(ROOT, 'j2me', 'src')
TYPEDEFS = '''typedef int undefined; typedef char undefined1; typedef short undefined2; typedef int undefined4; typedef long long undefined8;
typedef unsigned char byte; typedef unsigned int uint; typedef unsigned short ushort; typedef unsigned char uchar; typedef int bool;
typedef int code; typedef long long longlong; typedef unsigned long long ulonglong; typedef unsigned int dword; typedef unsigned short word;
typedef int undefined3; typedef int undefined5; typedef int undefined6; typedef int undefined7; typedef unsigned long ulong;
'''
WIDTH = {'char': (1, True), 'undefined1': (1, True), 'byte': (1, False), 'uchar': (1, False), 'bool': (1, False),
         'short': (2, True), 'undefined2': (2, True), 'ushort': (2, False), 'word': (2, False),
         'int': (4, True), 'undefined4': (4, True), 'undefined': (4, True), 'code': (4, True), 'uint': (4, False), 'dword': (4, False),
         'ulong': (4, False), 'undefined3': (4, True), 'long': (4, True)}
READ = {(1, True): 's8', (1, False): 'u8', (2, True): 's16', (2, False): 'u16', (4, True): 's32', (4, False): 's32'}
WRITE = {1: 'w8', 2: 'w16', 4: 'w32'}
GLOBALS = {'_game': 'G.game', '_event': 'G.event', '_map': 'G.map', '_gameMenu': 'G.gameMenu', '_slotInfo': 'G.slotInfo', '_npcUi': 'G.npcUi',
           '_questMenu': 'G.questMenu', '_guardian': 'G.guardian'}
OBJTYPES = ('Obj', 'Char', 'SObj', 'TEff')
ESC = {'\\0': 0, '\\a': 7, '\\b': 8, '\\t': 9, '\\n': 10, '\\v': 11, '\\f': 12, '\\r': 13, '\\"': 34, "\\'": 39, '\\\\': 92}
# original functions returning a fixed object: translated to the Java reference directly
ALIAS = {'MP_getHero': 'Pool.hero', 'CHAR_getHeroChar': 'Pool.hero', 'MP_getSummon': 'Pool.summon'}


# ---------------------------------------------------------------- Java index (original name -> Java method)
DOC = re.compile(r'/\*\*\s*(?:\*\s*)?([A-Za-z_]+_\w*)')
SIG = re.compile(r'(\s*)((?:(?:public|private|protected|static|final|synchronized)\s+)*)([\w\[\]]+)\s+(\w+)\s*\(([^)]*)\)\s*(throws\s+[\w.,\s]+)?\{', re.S)


MSIG = re.compile(r'\n(\s*)((?:(?:public|private|protected|static|final|synchronized)\s+)*static\s+(?:final\s+)?)([\w\[\]]+)\s+(\w+)\s*\(([^)]*)\)\s*(?:throws\s+[\w.,\s]+)?\{')


def by_name(name, methods):
    """fallback for an original name no javadoc cites: 'PREFIX_rest' -> the static method 'rest' (or rest without get/set kept) of a
    class whose name starts like the prefix, when exactly one exists"""
    if '_' not in name.strip('_'):
        return None
    pre, rest = name.split('_', 1)
    if not rest:
        return None
    m = rest[0].lower() + rest[1:]
    cands = [x for x in methods.get(m, []) if x[1].lower().startswith(pre[:3].lower())]
    if len(cands) == 1:
        return cands[0]
    return None


def java_index():
    idx = {}
    for dp, _, fs in os.walk(SRC):
        for f in fs:
            if not f.endswith('.java'):
                continue
            t = open(os.path.join(dp, f), encoding='utf-8').read()
            pkg = os.path.basename(dp)
            for m in DOC.finditer(t):
                end = t.index('*/', m.start()) + 2
                s = SIG.match(t, end)
                if not s or s.group(3) in ('class', 'new', 'return') or s.group(4) in ('if', 'while', 'for', 'switch'):
                    continue
                ps = [p.strip() for p in s.group(5).split(',') if p.strip()]
                params = [(p.split()[-2], p.split()[-1]) for p in ps]
                idx.setdefault(m.group(1), (pkg, f[:-5], s.group(4), 'static' in s.group(2).split(), params, s.group(3)))
    return idx


def java_methods():
    """all static methods: name -> [(pkg, class, name, True, params, rettype)]"""
    out = {}
    for dp, _, fs in os.walk(SRC):
        for f in fs:
            if not f.endswith('.java'):
                continue
            t = open(os.path.join(dp, f), encoding='utf-8').read()
            for s in MSIG.finditer(t):
                ps = [p.strip() for p in s.group(5).split(',') if p.strip()]
                params = [(p.split()[-2], p.split()[-1]) for p in ps]
                out.setdefault(s.group(4), []).append((os.path.basename(dp), f[:-5], s.group(4), True, params, s.group(3)))
    return out


METHODS = None


def lookup(idx, name):
    """Java method of an original function: javadoc index first, then the name fallback (marked by the caller)"""
    global METHODS
    j = idx.get(name)
    if j is not None:
        return j, False
    if METHODS is None:
        METHODS = java_methods()
    j = by_name(name, METHODS)
    return j, j is not None


class LCompound(c_ast.Compound):
    """a block that becomes a labelled Java block (target of forward gotos)"""
    __slots__ = ('jlabel',)


# ---------------------------------------------------------------- Ghidra text
def ghidra_func(name):
    text = open(GH, encoding='utf-8', errors='replace').read()
    m = re.search(r'(?m)^// ==== %s @(\w+) size=(\d+)\n(.*?)(?=^// ==== |\Z)' % re.escape(name), text, re.S)
    if not m:
        raise SystemExit('not in Ghidra output: ' + name)
    return int(m.group(1), 16) - 0x10000, m.group(3)


def clean(body):
    body = re.sub(r'/\*.*?\*/', '', body, flags=re.S)
    body = re.sub(r'\b(\w+)\._(\d+)_(\d+)_', lambda m: '__F%s(%s,%s)' % (m.group(1), m.group(2), m.group(3)), body)   # _game._8_4_
    body = body.replace('\\x', '\\x')
    return body


class Gen:
    def __init__(self, name, idx, inner=False):
        self.name, self.idx = name, idx
        self.addr, raw = ghidra_func(name)
        self.checks = []
        src = TYPEDEFS + clean(raw)
        ast = c_parser.CParser().parse(src)
        self.fn = [e for e in ast.ext if isinstance(e, c_ast.FuncDef)][0]
        self.vtype = {}            # C type name of locals / params
        self.ptr = set()           # variables used as struct pointers
        self.mixed = set()         # variables holding an object and a number
        self.split = set()         # ... split into an int 'v' and an Obj 'v_o'
        self.stride = set()        # object pointers advanced by constants (p_off)
        self.java = idx.get(name)
        decl = self.fn.decl.type
        self.params = []
        if decl.args:
            for p in decl.args.params:
                if isinstance(p, c_ast.Typename):
                    continue
                self.params.append(p.name)
                self.vtype[p.name] = self.ctype(p.type)
        self.ret_c = self.ctype(decl.type)
        self.find_ptrs(self.fn.body)
        # Java parameter names / types
        self.pmap = {}
        self.ptype = {}
        if self.java:
            jp = self.java[4]
            if len(jp) != len(self.params):
                self.check('Java has %d parameters, Ghidra %d: parameter mapping by position' % (len(jp), len(self.params)))
                for k in range(len(self.params), len(jp)):      # Ghidra lost trailing parameters: name them so pass-through calls can use them
                    self.params.append('param_%d' % (k + 1))
                    self.vtype['param_%d' % (k + 1)] = 'int'
            for (t, n), p in zip(jp, self.params):
                self.pmap[p] = n
                self.ptype[p] = t
                if t in OBJTYPES:
                    self.ptr.add(p)
        self.lines = []
        self.subst = {}
        self.blocks = set()
        self.label_blocks(self.fn.body)

    def label_blocks(self, node):
        """forward goto to a label later in an enclosing block: wrap the statements from the one holding the goto up to the label into a
        labelled Java block (goto -> 'break LABEL;')"""
        for n in list(self.walk(node)):
            if not isinstance(n, c_ast.Compound) or not n.block_items:
                continue
            items = n.block_items
            i = 0
            while i < len(items):
                it = items[i]
                if isinstance(it, c_ast.Label):
                    lab = it.name
                    j0 = None
                    for j in range(i):
                        if any(isinstance(x, c_ast.Goto) and x.name == lab for x in self.walk(items[j])):
                            j0 = j
                            break
                    if j0 is not None:
                        blk = LCompound(items[j0:i])
                        blk.jlabel = lab
                        self.blocks.add(lab)
                        items[j0:i] = [blk]
                        i = j0 + 2                  # past the label (now at j0 + 1)
                        continue
                i += 1

    def check(self, why):
        self.checks.append(why)
        return '// C2J-CHECK ' + why

    @staticmethod
    def ctype(t):
        while isinstance(t, (c_ast.TypeDecl,)):
            t = t.type
        if isinstance(t, c_ast.PtrDecl):
            return 'ptr'
        if isinstance(t, c_ast.IdentifierType):
            n = ' '.join(t.names)
            return {'unsigned int': 'uint', 'unsigned char': 'byte', 'unsigned short': 'ushort', 'long long': 'longlong'}.get(n, n)
        return 'int'

    def find_ptrs(self, node):
        for n in self.walk(node):
            if isinstance(n, c_ast.Decl) and n.name:
                self.vtype[n.name] = self.ctype(n.type)
            if isinstance(n, c_ast.UnaryOp) and n.op == '*':
                b = self.base(n.expr)
                if b:
                    self.ptr.add(b)
            # object-typed by the Java side: arguments passed to an Obj/Char parameter, results of calls returning an object
            if isinstance(n, c_ast.FuncCall) and isinstance(n.name, c_ast.ID):
                j = self.idx.get(n.name.name)
                if j and n.args:
                    for (pt, pn), a in zip(j[4], n.args.exprs):
                        if pt in OBJTYPES and isinstance(a, c_ast.ID):
                            self.ptr.add(a.name)
            if isinstance(n, c_ast.Assignment) and isinstance(n.lvalue, c_ast.ID) and isinstance(n.rvalue, c_ast.FuncCall) \
                    and isinstance(n.rvalue.name, c_ast.ID):
                f = n.rvalue.name.name
                if f in ALIAS or (self.idx.get(f) or (0, 0, 0, 0, 0, ''))[5] in OBJTYPES:
                    self.ptr.add(n.lvalue.name)
        # a Ghidra variable that holds an object in one place and a number in another (register reuse): flag it
        for n in self.walk(node):
            if isinstance(n, c_ast.Assignment) and isinstance(n.lvalue, c_ast.ID) and n.lvalue.name in self.ptr and n.op == '=':
                r = n.rvalue
                ok = (isinstance(r, c_ast.ID) and (r.name in self.ptr or r.name in GLOBALS)) or (isinstance(r, c_ast.Constant) and r.value == '0') \
                    or (isinstance(r, c_ast.FuncCall) and isinstance(r.name, c_ast.ID) and (r.name.name in ALIAS or (self.idx.get(r.name.name) or (0, 0, 0, 0, 0, ''))[5] in OBJTYPES or r.name.name not in self.idx)) \
                    or (isinstance(r, c_ast.Cast))
                if not ok:
                    self.mixed.add(n.lvalue.name)
        # 'p = p + K' on an object pointer (walking an array of slots inside a struct): p keeps the object, p_off accumulates K
        for n in self.walk(node):
            if isinstance(n, c_ast.Assignment) and isinstance(n.lvalue, c_ast.ID) and n.lvalue.name in self.ptr:
                r = n.rvalue
                if n.op == '+=' and isinstance(r, c_ast.Constant) or (n.op == '=' and isinstance(r, c_ast.BinaryOp) and r.op == '+'
                                                                       and isinstance(r.left, c_ast.ID) and r.left.name == n.lvalue.name
                                                                       and isinstance(r.right, c_ast.Constant)):
                    self.stride.add(n.lvalue.name)
        self.mixed -= self.stride
        for v in self.mixed:                      # split: 'v' stays an int, 'v_o' holds the object
            if v not in self.params:
                self.ptr.discard(v)
                self.split.add(v)

    def walk(self, n):
        yield n
        for _, c in n.children():
            yield from self.walk(c)

    def base(self, e):
        """variable that is the base of a deref expression (T *)(v + c) / (T *)v"""
        if isinstance(e, c_ast.Cast):
            e = e.expr
        if isinstance(e, c_ast.BinaryOp) and e.op == '+' and isinstance(e.left, c_ast.ID):
            return e.left.name
        if isinstance(e, c_ast.ID):
            return e.name
        return None

    # ------------------------------------------------------------ expressions
    def var(self, n):
        return self.pmap.get(n, n)

    def unsigned(self, e):
        if isinstance(e, c_ast.Cast):
            return self.ctype(e.to_type.type) in ('uint', 'dword', 'ulong')
        if isinstance(e, c_ast.ID):
            return self.vtype.get(e.name) in ('uint', 'dword', 'ulong')
        if isinstance(e, c_ast.BinaryOp) and e.op in ('+', '-'):
            # Ghidra types 'byte - 2' / 'ushort + k' as unsigned (the code compares with lo/ls/hi/hs): an unsigned operand makes it unsigned
            def uns(x):
                if isinstance(x, c_ast.ID):
                    return self.vtype.get(x.name) in ('uint', 'dword', 'ulong', 'byte', 'uchar', 'ushort', 'word')
                return self.unsigned(x)
            return uns(e.left) or uns(e.right)
        if isinstance(e, c_ast.Constant):
            return e.value.lower().endswith('u')
        return False

    def is_ptr_expr(self, r):
        if isinstance(r, c_ast.Cast):
            return self.is_ptr_expr(r.expr)
        if isinstance(r, c_ast.ID):
            return r.name in self.ptr or r.name in GLOBALS
        if isinstance(r, c_ast.FuncCall) and isinstance(r.name, c_ast.ID):
            f = r.name.name
            return f in ALIAS or (self.idx.get(f) or (0, 0, 0, 0, 0, ''))[5] in OBJTYPES
        return False

    def deref(self, e):
        """(java object expr, offset expr, width, signed) of '*(T *)(addr)'"""
        t = e.expr
        width, signed = 4, True
        if isinstance(t, c_ast.Cast):
            tt = t.to_type.type
            if isinstance(tt, c_ast.PtrDecl):
                width, signed = WIDTH.get(self.ctype(tt.type), (4, True))
            t = t.expr
        if isinstance(t, c_ast.BinaryOp) and t.op == '+' and isinstance(t.left, c_ast.ID) and t.left.name in self.split:
            return t.left.name + '_o.m', self.expr(t.right), width, signed
        if isinstance(t, c_ast.ID) and t.name in self.split:
            return t.name + '_o.m', '0', width, signed
        if isinstance(t, c_ast.BinaryOp) and t.op == '+' and isinstance(t.left, c_ast.ID) and t.left.name in self.stride:
            return self.var(t.left.name) + '.m', '%s + %s_off' % (self.expr(t.right), self.var(t.left.name)), width, signed
        if isinstance(t, c_ast.ID) and t.name in self.stride:
            return self.var(t.name) + '.m', self.var(t.name) + '_off', width, signed
        if isinstance(t, c_ast.BinaryOp) and t.op == '+' and isinstance(t.left, c_ast.ID) and t.left.name in self.ptr:
            return self.var(t.left.name) + '.m', self.expr(t.right), width, signed
        if isinstance(t, c_ast.ID) and t.name in self.ptr:
            return self.var(t.name) + '.m', '0', width, signed
        return None, None, width, signed

    def expr(self, e):
        k = type(e).__name__
        if k == 'Constant':
            v = e.value
            if e.type == 'char':
                c = v[1:-1]
                if c.startswith('\\x'):
                    return str(int(c[2:], 16) - (256 if int(c[2:], 16) > 127 else 0))
                if c.startswith('\\'):
                    if c not in ESC:
                        self.check('char literal %s' % c)
                    return str(ESC.get(c, 0))
                return str(ord(c))
            if e.type == 'string':
                return v
            v = v.rstrip('uUlL')
            n = int(v, 0)
            return hex(n) if n > 9 and v.startswith('0x') else str(n) if n < 0x80000000 else '0x%x' % n
        if k == 'ID':
            if e.name in GLOBALS:
                return GLOBALS[e.name]
            if e.name in self.subst:
                return self.subst.pop(e.name)
            if e.name.startswith('_') or e.name.startswith('DAT_') or e.name.startswith('PTR_'):
                self.check('global %s' % e.name)
                return e.name
            return self.var(e.name)
        if k == 'Cast':
            t = self.ctype(e.to_type.type)
            x = self.expr(e.expr)
            if isinstance(e.to_type.type, c_ast.PtrDecl):
                return x
            w = WIDTH.get(t, (4, True))
            if w == (1, True):
                return '(byte) (%s)' % x
            if w == (1, False):
                return '((%s) & 0xff)' % x
            if w == (2, True):
                return '(short) (%s)' % x
            if w == (2, False):
                return '((%s) & 0xffff)' % x
            return x
        if k == 'UnaryOp':
            if e.op == '*':
                o, off, w, s = self.deref(e)
                if o is None:
                    return self.check('deref of a non-object pointer') and 'C2J_DEREF(%s)' % self.expr(e.expr)
                return 'Bytes.%s(%s, %s)' % (READ[(w, s)], o, off)
            if e.op == '&':
                self.check('address-of (stack array / out parameter)')
                return '/*&*/' + self.expr(e.expr)
            if e.op in ('p++', 'p--', '++', '--'):
                v = self.expr(e.expr)
                return {'p++': '%s++', 'p--': '%s--', '++': '++%s', '--': '--%s'}[e.op] % v
            if e.op == 'sizeof':
                return self.check('sizeof') and '0'
            return '%s(%s)' % (e.op, self.expr(e.expr))
        if k == 'BinaryOp':
            l, r = self.expr(e.left), self.expr(e.right)
            op = e.op
            uns = self.unsigned(e.left) or self.unsigned(e.right)
            if op == '>>' and self.unsigned(e.left):
                op = '>>>'
            if op in ('<', '<=', '>', '>=') and uns:
                if isinstance(e.left, c_ast.BinaryOp) or isinstance(e.right, c_ast.BinaryOp):
                    self.check('unsigned compare of byte/ushort arithmetic (Ghidra typing): check lo/ls/hi/hs')
                return '((%s) + 0x80000000 %s (%s) + 0x80000000)' % (l, op, r)
            if op in ('/', '%') and uns:
                self.check('unsigned division')
            if op in ('&&', '||'):
                return '(%s) %s (%s)' % (self.cond(e.left), op, self.cond(e.right))
            if op in ('+', '-') and isinstance(e.left, c_ast.ID) and e.left.name in self.ptr and e.left.name not in self.stride:
                self.check('pointer arithmetic on %s' % e.left.name)
            return '(%s %s %s)' % (l, op, r)
        if k == 'Assignment':
            return self.assign(e)
        if k == 'FuncCall':
            return self.call(e)
        if k == 'TernaryOp':
            return '(%s ? %s : %s)' % (self.cond(e.cond), self.expr(e.iftrue), self.expr(e.iffalse))
        if k == 'ArrayRef':
            if isinstance(e.name, c_ast.ID) and e.name.name in GLOBALS:
                return '%s[%s]' % (GLOBALS[e.name.name], self.expr(e.subscript))
            self.check('array access')
            return '%s[%s]' % (self.expr(e.name), self.expr(e.subscript))
        if k == 'ExprList':
            # '(v = E, C)' with C reading v: C with the first read of v replaced by '(v = E)' (Java evaluates left to right)
            ex = e.exprs
            if len(ex) == 2 and isinstance(ex[0], c_ast.Assignment) and ex[0].op == '=' and isinstance(ex[0].lvalue, c_ast.ID) \
                    and any(isinstance(x, c_ast.ID) and x.name == ex[0].lvalue.name for x in self.walk(ex[1])):
                self.subst[ex[0].lvalue.name] = '(%s)' % self.assign(ex[0])
                r = self.expr(ex[1])
                if self.subst.pop(ex[0].lvalue.name, None) is not None:
                    self.check('comma expression')
                return r
            self.check('comma expression')
            return ', '.join(self.expr(x) for x in e.exprs)
        if k == 'StructRef':
            self.check('struct member')
            return '%s%s%s' % (self.expr(e.name), e.type, e.field.name)
        self.check('construct ' + k)
        return '/*%s*/0' % k

    def cond(self, e):
        """a C condition as a Java boolean"""
        if isinstance(e, c_ast.ExprList) and len(e.exprs) == 2 and isinstance(e.exprs[0], c_ast.Assignment) and e.exprs[0].op == '=' \
                and isinstance(e.exprs[0].lvalue, c_ast.ID) and any(isinstance(x, c_ast.ID) and x.name == e.exprs[0].lvalue.name for x in self.walk(e.exprs[1])):
            v = e.exprs[0].lvalue.name
            self.subst[v] = '(%s)' % self.assign(e.exprs[0])
            r = self.cond(e.exprs[1])
            if self.subst.pop(v, None) is not None:
                self.check('comma expression')
            return r
        if isinstance(e, c_ast.BinaryOp) and e.op in ('<', '<=', '>', '>=', '==', '!='):
            s = self.expr(e)
            return s[1:-1] if s.startswith('(') and s.endswith(')') else s
        if isinstance(e, c_ast.BinaryOp) and e.op in ('&&', '||'):
            return '%s %s %s' % (self.paren(self.cond(e.left)), e.op, self.paren(self.cond(e.right)))
        if isinstance(e, c_ast.UnaryOp) and e.op == '!':
            return '%s == 0' % self.expr(e.expr) if not self.isbool(e.expr) else '!' + self.paren(self.cond(e.expr))
        if self.isbool(e):
            return self.call(e)
        x = self.expr(e)
        if isinstance(e, c_ast.ID) and e.name in self.ptr:
            return '%s != null' % x
        return '%s != 0' % x

    @staticmethod
    def paren(s):
        return '(%s)' % s if (' ' in s and not (s.startswith('(') and s.endswith(')'))) else s

    def isbool(self, e):
        if isinstance(e, c_ast.FuncCall) and isinstance(e.name, c_ast.ID):
            j = lookup(self.idx, e.name.name)[0]
            return bool(j) and j[5] == 'boolean'
        return False

    def assign(self, e):
        rv = self.expr(e.rvalue)
        if e.op != '=':
            rv = '(%s %s %s)' % (self.expr(e.lvalue), e.op[:-1], rv)
        if isinstance(e.rvalue, c_ast.FuncCall) and self.isbool(e.rvalue):
            rv = '(%s ? 1 : 0)' % rv
        lv = e.lvalue
        if isinstance(lv, c_ast.ID) and lv.name in self.stride:
            r = e.rvalue
            k = self.expr(r if e.op == '+=' else r.right) if (e.op == '+=' or (isinstance(r, c_ast.BinaryOp) and isinstance(r.left, c_ast.ID)
                                                                                 and r.left.name == lv.name)) else None
            if k is not None:
                return '%s_off += %s' % (self.var(lv.name), k)
            self.check('%s re-pointed inside a strided walk' % lv.name)
        if isinstance(lv, c_ast.UnaryOp) and lv.op == '*':
            o, off, w, s = self.deref(lv)
            if o is None:
                self.check('store through a non-object pointer')
                return 'C2J_STORE(%s, %s)' % (self.expr(lv.expr), rv)
            return 'Bytes.%s(%s, %s, %s)' % (WRITE[w], o, off, rv)
        if isinstance(lv, c_ast.ArrayRef) and isinstance(lv.name, c_ast.ID) and lv.name.name in GLOBALS:
            return '%s[%s] = (byte) %s' % (GLOBALS[lv.name.name], self.expr(lv.subscript), rv)
        if isinstance(lv, c_ast.FuncCall) and isinstance(lv.name, c_ast.ID) and lv.name.name.startswith('__F'):
            g, off, n = self.field(lv)
            return 'Bytes.%s(%s, %s, %s)' % (WRITE[int(n)], g, off, rv)
        if isinstance(lv, c_ast.ID) and isinstance(e.rvalue, c_ast.FuncCall) and isinstance(e.rvalue.name, c_ast.ID) and \
                (e.rvalue.name.name in ALIAS or (self.idx.get(e.rvalue.name.name) or (0, 0, 0, 0, 0, ''))[5] in OBJTYPES) and lv.name not in self.split:
            self.ptr.add(lv.name)
        if isinstance(lv, c_ast.ID) and lv.name in self.split and self.is_ptr_expr(e.rvalue):
            return '%s_o = %s' % (lv.name, rv)
        if isinstance(lv, c_ast.ID):
            name = lv.name
            t = self.vtype.get(name, 'int')
            if name in self.ptr:
                return '%s = %s' % (self.var(name), rv)
            w = WIDTH.get(t, (4, True))
            if w == (1, True):
                rv = '(byte) %s' % self.paren(rv)
            elif w == (1, False):
                rv = '%s & 0xff' % self.paren(rv)
            elif w == (2, True):
                rv = '(short) %s' % self.paren(rv)
            elif w == (2, False):
                rv = '%s & 0xffff' % self.paren(rv)
            return '%s = %s' % (self.var(name), rv)
        self.check('assignment target')
        return '%s = %s' % (self.expr(lv), rv)

    def field(self, e):
        g = e.name.name[3:]
        off, n = [x.value for x in e.args.exprs]
        if g in GLOBALS:
            return GLOBALS[g], off if int(off) < 10 else hex(int(off)), n
        self.check('global %s' % g)
        return g, off, n

    def call(self, e):
        if not isinstance(e.name, c_ast.ID):
            self.check('indirect call')
            return 'C2J_ICALL()'
        n = e.name.name
        args = self.passthrough(n, list(e.args.exprs) if e.args else [])
        if n.startswith('__F'):
            g, off, w = self.field(e)
            return 'Bytes.%s(%s, %s)' % ({'1': 's8', '2': 's16', '4': 's32'}.get(w, 's32'), g, off)
        if n in ALIAS:
            return ALIAS[n]
        j, guessed = lookup(self.idx, n)
        if guessed:
            self.check('%s matched by name to %s.%s' % (n, j[1], j[2]))
        if j is None:
            leaf = self.leaf(n, args)
            if leaf is not None:
                return leaf
            self.unported.add(n)
            return '0 /* %s: later */' % n
        pkg, cls, meth, static, params, rt = j
        if len(args) != len(params):
            self.check('%s: %d args in Ghidra, Java takes %d (Ghidra drops pass-through registers: check r0..r3 in the disassembly)' % (n, len(args), len(params)))
        out = []
        for k, (pt, pn) in enumerate(params):
            if k >= len(args):
                out.append('/*?%s*/%s' % (pn, 'null' if pt in OBJTYPES else '0'))
                continue
            a = args[k]
            if pt in OBJTYPES:
                if isinstance(a, c_ast.ID):
                    self.ptr.add(a.name)
                s = self.expr(a)
                if isinstance(a, c_ast.ID) and a.name in self.split:
                    s = a.name + '_o'
                have = self.ptype.get(a.name, 'Obj') if isinstance(a, c_ast.ID) else 'Obj'
                out.append('null' if s == '0' else ('(%s) %s' % (pt, s) if pt != 'Obj' and have != pt else s))
            elif pt == 'boolean':
                out.append(self.cond(a))
            else:
                out.append(self.expr(a))
        q = '' if (pkg == 'game' and cls == self.cur_cls) else cls + '.'
        return '%s%s(%s)' % (q, meth, ', '.join(out))

    def passthrough(self, n, args):
        """Ghidra drops an argument whose register still holds the caller's parameter of the same position: fill those in (C2J-CHECK)"""
        need = None
        j = lookup(self.idx, n)[0]
        if j is not None:
            need = len(j[4])
        else:
            try:
                g = Gen(n, self.idx, inner=True)
                need = len(g.params)
            except BaseException:
                return args
        if need is not None and len(args) < need:
            add = []
            for k in range(len(args), need):
                if k < len(self.params):
                    add.append(c_ast.ID(self.params[k]))
                else:
                    return args
            self.check('%s: r%d..r%d assumed passed through unchanged (check the disassembly)' % (n, len(args), need - 1))
            return args + add
        return args

    def leaf(self, n, args):
        """an unported callee whose body is only 'return <expr of its params>;' is translated inline (CHAR_getX & co)"""
        try:
            g = Gen(n, self.idx, inner=True)
        except BaseException:                       # not in the Ghidra output (libc import) / does not parse
            return None
        items = g.fn.body.block_items or []
        items = [x for x in items if not isinstance(x, c_ast.Decl)]
        if len(items) == 2 and isinstance(items[0], c_ast.Assignment) and isinstance(items[0].lvalue, c_ast.ID) \
                and isinstance(items[0].rvalue, c_ast.FuncCall) and isinstance(items[0].rvalue.name, c_ast.ID) and items[0].rvalue.name.name in ALIAS:
            v = items[0].lvalue.name                      # 'v = MP_getHero(); return <expr of v>;'
            g.params.append(v)
            g.ptr.add(v)
            args = list(args) + [c_ast.ID('__alias_' + v)]
            self.pmap['__alias_' + v] = ALIAS[items[0].rvalue.name.name]
            self.ptr.add('__alias_' + v)
            items = items[1:]
        if len(items) != 1 or not isinstance(items[0], c_ast.Return) or items[0].expr is None:
            return None
        if any(isinstance(x, c_ast.FuncCall) for x in g.walk(items[0].expr)):
            return None
        if len(args) < len(g.params):
            self.check('%s inlined: Ghidra passes %d args, the callee reads %d' % (n, len(args), len(g.params)))
            return None
        # substitute: the callee's parameters become our argument expressions (our variable names; pointer-ness carried over)
        sub = {}
        for p, a in zip(g.params, args):
            if isinstance(a, c_ast.ID):
                sub[p] = a.name
                if p in g.ptr:
                    self.ptr.add(a.name)
            else:
                return None
        g.pmap = {p: self.var(sub[p]) for p in g.params}
        g.ptr = set(g.params) & g.ptr
        return '%s /* %s */' % (g.expr(items[0].expr), n)

    def leaf_stmts(self, n, args):
        """an unported callee whose body only stores into its parameters' objects (setters like MONSTER_setAttackCommand): inline statements"""
        try:
            g = Gen(n, self.idx, inner=True)
        except BaseException:
            return None
        items = [x for x in (g.fn.body.block_items or []) if not isinstance(x, c_ast.Decl)]
        if items and isinstance(items[-1], c_ast.Return) and items[-1].expr is None:
            items = items[:-1]
        if not items or len(items) > 8 or len(args) < len(g.params):
            return None
        for it in items:
            if not isinstance(it, c_ast.Assignment) or any(isinstance(x, c_ast.FuncCall) for x in g.walk(it)):
                return None
        pm = {}
        for p, a in zip(g.params, args):
            if isinstance(a, c_ast.ID):
                pm[p] = self.var(a.name)
                if p in g.ptr:
                    self.ptr.add(a.name)
            elif isinstance(a, c_ast.Constant):
                pm[p] = self.expr(a)
            else:
                return None
        g.pmap = pm
        g.ptr = set(g.params) & g.ptr
        return ['%s; // %s' % (g.expr(it), n) for it in items]

    # ------------------------------------------------------------ statements
    def emit(self, s, ind):
        self.lines.append('    ' * ind + s)

    def stmt(self, s, ind):
        k = type(s).__name__
        self.unported = set()
        if k in ('Compound', 'LCompound'):
            lab = getattr(s, 'jlabel', None)
            if lab:
                self.emit('%s: {' % lab, ind)
            for x in s.block_items or []:
                self.stmt(x, ind + (1 if lab else 0))
            if lab:
                self.emit('}', ind)
            return
        if k == 'Decl':
            return                                         # locals are declared at the top
        if k in ('Assignment', 'FuncCall', 'UnaryOp'):
            if k == 'FuncCall' and isinstance(s.name, c_ast.ID) and s.name.name in ALIAS:
                return                                     # result unused: nothing to do
            if k == 'FuncCall' and isinstance(s.name, c_ast.ID) and lookup(self.idx, s.name.name)[0] is None:
                st = self.leaf_stmts(s.name.name, self.passthrough(s.name.name, list(s.args.exprs) if s.args else []))
                if st is not None:
                    for x in st:
                        self.emit(x, ind)
                    return
            t = self.expr(s)
            self.flush_unported(ind)
            if not t.startswith('0 /*'):                  # a bare call of an unported function: only the throw above remains
                self.emit(t + ';', ind)
            return
        if k == 'Return':
            if s.expr is None:
                self.emit('return;', ind)
                return
            jr = self.java[5] if self.java else ('void' if self.ret_c == 'void' else 'int')
            if jr == 'boolean':
                v = self.cond(s.expr)
                v = {'0 != 0': 'false', '1 != 0': 'true'}.get(v, v)
            elif jr in OBJTYPES:
                v = self.expr(s.expr)
                v = 'null' if v == '0' else v
            else:
                v = self.expr(s.expr)
            self.flush_unported(ind)
            self.emit('return;' if jr == 'void' else 'return %s;' % v, ind)
            return
        if k == 'If':
            c = self.cond(s.cond)
            self.flush_unported(ind)
            self.emit('if (%s) {' % c, ind)
            self.stmt(s.iftrue, ind + 1)
            if s.iffalse is not None:
                if isinstance(s.iffalse, c_ast.If):
                    self.lines.append('    ' * ind + '} else')
                    n0 = len(self.lines)
                    self.stmt(s.iffalse, ind)
                    self.lines[n0] = self.lines[n0 - 1] + ' ' + self.lines[n0].lstrip()
                    del self.lines[n0 - 1]
                    return
                self.emit('} else {', ind)
                self.stmt(s.iffalse, ind + 1)
            self.emit('}', ind)
            return
        if k == 'While':
            if isinstance(s.cond, c_ast.ExprList):
                self.emit('while (true) { ' + self.check('comma condition of a while loop'), ind)
                for x in s.cond.exprs[:-1]:
                    self.emit(self.expr(x) + ';', ind + 1)
                self.emit('if (!(%s)) {' % self.cond(s.cond.exprs[-1]), ind + 1)
                self.emit('break;', ind + 2)
                self.emit('}', ind + 1)
            else:
                self.emit('while (%s) { %s' % (self.cond(s.cond), self.check('loop')), ind)
            self.stmt(s.stmt, ind + 1)
            self.emit('}', ind)
            return
        if k == 'DoWhile':
            self.emit('do { ' + self.check('loop'), ind)
            self.stmt(s.stmt, ind + 1)
            self.emit('} while (%s);' % self.cond(s.cond), ind)
            return
        if k == 'For':
            init = self.expr(s.init) if s.init is not None and not isinstance(s.init, c_ast.DeclList) else ''
            nxt = self.expr(s.next) if s.next is not None else ''
            self.emit('for (%s; %s; %s) { %s' % (init, self.cond(s.cond) if s.cond is not None else '', nxt, self.check('loop')), ind)
            self.stmt(s.stmt, ind + 1)
            self.emit('}', ind)
            return
        if k == 'Switch':
            self.emit('switch (%s) {' % self.expr(s.cond), ind)
            self.stmt(s.stmt, ind + 1)
            self.emit('}', ind)
            return
        if k == 'Case':
            self.emit('case %s:' % self.expr(s.expr), ind - 1)
            for x in s.stmts or []:
                self.stmt(x, ind)
            return
        if k == 'Default':
            self.emit('default:', ind - 1)
            for x in s.stmts or []:
                self.stmt(x, ind)
            return
        if k == 'Break':
            self.emit('break;', ind)
            return
        if k == 'Continue':
            self.emit('continue;', ind)
            return
        if k == 'Goto' and s.name in self.blocks:
            self.emit('break %s;' % s.name, ind)
            return
        if k == 'Goto':
            self.emit('if (true) throw new IllegalStateException("%s: goto %s: later"); %s' % (self.name, s.name, self.check('goto: restructure from the disassembly')), ind)
            return
        if k == 'Label':
            if s.name not in self.blocks:
                self.emit('// %s: %s' % (s.name, self.check('label (goto target)')), ind)
            self.stmt(s.stmt, ind)
            return
        if k == 'EmptyStatement':
            return
        self.emit('// ' + self.check('statement ' + k), ind)

    def flush_unported(self, ind):
        for n in sorted(self.unported):
            self.emit('if (true) throw new IllegalStateException("%s: %s: later"); // C2J-CHECK callee not ported' % (self.name, n), ind)
            self.checks.append('callee %s not ported' % n)
        self.unported = set()

    def gen(self):
        self.cur_cls = self.java[1] if self.java else None
        if self.java:
            pkg, cls, meth, static, params, rt = self.java
            sig = 'static %s %s(%s)' % (rt, meth, ', '.join('%s %s' % p for p in params))
        else:
            rt = 'void' if self.ret_c == 'void' else 'int'
            ps = ', '.join('%s %s' % ('Obj' if p in self.ptr else 'int', p) for p in self.params)
            sig = 'static %s %s(%s)' % (rt, self.name, ps)
        for v in sorted(self.mixed):
            self.check('%s holds an object and a number (Ghidra register reuse): split into %s / %s_o' % (v, v, v))
        body_start = len(self.lines)
        self.stmt(self.fn.body, 2)
        locs = sorted(n for n in self.vtype if n not in self.params)
        decl = []
        ints = [self.var(n) for n in locs if n not in self.ptr]
        objs = [self.var(n) for n in locs if n in self.ptr] + [n + '_o' for n in locs if n in self.split]
        if ints:
            decl.append('        int %s;' % ', '.join(ints))
        for v in sorted(self.stride):
            decl.append('        int %s_off = 0;                     // C2J: %s walks slots of its object' % (self.var(v), self.var(v)))
            self.checks.append('strided pointer %s' % v)
        if objs:
            decl.append('        Obj %s;' % ', '.join(objs))
        body = decl + self.lines[body_start:]
        out = ['    /** %s @%#x  (c2j draft: %d C2J-CHECK) */' % (self.name, self.addr, len(self.checks)), '    ' + sig + ' {'] + body + ['    }']
        txt = '\n'.join(out)
        txt = txt.replace('C2J_LATER("', 'later_("')
        return txt


def main():
    a = sys.argv[1:]
    stats = '--stats' in a
    idx = java_index()
    for n in [x for x in a if not x.startswith('--')]:
        g = Gen(n, idx)
        t = g.gen()
        if stats:
            print('%-40s %3d C2J-CHECK  %s' % (n, len(g.checks), '; '.join(sorted(set(g.checks)))[:200]))
        else:
            print(t)
            print()


if __name__ == '__main__':
    main()
