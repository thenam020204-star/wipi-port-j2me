#!/usr/bin/env python3
"""H4/EXD/_H_###_EXD sprite property files (hit/attack boxes) of AND2011, verified against the ORIGINAL
SPR_loadAttackProperty 0xc17e8 (heap structures compared byte for byte after loading in the reference emulator).
SPR_loadExtra @0xc1ee0 (hero) is the same routine (HERO_spriteLoad calls it); Boss/MONSTER loaders call SPR_loadAttackProperty.

File layout (CONFIRMED):
  u8 nRec                                   -> spr+0x5c
  nRec records, each starting with  u8 b0, s8 b1 :
    * b0 == 0 and b1 == 1 : HEADER record: 2 unknown bytes, u8 nItems, then nItems x { u8 type ; payload }
          type 0 : s16 a, s16 b            -> spr+0x00, spr+0x04 (ints)          (INFERRED: overhead anchor, e.g. HP-bar point)
          type 1 : s16 x4                  -> spr+0x08 .. 0x14                    (INFERRED: body rectangle x, y, w, h)
          type 2 : s16 x4                  -> spr+0x18 .. 0x24                    (INFERRED: second rectangle)
          the header consumes one table slot (slot stays empty)
    * otherwise: motion record: b0 = motion id (s8 index into the 400-byte table spr+0x58), b1 = number of entries
          nEntries x { u8 id, s8 nA, s8 nB, then nA groups x nB records }, record = { u8 type ; payload by type }:
            type 3, 13            : s16 x4 -> ints[0..3]
            type 4, 5, 6, 9, 10, 12 : s16 x2 -> ints[0], ints[1] ; ints[2] = ints[3] = -1
            type 7, 8, 11         : s16 x1 -> ints[0] ; ints[1..3] = -1
            other types (0,1,2,14..) : no payload (ints stay 0)
          Each of the nB elements is a 0x44-byte cell: +1 = type of the LAST group read, +4 + 0x10*g = 4 ints of group g (g < 4).
          Derived groups (executed after every record, the last execution wins):
            nA == 1 : groups 1..3 <- group 0
            nA == 2 : group 2 <- transform(group 1)   (group 3 stays 0)
            nA >= 3 : group 3 <- transform(group 2)   (so with nA == 4 the data read for group 3 is overwritten)
            transform by the cell's current type: {3,5,6,13}: (|x| - w, y, w, h') with w = src[2] , h' = src[3]...
              exact: dst = (|src0| - src2, src1, src2, src3) ; type 4: dst = (-src0, src1, -1, -1) ; otherwise dst = src
  (spr+0x54 = table of nRec 8-byte slots {ptr, count}; slot entry (8 bytes) = {u16 id, s16 nB, ptr to nB cells})
Usage: python3 tools/exd.py selftest <binary.mod> <res dir>
"""
import os
import struct
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))


def s8(v):
    return v - 256 if v & 0x80 else v


def s16(b, o):
    return struct.unpack_from('<h', b, o)[0]


