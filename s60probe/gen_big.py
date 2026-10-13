"""Generate Big<N> classes: Runnable whose run() is ~N KB of straight-line int code (version 47, no branches)."""
import sys, os
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'lgtlift'))
import jvm

def ic(c, v):
    c.ins.append(('iconst', v))
    c.depth += 1
    c.max_stack = max(c.max_stack, c.depth)


def make(kb, out):
    cf = jvm.ClassFile('Big%d' % kb, 'java/lang/Object', 0x21, ['java/lang/Runnable'])
    init = cf.add_method(1, '<init>', '()V', False)
    init.code.emit('aload', 0)
    init.code.emit('invokespecial', 'java/lang/Object', '<init>', '()V')
    init.code.emit('return_')
    m = cf.add_method(1, 'run', '()V', False)
    c = m.code
    c.local(2)
    ic(c, 1); c.emit('istore', 1)
    # each group: iload_1 iconst_3 imul sipush 1234 ixor istore_1  -> ~ 1+1+1+3+1+1 = 8 bytes
    n = (kb * 1024 - 64) // 8
    for i in range(n):
        c.emit('iload', 1); ic(c, 3); c.emit('imul'); ic(c, 1000 + (i & 255)); c.emit('ixor'); c.emit('istore', 1)
    c.emit('return_')
    with open(os.path.join(out, 'Big%d.class' % kb), 'wb') as f:
        f.write(cf.to_bytes())
    print('Big%d: code %d bytes' % (kb, len(c.assemble())))

if __name__ == '__main__':
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    for kb in (4, 16, 32, 48, 60):
        make(kb, out)
