#!/usr/bin/env python3
"""jardiff.py A.jar B.jar [-v] : so sánh hai jar (trước/sau patch).

- tài nguyên: thêm / bớt / đổi (kích thước, md5)
- class: thêm / bớt; với class có ở cả hai: phiên bản class, superclass/interface, field và method
  (tên + descriptor + access flag) thêm/bớt/đổi, và bytecode từng method (md5 của Code, có/không StackMap)
Đọc class file trực tiếp (không cần javap). Mã thoát 0 luôn; đây là báo cáo, không phải bộ chặn.
"""
import hashlib, struct, sys, zipfile


def md5(b):
    return hashlib.md5(b).hexdigest()[:10]


class ClassInfo:
    def __init__(self, b):
        self.minor, self.major = struct.unpack('>HH', b[4:8])
        n = struct.unpack('>H', b[8:10])[0]
        cp = [None] * n
        p = 10
        i = 1
        while i < n:
            t = b[p]
            if t == 1:
                ln = struct.unpack('>H', b[p + 1:p + 3])[0]
                cp[i] = ('u', b[p + 3:p + 3 + ln].decode('utf-8', 'replace'))
                p += 3 + ln
            elif t in (3, 4):
                cp[i] = ('n', b[p + 1:p + 5]); p += 5
            elif t in (5, 6):
                cp[i] = ('n', b[p + 1:p + 9]); p += 9; i += 1
            elif t in (7, 8, 16, 19, 20):
                cp[i] = ('r', struct.unpack('>H', b[p + 1:p + 3])[0]); p += 3
            elif t == 15:
                cp[i] = ('x', b[p + 1:p + 4]); p += 4
            else:  # 9,10,11,12,17,18
                cp[i] = ('rr', struct.unpack('>HH', b[p + 1:p + 5])); p += 5
            i += 1
        self.cp = cp
        self.access, this, sup, ic = struct.unpack('>HHHH', b[p:p + 8])
        p += 8
        self.name = self.cls(this)
        self.sup = self.cls(sup) if sup else None
        self.ifs = [self.cls(struct.unpack('>H', b[p + 2 * k:p + 2 * k + 2])[0]) for k in range(ic)]
        p += 2 * ic
        self.fields, p = self.members(b, p)
        self.methods, p = self.members(b, p)

    def utf(self, i):
        return self.cp[i][1]

    def cls(self, i):
        return self.utf(self.cp[i][1])

    def members(self, b, p):
        n = struct.unpack('>H', b[p:p + 2])[0]
        p += 2
        out = {}
        for _ in range(n):
            acc, ni, di, ac = struct.unpack('>HHHH', b[p:p + 8])
            p += 8
            code = None
            stackmap = False
            for _ in range(ac):
                an, al = struct.unpack('>HI', b[p:p + 6])
                body = b[p + 6:p + 6 + al]
                if self.utf(an) == 'Code':
                    clen = struct.unpack('>I', body[4:8])[0]
                    code = body[8:8 + clen]
                    q = 8 + clen
                    el = struct.unpack('>H', body[q:q + 2])[0]
                    q += 2 + 8 * el
                    sac = struct.unpack('>H', body[q:q + 2])[0]
                    q += 2
                    for _ in range(sac):
                        sn, sl = struct.unpack('>HI', body[q:q + 6])
                        if self.utf(sn) in ('StackMap', 'StackMapTable'):
                            stackmap = self.utf(sn)
                        q += 6 + sl
                p += 6 + al
            out[self.utf(ni) + self.utf(di)] = (acc, md5(code) if code is not None else None, len(code) if code else 0, stackmap)
        return out, p


def main():
    a, b = zipfile.ZipFile(sys.argv[1]), zipfile.ZipFile(sys.argv[2])
    verbose = '-v' in sys.argv
    na = {i.filename: i for i in a.infolist() if not i.is_dir()}
    nb = {i.filename: i for i in b.infolist() if not i.is_dir()}
    print('# jardiff %s -> %s' % (sys.argv[1], sys.argv[2]))
    print('kích thước: %d -> %d byte; mục: %d -> %d' % (
        sum(i.compress_size for i in na.values()), sum(i.compress_size for i in nb.values()), len(na), len(nb)))
    added = sorted(set(nb) - set(na))
    removed = sorted(set(na) - set(nb))
    changed = sorted(n for n in set(na) & set(nb) if a.read(n) != b.read(n))
    res = lambda l: [n for n in l if not n.endswith('.class')]
    cls = lambda l: [n for n in l if n.endswith('.class')]
    print('\n## Tài nguyên: +%d -%d ~%d' % (len(res(added)), len(res(removed)), len(res(changed))))
    for tag, l in (('+', res(added)), ('-', res(removed))):
        for n in l if verbose or len(l) <= 40 else l[:40] + ['… (%d mục nữa, dùng -v)' % (len(l) - 40)]:
            print(' %s %s' % (tag, n))
    for n in res(changed):
        print(' ~ %s %d -> %d (%s -> %s)' % (n, na[n].file_size, nb[n].file_size, md5(a.read(n)), md5(b.read(n))))
    print('\n## Class: +%d -%d ~%d' % (len(cls(added)), len(cls(removed)), len(cls(changed))))
    for tag, l in (('+', cls(added)), ('-', cls(removed))):
        for n in l:
            print(' %s %s' % (tag, n))
    for n in cls(changed):
        x, y = ClassInfo(a.read(n)), ClassInfo(b.read(n))
        print(' ~ %s' % n)
        if (x.major, x.minor) != (y.major, y.minor):
            print('    version %d.%d -> %d.%d' % (x.major, x.minor, y.major, y.minor))
        if x.sup != y.sup or x.ifs != y.ifs or x.access != y.access:
            print('    header %s %s %x -> %s %s %x' % (x.sup, x.ifs, x.access, y.sup, y.ifs, y.access))
        for kind, mx, my in (('field', x.fields, y.fields), ('method', x.methods, y.methods)):
            for k in sorted(set(mx) | set(my)):
                if k not in my:
                    print('    - %s %s' % (kind, k))
                elif k not in mx:
                    print('    + %s %s (%d byte)' % (kind, k, my[k][2]))
                elif mx[k][0] != my[k][0]:
                    print('    ! %s %s access %x -> %x' % (kind, k, mx[k][0], my[k][0]))
                elif mx[k][1] != my[k][1]:
                    print('    ~ %s %s bytecode %d -> %d byte' % (kind, k, mx[k][2], my[k][2]))
    # StackMap overview for B (CLDC needs StackMap, never StackMapTable)
    bad = []
    for n in nb:
        if n.endswith('.class'):
            c = ClassInfo(b.read(n))
            for k, v in c.methods.items():
                if v[3] == 'StackMapTable' or c.major > 48:
                    bad.append(n + ' ' + k)
    print('\n## B: class version > 48 / StackMapTable: %d%s' % (len(bad), '' if not bad else '  ' + '; '.join(bad[:5])))


if __name__ == '__main__':
    main()