def parse(b):
    """Returns dict(hdr=[10 ints], idx=bytearray(400), n=nRec, slots=[None | [ (id, nB, [cell bytearray(0x44)...]) ...]])."""
    b = bytes(b) + b'\0' * 16           # the original reads past the buffer end in two shipped files (see docs): 0 here
    n = b[0]
    hdr = [0] * 10
    idx = bytearray(400)
    slots = []
    p = 1
    for m in range(n):
        b0, b1 = b[p], s8(b[p + 1])
        q = p + 2
        if b0 == 0 and b1 == 1:
            cnt = b[p + 4]
            q = p + 5
            if cnt != 0 and cnt < 0x80:
                for _ in range(cnt):
                    t = b[q]; q += 1
                    if t == 0:
                        hdr[0], hdr[1] = s16(b, q), s16(b, q + 2); q += 4
                    elif t == 1:
                        hdr[2:6] = [s16(b, q + 2 * i) for i in range(4)]; q += 8
                    elif t == 2:
                        hdr[6:10] = [s16(b, q + 2 * i) for i in range(4)]; q += 8
            slots.append(None)
            p = q
            continue
        idx[b0 & 0xff] = m & 0xff
        entries = []
        for _ in range(max(b1, 0)):
            eid, nA, nB = b[q], s8(b[q + 1]), s8(b[q + 2])
            q += 3
            cells = [bytearray(0x44) for _ in range(max(nB, 0))]
            if nA > 0:
                for g in range(nA):
                    for k in range(nB):
                        cell = cells[k]
                        t = b[q]; q += 1
                        cell[1] = t
                        ints = [0, 0, 0, 0]
                        have = False
                        if t in (3, 13):
                            ints = [s16(b, q + 2 * i) for i in range(4)]; q += 8; have = True
                        elif t in (4, 5, 6, 9, 10, 12):
                            ints = [s16(b, q), s16(b, q + 2), -1, -1]; q += 4; have = True
                        elif t in (7, 8, 11):
                            ints = [s16(b, q), -1, -1, -1]; q += 2; have = True
                        if have:
                            struct.pack_into('<4i', cell, 4 + 16 * g, *ints)
                        if nA != 1:
                            sg, dg = (1, 2) if nA == 2 else (2, 3)
                            src = struct.unpack_from('<4i', cell, 4 + 16 * sg)
                            tt = cell[1]
                            if tt in (13, 3, 5, 6):
                                dst = (abs(src[0]) - src[2], src[1], src[2], src[3])
                            elif tt == 4:
                                dst = (-src[0], src[1], -1, -1)
                            else:
                                dst = src
                            struct.pack_into('<4i', cell, 4 + 16 * dg, *dst)
                        else:
                            src = struct.unpack_from('<4i', cell, 4)
                            for dg in (1, 2, 3):
                                struct.pack_into('<4i', cell, 4 + 16 * dg, *src)
            entries.append((eid, nB, cells))
        slots.append(entries)
        p = q
    return dict(hdr=hdr, idx=bytes(idx), n=n, slots=slots, end=p)


# --------------------------------------------------------------------------------- self-test
def selftest(binp, resdir):
    from and_hle import Harness
    h = Harness(binp, resdir).boot_dm()
    h.imports['MC_knlFree'] = lambda e, a: 0
    d = os.path.join(resdir, 'H4', 'EXD')
    files = sorted(os.listdir(d))
    bad = 0
    ncells = 0
    overrun = []
    trailing = []
    for fn in files:
        data = open(os.path.join(d, fn), 'rb').read()
        m = parse(data)
        if m['end'] > len(data):
            overrun.append((fn, m['end'] - len(data)))
        elif m['end'] != len(data):
            trailing.append((fn, len(data) - m['end']))
        spr = h.alloc(0x80)
        path = h.alloc(64)
        h.write(path, b'/H4/EXD/' + fn.encode() + b'\0')
        h.call('SPR_loadAttackProperty', spr, path)
        ok = True
        hdr = list(struct.unpack('<10i', h.read(spr, 40)))
        if hdr != m['hdr']:
            ok = False
        if h.u32(spr + 0x5c) != m['n']:
            ok = False
        tab = h.u32(spr + 0x54)
        idxp = h.u32(spr + 0x58)
        if h.read(idxp, 400) != m['idx']:
            ok = False
        if m['n'] and ok:
            for i, entries in enumerate(m['slots']):
                ptr, cnt = struct.unpack('<II', h.read(tab + 8 * i, 8))
                if entries is None:
                    if ptr != 0 or cnt != 0:
                        ok = False
                    continue
                if cnt != len(entries):
                    ok = False; break
                for j, (eid, nB, cells) in enumerate(entries):
                    e = h.read(ptr + 8 * j, 8)
                    got_id, got_nb, cp = struct.unpack('<HhI', e)
                    if got_id != eid or got_nb != nB:
                        ok = False; break
                    for k, cell in enumerate(cells):
                        ncells += 1
                        if h.read(cp + 0x44 * k, 0x44) != bytes(cell):
                            ok = False; break
        if not ok:
            bad += 1
            if bad <= 8:
                print('MISMATCH', fn)
    print('EXD files', len(files), 'mismatches', bad, '(cells compared', ncells, ') files read past their end by the original:', overrun, ', files with trailing bytes the original never reads:', trailing)
    return bad


if __name__ == '__main__':
    if sys.argv[1] == 'selftest':
        sys.exit(1 if selftest(sys.argv[2], sys.argv[3]) else 0)
