# -*- coding: utf-8 -*-
"""Codec for the `fontcus` font.bin container (unchanged from fontcusvnrebuilt).

Layout, exactly what fontcus.<clinit> reads:
    u2            utfLen
    u1[utfLen]    charset, Java modified UTF-8 (DataInputStream.readUTF)
    s1            fontHeight
    s1[nChars]    advance width per char, in charset order
    bits          1bpp atlas, row-major; row length = sum(widths);
                  LSB-first inside each byte; the bitstream is CONTINUOUS
                  across rows (no per-row byte padding)
"""
import struct

def _mutf8_decode(b):
    return b.replace(b'\xc0\x80', b'\x00').decode('utf-8')

def _mutf8_encode(s):
    out = bytearray()
    for ch in s:
        c = ord(ch)
        if c == 0:        out += b'\xc0\x80'
        elif c < 0x80:    out.append(c)
        elif c < 0x800:   out += bytes([0xc0 | (c >> 6), 0x80 | (c & 0x3f)])
        elif c < 0x10000: out += bytes([0xe0 | (c >> 12), 0x80 | ((c >> 6) & 0x3f), 0x80 | (c & 0x3f)])
        else: raise ValueError("supplementary char %r not supported" % ch)
    return bytes(out)

def decode(path_or_bytes):
    d = path_or_bytes if isinstance(path_or_bytes, (bytes, bytearray)) else open(path_or_bytes, 'rb').read()
    n = struct.unpack('>H', d[0:2])[0]
    chars = _mutf8_decode(d[2:2+n]); p = 2 + n
    h = d[p] - (256 if d[p] > 127 else 0); p += 1
    widths = []
    for _ in range(len(chars)):
        w = d[p]; p += 1
        widths.append(w - 256 if w > 127 else w)
    atlasW = sum(widths)
    need = (h * atlasW + 7) // 8
    bits = d[p:p+need]
    rows, acc, nbit, bi = [], 0, 8, 0
    for _ in range(h):
        row = bytearray(atlasW)
        for x in range(atlasW):
            if nbit >= 8:
                acc = bits[bi]; bi += 1; nbit = 0
            if acc & 1: row[x] = 1
            acc >>= 1; nbit += 1
        rows.append(row)
    return dict(chars=chars, height=h, widths=widths, atlasW=atlasW, rows=rows,
                trailing=d[p+need:], size=len(d))

def encode(chars, height, widths, rows):
    assert len(chars) == len(widths)
    atlasW = sum(widths)
    out = bytearray()
    u = _mutf8_encode(chars)
    out += struct.pack('>H', len(u)); out += u
    out.append(height & 0xFF)
    for w in widths: out.append(w & 0xFF)
    acc = nbit = 0
    for y in range(height):
        row = rows[y]
        assert len(row) == atlasW
        for x in range(atlasW):
            if row[x]: acc |= (1 << nbit)
            nbit += 1
            if nbit == 8: out.append(acc); acc = 0; nbit = 0
    if nbit: out.append(acc)
    return bytes(out)
