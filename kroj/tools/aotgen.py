#!/usr/bin/env python3
"""
Static Thumb -> Java translator for hot loop regions (build time).

usage: aotgen.py --bin FILE --base ADDR --class NAME --out FILE.java PROFILE[@PROFBASE]...

The image is the raw code file mapped at --base (LGT: binary.mod .text section extracted to a
file, KTF: client.bin). Profiles are "hexaddr count" lines (addresses relative to PROFBASE).

Each region is a strongly connected set of basic blocks (a loop nest) that contains no calls,
returns, indirect branches or host calls. Semantics mirror hl4.cpu.Cpu.execThumb exactly.
"""
import argparse, struct, collections, sys, re

p = argparse.ArgumentParser()
p.add_argument('--bin', required=True)
p.add_argument('--base', required=True)
p.add_argument('--fileoff', default='0')
p.add_argument('--size', default='0')
p.add_argument('--class', dest='cls', required=True)
p.add_argument('--out', required=True)
p.add_argument('--coverage', type=float, default=0.995)
p.add_argument('--maxregions', type=int, default=400)
p.add_argument('--maxinsns', type=int, default=400)
p.add_argument('--exclude', action='append', default=[], help='lo-hi hex address range to never translate')
p.add_argument('--noflagopt', action='store_true')
p.add_argument('--noveneer', action='store_true', help='do not fold "bl veneer; bx rM" call veneers')
p.add_argument('--hostcalls', action='store_true', help='call host functions of resolved LGT import thunks directly')
p.add_argument('--funcmaxinsns', type=int, default=0, help='max instructions of a function region (keeps methods under the JIT size limit)')
p.add_argument('--maxbytes', type=int, default=0, help='estimated bytecode limit per region method (e.g. 7200)')
p.add_argument('--funcregions', action='store_true', help='also translate hot non-loop code (exits at calls/returns)')
p.add_argument('--funcmin', type=int, default=1000, help='minimum profile count of a function region seed')
p.add_argument('--nodes', help='write covered instruction addresses here')
p.add_argument('--coldcut', type=float, default=0.0, help='leave out instructions executed less than this fraction of the seed count')
p.add_argument('profiles', nargs='+')
args = p.parse_args()

BASE = int(args.base, 0)
raw = open(args.bin, 'rb').read()
off = int(args.fileoff, 0)
size = int(args.size, 0) or (len(raw) - off)
code = raw[off:off + size]
END = BASE + len(code)


def h16(a):
    if a < BASE or a + 2 > END:
        return None
    return struct.unpack_from('<H', code, a - BASE)[0]


# ---------------------------------------------------------------------------
# decoding into categories

def is_barrier(op):
    """Instructions that cannot be part of a region (control transfer out of function / host)."""
    top = op >> 11
    if top in (29, 30, 31):  # BL/BLX halves
        return True
    if (op >> 10) == 0x11:  # hi-reg ops
        o = (op >> 8) & 3
        rd = (op & 7) | ((op >> 4) & 8)
        if o == 3:
            return True  # BX/BLX
        if o in (0, 2) and rd == 15:
            return True
    if top in (22, 23):
        sub = (op >> 8) & 15
        if sub in (0xC, 0xD) and (op & 0x100):
            return True  # POP {pc}
        if sub not in (0x0, 0x4, 0x5, 0xC, 0xD):
            return True
    if top in (26, 27):
        cc = (op >> 8) & 15
        if cc in (0xE, 0xF):
            return True
    return False


def branch_info(a, op):
    """Returns (kind, target) kind: None (not a branch), 'b' unconditional, 'bcc' conditional."""
    top = op >> 11
    if top == 28:
        off11 = op & 0x7FF
        if off11 & 0x400:
            off11 -= 0x800
        return 'b', a + 4 + off11 * 2
    if top in (26, 27):
        cc = (op >> 8) & 15
        if cc >= 0xE:
            return None, None
        imm = op & 0xFF
        if imm & 0x80:
            imm -= 0x100
        return 'bcc', a + 4 + imm * 2
    return None, None


# ---------------------------------------------------------------------------
# profile

hot = collections.Counter()
for spec in args.profiles:
    if '@' in spec:
        path, pb = spec.split('@')
        pb = int(pb, 0)
    else:
        path, pb = spec, 0
    for line in open(path):
        parts = line.split()
        if len(parts) != 2:
            continue
        a = int(parts[0], 16) + pb
        hot[a] += int(parts[1])
total = sum(hot.values())
print('profile total', total, file=sys.stderr)


# ---------------------------------------------------------------------------
# local CFG exploration around a hot address

EXCL = [tuple(int(x, 16) for x in e.split('-')) for e in args.exclude]


def excluded(a):
    return any(lo <= a < hi for lo, hi in EXCL)


