"""Control-flow graph + register liveness for one ARM function of the LGT binary."""
import armdec
from armdec import FP, IP, SP, LR, PC

DP_DEST = {'mov', 'mvn', 'add', 'sub', 'rsb', 'adc', 'sbc', 'rsc', 'and', 'orr', 'eor', 'bic', 'lsl', 'lsr', 'asr',
           'ror', 'rrx', 'movw', 'movt'}
DP_CMP = {'cmp', 'cmn', 'tst', 'teq'}
LOADS = {'ldr', 'ldrb', 'ldrh', 'ldrsb', 'ldrsh', 'ldrd'}
STORES = {'str', 'strb', 'strh', 'strd'}


def regs_of(op):
    t = op[0]
    if t == 'r':
        return [op[1]]
    if t == 'sh':
        return [op[1]]
    if t == 'shr':
        return [op[1], op[3]]
    if t == 'm':
        out = [op[1]]
        if op[2] is not None:
            out.append(op[2])
        return out
    if t == 'rl':
        return list(op[1])
    return []


def uses_defs(ins):
    """Syntactic (uses, defs, conditional_def) register sets.  Calls are handled by the caller."""
    op = ins.op
    ops = ins.ops
    u, d = set(), set()
    if op in DP_DEST:
        if ops:
            d.add(ops[0][1] if ops[0][0] == 'r' else None)
            for o in ops[1:]:
                u.update(regs_of(o))
    elif op in DP_CMP:
        for o in ops:
            u.update(regs_of(o))
    elif op in ('mul', 'mla', 'umull', 'smull', 'umlal', 'smlal'):
        if op in ('umull', 'smull', 'umlal', 'smlal'):
            d.update((ops[0][1], ops[1][1]))
            for o in ops[2:]:
                u.update(regs_of(o))
            if op in ('umlal', 'smlal'):
                u.update((ops[0][1], ops[1][1]))
        else:
            d.add(ops[0][1])
            for o in ops[1:]:
                u.update(regs_of(o))
    elif op in LOADS:
        d.add(ops[0][1])
        if op == 'ldrd':
            d.add(ops[1][1])
        m = ops[-1]
        u.update(regs_of(m))
        if m[7] or m[8]:
            d.add(m[1])
    elif op in STORES:
        u.update(regs_of(ops[0]))
        if op == 'strd':
            u.add(ops[1][1])
        m = ops[-1]
        u.update(regs_of(m))
        if m[7] or m[8]:
            d.add(m[1])
    elif op.startswith('ldm') or op == 'pop':
        if op == 'pop':
            base, rl = SP, ops[0][1]
            wb = True
        else:
            base, rl = ops[0][1], ops[1][1]
            wb = ins.wb
        u.add(base)
        d.update(rl)
        if wb:
            d.add(base)
    elif op.startswith('stm') or op == 'push':
        if op == 'push':
            base, rl = SP, ops[0][1]
            wb = True
        else:
            base, rl = ops[0][1], ops[1][1]
            wb = ins.wb
        u.add(base)
        u.update(rl)
        if wb:
            d.add(base)
    elif op == 'bx':
        u.add(ops[0][1])
    elif op in ('b', 'bl'):
        pass
    d.discard(None)
    return u, d


def is_return(ins):
    return ins.op == 'bx' and ins.ops[0] == ('r', LR)


class Block:
    __slots__ = ('start', 'insns', 'succ', 'pred', 'end')

    def __init__(self, start):
        self.start = start
        self.insns = []
        self.succ = []
        self.pred = []
        self.end = start


class CFG:
    def __init__(self, B, start):
        self.B = B
        self.start = start
        self.insns, self.data, self.order = B.decode_function(start)
        self.blocks = {}
        self.jump_tables = {}      # address of ldrls pc -> list of targets
        self._build()

    def _targets(self, ins):
        """Return (list of branch targets, falls_through, is_terminator_kind)."""
        op = ins.op
        if op == 'b':
            t = ins.ops[0][1]
            return [t], ins.cc != 'al'
        return [], True

    def _build(self):
        insns, order = self.insns, self.order
        leaders = {self.start}
        end = self.B.func_end(self.start)
        for i, a in enumerate(order):
            ins = insns[a]
            nxt = a + 4
            if ins.op == 'b':
                t = ins.ops[0][1]
                leaders.add(t)
                if nxt < end:
                    leaders.add(nxt)
            elif ins.op == 'bx' and ins.ops[0] == ('r', LR):
                if nxt < end:
                    leaders.add(nxt)
            elif ins.op in ('ldr',) and ins.ops[0] == ('r', PC):
                # computed jump (switch): ldrls pc,[pc,rN,lsl #2]
                m = ins.ops[1]
                if m[1] == PC and m[2] is not None:
                    base = a + 8
                    tgts = []
                    limit = None
                    ridx = m[2]
                    for j in range(i - 1, max(i - 4, -1), -1):
                        p = insns[order[j]]
                        if p.op == 'cmp' and p.ops[0] == ('r', ridx) and p.ops[1][0] == 'i':
                            limit = p.ops[1][1] + 1
                            break
                    k = 0
                    while (base + 4 * k) in self.data and (base + 4 * k) in self.B.reloc_words:
                        if limit is not None and k >= limit:
                            break
                        tgts.append(self.data[base + 4 * k])
                        k += 1
                    self.jump_tables[a] = tgts
                    leaders.update(tgts)
                    if nxt < end:
                        leaders.add(nxt)
            elif ins.op.startswith('ldm') and ins.ops and ins.ops[1][0] == 'rl' and PC in ins.ops[1][1]:
                if nxt < end:
                    leaders.add(nxt)
        # blocks
        cur = None
        for a in order:
            if a in leaders or cur is None:
                cur = Block(a)
                self.blocks[a] = cur
            cur.insns.append(a)
            cur.end = a + 4
        # edges
        for b in self.blocks.values():
            last = insns[b.insns[-1]]
            nxt = b.end
            succ = []
            if last.op == 'b':
                succ.append(last.ops[0][1])
                if last.cc != 'al':
                    succ.append(nxt)
            elif is_return(last) and last.cc == 'al':
                pass
            elif last.op in ('ldr',) and last.ops[0] == ('r', PC):
                succ.extend(self.jump_tables.get(last.addr, []))
                if last.cc != 'al':
                    succ.append(nxt)     # out-of-range falls through to the following `b default`
            elif last.op.startswith('ldm') and last.ops[1][0] == 'rl' and PC in last.ops[1][1] and last.cc == 'al':
                pass
            else:
                succ.append(nxt)
            b.succ = [s for s in succ if s in self.blocks]
        for b in self.blocks.values():
            for s in b.succ:
                self.blocks[s].pred.append(b.start)

    def rpo(self):
        seen, out = set(), []

        def dfs(a):
            stack = [(a, iter(self.blocks[a].succ))]
            seen.add(a)
            while stack:
                n, it = stack[-1]
                for s in it:
                    if s not in seen:
                        seen.add(s)
                        stack.append((s, iter(self.blocks[s].succ)))
                        break
                else:
                    out.append(n)
                    stack.pop()
        dfs(self.start)
        out.reverse()
        return out
