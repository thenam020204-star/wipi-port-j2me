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

LEAD0, TRAIL0, TRAILS = 0xC9, 0xA1, 94
MAXCODES = (0xFF - LEAD0) * TRAILS
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

    def code(self, w):
        k = self.index.get(w)
        if k is None:
            if len(self.dict) >= MAXCODES: raise SystemExit('dict.txt đầy (%d mã)' % MAXCODES)
            k = len(self.dict); self.dict.append(w); self.index[w] = k
        return bytes([LEAD0 + k // TRAILS, TRAIL0 + k % TRAILS])

    def fontsafe(self, w):
        for ch in w:
            if ch != ' ' and ch not in self.font: self.missing[ch] += 1
        return ''.join(ch if ch == ' ' or ch in self.font else '?' for ch in w)

    def ascii(self, c):
        if ord(c) < 0x80: return bytes([ord(c)])
        try: return c.encode('euc-kr')
        except UnicodeEncodeError: self.missing[c] += 1; return b'?'

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
            b = put(e, 254)
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