def explore(start, window=0x1000, mincount=0):
    """Explore instructions reachable from start (within window) without crossing barriers.
    Returns dict addr -> list of successor addrs (instruction level)."""
    succ = {}
    todo = [start]
    lo, hi = start - window, start + window
    while todo:
        a = todo.pop()
        if a in succ or a < lo or a >= hi:
            continue
        op = h16(a)
        if op is None:
            succ[a] = None
            continue
        if is_barrier(op) or excluded(a) or (mincount and hot.get(a, 0) < mincount):
            succ[a] = None  # barrier: not includable
            continue
        kind, tgt = branch_info(a, op)
        if kind == 'b':
            s = [tgt]
        elif kind == 'bcc':
            s = [a + 2, tgt]
        else:
            s = [a + 2]
        succ[a] = s
        todo.extend(s)
    return succ


def scc_containing(succ, node):
    """Nodes that are reachable from node and can reach node (instruction graph)."""
    fwd = set()
    stack = [node]
    while stack:
        x = stack.pop()
        if x in fwd or succ.get(x) is None:
            continue
        fwd.add(x)
        for y in succ[x]:
            stack.append(y)
    pred = collections.defaultdict(list)
    for x, ss in succ.items():
        if ss is None:
            continue
        for y in ss:
            pred[y].append(x)
    bwd = set()
    stack = [node]
    while stack:
        x = stack.pop()
        if x in bwd or succ.get(x) is None:
            continue
        bwd.add(x)
        for y in pred[x]:
            stack.append(y)
    return fwd & bwd


regions = []
covered = set()
acc = 0
for a, cnt in hot.most_common():
    if acc >= total * args.coverage or len(regions) >= args.maxregions:
        break
    if a in covered:
        continue
    op = h16(a)
    if op is None or is_barrier(op):
        continue
    succ = explore(a, mincount=max(1, int(cnt * args.coldcut)) if args.coldcut > 0 else 0)
    nodes = scc_containing(succ, a)
    if len(nodes) < 2:
        continue
    if len(nodes) > args.maxinsns:
        continue
    if nodes & covered:
        continue
    regions.append((nodes, succ))
    covered |= nodes
    for x in nodes:
        acc += hot.get(x, 0)
print('regions', len(regions), 'coverage %.3f' % (acc / total if total else 0), file=sys.stderr)


def terminal(a):
    """Instructions that leave a function region with a computed/known next pc:
    ('bl', next, ret) / ('blx', next, ret) for BL/BLX imm pairs (at the prefix), ('pop',) for POP {..pc},
    ('bx', m) / ('blxr', m) for BX/BLX register. None otherwise."""
    op = h16(a)
    if op is None:
        return None
    top = op >> 11
    if top == 30:
        op2 = h16(a + 2)
        if op2 is None or (op2 >> 11) not in (29, 31):
            return None
        off = op & 0x7FF
        if off & 0x400:
            off -= 0x800
        tgt = a + 4 + (off << 12) + ((op2 & 0x7FF) << 1)
        if (op2 >> 11) == 31:
            return ('bl', (tgt & 0xFFFFFFFF) | 1, (a + 4) | 1)
        return ('blx', tgt & ~3 & 0xFFFFFFFF, (a + 4) | 1)
    if top in (22, 23) and ((op >> 8) & 15) == 0xD:
        return ('pop',)
    if (op >> 8) == 0x47:
        m = (op >> 3) & 15
        return ('blxr' if op & 0x80 else 'bx', m)
    return None


def veneer(a):
    """register m if the Thumb instruction at a is a plain 'bx rM' (M != pc), else None"""
    if args.noveneer or excluded(a):
        return None
    op = h16(a)
    if op is None or (op >> 8) != 0x47 or (op & 0x87) != 0:
        return None
    m = (op >> 3) & 15
    return None if m == 15 else m


def explore_func(seed, window, mincount):
    nodes = set()
    todo = [seed]
    lo, hi = seed - window, seed + window
    while todo:
        a = todo.pop()
        if a in nodes or a in covered or a < lo or a >= hi or excluded(a):
            continue
        op = h16(a)
        if op is None or hot.get(a, 0) < mincount:
            continue
        if terminal(a):
            nodes.add(a)
            continue
        if is_barrier(op):
            continue
        nodes.add(a)
        kind, tgt = branch_info(a, op)
        todo.extend([tgt] if kind == 'b' else ([a + 2, tgt] if kind == 'bcc' else [a + 2]))
    return nodes


nloops = len(regions)
if args.funcregions:
    facc = 0
    for a, cnt in hot.most_common():
        if cnt < args.funcmin or len(regions) >= args.maxregions:
            break
        if a in covered:
            continue
        op = h16(a)
        if op is None or (is_barrier(op) and not terminal(a)):
            continue
        nodes = explore_func(a, 0x800, max(1, int(cnt * args.coldcut)) if args.coldcut > 0 else 1)
        if len(nodes) < 3:
            continue
        fmax = args.funcmaxinsns or args.maxinsns
        if len(nodes) > fmax:
            nodes = set(sorted(nodes, key=lambda x: -hot.get(x, 0))[:fmax])
        regions.append((nodes, None))
        covered |= nodes
        for x in nodes:
            facc += hot.get(x, 0)
        acc += sum(hot.get(x, 0) for x in nodes)
    print('function regions', len(regions) - nloops, 'total coverage %.3f' % (acc / total if total else 0), file=sys.stderr)


