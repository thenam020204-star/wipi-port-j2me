#!/usr/bin/env python3
"""Scan Android libbinary.so: map PLT imports, find which named functions call
soft-float helpers (__aeabi_f*/d*) and each import. Usage: so_floatscan.py <libbinary.so>"""
import sys, collections, capstone
from elftools.elf.elffile import ELFFile
from elftools.elf.relocation import RelocationSection

f = open(sys.argv[1], 'rb'); e = ELFFile(f); data = open(sys.argv[1], 'rb').read()
symtab = e.get_section_by_name('.symtab'); dynsym = e.get_section_by_name('.dynsym')
plt = e.get_section_by_name('.plt'); relplt = e.get_section_by_name('.rel.plt')
text = e.get_section_by_name('.text')
# ARM PLT: 20-byte header, 12 bytes per entry, order = .rel.plt order
pltmap = {}
for i, r in enumerate(relplt.iter_relocations()):
    pltmap[plt['sh_addr'] + 20 + 12 * i] = dynsym.get_symbol(r['r_info_sym']).name
funcs = []
for s in symtab.iter_symbols():
    if s['st_info']['type'] == 'STT_FUNC' and s['st_shndx'] != 'SHN_UNDEF' and s['st_size'] > 0:
        funcs.append((s['st_value'] & ~1, s['st_value'] & 1, s['st_size'], s.name))
funcs = sorted(set(funcs))
cs_t = capstone.Cs(capstone.CS_ARCH_ARM, capstone.CS_MODE_THUMB)
cs_a = capstone.Cs(capstone.CS_ARCH_ARM, capstone.CS_MODE_ARM)
calls = collections.defaultdict(set)
mode = collections.Counter()
for a, thumb, size, name in funcs:
    mode['thumb' if thumb else 'arm'] += 1
    off = a - text['sh_addr'] + text['sh_offset']
    md = cs_t if thumb else cs_a
    for ins in md.disasm(data[off:off + size], a):
        if ins.mnemonic in ('bl', 'blx') and ins.op_str.startswith('#'):
            t = int(ins.op_str[1:], 16)
            if t in pltmap:
                calls[pltmap[t]].add(name)
print('functions', len(funcs), dict(mode))
for imp in sorted(calls):
    if imp.startswith('__aeabi_') and imp[8] in 'fd' or imp.startswith('__aeabi_i2') or imp.startswith('__aeabi_d') or imp.startswith('__aeabi_f'):
        print('%-18s %3d  %s' % (imp, len(calls[imp]), ' '.join(sorted(calls[imp]))[:600]))
print('--- import usage counts')
print(' '.join('%s:%d' % (k, len(v)) for k, v in sorted(calls.items())))
