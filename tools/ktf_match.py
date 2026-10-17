#!/usr/bin/env python3
"""KTF <-> AND2011 function matching by call-graph propagation (T0.7; INFERRED names, reading aid only).
Seeds: the string matches of docs/xmatch_strings_ktf_so.tsv (KTF literal site -> one .so function name).  Then, repeatedly: for every matched
pair, the ordered callee lists of both sides are cut at callees already matched; a gap with the same number of callees on both sides is paired
position by position when the sizes are plausible (Thumb instruction count / ARM instruction count within 0.4..2.5).  A pair proposed by
several callers with conflicts is dropped.  Output: docs/ktf_match.tsv (img address, AND2011 name, how: seed / prop, votes).

Usage: python3 tools/ktf_match.py [--out docs/ktf_match.tsv]      then e.g.  grep MainGameFrame_ docs/ktf_match.tsv"""
import csv
import os
import sys
from collections import defaultdict

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from capstone import Cs, CS_ARCH_ARM, CS_MODE_ARM
from andelf import AndElf
from ktf_code import Ktf

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
B = os.path.join(ROOT, 'work', 'orig', 'android', '영웅서기4.apk!', 'res', 'raw', 'jarfile.mp3!', 'binary.mod')


def and_calls(e):
    md = Cs(CS_ARCH_ARM, CS_MODE_ARM)
    start = {a: n for n, (a, s, t) in e.fbyname.items()}
    code = {}
    for s in e.secs:
        if s.name == '.text':
            base, data = s['sh_addr'], s.data()
    out, size = {}, {}
    for n in set(e.plt.values()):
        out[n + '@plt'] = []
        size[n + '@plt'] = 30
    for n, (a, sz, t) in e.fbyname.items():
        seq = []
        cnt = 0
        for i in md.disasm(data[a - base:a - base + sz], a):
            cnt += 1
            if i.mnemonic in ('bl', 'b') and i.op_str.startswith('#'):
                tg = int(i.op_str[1:], 0)
                if tg in start and start[tg] != n and (i.mnemonic == 'bl' or not (a <= tg < a + sz)):
                    seq.append(start[tg])
                elif tg in e.plt and e.plt[tg].startswith('__aeabi_') and i.mnemonic == 'bl':
                    seq.append(e.plt[tg] + '@plt')       # runtime helpers: KTF links its own copies and reaches them by BL too
        out[n] = seq
        size[n] = max(cnt, 1)
    return out, size


def ktf_calls(k):
    import bisect
    out, size = {}, {}
    st = set(k.starts)
    for f in k.starts:
        body = k.body(f)
        size[f] = max(len(body), 1)
        seq = []
        for i in body:
            if i.mnemonic == 'bl' and i.op_str.startswith('#'):
                t = int(i.op_str[1:], 0)
                if t in st and t != f:
                    seq.append(t)
        out[f] = seq
    return out, size


def main():
    outp = sys.argv[sys.argv.index('--out') + 1] if '--out' in sys.argv else os.path.join(ROOT, 'docs', 'ktf_match.tsv')
    e = AndElf(B)
    k = Ktf()
    ac, asz = and_calls(e)
    kc, ksz = ktf_calls(k)
    a2k, k2a, how = {}, {}, {}
    for r in csv.DictReader(open(os.path.join(ROOT, 'docs', 'xmatch_strings_ktf_so.tsv')), delimiter='\t'):
        names = r['so_functions'].split(',')
        f = k.func_of(int(r['ktf_lit_site'], 16))
        if len(names) == 1 and f is not None and names[0] in ac:
            n = names[0]
            if a2k.get(n, f) != f or k2a.get(f, n) != n:
                a2k.pop(n, None)
                continue
            a2k[n], k2a[f] = f, n
            how[n] = ('seed', 0)

    def plausible(n, f):
        if n.endswith('@plt'):
            return ksz[f] <= 40                     # a KTF platform stub / runtime helper
        r = ksz[f] / asz[n]
        return 0.4 <= r <= 2.5

    for rnd in range(30):
        votes = defaultdict(int)
        for n, f in list(a2k.items()):
            sa, sk = ac[n], kc[f]
            # cut both callee sequences at callees whose match is known and appears on both sides in the same order
            ia = ik = 0
            ga, gk = [], []
            while ia <= len(sa) and ik <= len(sk):
                xa = sa[ia] if ia < len(sa) else None
                xk = sk[ik] if ik < len(sk) else None
                anchor = xa is not None and xa in a2k and a2k[xa] == xk
                if anchor or (xa is None and xk is None):
                    if len(ga) == len(gk):
                        for p, q in zip(ga, gk):
                            if p not in a2k and q not in k2a and plausible(p, q):
                                votes[(p, q)] += 1
                    ga, gk = [], []
                    ia += 1
                    ik += 1
                    if xa is None:
                        break
                    continue
                # advance the side that is not at a known callee (both unknown: collect both)
                if xa is not None and (xa not in a2k):
                    ga.append(xa)
                    ia += 1
                elif xk is not None and (xk not in k2a):
                    gk.append(xk)
                    ik += 1
                else:
                    # known callees in different order: give up this function's remaining gaps
                    break
        best_a, best_k = defaultdict(list), defaultdict(list)
        for (p, q), v in votes.items():
            best_a[p].append((v, q))
            best_k[q].append((v, p))
        new = 0
        for (p, q), v in votes.items():
            if len(set(x[1] for x in best_a[p])) == 1 and len(set(x[1] for x in best_k[q])) == 1:
                a2k[p], k2a[q] = q, p
                how[p] = ('prop%d' % rnd, v)
                new += 1
        print('round %d: +%d matches (total %d)' % (rnd, new, len(a2k)), flush=True)
        if not new:
            break
    with open(outp, 'w') as o:
        o.write('ktf_img\tand2011_name\tand_addr\thow\tvotes\tktf_insns\tand_insns\n')
        for n, f in sorted(a2k.items(), key=lambda x: x[1]):
            o.write('%#x\t%s\t%#x\t%s\t%d\t%d\t%d\n' % (f, n, e.fbyname[n][0] if n in e.fbyname else 0, how[n][0], how[n][1], ksz[f], asz[n]))
    print('wrote', outp, len(a2k), 'matches of', len(k.starts), 'KTF functions')


if __name__ == '__main__':
    main()
