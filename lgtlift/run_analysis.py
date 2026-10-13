"""Run the lifter's analysis pass over functions of an LGT binary and print the diagnostics summary.

usage: python3 -I run_analysis.py <binary.mod> [hexaddr ...]   (no addresses = all game functions)
"""
import sys
import collections
import re
sys.path.insert(0, __import__('os').path.dirname(__import__('os').path.abspath(__file__)))
from world import World
from interp import Interp


def classify(msg):
    m = re.sub(r'0x[0-9a-f]+|\b[0-9a-f]{4,}\b|\d+', '#', msg)
    return m[:90]


def main():
    path = sys.argv[1]
    sel = [int(x, 16) for x in sys.argv[2:]]
    W = World(path)
    I = Interp(W)
    fns = sel or I.all_functions()
    ctxs = [I.fn_of(a) for a in fns]
    for rnd in range(8):
        I.changed = False
        I.diag.items = []
        ok = 0
        for fn in ctxs:
            if I.analyze_fn(fn):
                ok += 1
        print('round', rnd, 'ok', ok, '/', len(ctxs), 'changed', I.changed, file=sys.stderr)
        if not I.changed:
            break
    cnt = collections.Counter()
    ex = {}
    for fn in ctxs:
        seen = set()
        for a, msg in fn.failures:
            k = classify(msg)
            if k in seen:
                continue
            seen.add(k)
            cnt[k] += 1
            ex.setdefault(k, (fn.addr, a, msg))
    print('functions ok: %d / %d' % (sum(1 for f in ctxs if not f.failures), len(ctxs)))
    print('failure categories (functions affected per category):')
    for k, n in cnt.most_common(60):
        fa, a, msg = ex[k]
        print('%5d  %-70s e.g. fn %x @%x: %s' % (n, k, fa, a, msg[:100]))
    dc = collections.Counter(d[0] for d in I.diag.items)
    print('diag categories:', dict(dc))
    return I, ctxs


if __name__ == '__main__':
    main()
