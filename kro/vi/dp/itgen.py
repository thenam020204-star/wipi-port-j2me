#!/usr/bin/env python3
# itgen.py: nối từ điển tay (it_lex.py: STEM/CAT/NAME) thành bản dịch cho IT.tsv; in các tên chưa có.
import re, sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import it_lex
from it_lex import STEM, CAT
NAME = getattr(it_lex, 'NAME', {})
PRE = [('고급', 'Cao cấp'), ('어퍼', 'Upper'), ('강화', 'Cường hóa'), ('하드', 'Hard')]
def res(n, depth=0):
    if n in NAME: return NAME[n]
    if n.strip() in NAME: return NAME[n.strip()]
    m = re.match(r'^(.+?)의(.+)$', n)
    if m and m.group(1) in STEM and m.group(2) in CAT and ' ' not in n:
        return CAT[m.group(2)] + ' ' + STEM[m.group(1)]
    for p, v in PRE:
        if n.startswith(p) and depth < 2:
            r = res(n[len(p):], depth + 1)
            if r: return v + ' ' + r
    return None
if __name__ == '__main__':
    done = {}
    out = os.path.join(sys.argv[1], 'dp_work', 'out.tsv') if len(sys.argv) > 1 else None
    rows = [l.rstrip('\n').split('\t') for l in open('IT.tsv', encoding='utf-8')][1:]
    work = [l.rstrip('\n').split('\t', 1) for l in open(os.path.join(sys.argv[1], 'dp_work', 'work.tsv'), encoding='utf-8') if l.strip()]
    have = {}
    if os.path.exists(out):
        for l in open(out, encoding='utf-8'):
            if l.strip(): k, s = l.rstrip('\n').split('\t', 1); have[int(k)] = s
    res_k, miss = [], []
    for k, ko in work:
        k = int(k)
        if k in have: continue
        r = res(ko)
        if r: res_k.append((k, r))
        else: miss.append((k, ko))
    mode = sys.argv[2] if len(sys.argv) > 2 else 'miss'
    if mode == 'miss':
        print(len(res_k), 'tự động,', len(miss), 'thiếu')
        print(' '.join('%d=%s' % (k, ko) for k, ko in miss[:int(sys.argv[3]) if len(sys.argv) > 3 else 400]))
    else:
        for k, r in res_k: print('%d\t%s' % (k, r))
