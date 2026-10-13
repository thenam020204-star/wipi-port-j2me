import sys, collections
from meta import Meta
import armdec
G=sys.argv[1]
M=Meta(G); B=M.B
im=B.im
THUNK=B.thunks
cnt=collections.Counter(); ex={}
slotc=collections.Counter()
for f in B.func_starts:
    if f>=B.game_end: continue
    insns,data,order=B.decode_function(f)
    for i,a in enumerate(order):
        ins=insns[a]
        if ins.op=='bx' and ins.ops[0][0]=='r' and ins.ops[0][1]!=14:
            rn=ins.ops[0][1]
            # previous must be mov lr,pc
            # backward search for definition of rn
            kind='?'
            for j in range(i-1,max(i-12,-1),-1):
                p=insns[order[j]]
                if p.op in('ldr',) and p.ops[0]==('r',rn):
                    m=p.ops[1]
                    if m[1]==15: 
                        t=a2=order[j]+8+m[5]
                        v=B.word(t)
                        kind='pool'
                        if v in THUNK: kind='thunk'
                        elif B.tb<=v<B.tend: kind='fn'
                        else: kind='poolother'
                    elif m[2] is not None: kind='idx'
                    else:
                        kind='slot'; slotc[m[5]//4]+=1
                    break
                if p.ops and p.ops[0]==('r',rn) and p.op in('mov','add'): kind='reg'; break
            cnt[kind]+=1
            ex.setdefault(kind,(hex(a)))
print(cnt); print(ex)
print(sorted(slotc.items()))
