import struct, sys, re
from elfimg import Img
def printable(s):
    return s is not None and 0 < len(s) < 160 and all(32 <= c < 127 for c in s)
def scan(im):
    """Find class records: {rec_addr: dict}. Validation: method/field structs point back to rec."""
    res = {}
    for nm,(base,d) in im.mem.items():
        if nm == '.bss': continue
        for a in range(base, base+len(d)-12, 4):
            pd = im.u32(a+8)
            f = im.find(pd)
            if not f or pd+0x4c > f[1]+len(f[2]): continue
            pname = im.u32(pd+8)
            n = im.cstr(pname) if im.find(pname) else None
            if not printable(n): continue
            pm = im.u32(pd+0x38); pf = im.u32(pd+0x3c)
            ok = False
            if pm and im.find(pm):
                cnt = im.u32(pm)
                if 0 < cnt < 5000 and im.u32(pm+4) == a: ok = True
            if not ok and pf and im.find(pf):
                cnt = im.u32(pf)
                if 0 < cnt < 5000 and im.u32(pf+4) == a: ok = True
            if not ok: continue
            res[a] = dict(rec=a, desc=pd, name=n.decode(), access=im.u32(pd), nxt=im.u32(pd+4), vt=im.u32(pd+12), sup=im.u32(pd+16),
                          flags=im.bytes(pd+0x1c+8,1)[0] if False else None, pm=pm, pf=pf)
    return res
if __name__ == '__main__':
    im = Img(sys.argv[1])
    r = scan(im)
    print(len(r))
    for a,c in sorted(r.items()):
        print(hex(a), c['name'], hex(c['access']), 'super', hex(c['sup']), 'methods', im.u32(c['pm']) if c['pm'] else 0, 'fields', im.u32(c['pf']) if c['pf'] else 0)
