#!/usr/bin/env python3
"""Shared helpers for the LGT-Android 2011 build (binary.mod == libbinary.so code).

- symbols (.symtab), PLT import map, GOT contents
- annotated ARM disassembly: resolves PC-relative literals, the GCC PIC idiom
  (ldr rX,[pc,#k]; add rX,pc,rX -> GOT base; ldr rY,[rX,rZ] -> GOT slot -> global),
  GOTOFF data addresses (add rY,rX,rZ), BL targets to functions/PLT imports.
The tracker is a straight-line constant propagator (reset at branch targets), so
annotations are evidence aids; every conclusion drawn from them cites the address.
"""
import bisect
import struct

import capstone
from elftools.elf.elffile import ELFFile


class AndElf:
    def __init__(self, path):
        self.path = path
        self.data = open(path, 'rb').read()
        self.f = open(path, 'rb')
        self.e = ELFFile(self.f)
        self.secs = [s for s in self.e.iter_sections()]
        st = self.e.get_section_by_name('.symtab')
        self.funcs = sorted({(s['st_value'] & ~1, s['st_size'], s.name, s['st_value'] & 1)
                             for s in st.iter_symbols()
                             if s['st_info']['type'] == 'STT_FUNC' and s['st_shndx'] != 'SHN_UNDEF'})
        self.objs = sorted({(s['st_value'], s['st_size'], s.name)
                            for s in st.iter_symbols()
                            if s['st_info']['type'] == 'STT_OBJECT' and s['st_shndx'] != 'SHN_UNDEF'})
        self.fbyname = {n: (a, sz, t) for a, sz, n, t in self.funcs}
        self.fstarts = [f[0] for f in self.funcs]
        self.ostarts = [o[0] for o in self.objs]
        dyn = self.e.get_section_by_name('.dynsym')
        plt = self.e.get_section_by_name('.plt')
        self.plt = {}
        for i, r in enumerate(self.e.get_section_by_name('.rel.plt').iter_relocations()):
            self.plt[plt['sh_addr'] + 20 + 12 * i] = dyn.get_symbol(r['r_info_sym']).name
        got = self.e.get_section_by_name('.got')
        self.got_base = got['sh_addr']
        self.got = (got['sh_addr'], got['sh_addr'] + got['sh_size'])
        # GLOB_DAT/ABS relocs naming GOT slots
        self.gotsym = {}
        for r in self.e.get_section_by_name('.rel.dyn').iter_relocations():
            if r['r_info_type'] in (21, 2) and r['r_info_sym']:
                self.gotsym[r['r_offset']] = dyn.get_symbol(r['r_info_sym']).name
        self.cs = capstone.Cs(capstone.CS_ARCH_ARM, capstone.CS_MODE_ARM)
        self.cs.detail = True

    # --- address helpers -------------------------------------------------
    def off(self, va):
        for s in self.secs:
            if s['sh_type'] != 'SHT_NOBITS' and s['sh_addr'] <= va < s['sh_addr'] + s['sh_size'] and s['sh_addr']:
                return va - s['sh_addr'] + s['sh_offset']
        return None

    def u32(self, va):
        o = self.off(va)
        return None if o is None else struct.unpack_from('<I', self.data, o)[0]

    def secname(self, va):
        for s in self.secs:
            if s['sh_addr'] and s['sh_addr'] <= va < s['sh_addr'] + s['sh_size']:
                return s.name
        return None

    def func_at(self, va):
        i = bisect.bisect_right(self.fstarts, va) - 1
        if i >= 0 and va < self.funcs[i][0] + max(self.funcs[i][1], 1):
            return self.funcs[i][2]
        return None

    def obj_at(self, va):
        i = bisect.bisect_right(self.ostarts, va) - 1
        if i >= 0 and va < self.objs[i][0] + max(self.objs[i][1], 1):
            o = self.objs[i]
            return o[2] if va == o[0] else '%s+%#x' % (o[2], va - o[0])
        return None

    def name(self, va):
        if va in self.plt:
            return self.plt[va] + '@plt'
        n = self.obj_at(va)
        if n:
            return n
        f = self.func_at(va)
        if f:
            a = self.fbyname[f][0]
            return f if va == a else '%s+%#x' % (f, va - a)
        s = self.secname(va)
        return '%s:%#x' % (s, va) if s else None

    def cstr(self, va, maxlen=80):
        o = self.off(va)
        if o is None:
            return None
        z = self.data.find(b'\0', o, o + maxlen)
        if z < 0 or z == o:
            return None
        b = self.data[o:z]
        if all(32 <= c < 127 or c >= 0xa1 or c in (9, 10) for c in b):
            return b.decode('euc-kr', 'replace')
        return None

    # --- disassembly -----------------------------------------------------
    def insns(self, fname):
        a, sz, t = self.fbyname[fname]
        assert not t, 'Thumb functions not expected in this build'
        o = self.off(a)
        return list(self.cs.disasm(self.data[o:o + sz], a))

    def annotate(self, fname):
        """Yield (insn, note) with resolved constants."""
        ins = self.insns(fname)
        targets = set()
        for i in ins:
            if i.group(capstone.CS_GRP_JUMP) and i.op_str.startswith('#'):
                targets.add(int(i.op_str[1:], 16))
        regs = {}
        out = []
        for i in ins:
            if i.address in targets:
                regs = {k: v for k, v in regs.items() if v[0] == 'got'}  # GCC keeps GOT base in a callee-saved reg
            note = ''
            ops = i.operands
            m = i.mnemonic
            try:
                if m == 'ldr' and len(ops) == 2 and ops[1].type == capstone.arm.ARM_OP_MEM:
                    mem = ops[1].mem
                    dst = i.reg_name(ops[0].reg)
                    base = i.reg_name(mem.base) if mem.base else None
                    idx = i.reg_name(mem.index) if mem.index else None
                    if base == 'pc' and not idx:
                        lit = i.address + 8 + mem.disp
                        v = self.u32(lit)
                        regs[dst] = ('lit', v)
                        note = 'lit=%#x' % v
                    elif base in regs and idx in regs and regs[base][0] == 'got' and regs[idx][0] == 'lit':
                        slot = (regs[base][1] + regs[idx][1]) & 0xffffffff
                        tgt = self.u32(slot)
                        nm = self.gotsym.get(slot) or self.name(tgt) if tgt is not None else None
                        regs[dst] = ('addr', tgt)
                        note = 'GOT[%#x] -> %#x %s' % (slot, tgt or 0, nm or '')
                    elif base in regs and regs[base][0] == 'addr' and not idx:
                        a = (regs[base][1] + mem.disp) & 0xffffffff
                        note = 'mem %#x %s' % (a, self.name(a) or '')
                        regs.pop(dst, None)
                    else:
                        regs.pop(dst, None)
                elif m in ('ldrb', 'ldrh', 'ldrsb', 'ldrsh', 'str', 'strb', 'strh') and ops[-1].type == capstone.arm.ARM_OP_MEM:
                    mem = ops[-1].mem
                    base = i.reg_name(mem.base) if mem.base else None
                    if base in regs and regs[base][0] == 'addr' and not mem.index:
                        a = (regs[base][1] + mem.disp) & 0xffffffff
                        note = 'mem %#x %s' % (a, self.name(a) or '')
                    if m.startswith('ldr'):
                        regs.pop(i.reg_name(ops[0].reg), None)
                elif m == 'add' and len(ops) == 3 and all(o.type == capstone.arm.ARM_OP_REG for o in ops):
                    dst, r1, r2 = (i.reg_name(o.reg) for o in ops)
                    if r1 == 'pc' and r2 in regs and regs[r2][0] == 'lit':
                        v = (i.address + 8 + regs[r2][1]) & 0xffffffff
                        regs[dst] = ('got', v) if v == self.got_base else ('addr', v)
                        note = ('GOTBASE %#x' if v == self.got_base else 'pcrel %#x') % v
                    elif r1 in regs and r2 in regs and regs[r1][0] in ('got', 'addr') and regs[r2][0] == 'lit':
                        v = (regs[r1][1] + regs[r2][1]) & 0xffffffff
                        regs[dst] = ('addr', v)
                        s = self.cstr(v)
                        note = 'GOTOFF %#x %s%s' % (v, self.name(v) or '', (' "%s"' % s) if s else '')
                    else:
                        regs.pop(dst, None)
                elif m == 'mov' and len(ops) == 2 and ops[1].type == capstone.arm.ARM_OP_REG:
                    dst, src = i.reg_name(ops[0].reg), i.reg_name(ops[1].reg)
                    if src in regs:
                        regs[dst] = regs[src]
                    else:
                        regs.pop(dst, None)
                elif m in ('bl', 'blx') and i.op_str.startswith('#'):
                    t = int(i.op_str[1:], 16)
                    note = '-> %s' % (self.name(t) or hex(t))
                    for r in ('r0', 'r1', 'r2', 'r3', 'r12', 'lr'):
                        regs.pop(r, None)
                elif m == 'b' and i.op_str.startswith('#'):
                    t = int(i.op_str[1:], 16)
                    nm = self.name(t)
                    if nm and not nm.startswith(fname):
                        note = 'tail-> %s' % nm
                elif ops and ops[0].type == capstone.arm.ARM_OP_REG:
                    regs.pop(i.reg_name(ops[0].reg), None)
            except Exception as ex:  # keep going; annotation only
                note = 'annot-err %s' % ex
            out.append((i, note))
        return out

    def dump(self, fname, limit=None):
        lines = []
        for i, note in self.annotate(fname)[:limit]:
            lines.append('  %08x  %-8s %-28s %s' % (i.address, i.mnemonic, i.op_str, ('; ' + note) if note else ''))
        return '\n'.join(lines)
