#!/usr/bin/env python3
"""Chroisen event scripts (EV##.dat, QUEST1.dat, the script field of DIAL.dat): split into translation units
and rebuild.

Script syntax (kro/re/formats.md §5): text with <TAG args> commands, CRLF lines. The dialog engine (0x245f0)
copies text bytes into 120-byte line buffers and wraps by measured width; '<n>' ends a message (key wait).
Tag arguments are collected into a 40-byte stack buffer (sp+0x4bc, no bound check) -> an encoded argument
must stay <= TAG_ARG_MAX bytes.

Units (what is translated):
  - text between separators: line ends, '<n>', <NAME ..>, <SELECT ..>, <BALLOON ..>, <TYPING ..>,
    <TYPING2 ..>, <SCENE ..>. Other tags inside a sentence (<EMO>, <DELAY> ...) stay inside the unit;
    tags/spaces at the start or end of a run are kept outside it.
  - the text argument of BALLOON / TYPING / TYPING2 / SCENE.
  - NAME arguments are not units: they come from names.tsv (portrait lookup compares them with the
    name table at 0x1400050, see build_vi.py).
  - PARA entries (prologue pre-wrapped for the autoscroll): consecutive 'text<n>' lines form one
    paragraph unit, re-wrapped by width when rebuilt.
"""
import re, struct

TAG_ARG_MAX = 39          # 40-byte argument buffer incl. NUL
ENTRY_MAX = 0xFFFF        # script length is stored with strh (0x150019c)
ARG_TAGS = ('BALLOON', 'TYPING', 'TYPING2', 'SCENE')
SEP_TAGS = ('n', 'NAME', 'SELECT') + ARG_TAGS
PARA = {('EV00.dat', 0)}
HAN = re.compile('[가-힣]')
TAG = re.compile(r'(<[^<>]*>)')


def tagname(t):
    m = re.match(r'<(/?[A-Za-z0-9]+)', t)
    return m.group(1) if m else ''


# ---- containers ----

def ev_entries(d):
    """EV##.dat: 10 x (u32 off, u32 len), offsets relative to byte 80"""
    return [struct.unpack_from('<II', d, 8 * i) for i in range(10)]


def ev_read(d):
    return [d[80 + o:80 + o + l] for o, l in ev_entries(d)]


def ev_write(orig, bodies):
    tab = ev_entries(orig)
    order = sorted(range(10), key=lambda i: (tab[i][0], i))
    out, pos, hdr = bytearray(), 0, [None] * 10
    for i in order:
        if not bodies[i] and tab[i][0] == 0: hdr[i] = (0, 0); continue  # empty entry written as (0, 0) (EV20/21)
        hdr[i] = (pos, len(bodies[i])); out += bodies[i]; pos += len(bodies[i])
    tail = orig[80 + max(o + l for o, l in tab):] if any(l for _, l in tab) else orig[80:]
    return b''.join(struct.pack('<II', *h) for h in hdr) + bytes(out) + tail


def q1_entries(d):
    """QUEST1.dat: u32 n, n x (u32 len, u32 off), data at 4 + 8n"""
    n = struct.unpack_from('<I', d, 0)[0]
    return [struct.unpack_from('<II', d, 4 + 8 * i) for i in range(n)]


def q1_read(d):
    tab = q1_entries(d); base = 4 + 8 * len(tab)
    return [d[base + o:base + o + l] for l, o in tab]


def q1_write(orig, bodies):
    tab = q1_entries(orig); base = 4 + 8 * len(tab)
    order = sorted(range(len(tab)), key=lambda i: (tab[i][1], i))
    out, pos, hdr = bytearray(), 0, [None] * len(tab)
    for i in order:
        hdr[i] = (len(bodies[i]), pos); out += bodies[i]; pos += len(bodies[i])
    end = base + max(o + l for l, o in tab)
    return struct.pack('<I', len(tab)) + b''.join(struct.pack('<II', *h) for h in hdr) + bytes(out) + orig[end:]


def dial_script(rec):
    """DIAL.dat record: [map, ?, lvmin, lvmax, L1][L1 bytes: label, never shown][L2][script + NUL];
    the loader (0x289fc) skips the label and runs the script (length = reclen - L1 - 6 after the patch)"""
    L1 = rec[4]
    return rec[6 + L1:-1]


def dial_rebuild(rec, script):
    L1 = rec[4]
    return rec[:5 + L1] + bytes([min(len(script) + 1, 0xFF)]) + script + b'\0'


# ---- units ----

