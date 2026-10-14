#!/usr/bin/env python3
"""Chroisen Việt hoá: bản dịch -> overlay cho jar.

  build_vi.py [--out DIR] [tr/*.tsv ...]      (mặc định: mọi file trong kro/vi/tr/)

Bản dịch (TSV UTF-8): 'ID<TAB>Tiếng Hàn<TAB>Tiếng Việt' (dòng đầu là tiêu đề nếu bắt đầu bằng 'ID').
  - áp cho MỌI mục có cùng câu Hàn; file tên '*_id.tsv' chỉ áp đúng ID đó (ghi đè).
  - '\\n' trong ô = xuống dòng; ô Việt trống = giữ tiếng Hàn; '<rỗng>' = chuỗi rỗng.
Kết quả (overlay, cấu trúc như gốc jar): binary.mod, data/*.dat, vi/dict.txt, vi/font.bin; báo cáo build/bao-cao-build.txt

Mã hoá: mỗi từ (chuỗi chữ cái) = 2 byte (lead 0xC9.., trail 0xA1..) = dòng trong vi/dict.txt (KroText vẽ bằng
font NAM.P 12px qua hàm glyph của game). Dấu cách, số, dấu câu, mã màu |NN |cNN |, %d %s %ld, thẻ <...> giữ ASCII.
Ô cố định (chuỗi trong binary) mà câu dịch dài hơn: cả cụm chữ giữa các mã điều khiển gộp thành 1 mã.
Describ (trợ giúp): ngắt dòng sẵn theo độ rộng thật (game tự ngắt theo số byte).
"""
import sys, os, re, json, struct, collections, shutil, glob
W = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, W)
import extract as X
import script as SC

LEAD0, TRAIL0, TRAILS = 0xC9, 0xA1, 94
BASE = (0xFF - LEAD0) * TRAILS     # 5076 codes with lead 0xC9..0xFE
# more codes: leads the game's draw loop never draws (KS X 1001 symbol rows, skipped by 0x297e8); 0xA1 (full-width
# space/punctuation row) and 0xA4 (jamo, drawn by the game) are not used. Same table in KroText.XLEADS.
XLEADS = [0xA2, 0xA3] + list(range(0xA5, 0xB0))
MAXCODES = BASE + len(XLEADS) * TRAILS
NAMES = os.path.join(W, 'names.tsv')
PARA_W = 180                       # prologue (autoscroll) line width, px
FONT = os.path.join(W, 'font', 'f12.bin')
ASCII_W, SPACE_W = 6, 3            # game ASCII cell; space of a Vietnamese string (KroText.spaceW)
DESCRIB_W, DESCRIB_BYTES = 150, 27  # help box: text width, game wraps when bytes*5 >= 155-15
REPL = {'…': '...', '“': '"', '”': '"', '‘': "'", '’': "'", '–': '-', '—': '-', ' ': ' ', '　': ' ',
        '·': '.', '•': '.', '×': 'x', '★': '*', '☆': '*', '♥': '*', '♡': '*', '→': '>', '←': '<', '~': '~'}
LEADP = '(["\'['          # attached to the following word
TRAILP = '.,!?:;)]"\'~'    # attached to the preceding word (drawn with the Vietnamese font, narrow)
CTRL = re.compile(r'\|c\d\d|\|\d\d|\||%[-0-9.]*l*[sdcxXu%]|<NAME [^>]*>|<[^>]*>|[\x00-\x1f]')


def load_font(path):
    d = open(path, 'rb').read()
    n = struct.unpack('>H', d[:2])[0]
    chars = d[2:2 + n].decode('utf-8')
    p = 2 + n + 1
    widths = list(d[p:p + len(chars)])
    return dict(zip(chars, widths))


def is_word_char(c):
    return c.isalpha() and not ('가' <= c <= '힣') and ord(c) < 0x3000


def unesc(s):
    return s.replace('\\n', '\n').replace('\\t', '\t').replace('\\r', '\r')


