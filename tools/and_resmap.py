#!/usr/bin/env python3
"""Which resource paths are loaded through UM_LoadResDes (DES) vs UM_LoadRes (plain)?
For every function calling one of the loaders, list the path-like strings it references
(GOTOFF string materialisations). Output: docs/resmap_and2011.tsv
Usage: python3 tools/and_resmap.py <binary.mod> <docs dir>"""
import os, re, sys, csv, collections
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from andelf import AndElf
e = AndElf(sys.argv[1]); d = sys.argv[2]
calls = collections.defaultdict(set)
for r in csv.DictReader(open(d + '/callgraph_and2011.tsv'), delimiter='\t'):
    if r['callee'] in ('UM_LoadResDes', 'UM_LoadRes', 'GetLoadResBinary', 'SM_CreateClip'):
        calls[r['caller']].add(r['callee'])
rx = re.compile(r'GOTOFF (0x[0-9a-f]+)')
rows = []
for fn in sorted(calls):
    strs = []
    for i, note in e.annotate(fn):
        m = rx.search(note)
        if m:
            s = e.cstr(int(m.group(1), 16), 120)
            if s and ('/' in s or '%' in s):
                strs.append(s)
    rows.append((fn, ','.join(sorted(calls[fn])), ' | '.join(dict.fromkeys(strs))))
with open(d + '/resmap_and2011.tsv', 'w', encoding='utf-8') as f:
    f.write('function\tloader\tpath_strings\n')
    for r in rows: f.write('\t'.join(r) + '\n')
for r in rows: print('%-34s %-28s %s' % r)
