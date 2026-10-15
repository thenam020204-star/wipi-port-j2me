#!/usr/bin/env python3
"""Generic framing survey of the game's data files (after the resource cipher, hl4des, where the game applies it).

F1 framing (CONFIRMED, Item_getOffset 0x92c48 / MONSTER_dataLoad_d / Boss_dataLoad_d / SUMMON_*):
    a file is a sequence of records  { s16le length, body[length] }  ; record k is found by skipping k records.
    DES-decoded files may end with up to 7 pad bytes 0x00.
This tool reports, per file: DES or raw, whether F1 consumes the file exactly, record count and the length range.
Usage: python3 tools/framing.py <res dir>
"""
import os
import struct
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import hl4des


def f1(b):
    p, lens = 0, []
    while p + 2 <= len(b):
        n = struct.unpack_from('<h', b, p)[0]
        if n < 0 or p + 2 + n > len(b):
            return None, p
        lens.append(n)
        p += 2 + n
    # DES-decoded files are padded with 0x00 up to a multiple of 8 bytes (CONFIRMED: every 'stopped at N-1' file has tail 00)
    if p != len(b) and not (len(b) - p < 8 and not any(b[p:])):
        return None, p
    return lens, p


DATA_DIRS = ('E', 'FR', 'HDAT', 'ITM', 'NPC', 'DAT')


def survey(res):
    rows = []
    for d in DATA_DIRS:
        for root, _, fs in sorted(os.walk(os.path.join(res, d))):
            for f in sorted(fs):
                if f.endswith('_BM') or f.endswith('_CIF') or f.startswith('FACE_'):
                    continue
                p = os.path.join(root, f)
                rel = os.path.relpath(p, res).replace(os.sep, '/')
                raw = open(p, 'rb').read()
                des = hl4des.is_des_resource(rel)
                b = hl4des.decrypt(raw) if des else raw
                lens, end = f1(b)
                rows.append((rel, 'DES' if des else 'raw', len(b), lens, end))
    return rows


if __name__ == '__main__':
    ok = bad = 0
    for rel, kind, n, lens, end in survey(sys.argv[1]):
        if lens is not None:
            ok += 1
            print('%-34s %-3s %6d  F1 records %4d  len %d..%d' % (rel, kind, n, len(lens), min(lens) if lens else 0, max(lens) if lens else 0))
        else:
            bad += 1
            print('%-34s %-3s %6d  not F1 (stopped at %d)' % (rel, kind, n, end))
    print('F1-exact files', ok, 'other', bad)
