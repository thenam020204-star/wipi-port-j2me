#!/usr/bin/env python3
"""AND2011 function inventory + call graph + WIPI-C import table.
Usage: python3 tools/and_symbols.py <binary.mod> <docs dir>
Edges = BL/BLX direct calls + B tail-calls to another function's start.
Writes symbols_and2011.tsv, callgraph_and2011.tsv, imports_and2011.tsv, objects_and2011.tsv."""
import collections, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import capstone
from andelf import AndElf

e = AndElf(sys.argv[1]); out = sys.argv[2]
def module(n):
    n = n.lstrip('_')
    return n.split('_')[0] if '_' in n else n
edges = set(); imp_callers = collections.defaultdict(set); indirect = collections.Counter()
bad = []; bad_b = []; tails = 0; ncalls = collections.Counter()
for a, sz, n, t in e.funcs:
    if not sz: continue
    for i in e.insns(n):
        if i.mnemonic in ('bl', 'blx') and i.op_str.startswith('#'):
            tgt = int(i.op_str[1:], 16)
            if tgt in e.plt:
                imp_callers[e.plt[tgt]].add(n); edges.add((n, e.plt[tgt] + '@plt')); ncalls[n] += 1
            else:
                f = e.func_at(tgt)
                if f and e.fbyname[f][0] == tgt:
                    edges.add((n, f)); ncalls[n] += 1
                else:
                    bad.append((i.address, n, tgt))
        elif i.mnemonic == 'b' and i.op_str.startswith('#'):
            # tail call: unconditional/conditional branch to the start of another function or a PLT stub
            tgt = int(i.op_str[1:], 16)
            if tgt in e.plt:
                imp_callers[e.plt[tgt]].add(n); edges.add((n, e.plt[tgt] + '@plt')); tails += 1
            elif not (a <= tgt < a + sz):
                f = e.func_at(tgt)
                if f and e.fbyname[f][0] == tgt:
                    edges.add((n, f)); tails += 1
                else:
                    bad_b.append((i.address, n, tgt))
        elif i.mnemonic in ('blx', 'bx') and not i.op_str.startswith('#') and i.op_str != 'lr':
            indirect[n] += 1
        elif i.mnemonic == 'mov' and i.op_str.startswith('pc,'):
            indirect[n] += 1
callers = collections.Counter(c for _, c in edges)
with open(os.path.join(out, 'symbols_and2011.tsv'), 'w') as f:
    f.write('vaddr\tsize\tname\tmodule\tmode\tn_callees\tn_callers\tindirect_calls\n')
    for a, sz, n, t in e.funcs:
        f.write('%#07x\t%d\t%s\t%s\t%s\t%d\t%d\t%d\n' % (a, sz, n, module(n), 'thumb' if t else 'arm',
                                                     len([1 for x, _ in edges if x == n]), callers[n], indirect[n]))
with open(os.path.join(out, 'callgraph_and2011.tsv'), 'w') as f:
    f.write('caller\tcallee\n')
    for c, d in sorted(edges): f.write('%s\t%s\n' % (c, d))
with open(os.path.join(out, 'imports_and2011.tsv'), 'w') as f:
    f.write('import\tn_callers\tcallers\n')
    for k in sorted(set(e.plt.values())):
        f.write('%s\t%d\t%s\n' % (k, len(imp_callers[k]), ','.join(sorted(imp_callers[k]))))
with open(os.path.join(out, 'objects_and2011.tsv'), 'w') as f:
    f.write('vaddr\tsize\tname\tsection\n')
    for a, sz, n in e.objs: f.write('%#07x\t%d\t%s\t%s\n' % (a, sz, n, e.secname(a)))
mods = collections.Counter(module(n) for _, _, n, _ in e.funcs)
print('functions', len(e.funcs), 'zero-size', sum(1 for f in e.funcs if not f[1]), 'objects', len(e.objs))
print('edges', len(edges), '(incl. tail-call branches %d)' % tails, 'BL to non-function-start', len(bad), bad[:5],
      'B out of function to non-start', len(bad_b), bad_b[:5])
print('functions with no caller (roots, before fptr tables)', sum(1 for _, _, n, _ in e.funcs if not callers[n]))
print('imports', len(set(e.plt.values())), 'unused imports', [k for k in set(e.plt.values()) if not imp_callers[k]])
print('functions with indirect calls', len(indirect), 'total', sum(indirect.values()))
print('top modules', mods.most_common(40))
