#!/usr/bin/env python3
"""Extract every text string of the game data into plain UTF-8 .txt files.

usage: textx.py <resource_root> <out_dir> <report.txt> [vectors.txt]

Writes <out_dir>/text/*.txt (one "key=text" line per string, editable inside
the jar) and <out_dir>/text/index.bin, the run-time map used by
wipi.host.Enc.txt():

  The game turns data bytes into Strings at a few call sites (new String(byte[]),
  remapped to Enc.txt). Enc.txt hashes the original bytes (FNV-1a 32), finds
  the entry here, and builds the String from the .txt line(s) instead.
  Composite strings (popup menus, skills, quest/explanation prefixes,
  animation scripts) keep their structural characters; only their text fields
  come from the .txt, and the char-count digits in front of a field are
  recomputed from the edited text.

Each parser below mirrors the bytecode of the loader named in its comment
(offsets, length fields, prefix widths), so the hashed byte range is exactly
the one the game passes to new String(...).

index.bin (big endian):
  u16 nFiles, nFiles x (u8 len, ascii name)            text file names
  u16 nEntries, nEntries x (u32 hash, u8 file, u16 key, u16 layout)  sorted by hash
  layouts: u16 nLayouts, then per layout u8 nFields, nFields x (u16 start, u16 len, u8 width)
  layout 0 = whole string is one field (key line "N"); otherwise field f of
  entry N is line "N.f". start/len count chars of the decoded original string;
  width = number of decimal digits right before the field holding its length.
"""
import os, re, struct, sys

root, outdir, report = sys.argv[1], sys.argv[2], sys.argv[3]
vec = open(sys.argv[4], 'w') if len(sys.argv) > 4 else None  # test vectors for TextCheck


def i32(d, o):
    return struct.unpack_from('<i', d, o)[0]


def fnv(b):
    h = 0x811c9dc5
    for x in b:
        h ^= x
        h = (h * 16777619) & 0xffffffff
    return h


def jdecode(b):
    """Same char segmentation as wipi.host.Enc.str (one char per ASCII byte or
    per CP949 double-byte code, '?' for a stray byte)."""
    out = []
    i = 0
    while i < len(b):
        c = b[i]
        if c < 0x80:
            out.append(chr(c))
            i += 1
        elif 0x81 <= c <= 0xfe and i + 1 < len(b) and (0x41 <= b[i + 1] <= 0x5a or 0x61 <= b[i + 1] <= 0x7a or 0x81 <= b[i + 1] <= 0xfe):
            try:
                out.append(bytes(b[i:i + 2]).decode('cp949'))
            except UnicodeDecodeError:
                out.append('?')
            i += 2
        else:
            out.append('?')
            i += 1
    return ''.join(out)


def rd(p):
    return open(os.path.join(root, p), 'rb').read()


def table(p):
    """count, then (offset,len) pairs: Explan/QExplan/Quest/Popup/Item/Skill/PetSkill/Animation."""
    d = rd(p)
    n = i32(d, 0)
    return [d[i32(d, 4 + i * 8):i32(d, 4 + i * 8) + i32(d, 8 + i * 8)] for i in range(n)]


def bundle(base):
    """ImageData.GetSize + NPC.LoadData: base.dat split by baseSize.dat."""
    s = rd(base + 'Size.dat')
    n = i32(s, 0)
    d = rd(base + '.dat')
    out, o = [], 0
    for k in range(n):
        z = i32(s, 4 + 4 * k)
        out.append(d[o:o + z])
        o += z
    return out


def talk_entries(buf):
    """NPC.LoadTalk: 8-byte (off,len) table; entry = TalkImg u8, int n5, text[n5], script."""
    if len(buf) < 8:
        return []
    n = i32(buf, 0) // 8
    res = []
    for k in range(n):
        n3, n4 = i32(buf, k * 8), i32(buf, k * 8 + 4)
        st = k * 8 + (n3 - (k + 1) * 8) + 8
        e = buf[st:st + n4]
        n5 = i32(e, 1)
        res.append((e[5:5 + n5], e[5 + n5:n4] if 5 + n5 < n4 else None))
    return res


# ---------------------------------------------------------------- layouts
WHOLE = ()


def lay_prefix2(s):
    # MapDat.LoadQuest (QTitle = substring(2)); GameCanvas: QExplan.substring(0,2) / substring(2)
    return ((2, len(s) - 2, 0),)


def lay_popup(s):
    # MapDat.LoadPopup: maxpop=s[0]; maxpop==0 -> len2 s[1:3], text, action2;
    # else maxpop x (len1, text, action2)
    m = int(s[0])
    if m == 0:
        n = int(s[1:3])
        f = ((3, n, 2),)
        end = 5 + n
    else:
        f, p = [], 1
        for _ in range(m):
            n = int(s[p])
            f.append((p + 1, n, 1))
            p += 1 + n + 2
        f, end = tuple(f), p
    if end != len(s):
        raise ValueError('popup grammar does not consume %r' % s)
    return f


