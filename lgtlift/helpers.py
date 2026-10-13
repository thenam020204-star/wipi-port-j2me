import sys, collections
from meta import Meta
G=sys.argv[1]
M=Meta(G); B=M.B
cnt=collections.Counter()
for f in B.func_starts:
    if f>=B.game_end: continue
    insns,data,order=B.decode_function(f)
    for a in data:
        v=data[a]
        if B.game_end<=(v&~1)<B.tend and a in B.reloc_words:
            cnt[v]+=1
print(sorted((hex(k),v) for k,v in cnt.items()))
print(hex(B.tb),hex(B.tend))
