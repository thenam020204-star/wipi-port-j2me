#!/usr/bin/env python3
"""Reproducible layout analysis of the three native binaries.

Usage: python3 -I tools/binlayout.py <ktf client.bin> <lgt binary.mod> <android libbinary.so>

KTF client.bin is NOT an ELF. Its first 0x70 bytes are a self-relocating Thumb
loader; everything printed for KTF is derived by re-executing that loader's
arithmetic (file offsets cited are the instructions that perform it).
"""
import struct
import sys

from elftools.elf.elffile import ELFFile


def u32(b, o):
    return struct.unpack_from('<I', b, o)[0]


def ktf(path):
    b = open(path, 'rb').read()
    print('== KTF', path, 'size', len(b))
    assert b[:4] == bytes.fromhex('04e0c046'), 'unexpected prologue'
    # file 0x0e: ldr r1,[pc,#0x54] -> lit@0x64 ; 0x10: ldr r2,[pc,#0x54] -> lit@0x68
    # file 0x12: add r1,pc (pc=0x16) ; 0x14: subs r1,r2 ; 0x16..0x18: adds r1,#6
    hdr = u32(b, 0x64) + 0x16 - u32(b, 0x68) + 6
    cnt = u32(b, hdr)
    tab = hdr + 4
    base = tab + 4 * cnt  # r0 = image start (file 0x20: adds r0,r1,r3)
    offs = [u32(b, tab + 4 * i) for i in range(cnt)]
    h = [u32(b, base + 4 * i) for i in range(5)]
    img_len = len(b) - base
    print('reloc header @file %#x: %d entries (table %#x..%#x)' % (hdr, cnt, tab, base))
    print('image start (r0) @file %#x, image length %#x' % (base, img_len))
    print('reloc targets image-relative %#x..%#x, all < image: %s' % (min(offs), max(offs), max(offs) < img_len))
    print('image header: ptrtab=[%#x,%#x) bss=[%#x,%#x) desc=%#x' % tuple(h))
    print('  ptrtab entries %d ; ptrtab_end == image length: %s ; bss_start == ptrtab_end: %s'
          % ((h[1] - h[0]) // 4, h[1] == img_len, h[2] == h[1]))
    print('  bss size %d (%#x)  [filename suffix check at file 0x48: cmp r1,r3]' % (h[3] - h[2], h[3] - h[2]))
    pt = [u32(b, base + h[0] + 4 * i) for i in range(min(4, (h[1] - h[0]) // 4))]
    print('  first ptrtab words', [hex(x) for x in pt])
    d = h[4]
    w = [u32(b, base + d + 4 * i) for i in range(4)]
    name = b[base + w[1]:b.index(b'\0', base + w[1])]
    print('  descriptor @%#x: %s name=%r' % (d, [hex(x) for x in w], name))
    return b, base, h


def elf(path, label):
    print('==', label, path)
    with open(path, 'rb') as f:
        e = ELFFile(f)
        print('type %s machine %s entry %#x flags %#x' % (e['e_type'], e['e_machine'], e['e_entry'], e['e_flags']))
        for s in e.iter_sections():
            if s.name:
                print('  %-16s %-14s addr=%#08x off=%#07x size=%#07x' % (s.name, s['sh_type'], s['sh_addr'],
                                                                        s['sh_offset'], s['sh_size']))
        st = e.get_section_by_name('.symtab')
        if st:
            n = sum(1 for s in st.iter_symbols() if s['st_info']['type'] == 'STT_FUNC' and s['st_shndx'] != 'SHN_UNDEF')
            print('  .symtab: %d symbols, %d defined functions' % (st.num_symbols(), n))
        for rs in ('.rel.text', '.rel.data', '.rel.dyn'):
            r = e.get_section_by_name(rs)
            if r:
                from collections import Counter
                c = Counter(x['r_info_type'] for x in r.iter_relocations())
                print('  %s: %s (2=R_ARM_ABS32, 23=R_ARM_RELATIVE, 21/22=GLOB_DAT/JUMP_SLOT)' % (rs, dict(c)))


if __name__ == '__main__':
    ktf(sys.argv[1])
    elf(sys.argv[2], 'LGT binary.mod')
    elf(sys.argv[3], 'Android libbinary.so')
