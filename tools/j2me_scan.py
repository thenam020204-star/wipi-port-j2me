#!/usr/bin/env python3
"""J2ME compatibility scanner: j2me_scan.py <game.jar> [stub.jar ...]
Parses every hl4/** class (constant pool, methods, access flags, code attr) and checks
 - class-file major version <= 49 and a StackMap (preverified) attribute on every method with branches
 - every external Class/Field/Method reference exists in the CLDC1.1+MIDP2.0 stub jars (superclass chain, interfaces)
 - no forbidden opcodes (jsr/ret are ok pre-verified? no: preverified code must not contain them), invokedynamic absent
 - code size per method < 64KB
Exit code 1 on any hard violation."""
import sys, zipfile, struct, collections

def parse(data):
    p = 0
    def u1():
        nonlocal p; v = data[p]; p += 1; return v
    def u2():
        nonlocal p; v = struct.unpack_from('>H', data, p)[0]; p += 2; return v
    def u4():
        nonlocal p; v = struct.unpack_from('>I', data, p)[0]; p += 4; return v
    assert u4() == 0xCAFEBABE
    minor = u2(); major = u2()
    n = u2(); cp = [None] * n; i = 1
    while i < n:
        t = u1()
        if t == 1:
            l = u2(); cp[i] = (1, data[p:p + l].decode('utf-8', 'replace')); p += l
        elif t in (3, 4): cp[i] = (t, u4())
        elif t in (5, 6): cp[i] = (t, u4(), u4()); i += 1
        elif t in (7, 8): cp[i] = (t, u2())
        elif t in (9, 10, 11, 12): cp[i] = (t, u2(), u2())
        elif t == 15: cp[i] = (t, u1(), u2())
        elif t == 16: cp[i] = (t, u2())
        elif t == 18: cp[i] = (t, u2(), u2())
        else: raise Exception('bad cp tag %d' % t)
        i += 1
    acc = u2(); this = u2(); sup = u2()
    ifs = [u2() for _ in range(u2())]
    def utf(i): return cp[i][1]
    def cname(i): return utf(cp[i][1])
    def members():
        res = []
        for _ in range(u2()):
            a = u2(); nm = utf(u2()); ds = utf(u2()); attrs = {}
            for _ in range(u2()):
                an = utf(u2()); l = u4(); attrs[an] = data[p:p + l]; skip(l)
            res.append((a, nm, ds, attrs))
        return res
    def skip(l):
        nonlocal p; p += l
    fields = members(); methods = members()
    return dict(major=major, cp=cp, name=cname(this), sup=cname(sup) if sup else None,
                ifs=[cname(i) for i in ifs], fields=fields, methods=methods, acc=acc, utf=utf, cname=cname)


OPLEN = {}
for o in range(0, 256): OPLEN[o] = 1
for o in (0x10, 0x12, 0x15,0x16,0x17,0x18,0x19, 0x36,0x37,0x38,0x39,0x3a, 0xa9, 0xbc): OPLEN[o] = 2
for o in (0x11, 0x13, 0x14, 0x84, 0xb2,0xb3,0xb4,0xb5,0xb6,0xb7,0xb8, 0xbb, 0xbd, 0xc0, 0xc1, 0xc6, 0xc7): OPLEN[o] = 3
for o in range(0x99, 0xa9): OPLEN[o] = 3
OPLEN[0xc5] = 4; OPLEN[0xb9] = 5; OPLEN[0xba] = 5; OPLEN[0xc8] = 5; OPLEN[0xc9] = 5
BRANCH = set(list(range(0x99, 0xa9)) + [0xc6, 0xc7, 0xc8, 0xc9, 0xaa, 0xab])
def walk(body):
    """yield (pc, opcode); handles tableswitch/lookupswitch/wide"""
    pc = 0
    while pc < len(body):
        op = body[pc]; yield pc, op
        if op == 0xaa:
            q = (pc + 4) & ~3; lo, hi = struct.unpack_from('>ii', body, q + 4); pc = q + 12 + 4 * (hi - lo + 1)
        elif op == 0xab:
            q = (pc + 4) & ~3; n = struct.unpack_from('>i', body, q + 4)[0]; pc = q + 8 + 8 * n
        elif op == 0xc4:
            pc += 6 if body[pc + 1] == 0x84 else 4
        else: pc += OPLEN[op]

