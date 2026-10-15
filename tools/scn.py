#!/usr/bin/env python3
"""MAP/SC/{e,n}####_scn scene-script containers (AND2011), verified against the ORIGINAL IT_Initialize @0x920ac /
Script_Init @0x91f38 run in the reference emulator (the `interpreter` object compared field by field).

File = DES (UM_LoadResDes in SCENE_Change @0x6f0bc).  Path: "/MAP/SC/e%04d_scn" (@0xd7798) when `(u8)(hero.class + 1) < 3`,
else "/MAP/SC/n%04d_scn" (@0xd77b0); %04d = _map+0x2d4 (scene index, `uVar8 = _map._724_4_`).  (class test read from the decompiler: the sign
handling of the byte is INFERRED; the e/n split of the 345 files = 183 / 162 is consistent with two class groups.)
Layout (CONFIRMED - all 345 files are consumed exactly):
  header, 12 bytes (SCENE_Change):
     [0] w1  width of the event-script length field (1 = u8, 2 = u16le)     -> IT_Initialize param_3
     [1] w2  same for the 2nd (condition?) script section                    -> param_4
     [2] w3  same for the string table                                       -> param_5
     [3]  -> _map+0x44 (u16)        [4] -> _map[0x288]      [5] -> _map[0x79]
     [6]  -> _map+0x48 (u16)        [7] -> _map+0x4a (u16)  [8] -> _map+0x4c (u16)     (the three monster-type shorts used by Map_SetMapData)
     [9]  = BGM id (s8; -1 = none; compared with Map_Get_BGM to decide whether to reload music)
     [10] = time-of-day class -> _lcdEff[10] and _map[0x68]
     [11] = FGI (foreground image id, s8) -> Event_setFGI
  then three sections  { u8 count N ; N x length (w bytes, little endian) ; the N bodies back to back } :
     section 1: event scripts  (interpreter+0x00; Script_Init also calls funcOnInitEvent(i, body_i) for each event)
     section 2: second script table (interpreter+0x14)
     section 3: string table (interpreter+0x30 count, +0x38 lengths, +0x34 pointers)
  The byte just after the header is the number of events (_event[8] = file[12]).
The script bodies are interpreted by the IT_* VM and the EVT_* callbacks (Phase E); this module only splits the container.
Usage: python3 tools/scn.py selftest <binary.mod> <res dir>
"""
import os
import struct
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import hl4des


def _sec(b, p, w):
    n = b[p]
    q = p + 1
    lens = []
    for _ in range(n):
        l = b[q]; q += 1
        if w > 1:
            l += b[q] * 256; q += 1
        lens.append(l)
    bodies = []
    for l in lens:
        bodies.append(bytes(b[q:q + l])); q += l
    return bodies, q


def parse(b):
    b = bytes(b)
    hdr = dict(w=(b[0], b[1], b[2]), b3=b[3], b4=b[4], b5=b[5], mon=(b[6], b[7], b[8]), bgm=(b[9] ^ 0x80) - 0x80,
               tod=b[10], fgi=(b[11] ^ 0x80) - 0x80)
    p = 12
    s1, p = _sec(b, p, b[0])
    s2, p = _sec(b, p, b[1])
    s3, p = _sec(b, p, b[2])
    return dict(hdr=hdr, events=s1, second=s2, strings=s3, end=p)


def selftest(binp, resdir):
    from and_hle import Harness
    h = Harness(binp, resdir).boot_dm()
    h.imports['MC_knlFree'] = lambda e, a: 0
    d = os.path.join(resdir, 'MAP', 'SC')
    files = sorted(os.listdir(d))
    interp = h.objaddr('interpreter')
    bad = 0
    pad = 0
    for fn in files:
        raw = open(os.path.join(d, fn), 'rb').read()
        data = hl4des.decrypt(raw)
        m = parse(data)
        if m['end'] != len(data):
            pad += 1
            if len(data) - m['end'] >= 8 or any(data[m['end']:]):
                bad += 1
                print('BAD END', fn)
        buf = h.alloc(len(data) + 16)
        h.write(buf, data)
        h.write(interp, b'\0' * 0x48)
        h.call('IT_Initialize', buf, 12, *m['hdr']['w'])
        ok = True
        for off, bodies in ((0x00, m['events']), (0x14, m['second'])):
            n = struct.unpack('<H', h.read(interp + off + 4, 2))[0]
            if n != len(bodies):
                ok = False; continue
            offs = struct.unpack('<%dH' % (n + 1), h.read(h.u32(interp + off + 8), 2 * (n + 1)))
            cum = [0]
            for x in bodies:
                cum.append((cum[-1] + len(x)) & 0xffff)
            ok &= list(offs) == cum
            tot = sum(len(x) for x in bodies)
            if tot > 0:
                ok &= h.read(h.u32(interp + off), tot) == b''.join(bodies)
        ns = h.u32(interp + 0x30)
        ok &= ns == len(m['strings'])
        if ns:
            lp, pp = h.u32(interp + 0x38), h.u32(interp + 0x34)
            for i, s in enumerate(m['strings']):
                ok &= h.u32(lp + 4 * i) == len(s)
                ok &= h.read(h.u32(pp + 4 * i), len(s)) == s
        if not ok:
            bad += 1
            if bad <= 10:
                print('MISMATCH', fn)
    print('scn files', len(files), 'mismatches', bad, '(files with DES padding after the last section:', pad, ')')
    return bad


if __name__ == '__main__':
    if sys.argv[1] == 'selftest':
        sys.exit(1 if selftest(sys.argv[2], sys.argv[3]) else 0)
