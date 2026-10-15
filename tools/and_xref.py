#!/usr/bin/env python3
"""Global-memory cross references for AND2011 (from andelf.annotate over every function).
Usage:
  python3 tools/and_xref.py build <binary.mod> <cache.tsv>          # annotate all functions once
  python3 tools/and_xref.py q <cache.tsv> <regex on target> [...]   # who touches a symbol(+off)
Cache columns: func, insn_addr, kind(load/store/addr/call), target_vaddr, target_name, insn."""
import os, re, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

def build(binp, out):
    from andelf import AndElf
    e = AndElf(binp)
    rx_mem = re.compile(r'mem (0x[0-9a-f]+) (\S*)')
    rx_got = re.compile(r'GOT\[0x[0-9a-f]+\] -> (0x[0-9a-f]+) (\S*)')
    rx_off = re.compile(r'GOTOFF (0x[0-9a-f]+) (\S*)')
    rx_call = re.compile(r'(?:tail)?-> (\S+)')
    n = 0
    with open(out, 'w', encoding='utf-8') as f:
        for a, sz, fn, t in e.funcs:
            if not sz: continue
            for i, note in e.annotate(fn):
                if not note: continue
                ins = '%s %s' % (i.mnemonic, i.op_str)
                m = rx_mem.search(note)
                if m:
                    kind = 'store' if i.mnemonic.startswith('st') else 'load'
                    f.write('%s\t%#x\t%s\t%s\t%s\t%s\n' % (fn, i.address, kind, m.group(1), m.group(2), ins)); n += 1
                    continue
                for rx, kind in ((rx_got, 'addr'), (rx_off, 'addr')):
                    m = rx.search(note)
                    if m:
                        f.write('%s\t%#x\t%s\t%s\t%s\t%s\n' % (fn, i.address, kind, m.group(1), m.group(2), ins)); n += 1
                        break
                else:
                    m = rx_call.search(note)
                    if m:
                        f.write('%s\t%#x\tcall\t-\t%s\t%s\n' % (fn, i.address, m.group(1), ins)); n += 1
    print('rows', n)

def query(cache, pats):
    rx = [re.compile(p) for p in pats]
    for line in open(cache, encoding='utf-8'):
        c = line.rstrip('\n').split('\t')
        if any(r.search(c[4]) for r in rx):
            print('%-34s %-9s %-6s %-30s %s' % (c[0], c[1], c[2], c[4], c[5]))

if __name__ == '__main__':
    if sys.argv[1] == 'build': build(sys.argv[2], sys.argv[3])
    else: query(sys.argv[2], sys.argv[3:])
