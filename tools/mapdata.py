#!/usr/bin/env python3
"""MAP/M/_MAP_M_### map files of AND2011, verified against the ORIGINAL Map_SetMapData 0x9b434 executed in the
reference emulator (game-object factories are stubbed and their arguments recorded).

File layout (CONFIRMED from Map_SetMapData / Map_addMapObject / Map_objIdxCreate; all 97 files consume
EXACTLY their size with it):
  u8   nAB                       -> _map[0x270]
  nAB x { u8 a, u8 b }           -> _map[0x271 + i] = a , _map[0x274 + i] = b      (arrays of 3, i < 3 expected)
  2 x  { s8 len ; len bytes }    -> two strings, copied with len+1 bytes into _map+0x278/0x280 and 0x27c/0x284
                                    (len <= 0 means 'no string': nothing consumed besides the length byte)
  u8   unk0 (skipped)            UNKNOWN
  u8   W   -> _map[0x7a]   (tiles)     u8 H -> _map[0x7b]   (tiles; one tile = 16 px, _map+0x80 = W*16, +0x84 = H*16)
  u8   nObjIdx                   (values >= 0x80 behave as 0 and consume nothing)  then nObjIdx x u8 (object image ids,
                                    Map_objIdxCreate)
  u8   nSpr ; nSpr x u8          (sprite-object ids; SprObject_create(id, Map_Time_Return(_map[0x68])))   same >= 0x80 rule
  W*H bytes layerA  -> *(_map+676)        W*H bytes layerB -> *(_map+704)   (_map[0x350] = layerB[0] & 8)
  4 x { u8 count (UNSIGNED, 0..255; the decompiler shows 'char' but the disassembly masks with 0xff, 0x9bb98) ;
        count x 8 bytes {u8 b0, u8 b1, u8 b2, u8 b3, s16le x, s16le y} }       lists 0..3 = moreBottom/bottom/middle/top objects
      record: b0 bit7 set -> sprite object (Map_getSprBody(b3, list) then SprObject_copy(b0 as s8, x, y, list)),
              otherwise a static MAPOBJ (MAPOBJ_createAndCopy)
  u8   nSummon ; nSummon x u8 type (0..2 index of the per-map monster table _map+0x48 (3 x s16); Map_getSummonBody/MONSTER_addBody)
  u8   nMon ; nMon entries { u8 type, u8 x, u8 y } -> for type < 3 (s8 compare): MONSTER_Clone(body, i, s8(x)*16+8, (y+1)*16, 0)
       Entries with type >= 3 are consumed as ONE byte only (0xab8e8) -- not present in any shipped map (all 97 parse exactly
       with 3-byte entries).  Runtime-dependent extras (boss: later entries skipped / cloned x6; monster kind '7' consumes an
       extra loop count) depend on monster tables, not on the file layout.

Usage:  python3 tools/mapdata.py dump <file>
        python3 tools/mapdata.py selftest <binary.mod> <res dir>
"""
import os
import struct
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))


def s8(v):
    return v - 256 if v & 0x80 else v


def parse(b):
    p = 0
    d = {}
    n = b[p]; p += 1
    d['ab'] = [(b[p + 2 * i], b[p + 2 * i + 1]) for i in range(n)]; p += 2 * n
    d['strs'] = []
    for _ in range(2):
        L = s8(b[p]); p += 1
        if L > 0:
            d['strs'].append(bytes(b[p:p + L])); p += L
        else:
            d['strs'].append(b'')
    d['unk0'], d['W'], d['H'] = b[p], b[p + 1], b[p + 2]
    cnt = b[p + 3]; p += 4
    if cnt != 0 and cnt < 0x80:
        d['objidx'] = list(b[p:p + cnt]); p += cnt
    else:
        d['objidx'] = []
    cnt = b[p]; p += 1
    if cnt != 0 and cnt < 0x80:
        d['spr'] = list(b[p:p + cnt]); p += cnt
    else:
        d['spr'] = []
    n = d['W'] * d['H']
    d['layerA'] = bytes(b[p:p + n]); p += n
    d['layerB'] = bytes(b[p:p + n]); p += n
    d['objs'] = []
    for _ in range(4):
        c = b[p]; p += 1
        lst = []
        for _ in range(c):
            lst.append(tuple(b[p:p + 4]) + struct.unpack_from('<hh', b, p + 4)); p += 8
        d['objs'].append(lst)
    c = b[p]; p += 1
    d['summon'] = list(b[p:p + c]) if 0 < c < 0x80 else []
    p += c if 0 < c < 0x80 else 0
    c = b[p]; p += 1
    k = c if 0 < c < 0x80 else 0
    d['mons'] = [tuple(b[p + 3 * i:p + 3 * i + 3]) for i in range(k)]; p += 3 * k
    d['end'] = p
    return d


