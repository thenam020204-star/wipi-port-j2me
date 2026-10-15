#!/usr/bin/env python3
"""J2ME compatibility scanner for compiled classes (CLDC 1.1 / MIDP 2.0 target).  Reads class files directly (bytecode first).

REJECT (non-zero exit, build refused):
  * class file version other than 45.x / 46..49 (target 1.1 expected: 45.3);
  * a Class/Fieldref/Methodref/InterfaceMethodref whose class or member (name + descriptor) is not found in the app classes or
    in the API stub jars (CLDC 1.1 + MIDP 2.0), searching superclasses and interfaces;
  * invokedynamic / ldc of MethodHandle (cannot occur at 45.3, checked anyway);
  * float/double instructions or F/D descriptors in app code, unless the class is listed in FLOAT_OK (project rule: logic is integer-only);
  * a method that allocates arrays with a constant size > ARRAY_MAX bytes.
WARN (reported, do not fail):
  * allocation (new/newarray/anewarray) inside paint/render/draw/update/run/process/tick style methods (GC pressure in the game loop);
  * direct recursion; synchronized / Thread.sleep / Timer use (timing risk); narrowing int->byte/short casts (listed as a count).
Usage: python3 tools/j2me_scan.py <classes dir> <api.jar> [<api.jar> ...] [--verbose]
"""
import os
import struct
import sys
import zipfile

FLOAT_OK = set()            # classes allowed to use float/double (none yet; make_Lenz_array etc. would be listed here)
ARRAY_MAX = 64 * 1024
LOOP_METHODS = ('paint', 'render', 'draw', 'update', 'run', 'process', 'tick', 'keyPressed', 'keyReleased')

# opcode -> operand byte count (-1 special)
_OPLEN = {}
for _o in range(0, 256):
    _OPLEN[_o] = 0
for _o in (0x10, 0x12, 0x15, 0x16, 0x17, 0x18, 0x19, 0x36, 0x37, 0x38, 0x39, 0x3a, 0xa9, 0xbc):
    _OPLEN[_o] = 1
for _o in (0x11, 0x13, 0x14, 0x84, 0xb2, 0xb3, 0xb4, 0xb5, 0xb6, 0xb7, 0xb8, 0xbb, 0xbd, 0xc0, 0xc1, 0xc6, 0xc7):
    _OPLEN[_o] = 2
for _o in range(0x99, 0xa9):
    _OPLEN[_o] = 2          # if*, goto, jsr
_OPLEN[0xc5] = 3
for _o in (0xb9, 0xba, 0xc8, 0xc9):
    _OPLEN[_o] = 4
_OPLEN[0xaa] = -1
_OPLEN[0xab] = -1
_OPLEN[0xc4] = -1

# fconst/dconst, fload/dload(+_n), fstore/dstore(+_n), faload/daload, fastore/dastore, arithmetic, conversions, compares, returns
FLOAT_OPS = set([0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x17, 0x18, 0x22, 0x23, 0x24, 0x25, 0x26, 0x27, 0x28, 0x29, 0x30, 0x31, 0x38, 0x39,
                 0x43, 0x44, 0x45, 0x46, 0x47, 0x48, 0x49, 0x4a, 0x51, 0x52, 0x62, 0x63, 0x66, 0x67, 0x6a, 0x6b, 0x6e, 0x6f,
                 0x72, 0x73, 0x76, 0x77, 0x86, 0x87, 0x88, 0x89, 0x8a, 0x8b, 0x8c, 0x8d, 0x8e, 0x8f, 0x90, 0x95, 0x96, 0x97,
                 0x98, 0xae, 0xaf])


