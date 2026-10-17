#!/usr/bin/env python3
"""First differing call between the ORIGINAL (tools/e_calls.py) and Java (tools/jcr.sh -> hl4.test.CallRec) call records (T0.1).
Usage: callcmp.py <ref calls> <java calls> [--all N] [--ctx N] [--nostate] [--only NAME[,NAME]]
Arguments and return values are compared at the width of the Ghidra signature (header '# sig'); 'ptr' and '*' match anything.
--nostate compares only names/args/returns (not the g/e/h/m/o hashes).  --only keeps the calls of those functions (both sides), e.g. when
the Java side inlines another recorded function in some callers.  Exit status 0 = no difference."""
import sys


def load(p):
    sig, calls = {}, []
    for ln in open(p):
        if ln.startswith('# sig '):
            _, _, n, aw, rw = ln.split()
            sig[n] = ([int(x) for x in aw.split(',')] if aw != '-' else [], int(rw))
        elif ln.startswith('C '):
            f = ln.split()
            st = dict(x.split('=', 1) for x in f[6:])
            calls.append({'tick': int(f[1]), 'depth': int(f[2]), 'name': f[3], 'args': f[4].split(',') if f[4] != '-' else [], 'ret': f[5],
                          'st': st, 'line': ln.rstrip(), 'dump': {}})
        elif ln.startswith('D ') and calls:
            _, k, hx = ln.split()
            calls[-1]['dump'][k] = bytes.fromhex(hx)
    return sig, calls


ONLY = False


def norm(v, w):
    if v in ('ptr', '*', '-', 'EXC') or v.startswith('o'):
        return v
    try:
        x = int(v)
    except ValueError:
        return v
    if w in (1, 2):
        return str(x & ((1 << (8 * w)) - 1))
    return str(x)


def same(a, b):
    return a == b or a in ('ptr', '*') or b in ('ptr', '*')


def diff(r, j, sig, nostate):
    out = []
    if r['name'] != j['name']:
        return ['name %s vs %s' % (r['name'], j['name'])]
    if r['tick'] != j['tick']:
        out.append('tick %d vs %d' % (r['tick'], j['tick']))
    if r['depth'] != j['depth'] and not ONLY:
        out.append('depth %d vs %d' % (r['depth'], j['depth']))
    aw, rw = sig.get(r['name'], ([], 4))
    if len(r['args']) != len(j['args']):
        out.append('argc %d vs %d' % (len(r['args']), len(j['args'])))
    else:
        for i, (a, b) in enumerate(zip(r['args'], j['args'])):
            w = aw[i] if i < len(aw) else 4
            if not same(norm(a, w), norm(b, w)):
                out.append('arg%d %s vs %s' % (i, a, b))
    if not same(norm(r['ret'], rw), norm(j['ret'], rw)) and not (rw == 0 or j['ret'] == '-'):
        out.append('ret %s vs %s' % (r['ret'], j['ret']))
    if j['ret'] == 'EXC':
        out.append('java threw')
    if not nostate:
        for k in ('g', 'e', 'h', 'm', 'o'):
            if r['st'].get(k) != j['st'].get(k):
                out.append('%s %s vs %s' % (k, r['st'].get(k), j['st'].get(k)))
    return out


def main():
    a = sys.argv[1:]
    nall = int(a[a.index('--all') + 1]) if '--all' in a else 1
    ctx = int(a[a.index('--ctx') + 1]) if '--ctx' in a else 4
    nostate = '--nostate' in a
    sig, rc = load(a[0])
    _, jc = load(a[1])
    if '--only' in a:
        global ONLY
        ONLY = True                             # nesting depth depends on which other functions are recorded: not compared
        keep = set(a[a.index('--only') + 1].split(','))
        rc = [c for c in rc if c['name'] in keep]
        jc = [c for c in jc if c['name'] in keep]
    if rc and jc:                               # compare over the ticks both runs reached (the Java run stops at its trace end / first frame difference)
        tmax = min(rc[-1]['tick'], jc[-1]['tick'])
        if jc[-1]['ret'] == 'EXC':
            tmax = jc[-1]['tick']
        rc = [c for c in rc if c['tick'] <= tmax]
        jc = [c for c in jc if c['tick'] <= tmax]
    n = min(len(rc), len(jc))
    shown = 0
    for i in range(n):
        d = diff(rc[i], jc[i], sig, nostate)
        if d:
            if shown == 0:
                for k in range(max(0, i - ctx), i):
                    print('   ok #%d %s' % (k, rc[k]['line']))
            print('DIFF #%d tick %d %s: %s' % (i, rc[i]['tick'], rc[i]['name'], '; '.join(d)))
            print('   ref  ' + rc[i]['line'])
            print('   java ' + jc[i]['line'])
            for k in sorted(set(rc[i]['dump']) & set(jc[i]['dump'])):
                x, y = rc[i]['dump'][k], jc[i]['dump'][k]
                runs = []
                for b in range(min(len(x), len(y))):
                    if x[b] != y[b]:
                        if runs and runs[-1][1] >= b - 1:
                            runs[-1][1] = b
                        else:
                            runs.append([b, b])
                if runs:
                    print('   bytes %s: %s' % (k, ' '.join('+%#x..%#x ref %s java %s' % (lo, hi, x[lo:hi + 1].hex(), y[lo:hi + 1].hex()) for lo, hi in runs[:12])))
            shown += 1
            if shown >= nall:
                break
    if shown == 0 and len(rc) != len(jc):
        k = n
        print('calls %d (ref) vs %d (java); first unmatched: %s' % (len(rc), len(jc), (rc[k] if k < len(rc) else jc[k])['line']))
        shown = 1
    names = {}
    for c in rc:
        names[c['name']] = names.get(c['name'], 0) + 1
    print('callcmp: %d ref calls, %d java calls, %s; %s' % (len(rc), len(jc), 'first difference above' if shown else '0 differences',
                                                           ' '.join('%s=%d' % x for x in sorted(names.items()))))
    sys.exit(1 if shown else 0)


if __name__ == '__main__':
    main()
