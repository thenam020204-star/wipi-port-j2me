#!/usr/bin/env python3
"""Chroisen (크로이센) Việt hoá: trích toàn bộ chữ Hàn -> catalog.json + out/KRO_dich.tsv

Nguồn: kro/jar (jar gốc đã bung). Định dạng (xem kro/re/formats.md):
  binary.mod   chuỗi EUC-KR trong rodata của .text (>= 0x56480) và .data; ô = tới đầu chuỗi kế tiếp
               (tối đa tới mốc 4 byte sau NUL, hoặc bước mảng char[N] cố định)            ID  B:<địa chỉ hex>
  data/*.dat   bảng u16: n x (u16 len, u16 off), off[0] = 4n; bản ghi = trường nhị phân + chuỗi
               có tiền tố độ dài u8 L (= độ dài + 1, NUL ở vị trí p+L)                      ID  T:<file>:<bản ghi>:<vị trí p>
  data/Describ%d.dat  văn bản: mục '//N' + các dòng CRLF (mã màu |NN...|)                 ID  D:<file>:<N>
  data/EV%02d.dat, QUEST1.dat, trường kịch bản của DIAL.dat: kịch bản (script.py), mỗi đoạn chữ
               giữa các mã tách (<n>, <NAME>, <SELECT>, hết dòng ...) là 1 mục              ID  S:<file>:<mục>:<thứ tự>
Giới hạn byte (bộ đệm của game, kro/re/formats.md): tên quest (QUEST0/00 p=31), tên bản đồ MD0, tên 49x <= 20;
mô tả quest <= 254; tham số thẻ (BALLOON/TYPING/SCENE) <= 39 kể cả phần số.
"""
import os, re, struct, json, glob, sys, collections
import script as SC
W = os.path.dirname(os.path.abspath(__file__))
JAR = os.path.join(W, '..', 'jar')
HAN = re.compile(rb'[\xb0-\xc8][\xa1-\xfe]')
TEXT_ADDR, TEXT_OFF, TEXT_SIZE = 0x1000, 0x34, 0x5dfd7
RODATA = 0x56480


def has_han(b):
    return HAN.search(b) is not None


def dec(b):
    return b.decode('euc-kr', 'replace')


def valid(b):
    try:
        b.decode('euc-kr')
        return True
    except UnicodeDecodeError:
        return False


def valid949(b):
    """CP949 (UHC) also: 2 item names of IT11 use the non-KS X 1001 syllable 얋 (0x9DF9)"""
    try:
        b.decode('cp949')
        return True
    except UnicodeDecodeError:
        return False


def elf_sections(d):
    shoff, = struct.unpack_from('<I', d, 0x20)
    n, = struct.unpack_from('<H', d, 0x30)
    out = []
    for i in range(n):
        name, typ, flags, addr, off, size = struct.unpack_from('<6I', d, shoff + 40 * i)
        if addr and size: out.append((addr, size, off, typ))
    return out


# Strings whose preceding byte is table data (not NUL), so the scan below skips them. Each was checked:
# exactly one literal-pool reference to the address (bằng chứng: kro/re/map.tsv "missed strings").
EXTRA_B = [0x57240, 0x572db, 0x580e2, 0x58274, 0x58314, 0x58c40, 0x5dca4, 0x5deaf, 0x5e1a4]


