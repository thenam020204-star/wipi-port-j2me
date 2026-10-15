#!/usr/bin/env python3
"""Measured field layout of the game's data tables (AND2011), taken from the ORIGINAL loaders run in the reference emulator.

For every loader the tool runs the original function (tools/loader_trace.py): each store into the destination structure is
paired with the file bytes read since the previous store.  File offsets are made RECORD-RELATIVE with the F1 framing
(tools/framing.py: records { s16le len, body[len] }, offset 0 = first body byte).  A line

    rec+3..4  ->  dst+0x178 w2

means: the loader copies file bytes [3,4] of the record into a 2-byte store at structure offset 0x178.  `src None` stores are values
computed by the loader (constants, derived fields).  Everything printed is MEASURED (CONFIRMED); field MEANING is not inferred here
(structure offsets are the game's own - names come with the game logic in Phase E).

Usage: python3 tools/tables.py <binary.mod> <res dir> [loader ...]
"""
import os
import struct
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import hl4des
from loader_trace import Tracer, trace_with_resource
from framing import f1


def records(data):
    out, p = [], 0
    while p + 2 <= len(data):
        n = struct.unpack_from('<h', data, p)[0]
        if n < 0 or p + 2 + n > len(data):
            break
        out.append((p + 2, n))
        p += 2 + n
    return out


class Spec:
    def __init__(self, name, loader, res, des, size, args, idxs=None, pre=None, stride=None, dyn=None):
        self.name, self.loader, self.res, self.des, self.size = name, loader, res, des, size
        self.args, self.idxs, self.pre, self.stride, self.dyn = args, idxs, pre, stride, dyn   # dyn: size of the heap block the loader allocates for its output (the first such MC_knlCalloc becomes the watched destination)


def run_spec(t, sp, resdir):
    h = t.h
    rel = sp.res
    raw = open(os.path.join(resdir, rel), 'rb').read()
    data = hl4des.decrypt(raw) if sp.des else raw
    recs = records(data) if f1(data)[0] is not None else [(0, len(data))]    # non-F1 files: offsets are file-absolute
    out = []
    t.keep_reads = True
    for k in (sp.idxs if sp.idxs is not None else [None]):
        dst = h.alloc(sp.size)
        h.write(dst, b'\0' * sp.size)
        if sp.pre:
            sp.pre(h, dst, k)
        t.watch_dst(dst, sp.size)
        saved = h.imports['MC_knlCalloc']
        if sp.dyn:
            def calloc(e, a, _s=saved):
                r = _s(e, a)
                if a[0] == sp.dyn and t.dst[0] == dst:
                    t.watch_dst(r, sp.dyn)
                return r
            h.imports['MC_knlCalloc'] = calloc
        try:
            ev, d, buf = trace_with_resource(t, rel, sp.loader, *sp.args(h, dst, k), des=sp.des)
        finally:
            h.imports['MC_knlCalloc'] = saved
        assert d == data, (sp.name, 'resource mismatch')
        for (doff, sz, val, lo, hi, rd) in ev:
            if not rd:
                out.append((k, None, None, None, doff, sz, val))
                continue
            # the record skip (Item_getOffset & co.) also reads the length prefixes of earlier records: attribute the store to
            # the record that holds the LAST byte read and keep only the reads inside that record's body
            top = rd[-1]
            ri = next((i for i, (s, n) in enumerate(recs) if s <= top < s + n), None)
            if ri is None:
                out.append((k, None, lo, hi, doff, sz, val))
            else:
                s, n = recs[ri]
                inb = [r - s for r in rd if s <= r < s + n]
                out.append((k, ri, min(inb), max(inb), doff, sz, val))
    return recs, out


def report(sp, recs, ev, upto=2):
    print('=== %s  (%s, %s, %d records)' % (sp.name, sp.res, 'DES' if sp.des else 'raw', len(recs)))
    byrec = {}
    for k, ri, lo, hi, doff, sz, val in ev:
        byrec.setdefault((k, ri), []).append((lo, hi, doff, sz, val))
    shown = 0
    for key in sorted(byrec, key=lambda x: (x[0] if x[0] is not None else -1, x[1] if x[1] is not None else -1)):
        if shown >= upto * 2 and key[1] is not None:
            continue
        shown += 1
        seen = {}
        for lo, hi, doff, sz, val in byrec[key]:
            seen[(doff, sz)] = (lo, hi, val)
        lines = []
        for (doff, sz), (lo, hi, val) in sorted(seen.items()):
            src = 'rec+%d..%d' % (lo, hi) if lo is not None and lo != hi else ('rec+%d' % lo if lo is not None else 'computed')
            lines.append('  %-12s -> dst+%#05x w%d   (=%#x)' % (src, doff, sz, val))
        print(' call idx %s record %s: %d stores' % (key[0], key[1], len(lines)))
        print('\n'.join(lines[:120]))


