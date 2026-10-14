#!/usr/bin/env python3
"""Applies a translation (i18n/<lang>/*.txt) to the generated text files.

  i18n.py <res/text dir> <i18n/lang dir> <report>

- data files (talk_*, crop, quest...): lines "key=translation", same keys as the
  generated file; a key missing from the generated file is reported.
- ui.txt: lines "original<TAB>translation" (ids of ui.txt follow the literal order
  of the code and may move; the original text does not).
Checks: control codes (/C0../C4, /Nxx, /Pxx) kept; fields with a 1-digit length
prefix (original <= 9 chars in popup/popup1/skill names) stay <= 9 chars.
Translations are NFC-normalised (the bitmap font has precomposed glyphs).
"""
import os, re, sys, unicodedata

src, tr, rep = sys.argv[1:4]
CODES = re.compile(r'/[CNPZSY][0-9A-Za-z]{0,2}')
SHORT = {'popup.txt', 'popup1.txt'}
out, errs = [], []


def esc(v):
    return v.replace('\\', '\\\\').replace('\n', '\\n')


def codes(s):
    return sorted(m.group(0)[:2] for m in CODES.finditer(s))


for name in sorted(os.listdir(tr)):
    p = os.path.join(src, name)
    if not name.endswith('.txt') or not os.path.exists(p):
        errs.append('%s: no generated file' % name)
        continue
    lines = open(p, encoding='utf-8').read().split('\n')
    kv = {}
    for i, l in enumerate(lines):
        if l and not l.startswith('#') and '=' in l:
            k, v = l.split('=', 1)
            kv.setdefault(k, []).append(i)
    tmap = {}
    for l in open(os.path.join(tr, name), encoding='utf-8').read().split('\n'):
        if not l or l.startswith('#'):
            continue
        l = unicodedata.normalize('NFC', l)
        if name == 'ui.txt':
            o, t = l.split('\t', 1)
            tmap[o] = t
        else:
            k, t = l.split('=', 1)
            tmap[k] = t
    done = 0
    used = set()
    for k, idxs in kv.items():
        for i in idxs:
            orig = lines[i].split('=', 1)[1]
            key = orig if name == 'ui.txt' else k
            if key not in tmap:
                continue
            t = tmap[key]
            used.add(key)
            if codes(orig) != codes(t):
                errs.append('%s %s: control codes differ: %r -> %r' % (name, k, orig, t))
                continue
            short = (name in SHORT or (name == 'skill.txt' and k.endswith('.0')))
            if short and len(orig) <= 9 and len(t) > 9:
                errs.append('%s %s: > 9 chars: %r' % (name, k, t))
                continue
            lines[i] = k + '=' + t
            done += 1
    for k in tmap:
        if k not in used:
            errs.append('%s: not in generated file: %r' % (name, k))
    open(p, 'w', encoding='utf-8').write('\n'.join(lines))
    out.append('%s: %d translated' % (name, done))

with open(rep, 'w', encoding='utf-8') as f:
    f.write('\n'.join(out + ['ERROR ' + e for e in errs]) + '\n')
print('i18n: %s; %d errors' % (', '.join(out), len(errs)))
for e in errs[:20]:
    print('  ' + e)
sys.exit(1 if errs else 0)
