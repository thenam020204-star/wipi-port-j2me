"""Entry-path closure: which functions are reachable from the MIDlet/Card entry points, and how many of them are clean.

usage: python3 -I closure.py <binary.mod>
Call graph = analysis-recorded callees (direct calls, every implementation of a called virtual family, every candidate of
an unresolved fixed slot) + a syntactic scan of direct `bl` targets (covers functions whose analysis failed early).
Class instantiation (Instantiate) pulls in every vtable implementation of the class (RTA-style over-approximation).
"""
import sys
import collections
sys.path.insert(0, __import__('os').path.dirname(__import__('os').path.abspath(__file__)))
from world import World
from interp import Interp
from run_analysis import classify


def run(path, rounds=8):
    W = World(path)
    I = Interp(W)
    fns = I.all_functions()
    ctxs = {a: I.fn_of(a) for a in fns}
    for rnd in range(rounds):
        I.changed = False
        I.diag.items = []
        for fn in ctxs.values():
            I.analyze_fn(fn)
        if not I.changed:
            break
    return W, I, ctxs


def static_bls(I, addr):
    insns, _d, order = I.B.decode_function(addr)
    out = set()
    for a in order:
        ins = insns[a]
        if ins.op == 'bl' and ins.ops and ins.ops[0][0] in ('a', 'i', 'imm', 'addr') or ins.op == 'bl':
            t = ins.ops[0][1] if ins.ops else None
            if isinstance(t, int) and t in I.B._fidx:
                out.add(t)
    return out


def closure(W, I, ctxs, roots):
    seen = set()
    classes = set()
    work = list(roots)
    callee_of = {}
    while work:
        a = work.pop()
        if a in seen or a not in ctxs:
            continue
        seen.add(a)
        fn = ctxs[a]
        cs = set(fn.callees) | static_bls(I, a)
        callee_of[a] = cs
        for c in cs:
            work.append(c)
        for cn in fn.insts:
            if cn not in classes:
                classes.add(cn)
                cm = W.cls.get(cn)
                while cm is not None:
                    work.extend(cm.vslots.values())
                    work.extend(cm.methods.keys())
                    cm = cm.local_super
    return seen, classes, callee_of


def main():
    W, I, ctxs = run(sys.argv[1])
    roots = []
    for n in ('Lm',):
        roots.extend(W.cls[n].methods.keys())
    for cm in W.cls.values():
        if cm.local_super is None and cm.jsuper != 'java/lang/Object':   # direct framework subclasses: Card, Jlet, TextBox
            roots.extend(cm.vslots.values())
            roots.extend(cm.methods.keys())
    seen, classes, _ = closure(W, I, ctxs, roots)
    clean = [a for a in seen if not ctxs[a].failures]
    bad = [a for a in seen if ctxs[a].failures]
    print('reachable functions: %d / %d total; clean %d, failing %d; instantiated classes: %d/%d'
          % (len(seen), len(ctxs), len(clean), len(bad), len(classes), len(W.cls)))
    cnt = collections.Counter()
    ex = {}
    for a in bad:
        s = set()
        for ad, msg in ctxs[a].failures:
            k = classify(msg)
            if k not in s:
                s.add(k); cnt[k] += 1; ex.setdefault(k, (a, ad, msg))
    for k, n in cnt.most_common(40):
        a, ad, msg = ex[k]
        print('%4d  %-70s e.g. fn %x @%x' % (n, k, a, ad))


if __name__ == '__main__':
    main()