def _cls(c):
    def pre(h, d, k):
        h.write(d + 0x16b, bytes([c]))
    return pre


def specs():
    S = []
    S.append(Spec('MONSTER_dataLoad_d', 'MONSTER_dataLoad_d', 'E/_ESDAT_0', True, 0x400, lambda h, d, k: (d, k), idxs=[0, 1, 2]))
    S.append(Spec('MONSTER_dataLoad_g', 'MONSTER_dataLoad_g', 'E/_EGDAT', False, 0x400, lambda h, d, k: (d, k), idxs=[0, 1]))
    S.append(Spec('Boss_dataLoad_d', 'Boss_dataLoad_d', 'E/_BSDAT_0', True, 0x400, lambda h, d, k: (d, k), idxs=[0, 1]))
    S.append(Spec('Boss_dataLoad_g', 'Boss_dataLoad_g', 'E/_BGDAT', False, 0x400, lambda h, d, k: (d, k), idxs=[0, 1]))
    S.append(Spec('HERO_loadArmsSkillInfo', 'HERO_loadArmsSkillInfo', 'HDAT/_H_SA', True, 0x1000, lambda h, d, k: (d,)))
    for c in (0, 2):
        S.append(Spec('HERO_loadSkillInfo[class %d]' % c, 'HERO_loadSkillInfo', 'HDAT/_H_S%03d' % c, True, 0x1000,
                      lambda h, d, k: (d,), pre=_cls(c)))
    for c in (0, 1):
        S.append(Spec('HERO_load_basic_stat[class %d]' % c, 'HERO_load_basic_stat', 'HDAT/_H_BH', True, 0x1000,
                      lambda h, d, k: (d,), pre=_cls(c)))
    S.append(Spec('SUMMON_loadSummonSkillInfo', 'SUMMON_loadSummonSkillInfo', 'HDAT/_H_SS', True, 0x1000, lambda h, d, k: (d, k), idxs=[0, 1]))
    S.append(Spec('SUMMON_loadSummonData_initD', 'SUMMON_loadSummonData_initD', 'HDAT/_H_BS', True, 0x1000, lambda h, d, k: (d,)))
    S.append(Spec('SUMMON_loadSummonData_G', 'SUMMON_loadSummonData_G', 'HDAT/_H_SG', False, 0x1000, lambda h, d, k: (d, k), idxs=[0, 1, 2]))
    S.append(Spec('NPC_dataLoad', 'NPC_dataLoad', 'NPC/_NPCG_DAT', False, 0x400, lambda h, d, k: (d, k), idxs=[0, 1]))
    S.append(Spec('HERO_loadParticleInfo', 'HERO_loadParticleInfo', 'HDAT/_H_PDAT', False, 0x54, lambda h, d, k: (), dyn=0x54, pre=None))
    for c in (0, 1, 2, 3):
        S.append(Spec('loadParticleSet[class %d]' % c, 'loadParticleSet', 'HDAT/_H_P000', False, 0x178, lambda h, d, k: (k,), idxs=[c], dyn=0x178))
    S.append(Spec('Item_loadSetOptData', 'Item_loadSetOptData', 'ITM/DAT/_ITM_SET_OPTION', True, 0x400, lambda h, d, k: (d, k), idxs=[0, 1, 2]))
    return S


if __name__ == '__main__':
    binp, resdir = sys.argv[1], sys.argv[2]
    want = set(sys.argv[3:])
    t = Tracer(binp, resdir)
    t.h.write(t.h.objaddr('_game') + 1, bytes([0]))
    for sp in specs():
        if want and sp.name not in want:
            continue
        try:
            recs, ev = run_spec(t, sp, resdir)
        except Exception as ex:      # keep going: one loader failing must not hide the others
            print('=== %s  FAILED: %r' % (sp.name, ex))
            continue
        report(sp, recs, ev)