# ---------------------------------------------------------------------------
# flag liveness (dead flag elimination)

ALLF = frozenset('nzCv')
CONDUSE = {0x0: 'z', 0x1: 'z', 0x2: 'C', 0x3: 'C', 0x4: 'n', 0x5: 'n', 0x6: 'v', 0x7: 'v',
           0x8: 'Cz', 0x9: 'Cz', 0xA: 'nv', 0xB: 'nv', 0xC: 'znv', 0xD: 'znv'}


def flag_du(op):
    """(defs, uses) flag sets of a Thumb instruction (defs = unconditionally written)."""
    top = op >> 11
    if top == 0:
        return (frozenset('nz') if ((op >> 6) & 31) == 0 else frozenset('nzC')), frozenset()
    if top in (1, 2):
        return frozenset('nzC'), frozenset()
    if top in (3, 5, 6, 7):
        return ALLF, frozenset()
    if top == 4:
        return frozenset('nz'), frozenset()
    if top == 8:
        if (op & 0x400) == 0:
            o = (op >> 6) & 15
            if o in (0, 1, 8, 12, 13, 14, 15):
                return frozenset('nz'), frozenset()
            if o in (2, 3, 4, 7):
                return frozenset('nz'), frozenset('C')   # C only changes when amount != 0
            if o in (5, 6):
                return ALLF, frozenset('C')
            return ALLF, frozenset()                    # NEG, CMP, CMN
        if ((op >> 8) & 3) == 1:
            return ALLF, frozenset()                    # hi CMP
        return frozenset(), frozenset()
    if top in (26, 27):
        cc = (op >> 8) & 15
        if cc < 0xE:
            return frozenset(), frozenset(CONDUSE[cc])
    return frozenset(), frozenset()


def flag_liveness(nodes):
    """live-out flag set per instruction of a region (all flags live at region exits)."""
    node_set = set(nodes)
    succ = {}
    for a in nodes:
        op = h16(a)
        if terminal(a):
            succ[a] = [None]            # exit: all flags live
            continue
        kind, tgt = branch_info(a, op)
        succ[a] = [tgt] if kind == 'b' else ([a + 2, tgt] if kind == 'bcc' else [a + 2])
    du = {a: flag_du(h16(a)) for a in nodes}
    live_in = {a: ALLF for a in nodes}
    live_out = {}
    changed = True
    while changed:
        changed = False
        for a in sorted(nodes, reverse=True):
            lo = frozenset()
            for y in succ[a]:
                lo |= live_in[y] if y in node_set else ALLF
            d, u = du[a]
            li = u | (lo - d)
            if li != live_in[a] or live_out.get(a) != lo:
                live_in[a] = li
                live_out[a] = lo
                changed = True
    return live_out


FLAG_RE = {f: re.compile(r'(?<![\w.])%s = [^;{}]*;\s?' % f) for f in 'nzCv'}


def strip_dead(code, live):
    for f in 'nzCv':
        if f not in live:
            code = FLAG_RE[f].sub('', code)
    return code


# ---------------------------------------------------------------------------
# code generation

def reg(r):
    return 'r%d' % r


