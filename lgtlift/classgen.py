"""Class synthesis: turn analysed functions into JVM class files (one per original class) + mapping file."""
import os
import jvm
from lift import Unsupported, parse_desc

ACC_PUBLIC, ACC_PRIVATE, ACC_STATIC, ACC_FINAL = 1, 2, 8, 0x10
OBJ = 'java/lang/Object'


class Gen:
    def __init__(self, I, ctxs):
        self.I = I
        self.W = I.W
        self.ctxs = {c.addr: c for c in ctxs}
        self.classes = {}
        self.mapping = []        # (new JVM ref, original ARM function address / orig name)
        self.failed = {}

    def class_for(self, name):
        cf = self.classes.get(name)
        if cf is None:
            cm = self.W.cls.get(name)
            sup = cm.jsuper if cm else OBJ
            ifs = [i for i in cm.ifaces] if cm else []
            acc = 0x21
            if cm and cm.vcount == 0 and cm.iw == 0 and not cm.methods and name == 'k':
                acc = 0x601
                sup = OBJ
            cf = jvm.ClassFile(name, sup or OBJ, acc, ifs)
            self.classes[name] = cf
            if cm:
                self.add_fields(cf, cm)
        return cf

    def add_fields(self, cf, cm):
        W = self.W
        for w in range(cm.own_start, cm.iw):
            f = cm.fields.get(w)
            if f:
                cf.add_field(ACC_PUBLIC, f.name, f.desc)
            else:
                isref = cm.ref_words[w] if w < len(cm.ref_words) else False
                cf.add_field(ACC_PUBLIC, 'w%d' % w, 'Ljava/lang/Object;' if isref else 'I')

    def emit_all(self):
        I = self.I
        for name in self.W.cls:
            self.class_for(name)
        for addr, fn in sorted(self.ctxs.items()):
            self.emit_one(fn)
        return self.classes

    def impl_owner(self, fn):
        I = self.I
        sig = fn.sig
        if fn.addr in I.ctor_fns:
            return I.ctor_fns[fn.addr]
        if sig.virtual_slot is not None:
            c = I.impl_class(sig)
            return c or sig.owner
        return sig.owner or self.W.owner_of_code(fn.addr)

    def emit_one(self, fn):
        I = self.I
        sig = fn.sig
        owner = self.impl_owner(fn)
        cf = self.class_for(owner)
        is_ctor = fn.addr in I.ctor_fns
        name = '<init>' if is_ctor else sig.name
        if is_ctor:
            sig.owner = I.ctor_fns[fn.addr]
            sig.static = False
        static = sig.static and not is_ctor
        try:
            desc = I.jvm_desc(sig) if not is_ctor else '(%s)V' % ''.join(I.jvm_kind_list(sig)[1:])
        except Unsupported as e:
            self.failed[fn.addr] = str(e)
            return
        acc = ACC_PUBLIC | (ACC_STATIC if static else 0)
        m = cf.add_method(acc, name, desc, static)
        if fn.failures:
            self.stub_body(m, fn, desc, static)
            self.failed[fn.addr] = fn.failures[0][1]
            return
        try:
            I.emit_fn(fn, m)
        except (Unsupported, ValueError) as e:
            cf.methods.remove(m)
            m = cf.add_method(acc, name, desc, static)
            self.stub_body(m, fn, desc, static)
            self.failed[fn.addr] = 'emit: %s' % e
            return
        self.mapping.append((owner, name, desc, fn.addr))

    def stub_body(self, m, fn, desc, static):
        """Body of a function that could not be lifted: throws a descriptive error (never silently wrong)."""
        c = m.code
        c.emit('iconst', 0) if False else None
        c.ins.append(('iconst', fn.addr))
        c.depth += 1
        c.max_stack = max(c.max_stack, 1)
        c.emit('invokestatic', 'Rt', 'unlifted', '(I)Ljava/lang/Throwable;')
        c.emit('athrow')

    def write(self, outdir):
        os.makedirs(outdir, exist_ok=True)
        for name, cf in self.classes.items():
            p = os.path.join(outdir, name + '.class')
            os.makedirs(os.path.dirname(p), exist_ok=True)
            with open(p, 'wb') as f:
                f.write(cf.to_bytes())
