# kseq.py: build a seq: key script from a compact list. usage: python3 kseq.py "LOAD, CLR 1500, s:m0, OK 1500, ..."
# token "KEY ms" = wait ms (default 700) then press KEY; "s:name" = screenshot; "LOAD" = load slot 1 from the main menu
import sys
LOAD = "u:OK.1500.42.99.de7508;1000:OK;1500:OK;1200:LEFT;600:OK;9000"
def build(spec):
    out = ['seq:']
    parts = []
    for t in [x.strip() for x in spec.split(',') if x.strip()]:
        if t == 'LOAD': parts.append(LOAD); continue
        if t.startswith('s:'): parts.append('500'); parts.append(t); continue
        if t.startswith('w:') or t.startswith('u:'): parts.append(t); continue
        if t.startswith('wait'): parts.append(t.split()[1]); continue
        k = t.split(); ms = k[1] if len(k) > 1 else '700'
        n = 1
        import re
        mm = re.match(r'^(.+)\*(\d+)$', k[0])
        if mm: k0, n = mm.group(1), int(mm.group(2))
        else: k0 = k[0]
        for _ in range(n): parts.append('%s:%s' % (ms, k0))
    return 'seq:' + ';'.join(parts)
if __name__ == '__main__': print(build(sys.argv[1]))
