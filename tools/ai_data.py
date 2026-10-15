#!/usr/bin/env python3
"""E/AI### enemy-AI script files (AND2011), verified against the ORIGINAL EnemyAIParsing_LoadData @0xe1c60 /
EnemyAIParsing_Init @0xe18f0 run in the reference emulator (heap structures compared byte for byte).

File layout (CONFIRMED - all 43 files are consumed exactly; the differential test compares every field the loader stores):
  u8  kind                                      -> ai+0x20c
  TRIGGER section (EnemyAIParsing_Init(.., trigger=1)) :
      s16le scriptLen ; u8 nTrig                (nTrig -> ai+0x220)
      u8 triggerCnt[nTrig]                      (-> table at ai+0x224)
      u8 triggerSize[nTrig]                     (-> table at ai+0x22c)
      u8 script[scriptLen]                      (-> trigger script object ai+0x214 : +0 ptr, +8 position table; the length is only passed to Token_New)
      derived position table (script obj +8): pos[0] = 1 ; pos[i] = pos[i-1] + 2 + triggerSize[i-1]   (8-bit arithmetic)
  ACTION section (EnemyAIParsing_Init(.., trigger=0)) :
      s16le scriptLen ; u8 nAct                 (nAct -> ai+0x234)
      u8 actionCnt[nAct] ; u8 actionSize[nAct] ; u8 position[nAct]   (-> ai+0x238 / ai+0x240 / action script obj +8)
      u8 script[scriptLen]                      (-> action script object ai+0x21c)
  STRINGS : u8 nStr ; u8 len[nStr] ; then the nStr strings back to back (no terminators)   (nStr -> ai+0x264)
The script bytes are a small token stream interpreted by EnemyAIParsing_* (Phase E; the bytes are kept verbatim here).
Usage: python3 tools/ai_data.py selftest <binary.mod> <res dir>
"""
import os
import struct
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))


def parse(b):
    b = bytes(b)
    out = {'kind': b[0]}
    p = 1
    ln, n = struct.unpack_from('<h', b, p)[0], b[p + 2]
    q = p + 3
    tc = list(b[q:q + n]); q += n
    ts = list(b[q:q + n]); q += n
    pos = [0] * n
    if n:
        pos[0] = 1
    for i in range(1, n):
        pos[i] = (pos[i - 1] + 2 + ts[i - 1]) & 0xff
    out['trig'] = dict(n=n, cnt=tc, size=ts, pos=pos, script=b[q:q + ln])
    q += ln
    ln, n = struct.unpack_from('<h', b, q)[0], b[q + 2]
    q += 3
    ac = list(b[q:q + n]); q += n
    az = list(b[q:q + n]); q += n
    ap = list(b[q:q + n]); q += n
    out['act'] = dict(n=n, cnt=ac, size=az, pos=ap, script=b[q:q + ln])
    q += ln
    ns = b[q]; q += 1
    lens = list(b[q:q + ns]); q += ns
    strs = []
    for l in lens:
        strs.append(b[q:q + l]); q += l
    out['strs'] = strs
    out['end'] = q
    return out


def selftest(binp, resdir):
    from and_hle import Harness
    h = Harness(binp, resdir).boot_dm()
    h.imports['MC_knlFree'] = lambda e, a: 0
    d = os.path.join(resdir, 'E')
    files = sorted(f for f in os.listdir(d) if f.startswith('AI'))
    bad = 0
    for f in files:
        idx = int(f[2:])
        data = open(os.path.join(d, f), 'rb').read()
        m = parse(data)
        ok = m['end'] == len(data)
        ai = h.alloc(0x400)
        h.write(ai, b'\0' * 0x400)
        h.call('EnemyAIParsing_LoadData', ai, idx)
        u8 = lambda o: h.read(ai + o, 1)[0]
        ptr = lambda o: h.u32(ai + o)
        ok &= u8(0x20c) == m['kind']
        for name, nO, cO, sO, scO in (('trig', 0x220, 0x224, 0x22c, 0x214), ('act', 0x234, 0x238, 0x240, 0x21c)):
            t = m[name]
            ok &= u8(nO) == t['n']
            ok &= list(h.read(ptr(cO), t['n'])) == t['cnt']
            ok &= list(h.read(ptr(sO), t['n'])) == t['size']
            so = ptr(scO)
            ok &= h.read(h.u32(so), len(t['script'])) == t['script']
            ok &= list(h.read(h.u32(so + 8), t['n'])) == t['pos']
        ok &= h.u32(ai + 0x264) == len(m['strs'])
        if m['strs']:
            lens_p, strs_p = h.u32(ai + 0x260), h.u32(ai + 0x24c)
            for i, s in enumerate(m['strs']):
                ok &= h.u32(lens_p + 4 * i) == len(s)
                ok &= h.read(h.u32(strs_p + 4 * i), len(s)) == s
        if not ok:
            bad += 1
            print('MISMATCH', f)
    print('AI files', len(files), 'mismatches', bad)
    return bad


if __name__ == '__main__':
    if sys.argv[1] == 'selftest':
        sys.exit(1 if selftest(sys.argv[2], sys.argv[3]) else 0)
