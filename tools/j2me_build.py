#!/usr/bin/env python3
"""Build the J2ME product (CLDC 1.1 / MIDP 2.0) reproducibly:

  1. compile  j2me/src/**.java with ecj  -1.3 -target 1.1  against the CLDC 1.1 and MIDP 2.0 API stubs ONLY (bootclasspath),
     so any reference outside the target profile is a compile error;
  2. compat scan (tools/j2me_scan.py) - violations reject the build;
  3. preverify (ProGuard -microedition: StackMap attributes, class version stays 45.3);
  4. pack  JAR (+ optional resource directory) and write the JAD (MIDlet-Jar-Size) and a SHA-256 manifest.

Tool jars (third-party, build tools only, never shipped): work/tools/j2me/{ecj-4.6.1,proguard-base-6.2.2,cldc-1.1-stub-1.0,midp-2.0-stub-1.0}.jar
Usage: python3 tools/j2me_build.py build [--res DIR] [--out DIR] | compile | test
"""
import hashlib
import os
import shutil
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TOOLS = os.path.join(ROOT, 'work', 'tools', 'j2me')
SRC = os.path.join(ROOT, 'j2me', 'src')
TEST = os.path.join(ROOT, 'j2me', 'test')
OUT = os.path.join(ROOT, 'work', 'build')
CLDC = os.path.join(TOOLS, 'cldc-1.1-stub-1.0.jar')
MIDP = os.path.join(TOOLS, 'midp-2.0-stub-1.0.jar')
ECJ = os.path.join(TOOLS, 'ecj-4.6.1.jar')
PG = os.path.join(TOOLS, 'proguard-base-6.2.2.jar')
ENV = dict(os.environ, JAVA_TOOL_OPTIONS='')


def java_files(d):
    out = []
    for r, _, fs in os.walk(d):
        out += [os.path.join(r, f) for f in fs if f.endswith('.java')]
    return sorted(out)


def run(cmd, **kw):
    p = subprocess.run(cmd, env=ENV, capture_output=True, text=True, **kw)
    if p.returncode:
        sys.stderr.write(p.stdout + p.stderr)
        raise SystemExit('FAILED: ' + ' '.join(cmd[:4]))
    return p.stdout + p.stderr


def compile_main(out=OUT):
    cls = os.path.join(out, 'classes')
    shutil.rmtree(cls, ignore_errors=True)
    os.makedirs(cls)
    msg = run(['java', '-jar', ECJ, '-1.3', '-target', '1.1', '-nowarn', '-bootclasspath', CLDC + os.pathsep + MIDP,
               '-d', cls] + java_files(SRC))
    if msg.strip():
        print(msg)
    return cls


def scan(cls):
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import j2me_scan
    bad = j2me_scan.scan_dir(cls, [CLDC, MIDP])
    if bad:
        raise SystemExit('compat scan: %d violation(s) - build rejected' % bad)


def preverify(cls, out=OUT):
    inj = os.path.join(out, 'unverified.jar')
    pv = os.path.join(out, 'preverified.jar')
    run(['jar', 'cf', inj, '-C', cls, '.'])
    pro = os.path.join(out, 'preverify.pro')
    open(pro, 'w').write('-injars %s\n-outjars %s\n-libraryjars %s\n-libraryjars %s\n-microedition\n-dontshrink\n-dontoptimize\n'
                         '-dontobfuscate\n-keep class *\n' % (inj, pv, CLDC, MIDP))
    if os.path.exists(pv):
        os.remove(pv)
    run(['java', '-cp', PG, 'proguard.ProGuard', '@' + pro])
    return pv


def build(res=None, out=OUT):
    os.makedirs(out, exist_ok=True)
    cls = compile_main(out)
    scan(cls)
    pv = preverify(cls, out)
    jar = os.path.join(out, 'hl4.jar')
    stage = os.path.join(out, 'stage')
    shutil.rmtree(stage, ignore_errors=True)
    os.makedirs(stage)
    run(['unzip', '-q', pv, '-d', stage])
    if res:
        run(['cp', '-r', res + '/.', stage])
    mf = os.path.join(out, 'MANIFEST.MF')
    open(mf, 'w').write('Manifest-Version: 1.0\nMIDlet-1: Heroes Lore 4, , hl4.Main\nMIDlet-Name: Heroes Lore 4\nMIDlet-Vendor: research\n'
                        'MIDlet-Version: 0.1.0\nMicroEdition-Configuration: CLDC-1.1\nMicroEdition-Profile: MIDP-2.0\n')
    if os.path.exists(jar):
        os.remove(jar)
    run(['jar', 'cfm', jar, mf, '-C', stage, '.'])
    size = os.path.getsize(jar)
    sha = hashlib.sha256(open(jar, 'rb').read()).hexdigest()
    open(os.path.join(out, 'hl4.jad'), 'w').write('MIDlet-1: Heroes Lore 4, , hl4.Main\nMIDlet-Name: Heroes Lore 4\nMIDlet-Vendor: research\n'
                                                   'MIDlet-Version: 0.1.0\nMIDlet-Jar-URL: hl4.jar\nMIDlet-Jar-Size: %d\n'
                                                   'MicroEdition-Configuration: CLDC-1.1\nMicroEdition-Profile: MIDP-2.0\n' % size)
    open(os.path.join(out, 'hl4.sha256'), 'w').write('%s  hl4.jar  %d bytes\n' % (sha, size))
    print('built', jar, size, 'bytes', sha)
    return jar


def compile_tests(out=OUT):
    """Tests run on a desktop JVM (they read the original resources from disk); they link against the classes ecj produced."""
    tc = os.path.join(out, 'testclasses')
    shutil.rmtree(tc, ignore_errors=True)
    os.makedirs(tc)
    cls = os.path.join(out, 'classes')
    run(['javac', '--release', '8', '-nowarn', '-cp', cls, '-d', tc] + java_files(TEST))
    return tc


if __name__ == '__main__':
    cmd = sys.argv[1] if len(sys.argv) > 1 else 'build'
    a = sys.argv[2:]
    res = a[a.index('--res') + 1] if '--res' in a else None
    out = a[a.index('--out') + 1] if '--out' in a else OUT
    if cmd == 'compile':
        compile_main(out)
    elif cmd == 'build':
        build(res, out)
    elif cmd == 'test':
        compile_main(out)
        compile_tests(out)
    else:
        raise SystemExit(__doc__)
