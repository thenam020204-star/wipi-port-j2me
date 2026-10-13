"""Loaded view of an LGT binary.mod: sections, relocated-word set, thunks, functions."""
import struct, bisect
from elfimg import Img
import armdec

class Binary:
    def __init__(self, path):
        self.im = Img(path)
        im = self.im
        self.tb, self.td = im.mem['.text']
        self.tend = self.tb + len(self.td)
        self.rels = []
        for nm, typ, addr, off, size in im.secs:
            if nm == '.rel.text':
                self.rels = [struct.unpack_from('<II', im.b, off + i * 8) for i in range(size // 8)]
        self.reloc_words = {o for o, i in self.rels}
        # thunks in .data: code `str lr,[sp,#-4]!; bl resolver` followed by (table,fn)
        self.thunks = {}
        db, dd = im.mem['.data']
        for a in range(db, db + len(dd) - 12, 4):
            if im.u32(a) == 0xe52de004 and (im.u32(a + 4) >> 24) == 0xeb:
                self.thunks[a] = (im.u32(a + 8), im.u32(a + 12))
        # function starts (APCS prologue)
        starts = []
        for a in range(self.tb, self.tend - 8, 4):
            w = struct.unpack_from('<I', self.td, a - self.tb)[0]
            if w == 0xe1a0c00d:
                w2 = struct.unpack_from('<I', self.td, a - self.tb + 4)[0]
                if (w2 & 0xffff0000) == 0xe92d0000:
                    starts.append(a)
        self.func_starts = starts
        self.game_end = 0xf4a00   # start of the Thumb runtime helpers / link tables
        self._fidx = {a: i for i, a in enumerate(starts)}

    def word(self, a):
        return struct.unpack_from('<I', self.td, a - self.tb)[0]

    def func_end(self, start):
        i = self._fidx[start]
        return self.func_starts[i + 1] if i + 1 < len(self.func_starts) else self.game_end

    def func_of(self, addr):
        i = bisect.bisect_right(self.func_starts, addr) - 1
        return self.func_starts[i] if i >= 0 else None

    def decode_function(self, start):
        """Return (insns dict addr->Insn, data dict addr->word, order list)."""
        end = self.func_end(start)
        insns = {}
        data = {}
        order = []
        # pass 1: pool words (reloc'd words + pc-relative ldr targets + jump tables)
        pool = set(a for a in self.reloc_words if start <= a < end)
        a = start
        pending_table = None
        while a < end:
            if a in pool:
                data[a] = self.word(a); a += 4; continue
            w = self.word(a)
            ins = armdec.decode(w, a)
            if ins is None:
                data[a] = w; a += 4; continue
            if ins.op == 'ldr' and ins.ops and len(ins.ops) >= 2 and ins.ops[1][0] == 'm' and ins.ops[1][1] == armdec.PC and ins.ops[1][2] is None:
                t = a + 8 + ins.ops[1][5]
                if start <= t < end: pool.add(t)
            insns[a] = ins; order.append(a)
            a += 4
        # drop instruction decodes that are really pool words discovered later
        for t in list(pool):
            if t in insns:
                del insns[t]; order.remove(t); data[t] = self.word(t)
        return insns, data, order
