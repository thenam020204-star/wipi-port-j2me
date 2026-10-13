import sys
from elfimg import Img
from classes import scan
def methods(im, c):
    pm = c['pm']; out=[]
    if not pm: return out
    n = im.u32(pm)
    for i in range(n):
        o = pm+4+i*0x1c
        out.append(dict(cls=im.u32(o), name=im.cstr(im.u32(o+4)).decode(), desc=im.cstr(im.u32(o+8)).decode(), acc=im.u16(o+12), argw=im.u16(o+14), unk3=im.u32(o+16), code=im.u32(o+20), unk4=im.u32(o+24), idx=i))
    return out
def fields(im, c):
    pf = c['pf']; out=[]
    if not pf: return out
    n = im.u32(pf)
    for i in range(n):
        o = pf+4+i*0x14
        out.append(dict(cls=im.u32(o), name=im.cstr(im.u32(o+4)).decode(), desc=im.cstr(im.u32(o+8)).decode(), flags=im.u16(o+12), unk2=im.u16(o+14), word=im.u32(o+16), idx=i))
    return out
if __name__=='__main__':
    im = Img(sys.argv[1]); r = scan(im)
    for a,c in sorted(r.items()):
        print('==', hex(a), c['name'], 'super', im.cstr(c['sup']) if c['sup'] > 0xf0000 else hex(c['sup']), 'vt', hex(c['vt']), 'iw', im.u16(c['desc']+24))
        for m in methods(im,c):
            print('  M', m['idx'], m['name'], m['desc'], hex(m['acc']), 'aw', m['argw'], 'code', hex(m['code']), hex(m['unk3']), hex(m['unk4']))
        if len(sys.argv)>2:
            for f in fields(im,c):
                print('  F', f['idx'], f['name'], f['desc'], hex(f['flags']), hex(f['unk2']), 'w', f['word'])
