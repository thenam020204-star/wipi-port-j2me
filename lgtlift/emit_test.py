import sys, os, subprocess
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from world import World
from interp import Interp
from classgen import Gen
path, out = sys.argv[1], sys.argv[2]
sel = [int(x, 16) for x in sys.argv[3:]]
W = World(path); I = Interp(W)
ctxs = [I.fn_of(a) for a in sel]
for rnd in range(8):
    I.changed = False
    for fn in ctxs:
        I.analyze_fn(fn)
    if not I.changed: break
for fn in ctxs: print('fn %x' % fn.addr, 'failures', fn.failures)
G = Gen(I, ctxs); G.emit_all(); G.write(out)
print('failed', G.failed)
for name, cf in G.classes.items():
    if any(m for m in cf.methods): print('class', name, [(m.name, m.desc) for m in cf.methods])