def lay_skill(s):
    # MapDat.LoadSkill: n=s[0]; name=s[1:1+n]; m=s[1+n:3+n]; explan=s[3+n:3+n+m]
    n = int(s[0])
    m = int(s[1 + n:3 + n])
    if 3 + n + m != len(s):
        raise ValueError('skill grammar does not consume %r' % s)
    return ((1, n, 1), (3 + n, m, 2))


def lay_anim(s):
    # NPC.AScript 'D': two digits = char count of the caption that follows
    f = []
    for m in re.finditer(r'D(\d\d)', s):
        n = int(m.group(1))
        st = m.end()
        if any(ord(c) > 0x7f for c in s[st:st + n]):
            if f and st < f[-1][0] + f[-1][1]:
                raise ValueError('overlapping caption in %r' % s)
            f.append((st, n, 2))
    return tuple(f)


# ---------------------------------------------------------------- sources
files = []        # [name, [(key, text or None(comment), comment)]]
entries = {}      # hash -> (fileNo, key, layout, original bytes)
layouts = [WHOLE]
lay_ix = {WHOLE: 0}
rep = open(report, 'w', encoding='utf-8')
nonkey = set()    # strings produced by the same call sites that are not text (scripts, tile names)
stats = {'strings': 0, 'dups': 0}


def lay_id(l):
    if l not in lay_ix:
        lay_ix[l] = len(layouts)
        layouts.append(l)
    return lay_ix[l]


def add(fname, items, title):
    """items: (key, bytes, layout_fn or None, comment)."""
    fno = len(files)
    lines = ['# ' + title,
             '# key=text   (one line per string; \\n = new line; /C0../C4 colour, /Nxx name, /Pxx popup codes must stay)']
    for key, b, lf, comment in items:
        if comment:
            lines.append('# ' + comment)
        s = jdecode(b)
        lay = lf(s) if lf else WHOLE
        cov = set()
        for st, n, w in (lay if lay else ((0, len(s), 0),)):
            if st < 0 or st + n > len(s) or (w and not s[st - w:st].isdigit()):
                raise ValueError('bad field %r in %s:%s %r' % ((st, n, w), fname, key, s))
            cov.update(range(st, st + n))
        for i, c in enumerate(s):
            if ord(c) > 0x7f and i not in cov:
                raise ValueError('text outside fields in %s:%s %r' % (fname, key, s))
        h = fnv(b)
        if vec:
            vec.write(b.hex() + '\t' + s.encode('utf-8').hex() + '\n')
        stats['strings'] += 1
        if h in entries:
            pf, pk, pl, pb = entries[h]
            if pb != b:
                raise ValueError('hash collision %08x %r %r' % (h, pb, b))
            if pl != lay:
                raise ValueError('same string, two layouts: %r' % s)
            stats['dups'] += 1
            lines.append('#%s = (same text as %s, key %s)' % (key, (files[pf][0] if pf < len(files) else fname), pk))
            continue
        entries[h] = (fno, key, lay, b)
        if not lay:
            lines.append('%d=%s' % (key, esc(s)))
        else:
            for f, (st, n, w) in enumerate(lay):
                lines.append('%d.%d=%s' % (key, f, esc(s[st:st + n])))
        rep.write('%s:%d %08x %s\n' % (fname, key, h, s))
    files.append((fname, lines))


def esc(s):
    return s.replace('\\', '\\\\').replace('\n', '\\n').replace('\r', '\\r')


# GameCanvas.LoadExplanDat ('@' = line break of the help page)
add('explan.txt', [(i, b, None, None) for i, b in enumerate(table('Data/Explan.dat'))],
    'Data/Explan.dat - help pages ("[title]@line@line...", @ = new line)')
# MapDat.LoadQuest
add('quest.txt', [(i, b, lay_prefix2, None) for i, b in enumerate(table('Data/Quest.dat'))],
    'Data/Quest.dat - quest titles')
add('qexplan.txt', [(i, b, lay_prefix2, None) for i, b in enumerate(table('Data/QExplan.dat'))],
    'Data/QExplan.dat - quest hints')
# MapDat.LoadItemName
add('item.txt', [(i, b, None, None) for i, b in enumerate(table('Data/Item.dat'))],
    'Data/Item.dat - quest item names')
# MapDat.LoadSkill
add('skill.txt', [(i, b, lay_skill, None) for i, b in enumerate(table('Data/Skill.dat'))],
    'Data/Skill.dat - skills: N.0 = name, N.1 = description')
# Pet.LoadSkillExplan
add('petskill.txt', [(i, b, None, None) for i, b in enumerate(table('Data/PetSkill.dat'))],
    'Data/PetSkill.dat - pet skill descriptions')
# MapDat.LoadPopup
for p, nm in (('Data/Popup.dat', 'popup.txt'), ('Data/Popup1.dat', 'popup1.txt')):
    add(nm, [(i, b, lay_popup, None) for i, b in enumerate(table(p))],
        p + ' - choice popups: N.0, N.1.. = the choices (or the message)')
