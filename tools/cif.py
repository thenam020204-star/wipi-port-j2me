#!/usr/bin/env python3
"""CIF sprite/animation index (CONFIRMED structure, SPR_loadNewSprite 0xc1f4c):
  s8 n0, n0 bytes (UNKNOWN meaning), u8 nAnim,
  per anim: s8 nDir; per dir: u8 nFrames; per frame: u8 nParts, then nParts parts.
  part size: if sprite.mode(+0x70) == 2: 10 if (b0 & 0x80) else 5 ; otherwise 9 if (b0 & 0x80) else 4.
The original stores, per (anim, dir), an array of frame offsets (offset of the frame's first part byte
AFTER nParts? -> see selftest: we compare exactly what the original stores).
Usage: python3 tools/cif.py dump <file> [mode] | selftest <binary.mod> <res dir>"""
import os, struct, sys


def parse(b, mode=0x11):
    n0 = struct.unpack_from('b', b, 0)[0]
    p = 1 if n0 <= 0 else n0 + 1
    head = b[1:p]
    n_anim = b[p]; p += 1
    anims = []
    for a in range(n_anim):
        n_dir = struct.unpack_from('b', b, p)[0]; p += 1
        dirs = []
        for d in range(max(n_dir, 0)):
            nf = b[p]; p += 1
            frames = []
            for f in range(nf):
                start = p                       # original stores r5 = offset of nParts byte
                npart = struct.unpack_from('b', b, p)[0]; p += 1
                parts = []
                for _ in range(max(npart, 0)):
                    long_ = b[p] & 0x80
                    size = (10 if long_ else 5) if mode == 2 else (9 if long_ else 4)   # 0xc2118: bne -> 9/4
                    parts.append(b[p:p + size]); p += size
                frames.append((start, parts))
            dirs.append(frames)
        anims.append(dirs)
    return {'n0': n0, 'head': head, 'anims': anims, 'end': p}


def selftest(binp, resdir):
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    from and_hle import Harness
    h = Harness(binp, resdir)
    h.imports['MC_knlAlloc'] = lambda e, a: e.alloc(a[0])
    files = sorted(os.path.join(r, f) for r, _, fs in os.walk(resdir) for f in fs if f.endswith('_CIF'))
    bad = n_end = 0
    modes = {}
    for p in files:
        rel = os.path.relpath(p, resdir).replace(os.sep, '/')
        b = open(p, 'rb').read()
        # loader mode is chosen by the caller (HERO_spriteLoad passes 2 @0x7e564, loadSimpleSprite 0x11);
        # pick the mode under which the structure consumes the file exactly, then load with that mode.
        m = mode = None
        for cand in (0x11, 2):
            try:
                mm = parse(b, cand)
            except Exception:
                continue
            if mm['end'] == len(b):
                m, mode = mm, cand
                break
        if m is None:
            bad += 1; print('PARSE-FAIL', rel); continue
        modes[mode] = modes.get(mode, 0) + 1
        spr = h.alloc(0x80); path = h.alloc(len(rel) + 2); h.write(path, b'/' + rel.encode() + b'\0')
        h.call('SPR_loadSprite', spr, path, mode)
        base = h.u32(spr + 0x4c)
        n_anim = h.read(spr + 0x3c, 1)[0]
        recs = h.u32(spr + 0x48)
        ok = n_anim == len(m['anims'])
        for a, dirs in enumerate(m['anims'] if ok else []):
            rec = recs + a * 0x4c
            if h.read(rec + 8, 1)[0] != len(dirs) & 0xff:
                ok = False; break
            for d, frames in enumerate(dirs):
                if h.read(rec + d, 1)[0] != len(frames):
                    ok = False; break
                arr = h.u32(rec + 0xc + 4 * d)
                for f, (start, _) in enumerate(frames):
                    if h.u32(arr + 4 * f) != start:      # original stores the byte offset (r5)
                        ok = False; break
        if not ok:
            bad += 1
            if bad <= 5: print('MISMATCH', rel)
    print('CIF files', len(files), 'structure mismatches', bad, 'modes used', modes)
    return bad


if __name__ == '__main__':
    if sys.argv[1] == 'dump':
        m = parse(open(sys.argv[2], 'rb').read(), int(sys.argv[3], 0) if len(sys.argv) > 3 else 0x11)
        print('n0', m['n0'], 'head', m['head'].hex(), 'anims', len(m['anims']), 'end', m['end'])
        for a, dirs in enumerate(m['anims']):
            print(' anim', a, 'dirs', [len(fr) for fr in dirs], 'parts/frame', [[len(pp) for _, pp in fr] for fr in dirs][:2])
    else:
        sys.exit(1 if selftest(sys.argv[2], sys.argv[3]) else 0)