class Piece:
    __slots__ = ('kind', 'text', 'ctx')

    def __init__(self, kind, text, ctx=''):
        self.kind, self.text, self.ctx = kind, text, ctx   # kind: 'f' fixed, 'u' unit, 'name', 'arg', 'para'

    def __repr__(self):
        return '%s(%r)' % (self.kind, self.text)


def _flush(run, out, ctx):
    """run = list of tokens (text and non-separator tags) -> fixed prefix + unit + fixed suffix"""
    if not run: return
    texts = [i for i, t in enumerate(run) if not t.startswith('<') and t.strip()]
    if not texts:
        out.append(Piece('f', ''.join(run))); return
    a, b = texts[0], texts[-1]
    pre = ''.join(run[:a]); post = ''.join(run[b + 1:])
    full = ''.join(run[a:b + 1])
    core = full.strip()
    lead = full[:len(full) - len(full.lstrip())]
    trail = full[len(full.rstrip()):]
    if pre + lead: out.append(Piece('f', pre + lead))
    out.append(Piece('u' if HAN.search(core) else 'f', core, ctx))
    if trail + post: out.append(Piece('f', trail + post))


def parse(s, para=False):
    """script text (str) -> [Piece]; identity(s) == s. para: inside <AUTOSCROLL> .. </AUTOSCROLL>, consecutive
    'text<n>' lines are one paragraph unit (prologue, pre-wrapped by hand in the original)"""
    out = []
    speaker = ''
    scroll = False
    lines = re.split(r'(\r\n|\n)', s)
    k = 0
    while k < len(lines):
        line = lines[k]; nl = lines[k + 1] if k + 1 < len(lines) else ''
        if para and scroll and PARA_LINE.match(line) and HAN.search(line):
            j, parts = k, []
            while j < len(lines):
                mm = PARA_LINE.match(lines[j])
                if not mm: break
                parts.append((mm.group(1), lines[j + 1] if j + 1 < len(lines) else ''))
                j += 2
            pc = Piece('para', ' '.join(p.strip() for p, _ in parts), 'scroll')
            pc.ctx = ('scroll', ''.join(p + '<n>' + e for p, e in parts), parts[-1][1])
            out.append(pc)
            k = j; continue
        if '<AUTOSCROLL' in line: scroll = True
        if '</AUTOSCROLL' in line: scroll = False
        run, ctx = [], speaker or '-'
        for tok in TAG.split(line):
            if tok == '': continue
            name = tagname(tok) if tok.startswith('<') else None
            if name not in SEP_TAGS:
                run.append(tok); continue
            _flush(run, out, ctx); run = []
            if name == 'NAME':
                m = re.match(r'<NAME ?(.*)>$', tok, re.S)
                arg = m.group(1)
                speaker = arg.strip()
                if HAN.search(arg):
                    out.append(Piece('f', '<NAME ')); out.append(Piece('name', arg)); out.append(Piece('f', '>'))
                else:
                    out.append(Piece('f', tok))
                ctx = speaker or '-'
            elif name in ARG_TAGS:
                m = re.match(r'<(%s) (\d+ )?(.*)>$' % name, tok, re.S) if name in ('BALLOON', 'SCENE') else \
                    re.match(r'<(%s) ()(.*)>$' % name, tok, re.S)
                if m and HAN.search(m.group(3)):
                    out.append(Piece('f', '<%s %s' % (name, m.group(2) or '')))
                    out.append(Piece('arg', m.group(3), name.lower()))
                    out.append(Piece('f', '>'))
                else:
                    out.append(Piece('f', tok))
            elif name == 'SELECT':
                out.append(Piece('f', tok)); ctx = 'select'
            else:  # <n>
                out.append(Piece('f', tok))
        _flush(run, out, ctx)
        if nl: out.append(Piece('f', nl))
        k += 2
    return out


PARA_LINE = re.compile(r'^([^<>]*\S[^<>]*)<n>$')


def units(s, para=False):
    """[(kind, ko, ctx)] in order (kind: 'u' text, 'arg' tag text, 'para' paragraph, 'name' NAME arg)"""
    r = []
    for p in parse(s, para):
        if p.kind == 'u': r.append(('u', p.text, p.ctx))
        elif p.kind == 'arg': r.append(('arg', p.text, p.ctx))
        elif p.kind == 'para': r.append(('para', p.text, 'scroll'))
        elif p.kind == 'name': r.append(('name', p.text, ''))
    return r


def identity(s, para=False):
    out = []
    for p in parse(s, para):
        out.append(p.ctx[1] if p.kind == 'para' else p.text)
    return ''.join(out)