def bin_strings(d):
    """[(addr, slot, bytes, nrefs, inner_refs)]: Hangul strings of binary.mod"""
    secs = elf_sections(d)
    words = collections.Counter()
    for a, size, o, typ in secs:
        if typ == 8: continue
        for k in range(0, size - 3, 4): words[struct.unpack_from('<I', d, o + k)[0]] += 1
    res = []
    for addr, size, o, typ in secs:
        if typ == 8: continue
        blob = d[o:o + size]
        for m in re.finditer(rb'[\x09\x0a\x0d\x20-\x7e\xa1-\xfe]{2,}\x00', blob):
            a = addr + m.start()
            s = m.group()[:-1]
            if addr == TEXT_ADDR and a < RODATA: continue
            if not has_han(s) or not valid(s): continue
            if m.start() > 0 and blob[m.start() - 1] != 0: continue
            e = m.start() + len(s)
            while e < len(blob) and blob[e] == 0: e += 1
            end = min(addr + e, (a + len(s) + 1 + 3) & ~3)
            # fixed-size char arrays: the next string may start before the 4-byte mark
            nxt = m.end()
            while nxt < len(blob) and blob[nxt] == 0: nxt += 1
            end = max(end, min(addr + nxt, a + len(s) + 1)) if addr + nxt < end else end
            inner = [k for k in range(1, len(s)) if words.get(a + k)]
            res.append((a, end - a, s, words.get(a, 0), inner))
        for a in EXTRA_B:
            if not (addr <= a < addr + size): continue
            k = a - addr
            s = blob[k:blob.index(b'\0', k)]
            nxt = k + len(s)
            while nxt < len(blob) and blob[nxt] == 0: nxt += 1
            end = min(addr + nxt, (a + len(s) + 1 + 3) & ~3)  # never past the 4-byte mark (zero bytes may be data)
            res.append((a, end - a, s, words.get(a, 0), []))
    res.sort()
    return res


def u16_table(b):
    """records of a (u16 len, u16 off) table file, or None when b is not one"""
    if len(b) < 8: return None
    first = struct.unpack_from('<H', b, 2)[0]
    if first % 4 or first < 4 or first > len(b): return None
    n = first // 4
    recs, pos = [], first
    for i in range(n):
        ln, off = struct.unpack_from('<HH', b, 4 * i)
        if off != pos: return None
        recs.append(b[off:off + ln]); pos = off + ln
    return recs if pos == len(b) else None


def prefixed_strings(rec):
    """[(p, bytes)]: u8-length-prefixed NUL-terminated Hangul strings of a record"""
    out, p = [], 0
    while p < len(rec) - 2:
        L = rec[p]
        if L >= 2 and p + L < len(rec) and rec[p + L] == 0:
            s = rec[p + 1:p + L]
            # no control bytes: a length byte of a preceding field can also look like a prefix
            # ("\x0b스카이밸리" at p-1): skip it, the real prefix follows
            if min(s) >= 0x20 and has_han(s) and valid949(s):
                out.append((p, s)); p += L + 1; continue
        p += 1
    return out


def describ_sections(b):
    """[(N, text)]: '//N' sections, lines joined with '\\n' (CRLF in the file)"""
    t = b.split(b'\r\n')
    secs, cur, lines = [], None, []
    for line in t:
        m = re.match(rb'^//(\d+)\s*$', line)
        if m:
            if cur is not None: secs.append((cur, lines))
            cur, lines = int(m.group(1)), []
        elif line.startswith(b'//'):
            continue  # comment ('//set align' ...)
        elif cur is not None:
            lines.append(line)
    if cur is not None: secs.append((cur, lines))
    out = []
    for n, lines in secs:
        while lines and not lines[-1].strip(): lines.pop()
        out.append((n, b'\n'.join(lines)))
    return out


def u32_strings(b):
    """[(off, bytes)]: NUL terminated Hangul strings anywhere in an event script (stage 2)"""
    out = []
    for m in re.finditer(rb'[\x09\x0a\x0d\x20-\x7e\xa1-\xfe]{2,}\x00', b):
        s = m.group()[:-1]
        if has_han(s) and valid(s): out.append((m.start(), s))
    return out


