#!/usr/bin/env python3
"""Literal re-implementation of the AND2011 resource/save cipher (MX_desInit / startDes /
desRound), written from the disassembly, plus a self-check against the original routine
executed in the reference emulator.

Structure (CONFIRMED, addresses = AND2011 vaddr):
- key  = first 8 chars of "J@IWO8N7L0E7E" (SaveDataInit 0xbee80, MX_desInit strncpy 8 @0x2add8)
- key schedule: standard PC1 / left-rotations (C.485.12850) / PC2 -> KEY4REAL[16][48]   (0x2ae20..0x2af2c)
- MX_desDecrypt: if KEY4ENCRYPT: reverse subkey order (swap rows i,15-i), clear flag;
  startDes(mode=0, len>>3, buf)                                                    (0x27fb0..0x28018)
- MX_desEncrypt: if !KEY4ENCRYPT: reverse back, set flag; startDes(mode=1, ...)     (0x28058..0x280c0)
- startDes per 8-byte block (ECB, trailing len%8 bytes untouched):
    bits = IP(block)
    mode!=0: a=bits[0:32], b=bits[32:64]       mode==0: a=bits[32:64], b=bits[0:32]   (0x27dc8 / 0x27f34)
    16 rounds (desRound 0x24cd4):  t=b; b = a ^ P(S(E(b) ^ K[r])); a = t
    mode!=0: swap32bit (0x24f88, called @0x27e18), out = IP^-1(a || b)
    mode==0: swap32bit is SKIPPED (b @0x27f78 -> 0x27e1c), out = IP^-1(b || a)
  Tables (IP, IP^-1, E, P, PC1, PC2, shifts) are verified equal to FIPS 46-3; the S-box is FIPS
  except one cell (S1[3][10] = 2, see S below).
Usage:
  python3 tools/hl4des.py selftest <binary.mod> <res dir>     # compare vs emulator on every file
  python3 tools/hl4des.py dec <in> <out>  |  enc <in> <out>
"""
import os
import sys

KEY = b'J@IWO8N7'

IP = [58, 50, 42, 34, 26, 18, 10, 2, 60, 52, 44, 36, 28, 20, 12, 4, 62, 54, 46, 38, 30, 22, 14, 6,
      64, 56, 48, 40, 32, 24, 16, 8, 57, 49, 41, 33, 25, 17, 9, 1, 59, 51, 43, 35, 27, 19, 11, 3,
      61, 53, 45, 37, 29, 21, 13, 5, 63, 55, 47, 39, 31, 23, 15, 7]
IP_1 = [40, 8, 48, 16, 56, 24, 64, 32, 39, 7, 47, 15, 55, 23, 63, 31, 38, 6, 46, 14, 54, 22, 62, 30,
        37, 5, 45, 13, 53, 21, 61, 29, 36, 4, 44, 12, 52, 20, 60, 28, 35, 3, 43, 11, 51, 19, 59, 27,
        34, 2, 42, 10, 50, 18, 58, 26, 33, 1, 41, 9, 49, 17, 57, 25]
E = [32, 1, 2, 3, 4, 5, 4, 5, 6, 7, 8, 9, 8, 9, 10, 11, 12, 13, 12, 13, 14, 15, 16, 17,
     16, 17, 18, 19, 20, 21, 20, 21, 22, 23, 24, 25, 24, 25, 26, 27, 28, 29, 28, 29, 30, 31, 32, 1]
P = [16, 7, 20, 21, 29, 12, 28, 17, 1, 15, 23, 26, 5, 18, 31, 10, 2, 8, 24, 14, 32, 27, 3, 9,
     19, 13, 30, 6, 22, 11, 4, 25]
PC1 = [57, 49, 41, 33, 25, 17, 9, 1, 58, 50, 42, 34, 26, 18, 10, 2, 59, 51, 43, 35, 27, 19, 11, 3,
       60, 52, 44, 36, 63, 55, 47, 39, 31, 23, 15, 7, 62, 54, 46, 38, 30, 22, 14, 6, 61, 53, 45, 37,
       29, 21, 13, 5, 28, 20, 12, 4]
PC2 = [14, 17, 11, 24, 1, 5, 3, 28, 15, 6, 21, 10, 23, 19, 12, 4, 26, 8, 16, 7, 27, 20, 13, 2,
       41, 52, 31, 37, 47, 55, 30, 40, 51, 45, 33, 48, 44, 49, 39, 56, 34, 53, 46, 42, 50, 36, 29, 32]