def gen_insn(a, op, out, labels, region_nodes, block_of):
    """Emit Java for instruction at a. Branch handling returns True if control ends here."""
    top = op >> 11
    rd = op & 7
    rs = (op >> 3) & 7
    E = out.append
    pcval = a + 4
    if top == 0:  # LSL imm
        imm = (op >> 6) & 31
        if imm == 0:
            E('%s = %s; n = %s < 0; z = %s == 0;' % (reg(rd), reg(rs), reg(rd), reg(rd)))
        else:
            E('{ int t = %s; C = ((t >>> %d) & 1) != 0; t <<= %d; %s = t; n = t < 0; z = t == 0; }' % (reg(rs), 32 - imm, imm, reg(rd)))
    elif top == 1:  # LSR imm
        imm = (op >> 6) & 31
        if imm == 0:
            E('{ C = %s < 0; %s = 0; n = false; z = true; }' % (reg(rs), reg(rd)))
        else:
            E('{ int t = %s; C = ((t >>> %d) & 1) != 0; t >>>= %d; %s = t; n = t < 0; z = t == 0; }' % (reg(rs), imm - 1, imm, reg(rd)))
    elif top == 2:  # ASR imm
        imm = (op >> 6) & 31
        if imm == 0:
            E('{ int t = %s; C = t < 0; t >>= 31; %s = t; n = t < 0; z = t == 0; }' % (reg(rs), reg(rd)))
        else:
            E('{ int t = %s; C = ((t >> %d) & 1) != 0; t >>= %d; %s = t; n = t < 0; z = t == 0; }' % (reg(rs), imm - 1, imm, reg(rd)))
    elif top == 3:  # ADD/SUB reg/imm3
        x = (op >> 6) & 7
        b = str(x) if (op & 0x400) else reg(x)
        if (op & 0x200) == 0:
            E('{ int a = %s, b = %s, t = a + b; n = t < 0; z = t == 0; C = (t ^ 0x80000000) < (a ^ 0x80000000); v = ((a ^ t) & (b ^ t)) < 0; %s = t; }' % (reg(rs), b, reg(rd)))
        else:
            E('{ int a = %s, b = %s, t = a - b; n = t < 0; z = t == 0; C = (a ^ 0x80000000) >= (b ^ 0x80000000); v = ((a ^ b) & (a ^ t)) < 0; %s = t; }' % (reg(rs), b, reg(rd)))
    elif top == 4:  # MOV imm
        r = (op >> 8) & 7
        imm = op & 0xFF
        E('%s = %d; n = false; z = %s;' % (reg(r), imm, 'true' if imm == 0 else 'false'))
    elif top == 5:  # CMP imm
        r = (op >> 8) & 7
        imm = op & 0xFF
        E('{ int a = %s, t = a - %d; n = t < 0; z = t == 0; C = (a ^ 0x80000000) >= (%d ^ 0x80000000); v = ((a ^ %d) & (a ^ t)) < 0; }' % (reg(r), imm, imm, imm))
    elif top == 6:  # ADD imm8
        r = (op >> 8) & 7
        imm = op & 0xFF
        E('{ int a = %s, t = a + %d; n = t < 0; z = t == 0; C = (t ^ 0x80000000) < (a ^ 0x80000000); v = ((a ^ t) & (%d ^ t)) < 0; %s = t; }' % (reg(r), imm, imm, reg(r)))
    elif top == 7:  # SUB imm8
        r = (op >> 8) & 7
        imm = op & 0xFF
        E('{ int a = %s, t = a - %d; n = t < 0; z = t == 0; C = (a ^ 0x80000000) >= (%d ^ 0x80000000); v = ((a ^ %d) & (a ^ t)) < 0; %s = t; }' % (reg(r), imm, imm, imm, reg(r)))
    elif top == 8:
        if (op & 0x400) == 0:
            gen_alu(op, E)
        else:
            o = (op >> 8) & 3
            d = (op & 7) | ((op >> 4) & 8)
            m = (op >> 3) & 15
            src = ('%d' % pcval) if m == 15 else reg(m)
            if o == 0:
                E('%s = %s + %s;' % (reg(d), reg(d), src))
            elif o == 1:
                E('{ int a = %s, b = %s, t = a - b; n = t < 0; z = t == 0; C = (a ^ 0x80000000) >= (b ^ 0x80000000); v = ((a ^ b) & (a ^ t)) < 0; }' % (reg(d) if d != 15 else str(pcval), src))
            elif o == 2:
                E('%s = %s;' % (reg(d), src))
    elif top == 9:  # LDR literal
        r = (op >> 8) & 7
        addr = (pcval & ~3) + ((op & 0xFF) << 2)
        E('%s = M.read32(%d);' % (reg(r), addr))
    elif top in (10, 11):
        ro = (op >> 6) & 7
        sub = (op >> 9) & 7
        ad = '(%s + %s)' % (reg(rs), reg(ro))
        if sub == 0:
            E('M.write32(%s & ~3, %s);' % (ad, reg(rd)))
        elif sub == 1:
            E('M.write16(%s & ~1, %s);' % (ad, reg(rd)))
        elif sub == 2:
            E('M.write8(%s, %s);' % (ad, reg(rd)))
        elif sub == 3:
            E('%s = (byte) M.read8(%s);' % (reg(rd), ad))
        elif sub == 4:
            E('%s = Cpu.ldrRot(M, %s);' % (reg(rd), ad))
        elif sub == 5:
            E('%s = M.read16(%s & ~1);' % (reg(rd), ad))
        elif sub == 6:
            E('%s = M.read8(%s);' % (reg(rd), ad))
        else:
            E('%s = (short) M.read16(%s & ~1);' % (reg(rd), ad))
    elif top == 12:
        E('M.write32((%s + %d) & ~3, %s);' % (reg(rs), ((op >> 6) & 31) << 2, reg(rd)))
    elif top == 13:
        E('%s = Cpu.ldrRot(M, %s + %d);' % (reg(rd), reg(rs), ((op >> 6) & 31) << 2))
    elif top == 14:
        E('M.write8(%s + %d, %s);' % (reg(rs), (op >> 6) & 31, reg(rd)))
    elif top == 15:
        E('%s = M.read8(%s + %d);' % (reg(rd), reg(rs), (op >> 6) & 31))
    elif top == 16:
        E('M.write16((%s + %d) & ~1, %s);' % (reg(rs), ((op >> 6) & 31) << 1, reg(rd)))
    elif top == 17:
        E('%s = M.read16((%s + %d) & ~1);' % (reg(rd), reg(rs), ((op >> 6) & 31) << 1))
    elif top == 18:
        r = (op >> 8) & 7
        E('M.write32((r13 + %d) & ~3, %s);' % ((op & 0xFF) << 2, reg(r)))
    elif top == 19:
        r = (op >> 8) & 7
        E('%s = Cpu.ldrRot(M, r13 + %d);' % (reg(r), (op & 0xFF) << 2))
    elif top == 20:
        r = (op >> 8) & 7
        E('%s = %d;' % (reg(r), (pcval & ~3) + ((op & 0xFF) << 2)))
    elif top == 21:
        r = (op >> 8) & 7
        E('%s = r13 + %d;' % (reg(r), (op & 0xFF) << 2))
    elif top in (22, 23):
        sub = (op >> 8) & 15
        if sub == 0:
            if op & 0x80:
                E('r13 -= %d;' % ((op & 0x7F) << 2))
            else:
                E('r13 += %d;' % ((op & 0x7F) << 2))
        elif sub in (4, 5):
            lst = op & 0xFF
            cnt = bin(lst).count('1') + (1 if op & 0x100 else 0)
            E('{ int ad = r13 - %d; r13 = ad; ad &= ~3;' % (cnt * 4))
            for i in range(8):
                if lst & (1 << i):
                    E('  M.write32(ad, %s); ad += 4;' % reg(i))
            if op & 0x100:
                E('  M.write32(ad, r14);')
            E('}')
        elif sub in (0xC, 0xD):
            lst = op & 0xFF
            E('{ int ad = r13 & ~3;')
            for i in range(8):
                if lst & (1 << i):
                    E('  %s = M.read32(ad); ad += 4;' % reg(i))
            E('  r13 = ad; }')
    elif top == 24:  # STMIA
        lst = op & 0xFF
        r = (op >> 8) & 7
        E('{ int ad = %s & ~3;' % reg(r))
        for i in range(8):
            if lst & (1 << i):
                E('  M.write32(ad, %s); ad += 4;' % reg(i))
        E('  %s = %s + %d; }' % (reg(r), reg(r), bin(lst).count('1') * 4))
    elif top == 25:  # LDMIA
        lst = op & 0xFF
        r = (op >> 8) & 7
        E('{ int base = %s, ad = base & ~3;' % reg(r))
        for i in range(8):
            if lst & (1 << i):
                E('  %s = M.read32(ad); ad += 4;' % reg(i))
        if not (lst & (1 << r)):
            E('  %s = base + %d;' % (reg(r), bin(lst).count('1') * 4))
        E('}')
    else:
        raise Exception('unexpected op %04x at %x' % (op, a))


