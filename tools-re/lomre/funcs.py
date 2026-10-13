import struct, sys
from elfimg import Img
import os
G=os.environ.get('LGT_BINARY', 'binary.mod')
def load():
    im = Img(G)
    tb, td = im.mem['.text']
    rels = []
    for nm,typ,addr,off,size in im.secs:
        if nm=='.rel.text':
            rels = [struct.unpack_from('<II', im.b, off+i*8) for i in range(size//8)]
    return im, tb, td, rels
if __name__=='__main__':
    im, tb, td, rels = load()
    relset = {o for o,i in rels}
    print('reloc words in text', len(relset), 'range', hex(min(relset)), hex(max(relset)))
    # APCS prologue: mov ip,sp (e1a0c00d)
    pro = []
    for a in range(tb, tb+len(td)-8, 4):
        w = struct.unpack_from('<I', td, a-tb)[0]
        if w == 0xe1a0c00d:
            w2 = struct.unpack_from('<I', td, a-tb+4)[0]
            if (w2 & 0xffff0000) == 0xe92d0000: pro.append(a)
    print('apcs prologues', len(pro), hex(pro[0]), hex(pro[-1]))
    # which reloc'd words are in text and what do they point to
    from collections import Counter
    c = Counter()
    for o in relset:
        v = struct.unpack_from('<I', td, o-tb)[0]
        f = im.find(v)
        c[f[0] if f else 'none'] += 1
    print(c)
