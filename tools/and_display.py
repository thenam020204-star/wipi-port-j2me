#!/usr/bin/env python3
"""Evidence dump for the AND2011 display/viewport logic -> docs/phaseB_display_dump.txt
Usage: python3 tools/and_display.py <binary.mod> <out.txt>"""
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from andelf import AndElf
e = AndElf(sys.argv[1])
FUNCS = ['DM_GET_FRAME_BUFFER_INFO', 'DM_Initialize', 'CUSTOM_grpGetDisplayInfo', 'MainFrame_isQcif',
         'MainFrame_Initialize', 'TitleFrame_Initialize', 'touch_setUiChange']
with open(sys.argv[2], 'w', encoding='utf-8') as f:
    for n in FUNCS:
        a, sz, _ = e.fbyname[n]
        f.write('== %s @%#x size %d\n%s\n\n' % (n, a, sz, e.dump(n)))
print('wrote', sys.argv[2])
