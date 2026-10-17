#!/usr/bin/env python3
"""Stage the original resources that the J2ME build packs into the JAR (local only: work/ is gitignored, originals are never committed).
Excluded: SND (MMF/OGG - audio is converted in Phase F), TOUCH/CIMG/S (touch-variant keypad art), kickbert (unused by the key path - UNKNOWN).
TOUCH/CIMG/NS is PACKED: the in-game HUD panel images (_newPadTouch, Hud.drawSingle).
TOUCH/CIF, TOUCH/EXD, TOUCH/IMG are PACKED: they are the dialog/menu skins of the AND2011 UI (docs/phaseE.md section 1).
Usage: python3 tools/j2me_stage_res.py [res dir] [out dir]"""
import os
import shutil
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
res = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, 'work', 'res')
out = sys.argv[2] if len(sys.argv) > 2 else os.path.join(ROOT, 'work', 'resjar_full')
EXCLUDE = {'SND', 'kickbert'}
shutil.rmtree(out, ignore_errors=True)
n = size = 0
for d in sorted(os.listdir(res)):
    if d in EXCLUDE:
        continue
    src = os.path.join(res, d)
    if os.path.isdir(src):
        shutil.copytree(src, os.path.join(out, d))
    else:
        os.makedirs(out, exist_ok=True)
        shutil.copy(src, out)
os.remove(os.path.join(out, 'TOUCH', 'CIMG', 'S'))
for r, _, fs in os.walk(out):
    for f in fs:
        n += 1
        size += os.path.getsize(os.path.join(r, f))
print('staged', n, 'files', size, 'bytes ->', out)
