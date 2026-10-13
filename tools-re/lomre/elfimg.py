"""Flat memory image of an LGT binary.mod (ELF32 ARM, section based)."""
import struct
class Img:
    def __init__(self, path):
        self.b = open(path,'rb').read()
        b = self.b
        shoff = struct.unpack_from('<I', b, 0x20)[0]
        shentsize, shnum, shstrndx = struct.unpack_from('<HHH', b, 0x2E)
        self.entry = struct.unpack_from('<I', b, 0x18)[0]
        self.secs = []
        shstr = None
        raw = []
        for i in range(shnum):
            name, typ, flags, addr, off, size, link, info, align, entsz = struct.unpack_from('<10I', b, shoff+i*shentsize)
            raw.append((name,typ,flags,addr,off,size))
        stroff = raw[shstrndx][4]
        self.mem = {}
        for name,typ,flags,addr,off,size in raw:
            nm = b[stroff+name: b.index(b'\0', stroff+name)].decode()
            self.secs.append((nm,typ,addr,off,size))
            if addr and size:
                data = b[off:off+size] if typ != 8 else bytes(size)
                self.mem[nm] = (addr, bytearray(data))
    def find(self, a):
        for nm,(base,d) in self.mem.items():
            if base <= a < base+len(d): return nm,base,d
        return None
    def u32(self, a):
        r = self.find(a)
        if not r: raise KeyError(hex(a))
        _,base,d = r
        return struct.unpack_from('<I', d, a-base)[0]
    def u16(self, a):
        _,base,d = self.find(a)
        return struct.unpack_from('<H', d, a-base)[0]
    def cstr(self, a, maxlen=300):
        r = self.find(a)
        if not r: return None
        _,base,d = r
        o = a-base
        e = d.find(b'\0', o, o+maxlen)
        if e < 0: return None
        return bytes(d[o:e])
    def bytes(self, a, n):
        _,base,d = self.find(a)
        return bytes(d[a-base:a-base+n])
