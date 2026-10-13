import struct
from funcs import load
im, tb, td, rels = load()
T = dict(pub=0xf5d14, imp=0xf5d60, inst=0xf5014, stat=0xf500c, virt=0xf4e94, iface=0xf4e7c, nonvirt=0xf4b3c)
def cstr(p): 
    s = im.cstr(p); return s.decode('latin1') if s is not None else None
def member(tbl, i):
    b = T[tbl] + i*8
    return cstr(im.u32(b)), cstr(im.u32(b+4))
def links(base):
    n = im.u32(base)
    out = []
    for k in range(n):
        o = base + 4 + k*0x18
        name = cstr(im.u32(o))
        v = struct.unpack_from('<10H', bytes(im.bytes(o+4, 20)))
        out.append(dict(name=name, inst=(v[0],v[1]), stat=(v[2],v[3]), virt=(v[4],v[5]), iface=(v[6],v[7]), nonvirt=(v[8],v[9])))
    return out
pub = links(T['pub']); imp = links(T['imp'])
if __name__ == '__main__':
    print('PUBLIC classes', len(pub))
    for c in pub: print(' ', c)
    print('IMPORTED classes', len(imp))
    for c in imp:
        print('==', c['name'], {k:v for k,v in c.items() if k!='name'})
        for tbl,key in (('inst','inst'),('stat','stat'),('virt','virt'),('iface','iface'),('nonvirt','nonvirt')):
            o,n = c[key]
            for i in range(o, o+n):
                print('   ', key, i, *member(tbl, i))
