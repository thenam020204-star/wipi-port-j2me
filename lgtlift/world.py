"""Static knowledge about the LGT Java binary: classes, fields, methods, vtables, import tables.

Everything here is derived from the binary (member tables, descriptors, ref bitmaps, vtables, import tables); the
confidence of each derived fact is documented next to it.
"""
import re
from meta import Meta
import coreabi

LOCAL_FRAMEWORK_SUPERS = ('org/kwis/msp/lcdui/Jlet', 'org/kwis/msp/lcdui/Card', 'org/kwis/msp/lwc/TextBoxComponent')

# static (no receiver) framework methods: (owner, name) -> True.  Evidence: the call pattern passes no receiver in r0
# (checked per call site by the lifter) and the public CLDC/WIPI API.  Confidence HIGH for the entries below.
FRAMEWORK_STATIC = {
    ('org/kwis/msp/media/Vibrator', 'on'), ('org/kwis/msp/media/Player', 'play'), ('org/kwis/msp/media/Player', 'stop'),
    ('org/kwis/msp/io/FileSystem', 'remove'), ('org/kwis/msp/io/FileSystem', 'exists'),
    ('java/lang/Integer', 'parseInt'), ('org/kwis/msp/lcdui/Font', 'getDefaultFont'), ('java/lang/Math', 'abs'),
    ('org/kwis/msp/handset/BackLight', 'alwaysOn'), ('org/kwis/msp/handset/HandsetProperty', 'getSystemProperty'),
    ('org/kwis/msf/io/URL', 'find'), ('org/kwis/msf/io/Network', 'disconnect'), ('org/kwis/msf/io/Network', 'connect'),
    ('java/lang/Thread', 'sleep'), ('java/lang/Thread', 'yield'), ('org/kwis/msp/lcdui/Display', 'getDefaultDisplay'),
    ('java/lang/System', 'currentTimeMillis'), ('java/lang/System', 'arraycopy'), ('java/lang/System', 'gc'),
    ('java/lang/String', 'valueOf'), ('org/kwis/msp/lcdui/Image', 'createImage'),
}


class Fld:
    __slots__ = ('owner', 'word', 'name', 'desc', 'isref', 'wide')

    def __init__(self, owner, word, name, desc, isref, wide=False):
        self.owner, self.word, self.name, self.desc, self.isref, self.wide = owner, word, name, desc, isref, wide

    def __repr__(self):
        return '%s.%s:%s@%d' % (self.owner, self.name, self.desc, self.word)


class Mth:
    __slots__ = ('owner', 'name', 'desc', 'acc', 'addr', 'slot', 'kind', 'static')

    def __init__(self, owner, name, desc, acc, addr, slot=None, kind='fn', static=False):
        self.owner, self.name, self.desc, self.acc, self.addr, self.slot, self.kind, self.static = \
            owner, name, desc, acc, addr, slot, kind, static

    def __repr__(self):
        return 'Mth(%s.%s%s @%x slot=%s)' % (self.owner, self.name, self.desc, self.addr or 0, self.slot)


class ClassModel:
    def __init__(self, c):
        self.info = c
        self.name = c.name
        self.jsuper = c.super
        self.ifaces = list(c.interfaces)
        self.iw = c.iw
        self.vcount = c.vcount
        self.static_words = c.static_words
        self.local_super = None          # ClassModel or None
        self.fields = {}                 # word -> Fld (declared by the member table)
        self.methods = {}                # addr -> Mth (member table)
        self.vslots = {}                 # slot(method index) -> code addr (own non-zero vtable entries)
        self.ref_words = []              # list[bool] over iw words
        self.own_start = 0
        self.code_lo = self.code_hi = None
        self.is_interface = False