SHIFTS = [1, 1, 2, 2, 2, 2, 2, 2, 1, 2, 2, 2, 2, 2, 2, 1]
S = [
    [14, 4, 13, 1, 2, 15, 11, 8, 3, 10, 6, 12, 5, 9, 0, 7, 0, 15, 7, 4, 14, 2, 13, 1, 10, 6, 12, 11, 9, 5, 3, 8,
     4, 1, 14, 8, 13, 6, 2, 11, 15, 12, 9, 7, 3, 10, 5, 0, 15, 12, 8, 2, 4, 9, 1, 7, 5, 11, 3, 14, 10, 0, 6, 13],
    [15, 1, 8, 14, 6, 11, 3, 4, 9, 7, 2, 13, 12, 0, 5, 10, 3, 13, 4, 7, 15, 2, 8, 14, 12, 0, 1, 10, 6, 9, 11, 5,
     0, 14, 7, 11, 10, 4, 13, 1, 5, 8, 12, 6, 9, 3, 2, 15, 13, 8, 10, 1, 3, 15, 4, 2, 11, 6, 7, 12, 0, 5, 14, 9],
    [10, 0, 9, 14, 6, 3, 15, 5, 1, 13, 12, 7, 11, 4, 2, 8, 13, 7, 0, 9, 3, 4, 6, 10, 2, 8, 5, 14, 12, 11, 15, 1,
     13, 6, 4, 9, 8, 15, 3, 0, 11, 1, 2, 12, 5, 10, 14, 7, 1, 10, 13, 0, 6, 9, 8, 7, 4, 15, 14, 3, 11, 5, 2, 12],
    [7, 13, 14, 3, 0, 6, 9, 10, 1, 2, 8, 5, 11, 12, 4, 15, 13, 8, 11, 5, 6, 15, 0, 3, 4, 7, 2, 12, 1, 10, 14, 9,
     10, 6, 9, 0, 12, 11, 7, 13, 15, 1, 3, 14, 5, 2, 8, 4, 3, 15, 0, 6, 10, 1, 13, 8, 9, 4, 5, 11, 12, 7, 2, 14],
    [2, 12, 4, 1, 7, 10, 11, 6, 8, 5, 3, 15, 13, 0, 14, 9, 14, 11, 2, 12, 4, 7, 13, 1, 5, 0, 15, 10, 3, 9, 8, 6,
     4, 2, 1, 11, 10, 13, 7, 8, 15, 9, 12, 5, 6, 3, 0, 14, 11, 8, 12, 7, 1, 14, 2, 13, 6, 15, 0, 9, 10, 4, 5, 3],
    [12, 1, 10, 15, 9, 2, 6, 8, 0, 13, 3, 4, 14, 7, 5, 11, 10, 15, 4, 2, 7, 12, 9, 5, 6, 1, 13, 14, 0, 11, 3, 8,
     9, 14, 15, 5, 2, 8, 12, 3, 7, 0, 4, 10, 1, 13, 11, 6, 4, 3, 2, 12, 9, 5, 15, 10, 11, 14, 1, 7, 6, 0, 8, 13],
    [4, 11, 2, 14, 15, 0, 8, 13, 3, 12, 9, 7, 5, 10, 6, 1, 13, 0, 11, 7, 4, 9, 1, 10, 14, 3, 5, 12, 2, 15, 8, 6,
     1, 4, 11, 13, 12, 3, 7, 14, 10, 15, 6, 8, 0, 5, 9, 2, 6, 11, 13, 8, 1, 4, 10, 7, 9, 5, 0, 15, 14, 2, 3, 12],
    [13, 2, 8, 4, 6, 15, 11, 1, 10, 9, 3, 14, 5, 0, 12, 7, 1, 15, 13, 8, 10, 3, 7, 4, 12, 5, 6, 11, 0, 14, 9, 2,
     7, 11, 4, 1, 9, 12, 14, 2, 0, 6, 10, 13, 15, 3, 5, 8, 2, 1, 14, 7, 4, 10, 8, 13, 15, 12, 9, 0, 3, 5, 6, 11]]
S_FIPS = [row[:] for row in S]
# CONFIRMED deviation: the game's MAT_SBOX (0xe0d0c) has S1[row 3][col 10] = 2 (FIPS: 3), byte 0xe0d46.
# This single cell is why a standard DES library cannot decrypt the resources.
S[0][58] = 2


def _bits(b):
    return [(c >> (7 - i)) & 1 for c in b for i in range(8)]