def gen_alu(op, E):
    rd = op & 7
    rm = (op >> 3) & 7
    o = (op >> 6) & 15
    d, m = reg(rd), reg(rm)
    nz = 'n = %s < 0; z = %s == 0;' % (d, d)
    if o == 0:
        E('%s &= %s; %s' % (d, m, nz))
    elif o == 1:
        E('%s ^= %s; %s' % (d, m, nz))
    elif o in (2, 3, 4, 7):
        # register specified shift (amount = low byte)
        body = {
            2: 'if (amt < 32) { C = ((val >>> (32 - amt)) & 1) != 0; val <<= amt; } else if (amt == 32) { C = (val & 1) != 0; val = 0; } else { C = false; val = 0; }',
            3: 'if (amt < 32) { C = ((val >>> (amt - 1)) & 1) != 0; val >>>= amt; } else if (amt == 32) { C = val < 0; val = 0; } else { C = false; val = 0; }',
            4: 'if (amt < 32) { C = ((val >> (amt - 1)) & 1) != 0; val >>= amt; } else { C = val < 0; val >>= 31; }',
            7: 'int a5 = amt & 31; if (a5 == 0) { C = val < 0; } else { val = (val >>> a5) | (val << (32 - a5)); C = val < 0; }',
        }[o]
        E('{ int amt = %s & 0xFF, val = %s; if (amt != 0) { %s } %s = val; %s }' % (m, d, body, d, nz))
    elif o == 5:  # ADC
        E('{ int a = %s, b = %s; long u = (a & 0xFFFFFFFFL) + (b & 0xFFFFFFFFL) + (C ? 1 : 0); int t = (int) u; n = t < 0; z = t == 0; C = (u >>> 32) != 0; v = ((a ^ t) & (b ^ t)) < 0; %s = t; }' % (d, m, d))
    elif o == 6:  # SBC
        E('{ int a = %s, b = %s; long u = (a & 0xFFFFFFFFL) + ((~b) & 0xFFFFFFFFL) + (C ? 1 : 0); int t = (int) u; n = t < 0; z = t == 0; C = (u >>> 32) != 0; v = ((a ^ b) & (a ^ t)) < 0; %s = t; }' % (d, m, d))
    elif o == 8:  # TST
        E('{ int t = %s & %s; n = t < 0; z = t == 0; }' % (d, m))
    elif o == 9:  # NEG
        E('{ int b = %s, t = 0 - b; n = t < 0; z = t == 0; C = (0 ^ 0x80000000) >= (b ^ 0x80000000); v = ((0 ^ b) & (0 ^ t)) < 0; %s = t; }' % (m, d))
    elif o == 10:  # CMP
        E('{ int a = %s, b = %s, t = a - b; n = t < 0; z = t == 0; C = (a ^ 0x80000000) >= (b ^ 0x80000000); v = ((a ^ b) & (a ^ t)) < 0; }' % (d, m))
    elif o == 11:  # CMN
        E('{ int a = %s, b = %s, t = a + b; n = t < 0; z = t == 0; C = (t ^ 0x80000000) < (a ^ 0x80000000); v = ((a ^ t) & (b ^ t)) < 0; }' % (d, m))
    elif o == 12:
        E('%s |= %s; %s' % (d, m, nz))
    elif o == 13:
        E('%s = %s * %s; %s' % (d, m, d, nz))
    elif o == 14:
        E('%s &= ~%s; %s' % (d, m, nz))
    else:
        E('%s = ~%s; %s' % (d, m, nz))


