"""Metadata of an LGT WIPI-Java binary: classes, members, import tables, string literals."""
import struct
from binimg import Binary

DESC_OFF = dict(access=0, nxt=4, name=8, vtable=12, sup=16, iref=20, iw=24, link=26, bitmap=32, flags=36,
                vcount=38, inames=40, fn_link=44, fn_getinit=48, fn_getcls=52, methods=56, fields=60,
                cfields=64, static_words=72)


class ClassInfo:
    def __init__(self):
        self.fields = []     # dict(name, desc, flags, word, idx)
        self.methods = []    # dict(name, desc, acc, argw, code, idx)
        self.vtable = []     # list of code addresses (slot 1..)
        self.public = False


class Meta:
    def __init__(self, path):
        self.B = Binary(path)
        self.im = self.B.im
        self.classes = {}      # name -> ClassInfo
        self.by_rec = {}
        self.by_desc = {}
        self._find_classes()
        self._read_import_tables()
        self._read_strings()
        self._link_hierarchy()

    # ---- helpers
    def cstr(self, p):
        s = self.im.cstr(p)
        return s.decode('latin1') if s is not None else None

    def _find_classes(self):
        im = self.im
        db, dd = im.mem['.data']
        # a class record is {vtab?, ?, ptr_descriptor}; descriptor.name points to the class name string.
        # All game classes live in .data; find descriptors by validating next-pointer/name patterns.
        cands = {}
        for a in range(db, db + len(dd) - 12, 4):
            pd = im.u32(a + 8)
            f = im.find(pd)
            if not f or f[0] != '.data' or pd + 76 > f[1] + len(f[2]):
                continue
            pn = im.u32(pd + 8)
            fn = im.find(pn)
            if not fn or fn[0] != '.text':
                continue
            nm = self.cstr(pn)
            if not nm or not all(32 <= ord(c) < 127 for c in nm) or len(nm) > 64:
                continue
            # the descriptor's three link functions must be code addresses
            ok = all(self.B.tb <= im.u32(pd + o) < self.B.tend for o in (44, 48, 52))
            if ok:
                cands[a] = (pd, nm)
        for rec, (pd, nm) in cands.items():
            c = ClassInfo()
            c.name = nm; c.rec = rec; c.desc = pd
            c.access = im.u32(pd)
            c.iw = im.u16(pd + 24)
            c.static_words = im.u32(pd + 72)
            c.vcount = im.u16(pd + 38)
            c.flags = im.bytes(pd + 36, 1)[0]
            c.fn_link, c.fn_getinit, c.fn_getcls = im.u32(pd + 44), im.u32(pd + 48), im.u32(pd + 52)
            c.sup_raw = im.u32(pd + 16)
            c.bitmap_ptr = im.u32(pd + 32)
            c.iface_names_ptr = im.u32(pd + 40)
            self.classes[nm] = c
            self.by_rec[rec] = c
            self.by_desc[pd] = c
        # fields / methods (only present for classes that have tables)
        for c in self.classes.values():
            pf = im.u32(c.desc + 60)
            if pf:
                n = im.u32(pf)
                for i in range(n):
                    o = pf + 4 + i * 20
                    c.fields.append(dict(idx=i, name=self.cstr(im.u32(o + 4)), desc=self.cstr(im.u32(o + 8)),
                                         flags=im.u16(o + 12), word=im.u32(o + 16)))
            pm = im.u32(c.desc + 56)
            if pm:
                n = im.u32(pm)
                for i in range(n):
                    o = pm + 4 + i * 28
                    c.methods.append(dict(idx=i, name=self.cstr(im.u32(o + 4)), desc=self.cstr(im.u32(o + 8)),
                                          acc=im.u16(o + 12), argw=im.u16(o + 14), code=im.u32(o + 20)))
            vt = im.u32(c.desc + 12)
            if vt:
                c.vtable = [im.u32(vt + 4 * (i + 1)) for i in range(c.vcount)]

    def _link_hierarchy(self):
        im = self.im
        for c in self.classes.values():
            sp = c.sup_raw
            if not sp:
                c.super = None
            elif sp in self.by_rec:
                c.super = self.by_rec[sp].name
            else:
                s = self.cstr(sp)
                c.super = s
            # interface names
            c.interfaces = []
            if c.iface_names_ptr:
                n = im.u32(c.iface_names_ptr)
                for i in range(min(n, 32)):
                    c.interfaces.append(self.cstr(im.u32(c.iface_names_ptr + 4 + 4 * i)))

    # ---- import tables (see wie: link_class_members)
    TBL = dict(pub=0xf5d14, imp=0xf5d60, inst=0xf5014, stat=0xf500c, virt=0xf4e94, iface=0xf4e7c, nonvirt=0xf4b3c)
    BSS = dict(finst=0x1500b00, fstat=0x1500e9e, vidx=0x1500e40, iidx=0x1500ea0, nvt=0x1500960, str=0x1500000)

    def _links(self, base):
        im = self.im
        n = im.u32(base)
        out = []
        for k in range(n):
            o = base + 4 + k * 0x18
            v = struct.unpack_from('<10H', im.bytes(o + 4, 20))
            out.append(dict(name=self.cstr(im.u32(o)), inst=v[0:2], stat=v[2:4], virt=v[4:6], iface=v[6:8], nonvirt=v[8:10]))
        return out

    def member(self, tbl, i):
        b = self.TBL[tbl] + i * 8
        return self.cstr(self.im.u32(b)), self.cstr(self.im.u32(b + 4))

    def _read_import_tables(self):
        self.pub_links = self._links(self.TBL['pub'])
        self.imp_links = self._links(self.TBL['imp'])
        self.inst_imp = {}    # k -> (owner, name, desc)
        self.stat_imp = {}
        self.virt_imp = {}
        self.iface_imp = {}
        self.nv_imp = {}      # k -> (owner, name, desc) with name None for class getters (local idx 0/1)
        for tag, links in (('pub', self.pub_links), ('imp', self.imp_links)):
            for L in links:
                for key, tbl, store in (('inst', 'inst', self.inst_imp), ('stat', 'stat', self.stat_imp),
                                        ('virt', 'virt', self.virt_imp), ('iface', 'iface', self.iface_imp),
                                        ('nonvirt', 'nonvirt', self.nv_imp)):
                    o, n = L[key]
                    for loc in range(n):
                        k = o + loc
                        name, desc = self.member(tbl, k)
                        if key == 'nonvirt' and loc == 0:
                            name, desc = '<clinitget>', None
                        elif key == 'nonvirt' and loc == 1:
                            name, desc = '<classget>', None
                        store[k] = (L['name'], name, desc, tag == 'pub')

    def _read_strings(self):
        im = self.im
        a = 0x1400170
        self.strings = []
        while True:
            p = im.u32(a + 4 * len(self.strings))
            f = im.find(p)
            if not f or f[0] != '.text':
                break
            ln = im.u16(p)
            if ln > 4000:
                break
            self.strings.append(im.bytes(p + 2, ln * 2).decode('utf-16-le', 'surrogatepass'))
        self.str_table = a

    # ---- queries
    def method_at(self, addr):
        for c in self.classes.values():
            for m in c.methods:
                if m['code'] == addr:
                    return c, m
        return None, None

if __name__ == '__main__':
    import sys
    M = Meta(sys.argv[1])
    for n, c in sorted(M.classes.items()):
        print(n, hex(c.rec), 'super', c.super, 'iw', c.iw, 'fields', len(c.fields), 'methods', len(c.methods), 'vt', len(c.vtable), 'ifaces', c.interfaces)
    print('strings', len(M.strings))