class World:
    def __init__(self, path):
        self.M = Meta(path)
        self.B = self.M.B
        self.im = self.B.im
        self.cls = {}
        for n, c in self.M.classes.items():
            self.cls[n] = ClassModel(c)
        for cm in self.cls.values():
            sup = cm.jsuper
            cm.local_super = self.cls.get(sup)
        self._fields()
        self._bitmaps()
        self._methods()
        self._ranges()
        self.classrec_fns = {}      # function address -> class name (class-record getters)
        for cm in self.cls.values():
            for a in (cm.info.fn_getinit, cm.info.fn_getcls):
                self.classrec_fns[a] = cm.name
        self.skip_fns = set()       # runtime support functions that must not be lifted
        for cm in self.cls.values():
            self.skip_fns.update((cm.info.fn_link, cm.info.fn_getinit, cm.info.fn_getcls))

    # ---- fields
    def _fields(self):
        for cm in self.cls.values():
            c = cm.info
            for f in c.fields:
                desc = f['desc']
                wide = desc in ('J', 'D')
                cm.fields[f['word']] = Fld(cm.name, f['word'], f['name'], desc, desc[0] in 'L[', wide)

    def _bitmaps(self):
        im = self.im
        for cm in self.cls.values():
            c = cm.info
            refs = [False] * cm.iw
            if c.bitmap_ptr:
                nb = (cm.iw + 7) // 8
                bm = im.bytes(c.bitmap_ptr, nb)
                for w in range(cm.iw):
                    refs[w] = bool(bm[w >> 3] & (0x80 >> (w & 7)))
            else:
                for w, f in cm.fields.items():
                    if w < cm.iw:
                        refs[w] = f.isref
            cm.ref_words = refs
            cm.own_start = cm.local_super.iw if cm.local_super else 0

    # ---- methods
    def _methods(self):
        for cm in self.cls.values():
            c = cm.info
            for m in c.methods:
                cm.methods[m['code']] = Mth(cm.name, m['name'], m['desc'], m['acc'], m['code'])
            for s, a in enumerate(c.vtable):
                if a:
                    cm.vslots[s] = a
        self.mth_by_addr = {}
        for cm in self.cls.values():
            for a, m in cm.methods.items():
                self.mth_by_addr[a] = m

    def _ranges(self):
        """Code ranges per class: functions between this class's link stub and the next class's link stub."""
        recs = sorted(((cm.info.fn_link, cm.name) for cm in self.cls.values()))
        for i, (a, n) in enumerate(recs):
            hi = recs[i + 1][0] if i + 1 < len(recs) else self.B.game_end
            self.cls[n].code_lo, self.cls[n].code_hi = a, hi
        self._range_list = recs

    def owner_of_code(self, addr):
        best = None
        for a, n in self._range_list:
            if a <= addr:
                best = n
            else:
                break
        return best

    # ---- hierarchy helpers
    def chain(self, name):
        out = []
        cm = self.cls.get(name)
        while cm:
            out.append(cm)
            cm = cm.local_super
        return out

    def is_local(self, name):
        return name in self.cls

    def framework_super(self, name):
        cm = self.cls.get(name)
        while cm and cm.local_super:
            cm = cm.local_super
        return cm.jsuper if cm else None

    def field_decl(self, cname, word):
        """Class that declares instance word `word` for instances of class cname (HIGH: layout from iw)."""
        for cm in self.chain(cname):
            if cm.own_start <= word < cm.iw:
                return cm
        return None

    def field_at(self, cname, word):
        """Return (declaring ClassModel, Fld or None)."""
        d = self.field_decl(cname, word)
        if not d:
            return None, None
        return d, d.fields.get(word)

    def word_is_ref(self, cname, word):
        cm = self.cls.get(cname)
        if cm and word < len(cm.ref_words):
            return cm.ref_words[word]
        return None

    def subclasses(self, name):
        return [cm for cm in self.cls.values() if name in (c.name for c in self.chain(cm.name))]

    def subtype(self, a, b):
        """a is subtype of b among local classes / known framework supers (conservative)."""
        if a == b:
            return True
        for cm in self.chain(a):
            if cm.name == b or cm.jsuper == b or b in cm.ifaces:
                return True
        return b == 'java/lang/Object'

    # ---- virtual slot lookup for local classes
    def vslot_impl(self, cname, slot):
        """Most-derived code address implementing method index `slot` for local class cname (HIGH)."""
        for cm in self.chain(cname):
            if slot in cm.vslots:
                return cm.vslots[slot]
        return None

    def vslot_root(self, cname, slot):
        """Topmost local class in the chain whose vtable has this slot (declaring class for JVM naming)."""
        root = None
        for cm in self.chain(cname):
            if cm.vcount > slot:
                root = cm
        return root

    def core_method(self, cname, slot):
        """Core (fixed-layout) library method at method-index slot, if known from the ABI table (HIGH from wie data)."""
        e = coreabi.CORE.get(cname)
        if e and slot in e['vtable']:
            return e['vtable'][slot]
        o = coreabi.CORE['java/lang/Object']
        if slot in o['vtable']:
            return o['vtable'][slot]
        return None


if __name__ == '__main__':
    import sys
    W = World(sys.argv[1])
    for n, cm in sorted(W.cls.items()):
        print(n, 'range %x-%x' % (cm.code_lo, cm.code_hi), 'own_start', cm.own_start, 'iw', cm.iw, 'refs',
              sum(cm.ref_words), 'vslots', len(cm.vslots))
    # sanity: f ref bitmap vs member table
    f = W.cls['f']
    bad = [(w, fl) for w, fl in f.fields.items() if f.ref_words[w] != fl.isref]
    print('f field ref mismatches', bad[:5], len(bad))
