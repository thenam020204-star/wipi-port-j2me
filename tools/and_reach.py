#!/usr/bin/env python3
"""Reachability over the AND2011 call graph.
Roots = init (_export_table) + cfunc callbacks + every address-taken function
(function pointers in .data/.data.rel.ro/.got, and code that materialises a function
address via GOT/GOTOFF, from work/cache/xref.tsv). Iterates: functions reachable from roots
that take further addresses are themselves roots (they all are, since address-taken set is global).
Usage: python3 tools/and_reach.py docs/ work/cache/xref.tsv > docs/reach_and2011.txt"""
import csv, sys, collections
d, xref = sys.argv[1], sys.argv[2]
funcs = [r['name'] for r in csv.DictReader(open(d + '/symbols_and2011.tsv'), delimiter='\t')]
fset = set(funcs)
g = collections.defaultdict(set)
for r in csv.DictReader(open(d + '/callgraph_and2011.tsv'), delimiter='\t'):
    g[r['caller']].add(r['callee'])
taken = {r['function'] for r in csv.DictReader(open(d + '/fptrs_and2011.tsv'), delimiter='\t')}
for line in open(xref, encoding='utf-8'):
    c = line.rstrip('\n').split('\t')
    if c[2] == 'addr' and c[4] in fset:
        taken.add(c[4])
roots = {'init'} | taken
seen = set(); st = list(roots)
while st:
    f = st.pop()
    if f in seen: continue
    seen.add(f); st.extend(g.get(f, ()))
live = [f for f in funcs if f in seen]; dead = [f for f in funcs if f not in seen]
print('# roots: init + %d address-taken functions' % len(taken))
print('# live %d / dead %d / total %d' % (len(live), len(dead), len(funcs)))
fl = ['make_Lenz_array', 'make_Warp_array', '_sin', '_sqrt', 'MGHEO_sqrt', 'DM_ConvertHSBtoRGB']
print('# float functions:', ', '.join('%s=%s' % (f, 'LIVE' if f in seen else 'dead') for f in fl))
mods = collections.Counter(f.lstrip('_').split('_')[0] for f in dead)
print('# dead by module:', dict(mods.most_common(30)))
print('# DEAD (unreachable from roots; INFERRED dead code — no BL/B, no data/GOT pointer, no address materialisation found):')
for f in dead: print(f)