COND = {
    0x0: 'z', 0x1: '!z', 0x2: 'C', 0x3: '!C', 0x4: 'n', 0x5: '!n', 0x6: 'v', 0x7: '!v',
    0x8: '(C && !z)', 0x9: '(!C || z)', 0xA: '(n == v)', 0xB: '(n != v)', 0xC: '(!z && n == v)', 0xD: '(z || n != v)',
}


def gen_region(idx, nodes):
    nodes = sorted(nodes)
    # block leaders: region entries, branch targets, instructions after branches
    leaders = set()
    node_set = set(nodes)
    for a in nodes:
        op = h16(a)
        kind, tgt = branch_info(a, op)
        if kind:
            if tgt in node_set:
                leaders.add(tgt)
            if a + 2 in node_set:
                leaders.add(a + 2)
    # entries: nodes with predecessors outside region (or fallthrough from outside)
    preds = collections.defaultdict(set)
    allsucc = {}
    for a in nodes:
        op = h16(a)
        kind, tgt = branch_info(a, op)
        s = [tgt] if kind == 'b' else ([a + 2, tgt] if kind == 'bcc' else [a + 2])
        allsucc[a] = s
        for y in s:
            preds[y].add(a)
    leaders.add(nodes[0])
    for a in nodes:
        # function regions: region entries after calls / at blocks whose predecessor is outside
        if a - 2 not in node_set or terminal(a - 2) or (a - 4 in node_set and terminal(a - 4) and terminal(a - 4)[0] in ('bl', 'blx')):
            leaders.add(a)
        t = terminal(a)
        if t and t[0] in ('bl', 'blx') and a + 4 in node_set:
            leaders.add(a + 4)
    leaders |= extra_leaders & node_set
    # any node could be entered from outside if some outside instruction branches to it; we mark
    # as entries: leaders that are targets from outside + first node. We conservatively make every
    # leader an entry (entries only take effect when the interpreter reaches that pc).
    leaders = sorted(leaders)
    label = {a: i for i, a in enumerate(leaders)}
    flive = flag_liveness(nodes) if not args.noflagopt else None
    lines = []
    L = lines.append
    L('    static int r%d(Cpu c, int entry) {' % idx)
    L('        final Memory M = c.m;')
    L('        final int[] R = c.r;')
    L('        int r0 = R[0], r1 = R[1], r2 = R[2], r3 = R[3], r4 = R[4], r5 = R[5], r6 = R[6], r7 = R[7];')
    L('        int r8 = R[8], r9 = R[9], r10 = R[10], r11 = R[11], r12 = R[12], r13 = R[13], r14 = R[14];')
    L('        boolean n = c.n, z = c.z, C = c.c, v = c.v;')
    L('        long ic = 0;')
    L('        int next;')
    L('        int lb = entry;')
    L('        loop: while (true) {')
    L('            switch (lb) {')
    for bi, start in enumerate(leaders):
        # block: from start until next leader or branch end
        L('            case %d: {' % bi)
        a = start
        body = []
        count = 0
        terminated = False
        while True:
            op = h16(a)
            count += 1
            tm = terminal(a)
            if tm:
                if tm[0] in ('bl', 'blx'):
                    count += 1
                    vm = veneer(tm[1] & ~1) if tm[0] == 'bl' else None
                    if vm is not None and args.hostcalls:
                        # call through a "bx rM" veneer. When rM is a resolved import thunk
                        # (ARM: ldr ip,[pc,#4]; bx ip; .word table; .word stub) the host function is
                        # called right here (same icount as bl + bx rM + ldr + bx ip + svc); else
                        # the bx is done here (one more instruction) and the region is left.
                        ret = tm[2]
                        rl = ret & ~1
                        cont = ('lb = %d; continue loop;' % label[rl]) if rl in label else ('next = %d; break loop;' % ret)
                        sync_out = ' '.join('R[%d] = r%d;' % (i, i) for i in range(14)) + ' R[14] = %d;' % ret
                        sync_in = ' '.join('r%d = R[%d];' % (i, i) for i in range(15))
                        body.append('{ int t = %s; int hid = c.thunkHost(t); if (hid != 0) { %s c.icount += ic + %d; ic = 0; c.host(hid); c.icount += 1; %s %s }' % (reg(vm), sync_out, count + 3, sync_in, cont))
                        body.append('  r14 = %d; ic += %d; next = t; break loop; }' % (ret, count + 1))
                    elif vm is not None:
                        # call through a "bx rM" veneer: do the bx here (one more instruction)
                        body.append('r14 = %d; ic += %d; next = %s; break loop;' % (tm[2], count + 1, reg(vm)))
                    else:
                        body.append('r14 = %d; ic += %d; next = %d; break loop;' % (tm[2], count, tm[1]))
                elif tm[0] == 'pop':
                    lst = op & 0xFF
                    body.append('{ int ad = r13 & ~3;')
                    for i in range(8):
                        if lst & (1 << i):
                            body.append('  %s = M.read32(ad); ad += 4;' % reg(i))
                    body.append('  next = M.read32(ad); r13 = ad + 4; }')
                    body.append('ic += %d; break loop;' % count)
                else:
                    m = tm[1]
                    src = str(a + 4) if m == 15 else reg(m)
                    if tm[0] == 'blxr':
                        body.append('next = %s; r14 = %d; ic += %d; break loop;' % (src, (a + 2) | 1, count))
                    else:
                        body.append('next = %s; ic += %d; break loop;' % (src, count))
                terminated = True
                break
            kind, tgt = branch_info(a, op)
            if kind == 'b':
                body_ic = count
                gen_exit_or_goto(body, tgt, label, node_set)
                terminated = True
                break
            if kind == 'bcc':
                cc = (op >> 8) & 15
                body.append('if (%s) { ic += %d; %s }' % (COND[cc], count, goto_str(tgt, label, node_set)))
                # fallthrough
                nxt = a + 2
                body.append('ic += %d;' % count)
                count = 0
                if nxt in label:
                    body.append(goto_str(nxt, label, node_set, fall=(bi + 1 < len(leaders) and leaders[bi + 1] == nxt)))
                elif nxt not in node_set:
                    body.append(goto_str(nxt, label, node_set))
                else:
                    raise Exception('fallthrough into non-leader?')
                terminated = True
                break
            if flive is None:
                gen_insn(a, op, body, label, node_set, None)
            else:
                tmp = []
                gen_insn(a, op, tmp, label, node_set, None)
                body.extend(strip_dead(x, flive[a]) for x in tmp)
            nxt = a + 2
            if nxt in label or nxt not in node_set:
                body.append('ic += %d;' % count)
                body.append(goto_str(nxt, label, node_set, fall=(nxt in label and bi + 1 < len(leaders) and leaders[bi + 1] == nxt)))
                terminated = True
                break
            a = nxt
        if not terminated:
            raise Exception('unterminated block')
        # fix unconditional branch ic
        out = []
        for ln in body:
            out.append(ln)
        # insert ic for the 'b' case
        for ln in out:
            L('                ' + ln.replace('@IC@', str(count)))
        L('            }')
    L('            default: throw new CpuException("bad region label");')
    L('            }')
    L('        }')
    L('        R[0] = r0; R[1] = r1; R[2] = r2; R[3] = r3; R[4] = r4; R[5] = r5; R[6] = r6; R[7] = r7;')
    L('        R[8] = r8; R[9] = r9; R[10] = r10; R[11] = r11; R[12] = r12; R[13] = r13; R[14] = r14;')
    L('        c.n = n; c.z = z; c.c = C; c.v = v;')
    L('        c.icount += ic;')
    L('        return next;')
    L('    }')
    return lines, leaders


