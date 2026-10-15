#!/usr/bin/env python3
"""HNF bitmap fonts of AND2011 (FT/*) + glyph composition, verified per character against the
ORIGINAL HF_DrawText executed in the reference harness.

File layouts (CONFIRMED, HF_load* 0x750d8..0x75430):
  _FT_KOR10X11 : 'HNF', u8 kind(0), u8 rowbits(16), u8 h(11), u8 nCho(8), u8 nJung(4), u8 nJong(4),
                 then 160 cho + 88 jung + 112 jong glyphs, each h*rowbits/8 bytes
  _FT_ENGL5X11 / _FT_ENGS7X7 : 'HNF', u8 1, u8 rowbits(8), u8 h, then 95 glyphs (' '..'~') x h bytes
  _FT_TYPE844  : ChoInfo[44] (set by 22*hasJong+jung), JungInfo[40] (20*hasJong+cho), JongInfo[22] (by jung)
  _FT_HANINFO  : 2350 x u16be Johab code of KS X 1001 syllables (row-major from 0xB0A1)
  _FT_HANCODE  : 93 bytes: [0..30] cho, [31..61] jung, [62..92] jong  (5-bit Johab field -> glyph index)
Composition (HF_DrawTextAnchorH2Color 0x75cb4..0x75e30): cho glyph copied, jung/jong OR-ed in.
Hangul row = 2 bytes: byte1 = left 8 px, byte0 = right 8 px, MSB = leftmost (0x76320..0x763e8).
Usage:
  python3 tools/hf_font.py selftest <binary.mod> <res dir>
"""
import os
import struct
import sys


class Fonts:
    def __init__(self, resdir):
        d = lambda n: open(os.path.join(resdir, 'FT', n), 'rb').read()
        k = d('_FT_KOR10X11')
        assert k[:3] == b'HNF'
        self.kh = k[5]
        self.kbytes = k[5] * (k[4] // 8)                       # ONE_WORD_BYTE
        o = 9
        self.cho = k[o:o + 160 * self.kbytes]; o += 160 * self.kbytes
        self.jung = k[o:o + 88 * self.kbytes]; o += 88 * self.kbytes
        self.jong = k[o:o + 112 * self.kbytes]
        t = d('_FT_TYPE844')
        self.cho_info, self.jung_info, self.jong_info = t[:44], t[44:84], t[84:106]
        self.haninfo = d('_FT_HANINFO')
        self.hancode = d('_FT_HANCODE')
        self.eng = {}
        for name, key in (('_FT_ENGL5X11', 0), ('_FT_ENGS7X7', 1)):
            b = d(name)
            assert b[:3] == b'HNF'
            self.eng[key] = (b[5], b[6:])                       # (height, 95*h bytes)

    # ---- Hangul -----------------------------------------------------------------------
    def johab(self, lead, trail):
        """EUC-KR pair -> Johab-like 16-bit code (subset: KS X 1001 syllables)."""
        if 0xb0 <= lead <= 0xc8 and 0xa1 <= trail <= 0xfe:
            i = (lead - 0xb0) * 94 + (trail - 0xa1)
            hi, lo = self.haninfo[2 * i], self.haninfo[2 * i + 1]
            return (hi << 8) | lo
        return None

    def compose(self, code):
        """Johab code -> list of h rows, each a 16-bit int (bit15 = leftmost pixel)."""
        t = self.hancode
        cho = t[(code >> 10) & 0x1f]
        jung = t[0x1f + ((code >> 5) & 0x1f)]
        jong = t[0x3e + (code & 0x1f)]
        hj = 1 if jong else 0
        jung_set = self.jung_info[20 * hj + cho]
        cho_set = self.cho_info[22 * hj + jung]
        jong_set = self.jong_info[jung]
        n = self.kbytes
        g = bytearray(self.cho[(cho_set * 20 + cho) * n:(cho_set * 20 + cho) * n + n])
        if jung:
            s = self.jung[(jung_set * 22 + jung) * n:(jung_set * 22 + jung) * n + n]
            for i in range(n):
                g[i] |= s[i]
        if hj:
            s = self.jong[(jong_set * 28 + jong) * n:(jong_set * 28 + jong) * n + n]
            for i in range(n):
                g[i] |= s[i]
        return [(g[2 * r + 1] << 8) | g[2 * r] for r in range(self.kh)]

    # ---- Latin ------------------------------------------------------------------------
    def eng_glyph(self, font, ch):
        h, data = self.eng[font]
        idx = 4 if ch == 0x5c else ch - 32                      # '\\' uses slot 4 (0x75f8c)
        return [data[idx * h + r] for r in range(h)]

    @staticmethod
    def eng_width(font, ch):                                    # HF_getRealWidthEng 0x74d78
        if font:
            return 5
        return 7 if ch == 0x5c else 5


# --------------------------------------------------------------------------- self-test
def _harness(binp, resdir):
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    from and_hle import Harness
    h = Harness(binp, resdir).boot_dm()
    h.call('HF_Initialize')
    off = h.u32(h.objaddr('_lcdG') + 0x10)
    return h, off


def draw_original(h, off, s, x, y, bg=0x1234):
    ptr, W, H = h.frames[off]
    h.write(ptr, struct.pack('<H', bg) * (W * H))
    h.call('HF_SetColor', 255, 255, 255)
    sb = h.alloc(len(s) + 1)
    h.write(sb, s + b'\0')
    h.call('HF_DrawText', off, x, y, sb, 0)
    _, _, px = h.frame_pixels(off)
    return W, H, {(i % W, i // W) for i, v in enumerate(px) if v != bg}, {v for v in px if v != bg}


def selftest(binp, resdir):
    f = Fonts(resdir)
    h, off = _harness(binp, resdir)
    X, Y = 20, 20
    bad = n = 0
    # derive the glyph origin once from a known syllable, then require every glyph to agree
    origin = {}
    def check(s, rows, width_bits, kind):
        nonlocal bad, n
        W, H, got, cols = draw_original(h, off, s, X, Y)
        exp_rel = {(c, r) for r, row in enumerate(rows) for c in range(width_bits) if (row >> (width_bits - 1 - c)) & 1}
        n += 1
        if not exp_rel and not got:
            return
        if kind not in origin:
            if not got:
                bad += 1; return
            dx = min(x for x, _ in got) - min(c for c, _ in exp_rel)
            dy = min(y for _, y in got) - min(r for _, r in exp_rel)
            origin[kind] = (dx, dy)
        dx, dy = origin[kind]
        exp = {(c + dx, r + dy) for c, r in exp_rel}
        if exp != got or (cols and cols != {0xffff}):
            bad += 1
            if bad <= 8:
                print('MISMATCH', kind, s, 'missing', sorted(exp - got)[:6], 'extra', sorted(got - exp)[:6])
    for lead in range(0xb0, 0xc9):
        for trail in range(0xa1, 0xff):
            code = f.johab(lead, trail)
            check(bytes([lead, trail]), f.compose(code), 16, 'han')
    for ch in range(0x21, 0x7f):
        if chr(ch) in '{|&$}\'"`^<;=':          # colour / control codes handled by the layout engine
            continue
        check(bytes([ch]), f.eng_glyph(0, ch), 8, 'eng')
    print('origins', origin, 'glyphs checked', n, 'mismatches', bad)
    return bad


if __name__ == '__main__':
    if sys.argv[1] == 'selftest':
        sys.exit(1 if selftest(sys.argv[2], sys.argv[3]) else 0)