class Cls:
    def __init__(self, data):
        self.d = data
        p = 0
        magic, self.minor, self.major, n = struct.unpack_from('>IHHH', data, 0)
        if magic != 0xCAFEBABE:
            raise ValueError('bad magic')
        p = 10
        self.cp = [None] * n
        i = 1
        while i < n:
            t = data[p]
            if t == 1:
                l = struct.unpack_from('>H', data, p + 1)[0]
                self.cp[i] = (1, data[p + 3:p + 3 + l].decode('utf-8', 'replace'))
                p += 3 + l
            elif t in (3, 4):
                self.cp[i] = (t, struct.unpack_from('>I', data, p + 1)[0]); p += 5
            elif t in (5, 6):
                self.cp[i] = (t, 0); p += 9; i += 1
            elif t in (7, 8):
                self.cp[i] = (t, struct.unpack_from('>H', data, p + 1)[0]); p += 3
            elif t in (9, 10, 11, 12):
                self.cp[i] = (t,) + struct.unpack_from('>HH', data, p + 1); p += 5
            elif t == 15:
                self.cp[i] = (t, data[p + 1], struct.unpack_from('>H', data, p + 2)[0]); p += 4
            elif t == 16:
                self.cp[i] = (t, struct.unpack_from('>H', data, p + 1)[0]); p += 3
            elif t == 18:
                self.cp[i] = (t,) + struct.unpack_from('>HH', data, p + 1); p += 5
            else:
                raise ValueError('cp tag %d' % t)
            i += 1
        self.access, this, sup, ni = struct.unpack_from('>HHHH', data, p)
        p += 8
        self.name = self.cls(this)
        self.super = self.cls(sup) if sup else None
        self.ifaces = []
        for _ in range(ni):
            self.ifaces.append(self.cls(struct.unpack_from('>H', data, p)[0])); p += 2
        self.fields, self.methods = [], []
        for lst in (self.fields, self.methods):
            cnt = struct.unpack_from('>H', data, p)[0]; p += 2
            for _ in range(cnt):
                acc, ni_, di, na = struct.unpack_from('>HHHH', data, p); p += 8
                code = None
                for _ in range(na):
                    an, al = struct.unpack_from('>HI', data, p)
                    if self.utf(an) == 'Code':
                        ml, mx = struct.unpack_from('>HH', data, p + 6)
                        cl = struct.unpack_from('>I', data, p + 10)[0]
                        code = data[p + 14:p + 14 + cl]
                    p += 6 + al
                lst.append((acc, self.utf(ni_), self.utf(di), code))

    def utf(self, i):
        return self.cp[i][1]

    def cls(self, i):
        return self.utf(self.cp[i][1])

    def member(self, i):
        t, ci, nt = self.cp[i]
        _, n, d = self.cp[nt]
        return self.cls(ci), self.utf(n), self.utf(d)


def load_api(jars):
    api = {}
    for j in jars:
        z = zipfile.ZipFile(j)
        for n in z.namelist():
            if n.endswith('.class'):
                c = Cls(z.read(n))
                api[c.name] = c
    return api


def find_member(classes, owner, name, desc, field):
    seen = set()
    stack = [owner]
    while stack:
        o = stack.pop()
        if o in seen:
            continue
        seen.add(o)
        c = classes.get(o)
        if c is None:
            continue
        for (acc, n, d, _) in (c.fields if field else c.methods):
            if n == name and d == desc:
                return True
        if c.super:
            stack.append(c.super)
        stack += c.ifaces
    return False


def insns(code):
    """yield (pc, op, operand-bytes-start)"""
    pc = 0
    n = len(code)
    while pc < n:
        op = code[pc]
        ln = _OPLEN[op]
        if op == 0xc4:                       # wide
            ln = 5 if code[pc + 1] == 0x84 else 3
            yield pc, code[pc + 1], pc + 2
            pc += 1 + ln
            continue
        if op in (0xaa, 0xab):
            q = (pc + 4) & ~3
            if op == 0xaa:
                lo, hi = struct.unpack_from('>ii', code, q + 4)
                ln = q - pc - 1 + 12 + 4 * (hi - lo + 1)
            else:
                npairs = struct.unpack_from('>i', code, q + 4)[0]
                ln = q - pc - 1 + 8 + 8 * npairs
        yield pc, op, pc + 1
        pc += 1 + ln


