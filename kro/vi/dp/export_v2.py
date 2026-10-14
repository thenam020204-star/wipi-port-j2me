#!/usr/bin/env python3
"""v2: catalog.json -> nguồn dp (id, gốc, dịch, ghi chú, giới hạn) cho thoại/quest/sự kiện.
   dp/DIAL.tsv (S:DIAL.dat), dp/QUEST.tsv (T:QUEST0/00), dp/EV.tsv (S:EV##), dp/Q1.tsv (S:QUEST1)
ghi chú = người nói (tên Hàn) hoặc balloon/typing/typing2/scene/select/scroll; giới hạn = ký tự (gần đúng byte,
build_vi.py kiểm chính xác)."""
import json, os, re
D = os.path.dirname(os.path.abspath(__file__))
cat = json.load(open(os.path.join(D, '..', 'catalog.json'), encoding='utf-8'))
esc = lambda t: t.replace('\r', '\\r').replace('\n', '\\n').replace('\t', '\\t')
groups = {'DIAL': r'S:DIAL\.dat:', 'QUEST': r'T:QUEST0+\.dat:', 'EV': r'S:EV\d\d\.dat:', 'Q1': r'S:QUEST1\.dat:'}
for g, pat in groups.items():
    rows = []
    for e in cat:
        if not re.match(pat, e['id']): continue
        lim = ''
        if e.get('kind') == 'arg': lim = 37 if e['ctx'] in ('balloon', 'scene') else 39
        elif e['id'].startswith('T:'):
            lim = e['max'] if e['max'] < 254 else ''
        ctx = e.get('ctx', 'tên quest' if e['id'].endswith(':31') else 'mô tả quest')
        rows.append('%s\t%s\t\t%s\t%s' % (e['id'], esc(e['ko']), ctx, lim))
    open(os.path.join(D, g + '.tsv'), 'w', encoding='utf-8').write('id\tgốc\tdịch\tghi chú\tgiới hạn\n' + '\n'.join(rows) + '\n')
    print(g, len(rows))
