import sys, collections
from meta import Meta
G=sys.argv[1]
M=Meta(G); B=M.B
cnt=collections.Counter(); starts=set(B.func_starts)
for f in B.func_starts:
    if f>=B.game_end: continue
    insns,data,order=B.decode_function(f)
    for a in order:
        i=insns[a]
        if i.op=='bl':
            cnt[i.ops[0][1]]+=1
print(len(cnt))
nf=[(hex(k),v) for k,v in cnt.items() if k not in starts]
print('non-func-start targets', nf[:40])
print(sorted(((hex(k),v) for k,v in cnt.items()), key=lambda x:-x[1])[:25])