def monster_spawns(b, d):
    """Monster placements exactly as Map_SetMapData 0x9b434 walks the list (stream semantics, no boss logic):
    nMon iterations; an iteration whose type byte (s8) is >= 3 consumes ONE byte and spawns nothing, otherwise it
    consumes 3 bytes (type, x, y) and spawns MONSTER_Clone(body, iteration index, s8(x)*16+8, (y+1)*16).
    QUIRK (original behaviour kept): the trailing x/y bytes of a type>=3 record are re-read as new records, so maps whose
    list contains such records spawn shifted / spurious monsters.  Boss rules (map flag 0x358, kinds '7' and ':') need
    the monster tables and are not modelled here."""
    # offset of the monster list = end - 3*len(mons) (all shipped maps use 3-byte records)
    p = d['end'] - 3 * len(d['mons'])
    n = len(d['mons'])
    out = []
    i = 0
    while i != n:
        t = s8(b[p]) if p < len(b) else 0
        if t >= 3:
            i += 1; p += 1
            continue
        x, y = (b[p + 1] if p + 1 < len(b) else 0), (b[p + 2] if p + 2 < len(b) else 0)
        out.append((i, s8(x) * 16 + 8, (y + 1) * 16))
        p += 3; i += 1
    return out


# --------------------------------------------------------------------------------- self-test
def selftest(binp, resdir):
    from and_hle import Harness
    from unicorn import UC_HOOK_CODE
    from unicorn.arm_const import UC_ARM_REG_LR, UC_ARM_REG_PC, UC_ARM_REG_R0, UC_ARM_REG_R1, UC_ARM_REG_R2, UC_ARM_REG_R3, UC_ARM_REG_SP
    h = Harness(binp, resdir).boot_dm()
    h.imports['MC_knlFree'] = lambda e, a: 0
    rec = []
    dummy = h.alloc(0x800)

    def stub(name, ret=0, record=False, ptrlen=0):
        addr = h.elf.fbyname[name][0]

        def cb(uc, a, sz, _):
            if record:
                regs = [uc.reg_read(r) for r in (UC_ARM_REG_R0, UC_ARM_REG_R1, UC_ARM_REG_R2, UC_ARM_REG_R3)]
                sp = uc.reg_read(UC_ARM_REG_SP)
                st = struct.unpack('<2I', bytes(uc.mem_read(sp, 8)))
                extra = bytes(uc.mem_read(regs[0], ptrlen)) if ptrlen else b''
                rec.append((name, regs, st, extra))
            uc.reg_write(UC_ARM_REG_R0, ret)
            uc.reg_write(UC_ARM_REG_PC, uc.reg_read(UC_ARM_REG_LR))
        h.uc.hook_add(UC_HOOK_CODE, cb, begin=addr, end=addr)
    stub('SprObject_create', 0, True)
    stub('Map_addSprBodyList', 0)
    stub('Map_Time_Return', 0)
    stub('MAPOBJ_createAndCopy', 0, True, ptrlen=0x80)
    stub('Map_getSprBody', 0, True)
    stub('SprObject_copy', 0)
    stub('Map_getSummonBody', 0, True)
    stub('MONSTER_addBody', 0, True)
    stub('Map_mobBody', dummy, True)
    stub('MONSTER_isBoss', 0)
    stub('MONSTER_Clone', 0, True)
    mp = h.objaddr('_map')
    files = sorted(os.listdir(os.path.join(resdir, 'MAP', 'M')))
    bad = 0
    tot_objs = tot_mons = 0
    for fn in files:
        data = open(os.path.join(resdir, 'MAP', 'M', fn), 'rb').read()
        d = parse(data)
        if d['end'] != len(data):
            bad += 1; print('SIZE', fn, d['end'], len(data)); continue
        buf = h.alloc(len(data) + 8); h.write(buf, data)
        h.write(mp + 832, struct.pack('<I', buf))
        h.write(mp + 692, b'\0\0')
        rec.clear()
        h.write(dummy, b'\0' * 0x800)
        h.call('Map_SetMapData')
        ok = True
        why = []
        if h.read(mp + 0x270, 1)[0] != len(d['ab']):
            ok = False; why.append('nAB')
        for i, (a, b2) in enumerate(d['ab'][:3]):
            if h.read(mp + 0x271 + i, 1)[0] != a or h.read(mp + 0x274 + i, 1)[0] != b2:
                ok = False; why.append('ab%d' % i)
        for k, s in enumerate(d['strs']):
            if s:
                ptr = h.u32(mp + 0x278 + 4 * k)
                if h.read(ptr, len(s)) != s:
                    ok = False; why.append('str%d' % k)
        if h.read(mp + 0x7a, 1)[0] != d['W'] or h.read(mp + 0x7b, 1)[0] != d['H']:
            ok = False; why.append('WH')
        n = d['W'] * d['H']
        if n > 0:
            if h.read(h.u32(mp + 676), n) != d['layerA']:
                ok = False; why.append('layerA')
            if h.read(h.u32(mp + 704), n) != d['layerB']:
                ok = False; why.append('layerB')
            if (h.read(mp + 0x350, 1)[0]) != (d['layerB'][0] & 8):
                ok = False; why.append('flag350')
        sids = [r[1][0] & 0xff for r in rec if r[0] == 'SprObject_create']
        if sids != d['spr']:
            ok = False; why.append('spr %s %s' % (sids[:3], d['spr'][:3]))
        # objects: sprite records -> Map_getSprBody(b3, list); static -> MAPOBJ_createAndCopy
        exp_spr = [(o[3], li) for li, lst in enumerate(d['objs']) for o in lst if o[0] & 0x80]
        got_spr = [(r[1][0] & 0xff, r[1][1] & 0xff) for r in rec if r[0] == 'Map_getSprBody']
        n_static = sum(1 for lst in d['objs'] for o in lst if not o[0] & 0x80)
        n_got = sum(1 for r in rec if r[0] == 'MAPOBJ_createAndCopy')
        tot_objs += sum(len(x) for x in d['objs'])
        if got_spr != exp_spr or n_got != n_static:
            ok = False; why.append('objs')
        else:
            # the 8-byte record fields reach MAPOBJ_createAndCopy's struct unchanged: find (b0..b3) and x,y ints in the copy
            exp_static = [o for lst in d['objs'] for o in lst if not o[0] & 0x80]
            got_static = [r[3] for r in rec if r[0] == 'MAPOBJ_createAndCopy']
            offs = set()
            for o, mem in zip(exp_static, got_static):
                pat = bytes(o[:4])
                i = mem.find(pat)
                xy = struct.pack('<ii', o[4], o[5])
                j = mem.find(xy)
                offs.add((i, j))
            if len(offs) > 1 or (offs and (-1, -1) in offs) or any(-1 in t for t in offs):
                ok = False; why.append('objfields %s' % sorted(offs)[:3])
        msum = [r[1][0] & 0xff for r in rec if r[0] == 'MONSTER_addBody']      # r0 = table[type]; compared via count only
        if len(msum) != len([t for t in d['summon'] if t < 3]):
            ok = False; why.append('summon count %d/%d' % (len(msum), len(d['summon'])))
        clones = [r for r in rec if r[0] == 'MONSTER_Clone']
        exp_cl = monster_spawns(data, d)
        got_cl = [((r[1][1] & 0xffffffff), (r[1][2] & 0xffffffff), r[1][3] & 0xffffffff) for r in clones]
        norm = lambda v: v - (1 << 32) if v & 0x80000000 else v
        got_cl = [(norm(a), norm(b), norm(c)) for a, b, c in got_cl]
        tot_mons += len(d['mons'])
        if got_cl != exp_cl:
            ok = False; why.append('mons got %d exp %d first diff %s' % (len(got_cl), len(exp_cl), next(((i, g, e) for i, (g, e) in enumerate(zip(got_cl, exp_cl)) if g != e), None)))
        if not ok:
            bad += 1
            if bad <= 8:
                print('MISMATCH', fn, why)
    print('maps', len(files), 'mismatches', bad, '(object records', tot_objs, ', monster entries', tot_mons, ')')
    return bad


if __name__ == '__main__':
    if sys.argv[1] == 'dump':
        d = parse(open(sys.argv[2], 'rb').read())
        for k, v in d.items():
            print(k, v if not isinstance(v, (bytes, list)) or len(v) < 12 else '<%d items>' % len(v))
    elif sys.argv[1] == 'selftest':
        sys.exit(1 if selftest(sys.argv[2], sys.argv[3]) else 0)
