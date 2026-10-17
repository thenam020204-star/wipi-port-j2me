#!/usr/bin/env python3
"""T0.2 acceptance harness: for each function, replace its hand-written Java method by the tools/c2j.py draft in a copy of the sources
(work/c2j/src_<NAME>), compile, and check it against the original with the call recorder (tools/jcr.sh + tools/callcmp.py).

Usage: c2j_eval.py <ref calls (tools/e_calls.py, covering all the functions)> <spec list used for the ref> <NAME,NAME..> <GameTraceTest args...>
Prints one line per function: C2J-CHECK count, compile result, callcmp result.  The hand port stays untouched (j2me/src)."""
import os
import re
import shutil
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import c2j

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))


def replace_method(tree, name, text):
    """replace the first method whose javadoc starts with NAME by 'text'; returns the file or None"""
    for dp, _, fs in os.walk(tree):
        for f in fs:
            if not f.endswith('.java'):
                continue
            p = os.path.join(dp, f)
            t = open(p, encoding='utf-8').read()
            for m in c2j.DOC.finditer(t):
                if m.group(1) != name:
                    continue
                end = t.index('*/', m.start()) + 2
                s = c2j.SIG.match(t, end)
                if not s:
                    continue
                i = s.end() - 1                     # the '{'
                depth = 0
                while True:
                    c = t[i]
                    if c == '{':
                        depth += 1
                    elif c == '}':
                        depth -= 1
                        if depth == 0:
                            break
                    elif c == '"':
                        i = t.index('"', i + 1)
                    elif t.startswith('//', i):
                        i = t.index('\n', i)
                    i += 1
                ls = t.rindex('\n', 0, m.start()) + 1
                open(p, 'w', encoding='utf-8').write(t[:ls] + text + t[i + 1:])
                return p
    return None


def main():
    ref, spec, names, rest = sys.argv[1], sys.argv[2], sys.argv[3].split(','), sys.argv[4:]
    idx = c2j.java_index()
    env = dict(os.environ, LC_ALL='C.UTF-8')
    for n in names:
        try:
            g = c2j.Gen(n, idx)
            txt = g.gen()
        except Exception as ex:
            print('%-34s c2j failed: %s' % (n, str(ex)[:100]))
            continue
        tree = os.path.join(ROOT, 'work', 'c2j', 'src_' + n)
        shutil.rmtree(tree, ignore_errors=True)
        shutil.copytree(os.path.join(ROOT, 'j2me', 'src'), tree)
        if not replace_method(tree, n, txt):
            print('%-34s no Java method' % n)
            continue
        cls = os.path.join(ROOT, 'work', 'c2j', 'cls_' + n)
        shutil.rmtree(cls, ignore_errors=True)
        os.makedirs(cls)
        files = [os.path.join(d, f) for d, _, fs in os.walk(tree) for f in fs if f.endswith('.java')]
        r = subprocess.run(['javac', '--release', '8', '-nowarn', '-encoding', 'UTF-8', '-cp', os.path.join(ROOT, 'work/tools/j2me/midp-2.0-stub-1.0.jar'),
                            '-d', cls] + files, capture_output=True, text=True, env=env)
        nerr = len(re.findall(r'error:', r.stdout + r.stderr))
        if nerr:
            first = [l for l in (r.stdout + r.stderr).split('\n') if 'error:' in l][0]
            print('%-34s checks %2d  compile: %d errors (%s)' % (n, len(g.checks), nerr, first.split('error:')[1].strip()[:80]))
            continue
        out = os.path.join(ROOT, 'work', 'c2j', 'calls_' + n + '.txt')
        env2 = dict(env, CR_SRC=tree)
        subprocess.run([os.path.join(ROOT, 'tools', 'jcr.sh'), spec, ref, out] + rest, capture_output=True, text=True, env=env2)
        c = subprocess.run(['python3', os.path.join(ROOT, 'tools', 'callcmp.py'), ref, out, '--only', n, '--ctx', '0'], capture_output=True, text=True)
        crashed = any(' EXC ' in l for l in open(out)) if os.path.exists(out) else True
        lines = c.stdout.strip().split('\n')
        summ = lines[-1].split(';')[0]
        diff = [l for l in lines if l.startswith('DIFF') or l.startswith('calls ')]
        print('%-34s checks %2d  compile ok  %s%s%s' % (n, len(g.checks), summ.replace('callcmp: ', ''), '  JAVA THREW' if crashed else '',
                                                    ('  | ' + diff[0][:150]) if diff else ''))


if __name__ == '__main__':
    main()
