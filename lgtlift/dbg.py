"""Debug helper: python3 -I dbg.py <binary> <fnaddr hex> [rounds]  -> failures + register state at first failure."""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from world import World
from interp import Interp
W = World(sys.argv[1]); I = Interp(W)
fa = int(sys.argv[2], 16)
fns = [I.fn_of(a) for a in I.all_functions()]
for rnd in range(int(sys.argv[3]) if len(sys.argv) > 3 else 4):
    I.changed = False
    for fn in fns:
        I.analyze_fn(fn)
    if not I.changed:
        break
fn = I.fn_of(fa)
print('failures', fn.failures)
st = getattr(fn, 'fail_state', None)
if st is not None:
    for r in range(16):
        print('r%d' % r, st.regs[r])
    print('slots', {o: v for o, v in st.slots.items()})
    print('flags', st.flags)
print('sig', fn.sig.name, fn.sig.desc, [p.kind() for p in fn.sig.params], fn.sig.used)
