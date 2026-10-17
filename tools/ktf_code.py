#!/usr/bin/env python3
"""KTF client.bin code model (T0.7: matching KTF functions with AND2011 names; reading aid, verification is by running code).
The image (file 0x1104.., Thumb, self-relocating, docs/binary_layout.md section 1) is decoded with capstone; function starts = BL targets
(+ the entry functions of the descriptor).  Addresses are image offsets (img+X).

  python3 tools/ktf_code.py funcs                      -> number of functions, size histogram
  python3 tools/ktf_code.py imm 0x96,0xd,0xb           -> functions whose immediates include all the given values
  python3 tools/ktf_code.py str <substring>            -> functions referencing a C string (GOT-relative literal, base img+0x88bb4)
  python3 tools/ktf_code.py dis <img addr>             -> disassembly of the function at that address (with string / call annotations)
  python3 tools/ktf_code.py callers <img addr>         -> functions calling it
"""
import os
import re
import struct
import sys

from capstone import Cs, CS_ARCH_ARM, CS_MODE_THUMB

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
BIN = os.path.join(ROOT, 'work', 'orig', 'ktf', '010100D4.jar!', 'client.bin387872')
IMG = 0x1104
CODE_END = 0x806b4          # start of the C string / data area (docs/binary_layout.md)
GOT = 0x88bb4


class Ktf:
    def __init__(self, path=BIN):
        self.img = open(path, 'rb').read()[IMG:]
        self.md = Cs(CS_ARCH_ARM, CS_MODE_THUMB)
        self.md.skipdata = True
        # linear sweep loses sync after literal pools: decode again segment by segment from every BL target until the target set is stable
        targets = {0}
        while True:
            seg = sorted(t for t in targets if 0 <= t < CODE_END and not t & 1)
            self.ins = []
            for a, b in zip(seg, seg[1:] + [CODE_END]):
                self.ins += list(self.md.disasm(self.img[a:b], a))
            new = set(targets)
            for i in self.ins:
                if i.mnemonic == 'bl' and i.op_str.startswith('#'):
                    new.add(int(i.op_str[1:], 0))
            if new == targets:
                break
            targets = new
        self.at = {i.address: k for k, i in enumerate(self.ins)}
        starts = set()
        self.calls = []
        for i in self.ins:
            if i.mnemonic == 'bl' and i.op_str.startswith('#'):
                t = int(i.op_str[1:], 0)
                if 0 <= t < CODE_END:
                    starts.add(t)
                    self.calls.append((i.address, t))
        # GCC Thumb also uses BL as a long jump inside a function: a BL target starts a function only if it begins with push {..lr} or
        # follows a return / padding / literal data (otherwise it is a far label of the enclosing function)
        def is_start(t):
            k = self.at.get(t)
            if k is None:
                return False
            i = self.ins[k]
            if i.mnemonic == 'push' and 'lr' in i.op_str:
                return True
            if k == 0:
                return True
            p = self.ins[k - 1]
            return (p.mnemonic == 'pop' and 'pc' in p.op_str) or (p.mnemonic == 'bx' and p.op_str == 'lr') or p.mnemonic in ('.byte', '.short') \
                or (p.mnemonic == 'movs' and p.op_str == 'r0, r0') or self.lit_area(p.address)
        self.lits = set()
        for i in self.ins:
            m = re.match(r'\w+, \[pc, #(-?0x[0-9a-f]+|\d+)\]', i.op_str)
            if i.mnemonic.startswith('ldr') and m:
                a = ((i.address + 4) & ~3) + int(m.group(1), 0)
                self.lits.update((a, a + 2))
        self.starts = sorted(t for t in starts if is_start(t))
        self.far = sorted(starts - set(self.starts))

    def lit_area(self, a):
        return a in self.lits

    def func_of(self, a):
        import bisect
        k = bisect.bisect_right(self.starts, a) - 1
        return self.starts[k] if k >= 0 else None

    def body(self, f):
        import bisect
        k = bisect.bisect_right(self.starts, f)
        end = self.starts[k] if k < len(self.starts) else CODE_END
        i0 = self.at.get(f)
        if i0 is None:
            return []
        out = []
        for i in self.ins[i0:]:
            if i.address >= end:
                break
            out.append(i)
        return out

    def lit(self, i):
        """value of an 'ldr rX, [pc, #n]' literal"""
        m = re.match(r'(\w+), \[pc, #(-?0x[0-9a-f]+|\d+)\]', i.op_str)
        if i.mnemonic.startswith('ldr') and m:
            a = ((i.address + 4) & ~3) + int(m.group(2), 0)
            if 0 <= a + 4 <= len(self.img):
                return struct.unpack_from('<I', self.img, a)[0]
        return None

    def cstr(self, a):
        if not (0 <= a < len(self.img)):
            return None
        e = self.img.find(b'\0', a, a + 200)
        if e <= a:
            return None
        s = self.img[a:e]
        if all(32 <= c < 127 or c >= 0x80 for c in s):
            return s.decode('euc-kr', 'replace')
        return None

    def strings(self, f):
        out = []
        for i in self.body(f):
            v = self.lit(i)
            if v is not None:
                for cand in ((v + GOT) & 0xffffffff, v):
                    s = self.cstr(cand) if 0x806b4 <= cand < 0x88bb4 else None
                    if s:
                        out.append((i.address, s))
                        break
        return out

    def imms(self, f):
        out = set()
        for i in self.body(f):
            for m in re.finditer(r'#(-?0x[0-9a-f]+|-?\d+)', i.op_str):
                if i.mnemonic in ('bl', 'b', 'blx') or i.mnemonic.startswith('b') and len(i.mnemonic) <= 3:
                    continue
                out.add(int(m.group(1), 0))
            v = self.lit(i)
            if v is not None:
                out.add(v)
        return out


def main():
    k = Ktf()
    cmd = sys.argv[1]
    if cmd == 'funcs':
        print('%d instructions, %d functions (BL targets that start a function), %d far-jump BL targets' % (len(k.ins), len(k.starts), len(k.far)))
    elif cmd == 'imm':
        want = [int(x, 0) for x in sys.argv[2].split(',')]
        for f in k.starts:
            s = k.imms(f)
            if all(w in s for w in want):
                print('img+%#x  size %d  calls-in %d' % (f, len(k.body(f)), sum(1 for a, t in k.calls if t == f)))
    elif cmd == 'str':
        for f in k.starts:
            for a, s in k.strings(f):
                if sys.argv[2] in s:
                    print('img+%#x (at %#x) %r' % (f, a, s))
    elif cmd == 'dis':
        f = k.func_of(int(sys.argv[2], 0))
        names = {}
        for i in k.body(f):
            v = k.lit(i)
            ann = ''
            if v is not None:
                s = k.cstr((v + GOT) & 0xffffffff) or (k.cstr(v) if 0x806b4 <= v < 0x88bb4 else None)
                ann = ('  ; "%s"' % s) if s else '  ; =%#x' % v
            print('  %06x  %-8s %s%s' % (i.address, i.mnemonic, i.op_str, ann))
    elif cmd == 'callers':
        t = int(sys.argv[2], 0)
        for a, tt in k.calls:
            if tt == t:
                print('img+%#x in img+%#x' % (a, k.func_of(a)))


if __name__ == '__main__':
    main()
