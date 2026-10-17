#!/usr/bin/env python3
"""Instrumented copy of the Java sources for the call recorder (T0.1): every method whose javadoc starts with one of the chosen ORIGINAL
function names (`/** NAME ... @0xaddr`) is renamed NAME$cr and wrapped by a method with the old name that reports the call to hl4.test.CallRec.
Only the copy in <outdir> is changed; j2me/src (the release sources) is never touched.

Usage: callrec_inst.py <outdir> <spec>[,<spec>..]      (spec syntax: tools/e_calls.py; the same list is given to both tools)
  NAME          Java parameters (receiver first for an instance method) = original arguments in order; when the counts differ
                from the recorded argument count every argument is recorded as '*' (wildcard) and a warning asks for a map
  NAME=j0:j1:.. original argument i = Java parameter j_i ('-' = no Java counterpart, recorded as '*')
Prints the methods it wrapped; exits with an error if a name matches no method."""
import os
import re
import shutil
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from e_calls import spec_sigs

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
SRC = os.path.join(ROOT, 'j2me', 'src')
DOC = re.compile(r'/\*\*\s*(?:\*\s*)?([A-Za-z_]\w*)[^\n]*?@0x[0-9a-f]+')
SIG = re.compile(r'(\s*)((?:(?:public|private|protected|static|final|synchronized)\s+)*)([\w\[\]]+)\s+(\w+)\s*\(([^)]*)\)\s*(throws\s+[\w.,\s]+)?\{', re.S)


def wrapper(indent, mods, rt, name, params, origname, amap, norig):
    static = 'static' in mods.split()
    names = [re.sub(r'\[\]', '', p.split()[-1]) for p in params]
    jvals = ([] if static else ['this']) + names
    if amap == 'auto':
        if len(jvals) == norig:
            amap = list(range(norig))
        else:
            print('  WARNING %s: %d Java values vs %d original args: args recorded as * (give NAME=j0/j1/..)' % (origname, len(jvals), norig))
            amap = [None] * norig
    vals = ['"*"' if j is None else (jvals[j] if _isobj(params, static, j) else 'hl4.test.CallRec.v(%s)' % jvals[j]) for j in amap]
    call = '%s$cr(%s)' % (name, ', '.join(names))
    i2 = indent + '    '
    body = [i2 + 'hl4.test.CallRec.enter("%s", new Object[] {%s});' % (origname, ', '.join(vals)), i2 + 'try {']
    if rt == 'void':
        body += [i2 + '    ' + call + ';', i2 + '    hl4.test.CallRec.exitVoid();']
    else:
        body += [i2 + '    %s r$ = %s;' % (rt, call), i2 + '    hl4.test.CallRec.exit(r$);', i2 + '    return r$;']
    body += [i2 + '} catch (RuntimeException ex$) {', i2 + '    hl4.test.CallRec.fail(ex$);', i2 + '    throw ex$;', i2 + '}']
    return '%s%s%s %s(%s) {\n%s\n%s}\n\n' % (indent, mods, rt, name, ', '.join(params), '\n'.join(body), indent)


def _isobj(params, static, j):
    """Java value j is passed as the object itself (so CallRec can hash a pool object) rather than as CallRec.v(..)"""
    if not static:
        if j == 0:
            return True
        j -= 1
    t = params[j].split()[-2] if len(params[j].split()) > 1 else ''
    return t not in ('int', 'byte', 'short', 'char', 'boolean', 'long')


def main():
    outdir = sys.argv[1]
    sg, _, maps = spec_sigs(sys.argv[2].split(','))
    specs = dict((n, 'auto' if m is None else m) for n, m in maps.items())
    if os.path.exists(outdir):
        shutil.rmtree(outdir)
    shutil.copytree(SRC, outdir)
    found = {n: 0 for n in specs}
    for dp, _, fs in os.walk(outdir):
        for f in fs:
            if not f.endswith('.java'):
                continue
            p = os.path.join(dp, f)
            t = open(p, encoding='utf-8').read()
            edits = []
            for m in DOC.finditer(t):
                n = m.group(1)
                if n not in specs:
                    continue
                end = t.index('*/', m.start()) + 2
                s = SIG.match(t, end)
                if not s or s.group(4) in ('if', 'while', 'for', 'switch'):
                    continue
                indent, mods, rt, name, ps = s.group(1).split('\n')[-1], s.group(2), s.group(3), s.group(4), s.group(5)
                params = [x.strip() for x in ps.split(',') if x.strip()]
                if 'abstract' in mods or rt in ('class', 'interface', 'new', 'return'):
                    continue
                ls = t.rindex('\n', 0, m.start()) + 1
                edits.append((ls, wrapper(indent, mods, rt, name, params, n, specs[n], len(sg[n][0]))))
                nm = s.start(4)
                edits.append((nm + len(name), '$cr'))
                found[n] += 1
                print('wrapped %s -> %s.%s(%s)' % (n, f[:-5], name, ', '.join(params)))
            for pos, ins in sorted(edits, reverse=True):
                t = t[:pos] + ins + t[pos:]
            if edits:
                open(p, 'w', encoding='utf-8').write(t)
    bad = [n for n, c in found.items() if c == 0]
    if bad:
        raise SystemExit('no Java method found for: ' + ' '.join(bad))


if __name__ == '__main__':
    main()