def scan_dir(cdir, apijars, verbose=False, float_ok=FLOAT_OK):
    api = load_api(apijars)
    app = {}
    for r, _, fs in os.walk(cdir):
        for f in fs:
            if f.endswith('.class'):
                c = Cls(open(os.path.join(r, f), 'rb').read())
                app[c.name] = c
    allc = dict(api)
    allc.update(app)
    rej, warn = [], []
    stats = {'narrow': 0}
    for name, c in sorted(app.items()):
        if not (c.major == 45 or 46 <= c.major <= 49):
            rej.append((name, 'class version %d.%d' % (c.major, c.minor)))
        for i, e in enumerate(c.cp):
            if e is None:
                continue
            if e[0] in (15, 16, 18):
                rej.append((name, 'invokedynamic/MethodHandle constant'))
            if e[0] == 7:
                cn = c.utf(e[1])
                if cn.startswith('['):
                    continue
                if cn not in allc:
                    rej.append((name, 'class not in CLDC1.1/MIDP2.0 nor app: ' + cn))
            elif e[0] in (9, 10, 11):
                owner, n, d = c.member(i)
                if owner.startswith('['):
                    continue
                if not find_member(allc, owner, n, d, e[0] == 9):
                    rej.append((name, 'member not in CLDC1.1/MIDP2.0 nor app: %s.%s%s' % (owner, n, d)))
        for (acc, fn, fd, _) in c.fields:
            if _has_fd(fd) and name not in float_ok:
                rej.append((name, 'float/double field %s %s' % (fn, fd)))
        for (acc, mn, md, code) in c.methods:
            desc_f = _has_fd(md)
            if desc_f and name not in float_ok:
                rej.append((name, 'float/double in descriptor %s%s' % (mn, md)))
            if code is None:
                continue
            loopish = mn in LOOP_METHODS or mn.startswith(('paint', 'render', 'draw', 'update', 'process'))
            last_const = None
            for pc, op, a in insns(code):
                if op in FLOAT_OPS and name not in float_ok:
                    rej.append((name, 'float/double instruction 0x%02x in %s%s @%d' % (op, mn, md, pc)))
                if op in (0x12, 0x13, 0x14) and name not in float_ok:
                    ci = code[a] if op == 0x12 else struct.unpack_from('>H', code, a)[0]
                    if c.cp[ci][0] in (4, 6):
                        rej.append((name, 'float/double constant in %s%s @%d' % (mn, md, pc)))
                if op == 0xbc and code[a] in (6, 7) and name not in float_ok:
                    rej.append((name, 'float/double array in %s%s @%d' % (mn, md, pc)))
                if op in (0x91, 0x93):
                    stats['narrow'] += 1
                if op in (0xbb, 0xbc, 0xbd, 0xc5) and loopish:
                    warn.append((name, 'allocation in %s%s @%d' % (mn, md, pc)))
                if op in (0xbc, 0xbd) and last_const is not None and last_const > 0:
                    elt = {0xbc: {4: 1, 5: 2, 6: 4, 7: 8, 8: 1, 9: 2, 10: 4, 11: 8}.get(code[a], 4), 0xbd: 4}[op]
                    if last_const * elt > ARRAY_MAX:
                        rej.append((name, 'array of %d bytes in %s%s' % (last_const * elt, mn, md)))
                if op in (0xb6, 0xb7, 0xb8, 0xb9):
                    owner, n, d = c.member(struct.unpack_from('>H', code, a)[0])
                    if owner == name and n == mn and d == md:
                        warn.append((name, 'direct recursion in %s%s' % (mn, md)))
                    if (owner, n) in (('java/lang/Thread', 'sleep'), ('java/util/Timer', 'schedule')):
                        warn.append((name, 'timing: %s.%s in %s' % (owner, n, mn)))
                if op == 0xc2 or op == 0xc3:
                    warn.append((name, 'monitor in %s%s' % (mn, md)))
                if op == 0x10:
                    last_const = code[a] - 256 if code[a] > 127 else code[a]
                elif op == 0x11:
                    last_const = struct.unpack_from('>h', code, a)[0]
                elif 0x03 <= op <= 0x08:
                    last_const = op - 3
                elif op == 0x12:
                    e = c.cp[code[a]]
                    last_const = e[1] if e[0] == 3 else None
                else:
                    last_const = None
            if acc & 0x20:
                warn.append((name, 'synchronized method %s%s' % (mn, md)))
    for n, m in rej:
        print('REJECT', n, m)
    if verbose:
        for n, m in warn:
            print('warn  ', n, m)
    print('scan: %d classes, %d reject, %d warn, narrowing casts %d' % (len(app), len(rej), len(warn), stats['narrow']))
    return len(rej)


def _has_fd(desc):
    """True if a method descriptor mentions primitive F or D (not inside a class name)."""
    i = 0
    while i < len(desc):
        ch = desc[i]
        if ch == 'L':
            i = desc.index(';', i)
        elif ch in 'FD':
            return True
        i += 1
    return False


if __name__ == '__main__':
    args = [a for a in sys.argv[1:] if not a.startswith('--')]
    sys.exit(1 if scan_dir(args[0], args[1:], '--verbose' in sys.argv) else 0)