def goto_str(tgt, label, node_set, fall=False):
    if tgt in label:
        if fall:
            return '/* fall through */'
        return 'lb = %d; continue loop;' % label[tgt]
    return 'next = %d; break loop;' % (tgt | 1)


def gen_exit_or_goto(body, tgt, label, node_set):
    body.append('ic += @IC@;')
    body.append(goto_str(tgt, label, node_set))


# keep every region method under the JIT's huge-method limit (HotSpot: 8000 bytecode bytes are not
# compiled). Bytecode size is estimated from the generated Java text; oversized regions lose their
# coldest instructions (they become region exits) until they fit.
extra_leaders = set()
if args.maxbytes > 0:
    shrunk = 0
    for ri in range(len(regions)):
        nodes, succ = regions[ri]
        while len(nodes) > 16:
            lines, _ = gen_region(ri, nodes)
            est = int(sum(len(x) for x in lines) * 0.28)
            if est <= args.maxbytes:
                break
            keep = int(len(nodes) * 0.9)
            nodes = set(sorted(nodes, key=lambda x: (-hot.get(x, 0), x))[:keep])
            shrunk += 1
        regions[ri] = (nodes, succ)
    print('size check: %d shrink steps' % shrunk, file=sys.stderr)

# static exit targets of all regions become entries of the region that contains them
extra_leaders = set()
for nodes, _ in regions:
    for a in nodes:
        tm = terminal(a)
        if tm:
            if tm[0] == 'bl':
                extra_leaders.add(tm[1] & ~1)
            continue
        kind, tgt = branch_info(a, h16(a))
        if kind and tgt not in nodes:
            extra_leaders.add(tgt)
        if kind == 'bcc' and a + 2 not in nodes:
            extra_leaders.add(a + 2)
        if not kind and a + 2 not in nodes:
            extra_leaders.add(a + 2)