def refs(c):
    cp = c['cp']; out = []
    for e in cp:
        if e and e[0] in (9, 10, 11):
            cls = c['cname'](e[1]); nt = cp[e[2]]; out.append((e[0], cls, c['utf'](nt[1]), c['utf'](nt[2])))
        elif e and e[0] == 7:
            out.append((7, c['utf'](e[1]), None, None))
    return out

def load_jar(path, prefix=None):
    z = zipfile.ZipFile(path); res = {}
    for n in z.namelist():
        if n.endswith('.class') and (prefix is None or n.startswith(prefix)):
            c = parse(z.read(n)); res[c['name']] = c
    return res

def has_member(stubs, cls, name, desc, kind):
    seen = set(); q = [cls]
    while q:
        k = q.pop()
        if k in seen or k not in stubs: continue
        seen.add(k); c = stubs[k]
        for a, nm, ds, _ in (c['methods'] if kind != 9 else c['fields']):
            if nm == name and ds == desc: return True
        if c['sup']: q.append(c['sup'])
        q.extend(c['ifs'])
    return False

def main():
    jar = sys.argv[1]; stubjars = sys.argv[2:]
    stubs = {}
    for s in stubjars: stubs.update(load_jar(s))
    game = load_jar(jar, 'hl4/')
    bad = collections.Counter(); warn = []
    for name, c in sorted(game.items()):
        if c['major'] > 49: bad['classversion %s=%d' % (name, c['major'])] += 1
        for r in refs(c):
            kind, cls, nm, ds = r
            if cls.startswith('hl4/') or cls.startswith('['): continue
            if kind == 7:
                if cls not in stubs: bad['missing class ' + cls] += 1
            elif cls not in stubs:
                bad['missing class ' + cls] += 1
            elif not has_member(stubs, cls, nm, ds, kind):
                bad['missing member %s.%s%s' % (cls, nm, ds)] += 1
        for a, nm, ds, attrs in c['methods']:
            code = attrs.get('Code')
            if code is None: continue
            clen = struct.unpack_from('>I', code, 4)[0]
            if clen >= 65535: bad['method too big %s.%s' % (name, nm)] += 1
            body = code[8:8 + clen]
            for _pc, _op in walk(body):
                if _op in (0xa8, 0xa9): bad['jsr/ret in %s.%s' % (name, nm)] += 1
                if _op == 0xba: bad['invokedynamic in %s.%s' % (name, nm)] += 1
            # branches present -> expect a StackMap attribute in Code attributes
            # (scan attribute names)
            p = 8 + clen; el = struct.unpack_from('>H', code, p)[0]; p += 2 + 8 * el
            na = struct.unpack_from('>H', code, p)[0]; p += 2; names = []
            for _ in range(na):
                an = c['utf'](struct.unpack_from('>H', code, p)[0]); l = struct.unpack_from('>I', code, p + 2)[0]; names.append(an); p += 6 + l
            if 'StackMap' not in names and 'StackMapTable' not in names and any(o in BRANCH for _, o in walk(body)):
                warn.append('no StackMap: %s.%s%s' % (name, nm, ds))
    print('classes scanned:', len(game), ' stubs:', len(stubs))
    for k, v in sorted(bad.items()): print('VIOLATION', k)
    print('preverify warnings (methods with branch-like bytes lacking StackMap):', len(warn))
    for w in warn[:10]: print('  ', w)
    sys.exit(1 if bad else 0)
main()