# MapDat.LoadCropData: 35-byte records, name length at +17, name at +18
d = rd('Data/cropdat.dat')
add('crop.txt', [(i, d[4 + i * 35 + 18:4 + i * 35 + 18 + d[4 + i * 35 + 17]], None, None)
                 for i in range(i32(d, 0))], 'Data/cropdat.dat - crop names')
# NPC.NPCSetup: buffer from NPC.GetNPCBuff (Data/NPC/b chunk i): name = buf[1 : 1+buf[0]]
items = []
for b in range(4):
    for i, buf in enumerate(bundle('Data/NPC/%d' % b)):
        items.append((b * 100 + i, buf[1:1 + buf[0]], None, 'Data/NPC/%d.dat #%d' % (b, i)))
add('npc_name.txt', items, 'NPC names (key = bundle*100 + index)')
# Pet.CheckVersion: u32 version, u8 n, name[n]
items = []
for i in range(25):
    d = rd('Data/Pet/%d.pet' % i)
    items.append((i, d[5:5 + d[4]], None, None))
add('pet_name.txt', items, 'Data/Pet/N.pet - pet names')
# MapDat.LoadMap: map name at the end of the .fmd
items = []
for f in sorted(os.listdir(os.path.join(root, 'Map')), key=lambda x: int(x.split('.')[0])):
    d = rd('Map/' + f)
    p = 1200
    for _ in range(2):
        n = d[p]
        nonkey.add(d[p + 1:p + 1 + n])
        p += 1 + 10
    p += 1  # Kind = byArray[++n6]
    p += 1
    k = 0
    while k < 100:
        if d[p] == 2:
            p += 100 - k
            k = 110
        else:
            p += 1
        k += 1
    k = 0
    while k < 150:
        if d[p] == 2:
            p += 150 - k
            k = 150
        else:
            p += 1
        k += 1
    ns = d[p]
    p += 1
    for _ in range(ns):
        p += 2
        sc = d[p:p + 10]
        z = sc.find(b'\0')
        nonkey.add(sc[:z] if z >= 0 else sc)
        p += 10
    name = d[p + 1:p + 1 + d[p]]
    items.append((int(f.split('.')[0]), name, None, None))
add('map_name.txt', items, 'Map/N.fmd - map names')
# NPC.LoadAnimation
items = []
for i, b in enumerate(table('Data/Animation.dat')):
    s = jdecode(b)
    if lay_anim(s):
        items.append((i, b, lay_anim, None))
    else:
        nonkey.add(b)
add('anim.txt', items, 'Data/Animation.dat - captions of the cut-scenes (N.0, N.1.. = captions of script N)')
# NPC.LoadTalk: n=0 T0.tlk; 1-10 Talk/0; 11-25 Talk/1; 26-45 Talk/2; 46-65 (and 78+) Talk/3
talks = [('talk_T0.txt', 'Data/Talk/T0.tlk (talk 0)', rd('Data/Talk/T0.tlk'))]
first = {0: 1, 1: 11, 2: 26, 3: 46}
for b in range(4):
    for k, buf in enumerate(bundle('Data/Talk/%d' % b)):
        talks.append(('talk_%d_%02d.txt' % (b, k), 'Data/Talk/%d.dat part %d (talk %d)' % (b, k, first[b] + k), buf))
for nm, title, buf in talks:
    items = []
    for i, (t, sc) in enumerate(talk_entries(buf)):
        items.append((i, t, None, None))
        if sc:
            nonkey.add(sc)
    add(nm, items, title + ' - dialogue lines')

# no non-text string of the same call sites may hit the index
for b in nonkey:
    if vec:
        vec.write(b.hex() + '\n')
    if fnv(b) in entries and entries[fnv(b)][3] != b:
        raise ValueError('non-text string collides with a text hash: %r' % b)
    if fnv(b) in entries:
        raise ValueError('non-text string equals a text entry: %r' % b)

os.makedirs(os.path.join(outdir, 'text'), exist_ok=True)
for nm, lines in files:
    with open(os.path.join(outdir, 'text', nm), 'w', encoding='utf-8', newline='\n') as o:
        o.write('\n'.join(lines) + '\n')
with open(os.path.join(outdir, 'text', 'index.bin'), 'wb') as o:
    o.write(struct.pack('>H', len(files)))
    for nm, _ in files:
        o.write(struct.pack('>B', len(nm)) + nm.encode('ascii'))
    hs = sorted(entries)
    o.write(struct.pack('>H', len(hs)))
    for h in hs:
        fno, key, lay, _ = entries[h]
        o.write(struct.pack('>IBHH', h, fno, key, lay_id(lay)))
    o.write(struct.pack('>H', len(layouts)))
    for l in layouts:
        o.write(struct.pack('>B', len(l)))
        for st, n, w in l:
            o.write(struct.pack('>HHB', st, n, w))
size = os.path.getsize(os.path.join(outdir, 'text', 'index.bin'))
msg = 'textx: %d strings (%d unique, %d duplicates) in %d files, %d layouts, index %d bytes, %d non-text checked' % (
    stats['strings'], len(entries), stats['dups'], len(files), len(layouts), size, len(nonkey))
rep.write(msg + '\n')
rep.close()
if vec:
    vec.close()
print(msg)