out = []
O = out.append
O('package hl4.aot;')
O('')
O('import hl4.cpu.Aot;')
O('import hl4.cpu.Cpu;')
O('import hl4.cpu.CpuException;')
O('import hl4.cpu.Memory;')
O('')
O('/** Generated by tools/aotgen.py - statically translated hot Thumb regions. Do not edit. */')
O('public final class %s implements Aot {' % args.cls)
entries = []
bodies = []
lo = min(min(n) for n, _ in regions) if regions else BASE
hi = max(max(n) for n, _ in regions) + 2 if regions else BASE + 2
for i, (nodes, succ) in enumerate(regions):
    lines, leaders = gen_region(i, nodes)
    bodies.extend(lines)
    for li, a in enumerate(leaders):
        entries.append((a, (i << 8) | li))
O('    static final int BASE = %d;' % lo)
O('    static final int SIZE = %d;' % ((hi - lo) // 2))
# entry table as string constants (keeps <clinit> small): 4 chars per entry
# (halfword offset from BASE as 2x16 bit, region id + 1 as 2x16 bit)
def jesc(c):
    if c == 0x0A: return '\\n'
    if c == 0x0D: return '\\r'
    if c == 0x22: return '\\"'
    if c == 0x5C: return '\\\\'
    return '\\u%04x' % c
chars = []
for a, v in entries:
    off = (a - lo) >> 1
    chars += [off >> 16, off & 0xFFFF, (v + 1) >> 16, (v + 1) & 0xFFFF]
O('    static final String[] ENTRIES = {')
CH = 8000
for i in range(0, len(chars), CH):
    O('        "%s",' % ''.join(jesc(c) for c in chars[i:i + CH]))
O('    };')
O('    private int[] map;')
O('    public int base() { return BASE; }')
O('    public int[] map() {')
O('        if (map == null) {')
O('            map = new int[SIZE];')
O('            StringBuffer sb = new StringBuffer();')
O('            for (int i = 0; i < ENTRIES.length; i++) sb.append(ENTRIES[i]);')
O('            String e = sb.toString();')
O('            for (int i = 0; i + 3 < e.length(); i += 4) map[(e.charAt(i) << 16) | e.charAt(i + 1)] = (e.charAt(i + 2) << 16) | e.charAt(i + 3);')
O('        }')
O('        return map;')
O('    }')
# dispatch: groups of 200 regions per method (each method stays far below the JIT size limit)
GROUP = 200
O('    public int run(int id, Cpu c) {')
O('        id -= 1;')
O('        int entry = id & 0xFF;')
O('        int r = id >> 8;')
O('        switch (r / %d) {' % GROUP)
for g in range((len(regions) + GROUP - 1) // GROUP):
    O('            case %d: return d%d(r, c, entry);' % (g, g))
O('        }')
O('        throw new CpuException("bad region " + id);')
O('    }')
for g in range((len(regions) + GROUP - 1) // GROUP):
    O('    static int d%d(int r, Cpu c, int entry) {' % g)
    O('        switch (r) {')
    for i in range(g * GROUP, min(len(regions), (g + 1) * GROUP)):
        O('            case %d: return r%d(c, entry);' % (i, i))
    O('        }')
    O('        throw new CpuException("bad region " + r);')
    O('    }')
out.extend(bodies)
O('}')
open(args.out, 'w').write('\n'.join(out) + '\n')
if args.nodes:
    open(args.nodes, 'w').write('\n'.join('%x %d' % (a, i) for i, (nodes, _) in enumerate(regions) for a in sorted(nodes)) + '\n')
print('wrote', args.out, len(regions), 'regions', len(entries), 'entries', file=sys.stderr)