def _bytes(bits):
    return bytes(sum(bits[8 * k + i] << (7 - i) for i in range(8)) for k in range(len(bits) // 8))


def subkeys(key=KEY):
    k = _bits(key[:8].ljust(8, b'\0'))
    cd = [k[i - 1] for i in PC1]
    c, d = cd[:28], cd[28:]
    out = []
    for s in SHIFTS:
        c = c[s:] + c[:s]
        d = d[s:] + d[:s]
        out.append([(c + d)[i - 1] for i in PC2])
    return out


def _f(r, k):
    x = [r[i - 1] ^ k[j] for j, i in enumerate(E)]
    o = []
    for n in range(8):
        b = x[6 * n:6 * n + 6]
        v = S[n][(b[0] * 2 + b[5]) * 16 + b[1] * 8 + b[2] * 4 + b[3] * 2 + b[4]]
        o += [(v >> 3) & 1, (v >> 2) & 1, (v >> 1) & 1, v & 1]
    return [o[i - 1] for i in P]


def _block(blk, ks, mode):
    bits = _bits(blk)
    t = [bits[i - 1] for i in IP]
    if mode:
        a, b = t[:32], t[32:]
    else:
        a, b = t[32:], t[:32]
    for k in ks:
        a, b = b, [x ^ y for x, y in zip(a, _f(b, k))]
    # mode!=0: swap32bit(a, b) @0x27e18 then output r7||r8 => a||b
    # mode==0: branch 0x27f78 -> 0x27e1c skips swap32bit       => b||a
    pre = a + b if mode else b + a
    return _bytes([pre[i - 1] for i in IP_1])


def decrypt(data, key=KEY):
    ks = subkeys(key)[::-1]
    n = len(data) // 8 * 8
    return b''.join(_block(data[i:i + 8], ks, 0) for i in range(0, n, 8)) + data[n:]


def encrypt(data, key=KEY):
    ks = subkeys(key)
    n = len(data) // 8 * 8
    return b''.join(_block(data[i:i + 8], ks, 1) for i in range(0, n, 8)) + data[n:]


def verify_tables(e):
    """Compare the tables embedded in the binary with the constants above."""
    def tb(name, n, stride=1):
        a = [o for o in e.objs if o[2] == name][0][0]
        off = e.off(a)
        return list(e.data[off:off + n * stride:stride])
    ok = {
        'MAT_IP': tb('MAT_IP', 64) == IP, 'MAT_IP_1': tb('MAT_IP_1', 64) == IP_1,
        'MAT_EXP': tb('MAT_EXP', 48) == E, 'MAT_PERMU': tb('MAT_PERMU', 32) == P,
        'MAT_PC_1': tb('MAT_PC_1', 56) == PC1, 'MAT_PC_2': tb('MAT_PC_2', 48) == PC2,
        'MAT_SBOX(patched)': tb('MAT_SBOX', 512) == sum(S, []),
        'MAT_SBOX diffs vs FIPS': [(i, v) for i, (v, w) in enumerate(zip(tb('MAT_SBOX', 512), sum(S_FIPS, []))) if v != w],
        'SHIFTS': tb('C.485.12850', 16, 4) == SHIFTS,
    }
    keystr = e.cstr(0xd9830)
    ok['key'] = keystr == 'J@IWO8N7L0E7E'
    return ok


# Resource paths loaded through UM_LoadResDes (CONFIRMED by call sites, docs/resmap_and2011.tsv)
DES_PATTERNS = [r'^E/_BSDAT_\d+$', r'^E/_ESDAT_\d+$', r'^FR/_FR_(BA|PL|EN|SU|SK)$', r'^HDAT/_H_S\d{3}$',
                r'^HDAT/_H_(SA|BH|BS|SS)$', r'^ITM/DAT/', r'^NPC/_QUEST_\d+_DAT$', r'^NPC/NPCUI_ARMSSHOP_DAT_\d+$',
                r'^NPC/_NPCUI_(COMBINE|SHOP|GUARDIANSHOP)_DAT_\d+$', r'^NPC/PROBABILITY_DAT$', r'^NPC/NPCUI_GUARDIANSHOP_DAT$', r'^MAP/SC/[en]\d{4}_scn$']


def is_des_resource(rel):
    import re
    return any(re.search(p, rel) for p in DES_PATTERNS)


def selftest(binp, resdir):
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    from and_emu import Emu, std_imports
    emu = std_imports(Emu(binp))
    print('tables:', verify_tables(emu.elf))
    emu.call('SaveDataInit')
    # schedule produced by the original MX_desInit (KEY4REAL, 16x48 bytes)
    ks_emu = [list(emu.read(0xef548 + 48 * r, 48)) for r in range(16)]
    print('key schedule == original KEY4REAL:', ks_emu == subkeys())
    files = []
    for root, _, fs in os.walk(resdir):
        for fn in fs:
            p = os.path.join(root, fn)
            if is_des_resource(os.path.relpath(p, resdir).replace(os.sep, '/')):
                files.append(p)
    files.sort()
    bad = 0
    buf = emu.alloc(1 << 20)
    for p in files:
        raw = open(p, 'rb').read()
        if not raw or len(raw) > (1 << 20):
            continue
        emu.write(buf, raw)
        emu.call('MX_desDecrypt', buf, len(raw))
        ref = emu.read(buf, len(raw))
        if decrypt(raw) != ref:
            bad += 1
            print('MISMATCH dec', p)
        emu.write(buf, ref)
        emu.call('MX_desEncrypt', buf, len(raw))
        if encrypt(ref) != emu.read(buf, len(raw)):
            bad += 1
            print('MISMATCH enc', p)
    print('files checked', len(files), 'mismatches', bad)
    return bad


if __name__ == '__main__':
    if sys.argv[1] == 'selftest':
        sys.exit(1 if selftest(sys.argv[2], sys.argv[3]) else 0)
    data = open(sys.argv[2], 'rb').read()
    open(sys.argv[3], 'wb').write(decrypt(data) if sys.argv[1] == 'dec' else encrypt(data))