def read_translations(paths):
    by_ko, by_id = {}, {}
    for p in paths:
        only_id = p.endswith('_id.tsv')
        for ln, line in enumerate(open(p, encoding='utf-8-sig')):
            line = line.rstrip('\n')
            if not line.strip() or line.startswith('#') or (ln == 0 and line.startswith('ID\t')): continue
            r = line.split('\t')
            if len(r) < 3: raise SystemExit('%s:%d: thiếu cột (ID, Hàn, Việt)' % (p, ln + 1))
            i, ko, vi = r[0].strip(), unesc(r[1]), unesc(r[2])
            if vi.strip() == '': continue
            if vi.strip() == '<rỗng>': vi = ''
            if only_id: by_id[i] = vi
            else: by_ko[ko] = vi
    return by_ko, by_id


class Encoder:
    def __init__(self, font):
        self.font = font
        self.dict, self.index = [], {}
        self.missing = collections.Counter()

    def clean(self, s):
        return ''.join(REPL.get(c, c) for c in s)

    def code(self, w, alias=''):
        key = (w, alias) if alias else w   # alias: same text, separate code (string compares must differ)
        k = self.index.get(key)
        if k is None:
            if len(self.dict) >= MAXCODES: raise SystemExit('dict.txt đầy (%d mã)' % MAXCODES)
            k = len(self.dict); self.dict.append(w); self.index[key] = k
        if k < BASE: return bytes([LEAD0 + k // TRAILS, TRAIL0 + k % TRAILS])
        k -= BASE
        return bytes([XLEADS[k // TRAILS], TRAIL0 + k % TRAILS])

    def raw(self, t):
        """untranslated / fixed script text: ASCII and Hangul (KS X 1001 rows 0xB0..0xC8, jamo 0xA4) as in the
        original; any other symbol would hit a Vietnamese code (lead 0xA2..0xAF) -> REPL / font word / '?'"""
        out = bytearray()
        for c in t:
            if ord(c) < 0x80: out.append(ord(c)); continue
            try: b = c.encode('euc-kr')
            except UnicodeEncodeError: b = b''
            if len(b) == 2 and (0xB0 <= b[0] <= 0xC8 or b[0] == 0xA4): out += b; continue
            r = REPL.get(c, c)
            if all(ord(x) < 0x80 for x in r): out += r.encode('ascii')
            elif c in self.font: out += self.code(c)
            else: self.missing[c] += 1; out += b'?'
        return bytes(out)

    def fontsafe(self, w):
        for ch in w:
            if ch != ' ' and ch not in self.font: self.missing[ch] += 1
        return ''.join(ch if ch == ' ' or ch in self.font else '?' for ch in w)

    def ascii(self, c):
        return self.raw(c)

    def tag(self, t):
        m = re.match(r'<NAME (.*)>$', t, re.S)
        if m: return b'<NAME ' + self.encode(m.group(1)) + b'>'
        return t.encode('euc-kr')

    def encode(self, s):
        """words -> codes, everything else ASCII"""
        s = self.clean(s)
        out = bytearray(); i = 0
        while i < len(s):
            m = CTRL.match(s, i)
            if m:
                g = m.group()
                out += self.tag(g) if g.startswith('<') else g.encode('latin-1'); i = m.end(); continue
            c = s[i]
            if is_word_char(c) or (c in LEADP and i + 1 < len(s) and is_word_char(s[i + 1])):
                # word token: optional opening bracket/quote + letters (+ hyphenated letters) + closing punctuation
                j = i + 1
                while j < len(s) and (is_word_char(s[j]) or (s[j] in "-'" and j + 1 < len(s) and is_word_char(s[j + 1]))): j += 1
                while j < len(s) and s[j] in TRAILP and not CTRL.match(s, j): j += 1
                out += self.code(self.fontsafe(s[i:j])); i = j; continue
            out += self.ascii(c); i += 1
        return bytes(out)

    def encode_compact(self, s):
        """each run of text between control codes -> one code"""
        s = self.clean(s)
        out = bytearray(); pos = 0
        for m in list(CTRL.finditer(s)) + [None]:
            seg = s[pos:m.start()] if m else s[pos:]
            core = seg.strip(' ')
            if core and any(is_word_char(c) for c in core):
                a = seg.index(core)
                out += seg[:a].encode('ascii') + self.code(self.fontsafe(core)) + seg[a + len(core):].encode('ascii')
            else:
                for c in seg: out += self.ascii(c)
            if m is None: break
            g = m.group()
            out += self.tag(g) if g.startswith('<') else g.encode('latin-1')
            pos = m.end()
        return bytes(out)

    def fit(self, s, limit):
        b = self.encode(s)
        if limit is None or len(b) <= limit: return b, False
        return self.encode_compact(s), True

    def width(self, s):
        """drawn width of a translated (not yet encoded) line, as KroText/the game will draw it"""
        s = self.clean(s); w = 0; i = 0
        viet = any(is_word_char(c) for c in s)
        while i < len(s):
            m = CTRL.match(s, i)
            if m:
                g = m.group()
                if g.startswith('|c'): w += ASCII_W  # measure quirk: second digit counted once
                i = m.end(); continue
            c = s[i]
            if is_word_char(c) or (c in LEADP + TRAILP and c in self.font and viet): w += self.font.get(c, self.font.get('?', 6))
            elif c == ' ': w += SPACE_W if viet else ASCII_W
            else: w += ASCII_W if ord(c) < 0x80 else 10
            i += 1
        return w


def check_codes(ko, vi):
    issues = []
    f = lambda t: re.findall(r'%[-0-9.]*l*[sdcxXu]', t)
    if f(ko) != f(vi): issues.append('thứ tự/số %%d %%s khác gốc %s -> %s' % (f(ko), f(vi)))
    t = lambda x: [g for g in re.findall(r'<[^>]*>', x) if not g.startswith('<NAME')]
    if t(ko) != t(vi): issues.append('thẻ <...> khác gốc')
    if ko.count('|') != vi.count('|'): issues.append('số dấu | (mã màu) khác gốc (%d -> %d)' % (ko.count('|'), vi.count('|')))
    return issues


def wrap(enc, text, width, maxbytes):
    """greedy word wrap of one source line: keeps its leading spaces as indent of every piece"""
    ind = len(text) - len(text.lstrip(' '))
    indent, words = text[:ind], text[ind:].split(' ')
    lines, cur = [], ''
    for w in words:
        if w == '': continue
        cand = (cur + ' ' + w) if cur else w
        if cur and (enc.width(indent + cand) > width or len(enc.encode(indent + cand)) > maxbytes):
            lines.append(indent + cur); cur = w
        else: cur = cand
    lines.append(indent + cur)
    return lines


def load_names():
    """names.tsv: 'Hàn<TAB>Việt[<TAB>riêng]' for <NAME> arguments; 'riêng' = own codes (a misspelt name of the original
    does not match the portrait table, keep it that way)"""
    r = {}
    if os.path.exists(NAMES):
        for l in open(NAMES, encoding='utf-8'):
            c = l.rstrip('\n').split('\t')
            if len(c) >= 2 and c[0] and not c[0].startswith('#'):
                r[c[0]] = (c[1], c[2] if len(c) > 2 and c[2] else '')
    return r


def main():
    args = sys.argv[1:]
    out = os.path.join(W, 'build', 'overlay')
    if '--out' in args:
        i = args.index('--out'); out = args[i + 1]; del args[i:i + 2]
    paths = args or sorted(glob.glob(os.path.join(W, 'tr', '*.tsv')))
    by_ko, by_id = read_translations(paths)
    cat = json.load(open(os.path.join(W, 'catalog.json'), encoding='utf-8'))
    enc = Encoder(load_font(FONT))
    report, stats = [], collections.Counter()

    def tr(e):
        vi = by_id.get(e['id'], by_ko.get(e['ko']))
        if vi is None: stats['chưa dịch'] += 1; return None
        stats['đã dịch'] += 1
        for p in check_codes(e['ko'], vi): report.append('%s: %s | %s' % (e['id'], p, vi))
        return vi

    def put(e, limit):
        vi = tr(e)
        if vi is None: return None
        b, compact = enc.fit(vi, limit)
        if compact: stats['gộp cụm'] += 1
        if limit is not None and len(b) > limit:
            report.append('%s: QUÁ DÀI (%d > %d byte) | %s' % (e['id'], len(b), limit, vi)); stats['quá dài'] += 1; return None
        return b

    if os.path.exists(out): shutil.rmtree(out)
    os.makedirs(os.path.join(out, 'data')); os.makedirs(os.path.join(out, 'vi'))
    byfile = collections.defaultdict(list)
    for e in cat:
        p = e['id'].split(':')
        byfile[(p[0], p[1] if p[0] != 'B' else '')].append(e)

    # u16 table files: rebuild records (strings change length -> offsets recomputed)
    for (kind, name), es in sorted(byfile.items()):
        if kind != 'T': continue
        path = os.path.join(X.JAR, 'data', name)
        recs = [bytearray(r) for r in X.u16_table(open(path, 'rb').read())]
        changed = False
        for e in sorted(es, key=lambda e: (-int(e['id'].split(':')[2]), -int(e['id'].split(':')[3]))):
            b = put(e, e.get('max', 254))
            if b is None: continue
            _, _, ri, p = e['id'].split(':'); ri, p = int(ri), int(p)
            r = recs[ri]
            L = r[p]
            assert r[p + L] == 0 and X.dec(bytes(r[p + 1:p + L])) == e['ko'], e['id']
            recs[ri] = r[:p] + bytes([len(b) + 1]) + b + b'\0' + r[p + L + 1:]
            changed = True
        if changed:
            n = len(recs); hdr = bytearray(); pos = 4 * n
            for r in recs:
                hdr += struct.pack('<HH', len(r), pos); pos += len(r)
            if pos > 0xFFFF: raise SystemExit('%s: quá 64 KB sau khi dịch (%d)' % (name, pos))
            open(os.path.join(out, 'data', name), 'wb').write(bytes(hdr) + b''.join(recs))

    # Describ%d.dat: rebuild the translated sections (pre-wrapped), other lines as in the original
    for (kind, name), es in sorted(byfile.items()):
        if kind != 'D': continue
        orig = open(os.path.join(X.JAR, 'data', name), 'rb').read()
        tmap = {}
        for e in es:
            vi = tr(e)
            if vi is None: continue
            lines = []
            for src in vi.split('\n'):
                lines += wrap(enc, src, DESCRIB_W, DESCRIB_BYTES) if src.strip() else ['']
            tmap[int(e['id'].split(':')[2])] = [enc.encode(l) for l in lines]
        if not tmap: continue
        res, cur, skip = [], None, False
        for line in orig.split(b'\r\n'):
            m = re.match(rb'^//(\d+)\s*$', line)
            if m:
                cur = int(m.group(1)); skip = cur in tmap
                res.append(line)
                if skip: res += tmap[cur] + [b'']
                continue
            if skip and not line.startswith(b'//'): continue
            res.append(line)
        open(os.path.join(out, 'data', name), 'wb').write(b'\r\n'.join(res))

    # event scripts: EV##.dat, QUEST1.dat, script field of DIAL.dat (script.py units, names.tsv for <NAME>)
    names = load_names()
    errors = []

    def name_bytes(ko):
        v = names.get(ko.strip())
        if v is None:
            report.append('NAME chưa có trong names.tsv: %s' % ko); stats['tên chưa dịch'] += 1
            return enc.raw(ko)
        vi, alias = v
        return b' '.join(enc.code(enc.fontsafe(w), alias) for w in vi.split(' ')) if alias else enc.encode(vi)

    def script(name, i, body):
        s = body.decode('euc-kr')
        para = (name, i) in SC.PARA
        out = bytearray(); k = 0
        for pc in SC.parse(s, para):
            if pc.kind == 'f': out += enc.raw(pc.text); continue
            if pc.kind == 'name': out += name_bytes(pc.text); continue
            e = {'id': 'S:%s:%d:%d' % (name, i, k), 'ko': pc.text}; k += 1
            vi = tr(e)
            if pc.kind == 'para':
                if vi is None: out += enc.raw(pc.ctx[1]); continue
                eol = pc.ctx[2] or '\r\n'
                lines = wrap(enc, ' '.join(vi.split()), PARA_W, 100)
                out += b''.join(enc.encode(l) + b'<n>' + eol.encode('ascii') for l in lines)
                continue
            b = enc.raw(pc.text) if vi is None else enc.encode(vi)
            if pc.kind == 'arg':
                pre = out[out.rindex(b'<'):]
                if len(pre) - len(pre.split(b' ')[0]) - 1 + len(b) > SC.TAG_ARG_MAX:
                    errors.append('%s: tham số thẻ quá dài (%d > %d byte) | %s' % (
                        e['id'], len(pre) - len(pre.split(b' ')[0]) - 1 + len(b), SC.TAG_ARG_MAX, vi))
            out += b
        if len(out) > SC.ENTRY_MAX: errors.append('%s:%d: kịch bản quá 64 KB (%d)' % (name, i, len(out)))
        return bytes(out)

    for f in sorted(glob.glob(os.path.join(X.JAR, 'data', 'EV*.dat'))):
        name = os.path.basename(f); orig = open(f, 'rb').read()
        new = SC.ev_write(orig, [script(name, i, b) for i, b in enumerate(SC.ev_read(orig))])
        if new != orig: open(os.path.join(out, 'data', name), 'wb').write(new)
    orig = open(os.path.join(X.JAR, 'data', 'QUEST1.dat'), 'rb').read()
    new = SC.q1_write(orig, [script('QUEST1.dat', i, b) for i, b in enumerate(SC.q1_read(orig))])
    if new != orig: open(os.path.join(out, 'data', 'QUEST1.dat'), 'wb').write(new)
    orig = open(os.path.join(X.JAR, 'data', 'DIAL.dat'), 'rb').read()
    recs = [SC.dial_rebuild(r, script('DIAL.dat', i, SC.dial_script(r))) for i, r in enumerate(X.u16_table(orig))]
    hdr = bytearray(); pos = 4 * len(recs)
    for r in recs:
        hdr += struct.pack('<HH', len(r), pos); pos += len(r)
    if pos > 0xFFFF: errors.append('DIAL.dat: quá 64 KB sau khi dịch (%d)' % pos)
    new = bytes(hdr) + b''.join(recs)
    if new != orig: open(os.path.join(out, 'data', 'DIAL.dat'), 'wb').write(new)

    # binary.mod: in place, slot = bytes to the next string; shared tails stay at their address
    d = bytearray(open(os.path.join(X.JAR, 'binary.mod'), 'rb').read())
    secs = X.elf_sections(bytes(d))

    def off(a):
        for sa, size, so, typ in secs:
            if sa <= a < sa + size: return so + a - sa
        raise KeyError(hex(a))
    ptr_moves = []
    for e in byfile[('B', '')]:
        b = put(e, e['max'])
        if b is None: continue
        a = int(e['id'][2:], 16); o = off(a)
        orig = bytes(d[o:d.index(b'\0', o)])
        moved, keep = [], False
        for k in e.get('inner', []):
            tid = '%s+%d' % (e['id'], k)
            tv = by_id.get(tid)
            if tv is not None:
                # a string that ends where another one starts (shared tail): the tail gets its own copy after the
                # NUL of the translated string and every pointer to it is redirected (literal pools, tables)
                tb, _ = enc.fit(tv, None)
                pos = len(b) + 1
                if pos + len(tb) + 1 > e['max'] + 1:
                    tb, _ = enc.fit(tv, None); tb = enc.encode_compact(tv)
                if pos + len(tb) + 1 > e['max'] + 1:
                    report.append('%s: ĐUÔI %s không đủ chỗ (%d > %d byte) | %s' % (e['id'], tid, pos + len(tb) + 1, e['max'] + 1, tv)); keep = True; break
                moved.append((a + k, a + pos, tb)); continue
            suf = orig[k:]
            ok = False
            for cand in (b, enc.encode_compact(tr(e))):
                if cand.endswith(suf) and len(cand) - len(suf) <= k:
                    b = cand[:len(cand) - len(suf)] + b' ' * (k - (len(cand) - len(suf))) + suf; ok = True; break
            if not ok:
                report.append('%s: ĐUÔI DÙNG CHUNG ở +%d (%r) không giữ được -> giữ gốc' % (e['id'], k, suf)); keep = True; break
        if keep: continue
        buf = bytearray(b + b'\0')
        for old_a, new_a, tb in moved:
            buf += tb + b'\0'
            ptr_moves.append((old_a, new_a))
        b = bytes(buf[:-1]) if not moved else bytes(buf[:-1])
        d[o:o + e['max'] + 1] = b + b'\0' * (e['max'] + 1 - len(b))
    # redirect pointers of moved shared tails (aligned words equal to the old address, in every loaded section)
    for old_a, new_a in ptr_moves:
        n = 0
        for sa, size, so, typ in secs:
            if typ == 8: continue
            for q in range(so, so + size - 3, 4):
                if struct.unpack_from('<I', d, q)[0] == old_a:
                    struct.pack_into('<I', d, q, new_a); n += 1
        stats['đuôi chuyển'] += 1
        if n == 0: report.append('đuôi %x: không thấy con trỏ' % old_a)
    # code patches: patches.txt 'addr old_hex new_hex  # ghi chú'
    pp = os.path.join(W, '..', 're', 'patches.txt')
    if os.path.exists(pp):
        for l in open(pp, encoding='utf-8'):
            l = l.split('#')[0].split()
            if not l: continue
            a, old, new = int(l[0], 16), bytes.fromhex(l[1]), bytes.fromhex(l[2])
            o = off(a)
            if d[o:o + len(old)] != old: raise SystemExit('patch %x: không khớp mã gốc (%s)' % (a, d[o:o + len(old)].hex()))
            d[o:o + len(new)] = new
            stats['vá mã'] += 1
    open(os.path.join(out, 'binary.mod'), 'wb').write(d)

    # portrait lookup (0x232b0): strcmp(<NAME> argument, name table 0x1400050 + 9*i, i = 0..8)
    for i in range(9):
        o = off(0x1400050 + 9 * i)
        tb = bytes(d[o:d.index(b'\0', o)])
        ko = X.dec(open(os.path.join(X.JAR, 'binary.mod'), 'rb').read()[o:o + 9].split(b'\0')[0])
        if ko in names and name_bytes(ko) != tb:
            errors.append('bảng tên 0x%x (%s): mã khác <NAME> (%s / %s)' % (0x1400050 + 9 * i, ko, tb.hex(), name_bytes(ko).hex()))
    # T fields: hard byte limits (fixed buffers)
    for e in cat:
        if e['id'].startswith('T:') and e.get('max', 254) < 254:
            vi = by_id.get(e['id'], by_ko.get(e['ko']))
            if vi is not None and len(enc.fit(vi, None)[0]) > e['max'] and len(enc.encode_compact(vi)) > e['max']:
                errors.append('%s: quá %d byte | %s' % (e['id'], e['max'], vi))
        elif re.match(r'T:QUEST0?0?\.dat:', e['id']):  # quest description: wrapped text, no phrase codes
            vi = by_id.get(e['id'], by_ko.get(e['ko']))
            if vi is not None and len(enc.encode(vi)) > 254:
                errors.append('%s: mô tả quest quá 254 byte (%d) | %s' % (e['id'], len(enc.encode(vi)), vi))
    if errors:
        print('\n'.join(errors[:60]))
        raise SystemExit('LỖI: %d chuỗi vượt giới hạn bộ đệm của game' % len(errors))
    with open(os.path.join(out, 'vi', 'dict.txt'), 'w', encoding='utf-8', newline='\n') as f:
        f.write('\n'.join(enc.dict) + '\n')
    shutil.copy(FONT, os.path.join(out, 'vi', 'font.bin'))
    os.makedirs(os.path.join(W, 'build'), exist_ok=True)
    with open(os.path.join(W, 'build', 'bao-cao-build.txt'), 'w', encoding='utf-8') as f:
        f.write('Thống kê: %s\nMã từ điển: %d / %d\n' % (dict(stats), len(enc.dict), MAXCODES))
        if enc.missing: f.write('Ký tự font thiếu: %s\n' % dict(enc.missing))
        f.write('\n'.join(report) + '\n')
    print('overlay:', out, dict(stats), 'dict', len(enc.dict), 'cảnh báo', len(report))
    if enc.missing: print('font thiếu:', dict(enc.missing))


if __name__ == '__main__':
    main()
