#!/usr/bin/env python3
"""Phase E self-tests: Java ports vs goldens taken directly from the ORIGINAL routines (tools/e_golden.py).
Usage: python3 tools/e_test.py [res dir]     Writes docs/phaseE_selftest.txt."""
import os
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, 'tools'))
import j2me_build as B

res = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, 'work', 'res')
gold = os.path.join(ROOT, 'work', 'golden')
os.makedirs(gold, exist_ok=True)
AND = os.path.join(ROOT, 'work', 'orig', 'android', '영웅서기4.apk!', 'res', 'raw', 'jarfile.mp3!', 'binary.mod')
cls = B.compile_main()
B.scan(cls)
tc = B.compile_tests()
env = dict(os.environ, JAVA_TOOL_OPTIONS='', LC_ALL='C.UTF-8')
cp = cls + os.pathsep + tc + os.pathsep + res
out = []


def gen(kind):
    if not os.path.exists(os.path.join(gold, kind + '.txt')):
        subprocess.run([sys.executable, os.path.join(ROOT, 'tools', 'e_golden.py'), kind, AND, res, gold], check=True, env=env)


def java(*args):
    p = subprocess.run(['java', '-cp', cp] + list(args), env=env, capture_output=True, text=True)
    out.append((p.stdout + p.stderr).strip())
    print(out[-1])
    return p.returncode


rc = 0
for kind, test in (('hf', 'hl4.test.HfTest'),):
    gen(kind)
    rc |= java(test, gold)
open(os.path.join(ROOT, 'docs', 'phaseE_selftest.txt'), 'w').write('\n'.join(o.replace('Picked up JAVA_TOOL_OPTIONS: \n', '') for o in out) + '\n')
sys.exit(rc)
