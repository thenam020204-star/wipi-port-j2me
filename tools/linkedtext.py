#!/usr/bin/env python3
"""'LinkedText' string tables (CONFIRMED, getLinkedText 0x9e514):
u16le total_size, u16le count, u16le offset[count] (absolute from file start), NUL-terminated EUC-KR strings.
getLinkedText(buf, k) = buf + u16le(buf + 4 + 2k)  (no bounds check in the original).
Usage: python3 tools/linkedtext.py dump <file> | selftest <binary.mod> <res dir>"""
import os, struct, sys


def parse(b):
    size, n = struct.unpack_from('<HH', b, 0)
    offs = struct.unpack_from('<%dH' % n, b, 4)
    out = []
    for o in offs:
        z = b.find(b'\0', o)
        out.append(b[o:z if z >= 0 else len(b)])
    return size, offs, out


def is_linkedtext(b):
    try:
        size, n = struct.unpack_from('<HH', b, 0)
        if size != len(b) or n == 0 or 4 + 2 * n > len(b):
            return False
        offs = struct.unpack_from('<%dH' % n, b, 4)
        return all(4 + 2 * n <= o < len(b) for o in offs) and list(offs) == sorted(offs)
    except Exception:
        return False


def selftest(binp, resdir):
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    from and_hle import Harness
    h = Harness(binp, resdir)
    files = sorted(os.path.join(r, f) for r, _, fs in os.walk(resdir) for f in fs
                   if is_linkedtext(open(os.path.join(r, f), 'rb').read()))
    bad = 0
    for p in files:
        b = open(p, 'rb').read(); _, offs, out = parse(b)
        buf = h.alloc(len(b) + 1); h.write(buf, b + b'\0')
        for k in range(len(offs)):
            a = h.call('getLinkedText', buf, k)
            if h.cstr(a, 65536) != out[k]:
                bad += 1; print('MISMATCH', p, k); break
    print('linkedtext files', len(files), 'mismatches', bad)
    for p in files: print('  ', os.path.relpath(p, resdir), len(parse(open(p, 'rb').read())[2]))
    return bad


if __name__ == '__main__':
    if sys.argv[1] == 'dump':
        for i, s in enumerate(parse(open(sys.argv[2], 'rb').read())[2]):
            print(i, s.decode('euc-kr', 'replace'))
    else:
        sys.exit(1 if selftest(sys.argv[2], sys.argv[3]) else 0)