def catalog():
    cat = []
    d = open(os.path.join(JAR, 'binary.mod'), 'rb').read()
    for a, slot, s, nref, inner in bin_strings(d):
        cat.append({'id': 'B:%x' % a, 'ko': dec(s), 'max': slot - 1, 'refs': nref, 'inner': inner})
    for f in sorted(glob.glob(os.path.join(JAR, 'data', '*.dat'))):
        name = os.path.basename(f)
        b = open(f, 'rb').read()
        if not has_han(b): continue
        if name.startswith('Describ'):
            for n, t in describ_sections(b):
                if has_han(t): cat.append({'id': 'D:%s:%d' % (name, n), 'ko': dec(t)})
            # section headers with a command ("//0 /set align": Describ0 info screen) are not '//N' sections:
            # their Hangul lines are translated one by one (L:file:line, line index in the CRLF split)
            cur = None
            for k, line in enumerate(b.split(b'\r\n')):
                m = re.match(rb'^//(\d+)(\s*)(.*)$', line)
                if m: cur = m.group(3).strip() != b''; continue
                if cur and has_han(line): cat.append({'id': 'L:%s:%d' % (name, k), 'ko': dec(line)})
            continue
        if name == 'DIAL.dat':
            for i, r in enumerate(u16_table(b)):
                script_units(cat, name, i, SC.dial_script(r))
            continue
        if re.match(r'EV\d\d\.dat$', name):
            for i, body in enumerate(SC.ev_read(b)):
                script_units(cat, name, i, body)
            continue
        if name == 'QUEST1.dat':
            for i, body in enumerate(SC.q1_read(b)):
                script_units(cat, name, i, body)
            continue
        recs = u16_table(b)
        if recs is not None:
            for i, r in enumerate(recs):
                for p, s in prefixed_strings(r):
                    cat.append({'id': 'T:%s:%d:%d' % (name, i, p), 'ko': s.decode('cp949'), 'max': t_limit(name, p)})
            continue
    return cat


def t_limit(name, p):
    """byte limit of an u8-prefixed string (without NUL): fixed 21-byte buffers of the game"""
    if name in ('QUEST0.dat', 'QUEST00.dat') and p == 31: return 20   # 0x4a078: title -> quest+0x22, error if L > 0x15
    if name == 'MD0.dat' or re.match(r'49\d\.dat$', name): return 20  # 0x49dc4 / 0x49edc: 21-byte copies
    return 254


def script_units(cat, name, i, body):
    s = body.decode('euc-kr')
    k = 0
    for kind, ko, ctx in SC.units(s, (name, i) in SC.PARA):
        if kind == 'name': continue
        e = {'id': 'S:%s:%d:%d' % (name, i, k), 'ko': ko, 'ctx': ctx, 'kind': kind}
        cat.append(e); k += 1


def coverage():
    """Hangul pairs per file: in catalogued strings vs in the whole file (sanity check)"""
    rep = []
    for f in sorted(glob.glob(os.path.join(JAR, 'data', '*.dat'))):
        b = open(f, 'rb').read()
        recs = u16_table(b)
        if recs is None: continue
        tot = sum(len(HAN.findall(r)) for r in recs)
        got = sum(len(HAN.findall(s)) for r in recs for p, s in prefixed_strings(r))
        if tot: rep.append((os.path.basename(f), got, tot))
    return rep


def main():
    cat = catalog()
    json.dump(cat, open(os.path.join(W, 'catalog.json'), 'w', encoding='utf-8'), ensure_ascii=False, indent=0)
    os.makedirs(os.path.join(W, 'out'), exist_ok=True)
    seen = collections.OrderedDict()
    for e in cat:
        k = e['ko']
        mx = e.get('max')
        if k not in seen: seen[k] = [e['id'], 1, mx]
        else:
            seen[k][1] += 1
            if mx is not None: seen[k][2] = mx if seen[k][2] is None else min(seen[k][2], mx)
    with open(os.path.join(W, 'out', 'KRO_dich.tsv'), 'w', encoding='utf-8') as f:
        f.write('ID\tTiếng Hàn\tTiếng Việt\tSố lần\tTối đa byte\n')
        for k, (i, n, m) in seen.items():
            f.write('%s\t%s\t\t%d\t%s\n' % (i, k.replace('\r', '\\r').replace('\n', '\\n').replace('\t', '\\t'), n, '' if m is None else m))
    print('catalog: %d mục, %d chuỗi không trùng, %d ký tự' % (len(cat), len(seen), sum(len(k) for k in seen)))
    c = collections.Counter()
    for e in cat:
        p = e['id'].split(':')
        c[p[0] + ':' + (re.sub(r'\d+', '#', p[1]) if len(p) > 2 else '')] += 1
    for k, v in sorted(c.items()): print('  ', k, v)
    for name, got, tot in coverage():
        if got != tot: print('  coverage %s: %d/%d cặp Hangul trong chuỗi' % (name, got, tot))


if __name__ == '__main__':
    main()
