import sys, collections
from meta import Meta
G=sys.argv[1]
M=Meta(G); B=M.B
c=collections.Counter(); cc=collections.Counter(); sh=collections.Counter(); unk=[]
tot=0
for f in B.func_starts:
    if f>=B.game_end: continue
    insns,data,order=B.decode_function(f)
    for a in order:
        i=insns[a]; tot+=1
        key=i.op+('s' if i.s else '')
        c[(key)]+=1
        if i.cc!='al': cc[(i.cc, i.op)]+=1
        if i.op=='unk': unk.append(i)
print('total',tot)
print(sorted(c.items(), key=lambda x:-x[1]))
print()
print(sorted(cc.items(), key=lambda x:-x[1])[:60])
print(len(unk), unk[:10])
