"""Back end driver: analyse -> classfiles -> (ProGuard preverify) for an LGT binary.

usage: python3 -I build.py <binary.mod> <outdir>
Writes <outdir>/classes/*.class (+ runtime classes compiled separately), mapping.tsv, stats.
"""
import sys
import os
import collections
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import closure
from classgen import Gen


def main():
    path, out = sys.argv[1], sys.argv[2]
    W, I, ctxs = closure.run(path)
    G = Gen(I, list(ctxs.values()))
    G.emit_all()
    cdir = os.path.join(out, 'classes')
    G.write(cdir)
    with open(os.path.join(out, 'mapping.tsv'), 'w') as f:
        f.write('# new class\tnew method\tdescriptor\toriginal ARM function\n')
        for owner, name, desc, addr in sorted(G.mapping, key=lambda x: x[3]):
            f.write('%s\t%s\t%s\t0x%x\n' % (owner, name, desc, addr))
    nok = sum(1 for c in ctxs.values() if not c.failures)
    print('functions: %d clean / %d; emitted methods %d, stubs %d' % (nok, len(ctxs), len(G.mapping), len(G.failed)))
    # code-size statistics (bytes of bytecode per ARM instruction)
    tot_b = tot_i = 0
    worst = []
    for addr, fn in sorted(ctxs.items()):
        if fn.failures:
            continue
        for cf in G.classes.values():
            pass
    sizes = []
    for owner, name, desc, addr in G.mapping:
        cf = G.classes[owner]
        m = [x for x in cf.methods if x.name == name and x.desc == desc][0]
        n = len(m.code.assemble()) if hasattr(m.code, 'assemble') else 0
        ninsn = (I.B.func_end(addr) - addr) // 4
        sizes.append((n, ninsn, addr))
    sizes.sort(reverse=True)
    tb = sum(s[0] for s in sizes)
    ti = sum(s[1] for s in sizes)
    print('bytecode bytes %d for %d ARM insns -> %.2f bytes/insn' % (tb, ti, tb / max(1, ti)))
    print('largest methods (bytes, ARM insns, fn):', [(s[0], s[1], hex(s[2])) for s in sizes[:8]])


if __name__ == '__main__':
    main()
