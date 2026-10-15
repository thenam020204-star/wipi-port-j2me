#!/usr/bin/env python3
"""MrgText string tables (CONFIRMED, MrgText_load 0x9e5c4): s8 count, count x {u16be len, bytes[len]}.
Strings are EUC-KR with the game's inline control codes; no terminator is added by the loader.
Usage: python3 tools/mrgtext.py dump <file> | selftest <binary.mod> <res dir>"""
import os, struct, sys


def parse(b):
    n = struct.unpack_from('b', b, 0)[0]
    off, out = 1, []
    for _ in range(max(n, 0)):
        ln = (b[off] << 8) | b[off + 1]
        out.append(b[off + 2:off + 2 + ln]); off += 2 + ln
    return out, off


def is_mrgtext(b):
    try:
        out, end = parse(b)
        return len(b) > 1 and b[0] < 0x80 and out and end == len(b)
    except Exception:
        return False


def selftest(binp, resdir):
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    from and_hle import Harness
    h = Harness(binp, resdir)
    files = []
    for root, _, fs in os.walk(resdir):
        for fn in fs:
            p = os.path.join(root, fn)
            if is_mrgtext(open(p, 'rb').read()):
                files.append(p)
    files.sort(); bad = 0
    for p in files:
        rel = os.path.relpath(p, resdir).replace(os.sep, '/')
        out, _ = parse(open(p, 'rb').read())
        obj = h.alloc(8); path = h.alloc(len(rel) + 2); h.write(path, b'/' + rel.encode() + b'\0')
        h.call('MrgText_load', obj, path)
        tab, cnt = struct.unpack('<Ii', h.read(obj, 8))
        got = []
        for i in range(cnt):
            sp, ln = struct.unpack('<Ii', h.read(tab + 8 * i, 8))
            got.append(h.read(sp, ln))
        if got != out:
            bad += 1; print('MISMATCH', rel, len(got), len(out))
    print('mrgtext files', len(files), 'mismatches', bad)
    for p in files: print('  ', os.path.relpath(p, resdir), len(parse(open(p, 'rb').read())[0]))
    return bad


if __name__ == '__main__':
    if sys.argv[1] == 'dump':
        for i, s in enumerate(parse(open(sys.argv[2], 'rb').read())[0]):
            print(i, s.decode('euc-kr', 'replace'))
    else:
        sys.exit(1 if selftest(sys.argv[2], sys.argv[3]) else 0)
