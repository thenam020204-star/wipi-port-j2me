#!/usr/bin/env python3
"""Call recorder of the ORIGINAL (T0.1, docs/PLAN_NEXT.md): every call of the chosen functions in a tick range, in exit order, with its
arguments, return value and CRC32 hashes of the game state right after the call.  The Java side (hl4.test.CallRec, enabled by tools/jcr.sh)
writes the same line format; tools/callcmp.py shows the first call that differs.

Usage: e_calls.py <binary.mod> <res> <out.txt> <from_tick> <to_tick> <spec>[,spec..] [tick:key ...]
  spec = NAME[/N][+r][=map]: /N records N arguments, +r records the return value (both override a wrong Ghidra signature: check the
  disassembly); =map is for the Java side only (tools/callrec_inst.py).  tools/jcr.sh takes the same spec list.
  env CALLDUMP=h,g,e,o   also writes the masked bytes after each call ('D <key> <hex>' lines; o = first pool-object argument), so
                         tools/callcmp.py can name the differing byte ranges (the Java side: -Dcallrec.dump=..., tools/jcr.sh passes CALLDUMP)
  env CALLER=1           appends 'from=<caller function>' (reading aid; not compared by callcmp)
  env PROBE=<file>       after tick <to_tick>, calls the functions listed in <file> directly (one call per line: 'NAME arg arg ..', args =
                         integers or pool objects 'o<type>.<idx>'; '#' comments); recorded with tick <to_tick>+1.  Java: GameTraceTest
                         --probe <to_tick> <file> (tools/jcr.sh).  For functions no trace reaches yet.  Calls run in order on the same state.
  env SNAPLOAD=<snapshot> starts from a snapshot of tools/e_snap.py instead of tick 0 (T0.3; the scenario tokens stay the full scenario)

Line format (one per call, written when the call returns):
  C <tick> <depth> <NAME> <arg,arg,..> <ret> g=<crc> e=<crc> h=<crc> m=<crc> [o=<id>:<crc>,..]
    args/ret: signed 32-bit decimal, or a pool object identity 'o<type>.<idx>' (from the object header +0x14 / +0x16), or 'ptr' (pointer outside
    the pools: heap or a global; compared as a wildcard), '-' for void.  depth = number of recorded calls still open at entry.
    g/e/h/m = _game, _event, hero, all 25 monster slots; o = each pool-object argument after the call.  Pointer slots of the original
    are zeroed before hashing (masks of tools/e_state.py, written to the header so the Java side applies exactly the same ones).
Header: '# sig NAME <widths of args> <width of ret>' (from the Ghidra signature: 1/2/4 bytes, 0 = void), '# mask NAME lo-hi,..', '# family TYPE NAME'."""
import os
import re
import sys
import zlib

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from unicorn import UC_HOOK_CODE, arm_const
import ref_game
from e_state import mask_for

GH = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'ghidra', 'decomp_and2011.c')
# pool arrays (real addresses = Ghidra - 0x10000; same table as tools/e_state.py dump()): family, ghidra address, stride, count
POOLS = [('summon', 0x112500, 0x63c, 1), ('monster', 0x1200fc, 0x430, 25), ('npc', 0x155e40, 0x1cc, 20), ('mapobj', 0x112b3c, 0x3c, 900),
         ('item', 0x1269b0, 0x270, 200), ('arms', 0x145130, 0x270, 70), ('sprobj', 0x158ed8, 0xa0, 100), ('effect', 0x14fbd0, 0x44, 100),
         ('teffect', 0x151660, 0xb8, 100), ('drop', 0x158230, 0x60, 30), ('tile', 0x11fe2c, 0x24, 20)]
# struct type (+0x14) of each family (OBJ types, = hl4.game.Pool constants)
FAMILY = {1: 'npc', 2: 'hero', 3: 'monster', 4: 'summon', 5: 'effect', 6: 'teffect', 7: 'mapobj', 8: 'item', 9: 'drop', 10: 'tile', 12: 'arms', 16: 'sprobj'}
WIDTH = {'char': 1, 'uchar': 1, 'byte': 1, 'undefined1': 1, 'bool': 1, 'short': 2, 'ushort': 2, 'undefined2': 2, 'void': 0}
STATE = [('g', '_game'), ('e', '_event')]


def parse_spec(tok):
    """'NAME[/N][+r][=j0:j1:..]' -> (name, nargs or None, force_ret, map or None)"""
    m = re.match(r'^(\w+)(?:/(\d+))?(\+r)?(?:=(.*))?$', tok)
    if not m:
        raise SystemExit('bad spec ' + tok)
    mp = None if m.group(4) is None else [None if x == '-' else int(x) for x in m.group(4).split(':')]
    return m.group(1), (int(m.group(2)) if m.group(2) else None), bool(m.group(3)), mp


def spec_sigs(toks):
    """Ghidra signatures with the spec overrides applied: {name: (arg widths, ret width)}, [names in order], {name: map}"""
    ps = [parse_spec(t) for t in toks]
    sg = sigs([p[0] for p in ps])
    miss = [p[0] for p in ps if p[0] not in sg]
    if miss:
        raise SystemExit('no Ghidra signature for: ' + ' '.join(miss))
    for n, na, fr, mp in ps:
        aw, rw = sg[n]
        if na is not None:
            aw = (aw + [4] * na)[:na]
        sg[n] = (aw, 4 if fr else rw)
    return sg, [p[0] for p in ps], {p[0]: p[3] for p in ps}


def sigs(names):
    """Ghidra signature -> (arg widths, ret width) for each name"""
    want = set(names)
    out = {}
    lines = open(GH, encoding='utf-8', errors='replace').read().split('\n')
    for i, ln in enumerate(lines):
        m = re.match(r'// ==== (\S+) @', ln)
        if m and m.group(1) in want:
            decl = ''
            for k in range(i + 1, i + 12):
                if lines[k].strip() == '{':
                    break
                decl += ' ' + lines[k]
            decl = re.sub(r'/\*.*?\*/', ' ', decl)
            d = re.match(r'\s*(.*?)\s*\b' + re.escape(m.group(1)) + r'\s*\((.*)\)', decl)
            rt, ps = d.group(1), d.group(2).strip()
            rw = 4 if '*' in rt else WIDTH.get(rt.split()[-1], 4)
            aw = []
            if ps and ps != 'void':
                for p in ps.split(','):
                    t = p.strip().rsplit(' ', 1)[0]
                    aw.append(4 if '*' in p else WIDTH.get(t, 4))
            out[m.group(1)] = (aw, rw)
    return out


def main():
    binp, res, outp, t0, t1 = sys.argv[1], sys.argv[2], sys.argv[3], int(sys.argv[4]), int(sys.argv[5])
    sg, fns, _ = spec_sigs(sys.argv[6].split(','))
    script = ref_game.parse_script(sys.argv[7:])
    import e_snap
    snap = os.environ.get('SNAPLOAD')
    g, first = e_snap.boot(binp, res)
    rd = g.read
    pools = [(f, a - 0x10000, st, n) for f, a, st, n in POOLS]
    hero = g.objaddr('_pool')
    pools.insert(0, ('hero', hero, 0xd50, 1))
    gsz = {o[2]: (o[0], o[1]) for o in g.elf.objs}
    fam = FAMILY
    masks = {f: mask_for(f) for f in [p[0] for p in pools] + ['_game', '_event']}

    def mbytes(b, name):
        b = bytearray(b)
        for lo, hi in masks[name]:
            b[lo:hi] = bytes(len(b[lo:hi]))
        return bytes(b)

    def masked(b, name):
        return '%08x' % (zlib.crc32(mbytes(b, name)) & 0xffffffff)
    caller = os.environ.get('CALLER') == '1'
    dumpk = [k for k in os.environ.get('CALLDUMP', '').split(',') if k]

    def dumps(args):
        out = []
        for k in dumpk:
            if k == 'h':
                b = mbytes(rd(hero, 0xd50), 'hero')
            elif k in ('g', 'e'):
                nm = dict(STATE)[k]
                b = mbytes(rd(gsz[nm][0], gsz[nm][1]), nm)
            else:
                b = None
                for v in args:
                    for f, a, st, n in pools:
                        if b is None and a <= v < a + st * n and (v - a) % st == 0:
                            b = mbytes(rd(v, st), f)
                if b is None:
                    continue
            out.append('D %s %s\n' % (k, b.hex()))
        return ''.join(out)

    def ident(v):
        """pool object identity, 'ptr' or the signed value"""
        for f, a, st, n in pools:
            if a <= v < a + st * n:
                if (v - a) % st:
                    return 'ptr'
                return 'o%d.%d' % (rd(v + 0x14, 1)[0], int.from_bytes(rd(v + 0x16, 2), 'little'))
        if 0xd0000 <= v < 0x150000 or v >= 0x10000000:       # .rodata/.data/.bss (0xd35d0..0x14e1a0) or heap
            return 'ptr'
        return str(v - (1 << 32) if v & 0x80000000 else v)

    def objhash(v):
        for f, a, st, n in pools:
            if a <= v < a + st * n and (v - a) % st == 0:
                return masked(rd(v, st), f)
        return None

    def state():
        s = ['%s=%s' % (k, masked(rd(gsz[nm][0], gsz[nm][1]), nm)) for k, nm in STATE]
        s.append('h=' + masked(rd(hero, 0xd50), 'hero'))
        ma = pools[2][1]
        s.append('m=%08x' % (zlib.crc32(b''.join(bytes.fromhex(masked(rd(ma + i * 0x430, 0x430), 'monster')) for i in range(25))) & 0xffffffff))
        return ' '.join(s)

    out = open(outp, 'w')
    for f in fns:
        aw, rw = sg[f]
        out.write('# sig %s %s %d\n' % (f, ','.join(map(str, aw)) or '-', rw))
    for nm, r in sorted(masks.items()):
        out.write('# mask %s %s\n' % (nm, ','.join('%d-%d' % x for x in r) or '-'))
    for t, f in sorted(fam.items()):
        out.write('# family %d %s\n' % (t, f))
    out.write('# scenario %s; ticks %d..%d%s\n' % (' '.join(sys.argv[7:]), t0, t1, '; snapshot ' + snap if snap else ''))
    cur = [-1]
    pend = []
    retaddrs = set()
    uc = g.uc
    R = (arm_const.UC_ARM_REG_R0, arm_const.UC_ARM_REG_R1, arm_const.UC_ARM_REG_R2, arm_const.UC_ARM_REG_R3)
    SP = arm_const.UC_ARM_REG_SP

    def onret(uc, addr, size, ud):
        sp = uc.reg_read(SP)
        for i in range(len(pend) - 1, -1, -1):
            p = pend[i]
            if p[1] == addr and sp == p[2]:
                del pend[i]
                rw = sg[p[0]][1]
                ret = '-' if rw == 0 else ident(uc.reg_read(R[0]))
                oh = ['%s:%s' % (ident(v), h) for v, h in ((v, objhash(v)) for v in p[3]) if h is not None]
                out.write('C %d %d %s %s %s %s%s%s\n' % (p[4], p[5], p[0], ','.join(ident(v) for v in p[3]) or '-', ret, state(),
                                                          (' o=' + ','.join(oh)) if oh else '', (' from=' + g.elf.name(p[1])) if caller else ''))
                if dumpk:
                    out.write(dumps(p[3]))
                return

    def mk(fn):
        a = g.elf.fbyname[fn][0]
        n = len(sg[fn][0])

        def hook(uc, addr, size, ud):
            if cur[0] < t0:
                return
            lr = uc.reg_read(arm_const.UC_ARM_REG_LR) & ~1
            sp = uc.reg_read(SP)
            args = [uc.reg_read(R[i]) if i < 4 else int.from_bytes(rd(sp + 4 * (i - 4), 4), 'little') for i in range(n)]
            pend.append((fn, lr, sp, args, cur[0], len(pend)))
            if lr not in retaddrs:
                retaddrs.add(lr)
                uc.hook_add(UC_HOOK_CODE, onret, begin=lr, end=lr)
        uc.hook_add(UC_HOOK_CODE, hook, begin=a, end=a)
    for f in fns:
        mk(f)
    for i in range(first, t1 + 1):
        e_snap.maybe_save(g, i)
        cur[0] = i
        ref_game.tick_script(g, script, i)
    probe = os.environ.get('PROBE')
    if probe:
        cur[0] = t1 + 1
        base = {f: (a, st) for f, a, st, n in pools}
        from and_emu import RET_MAGIC
        for ln in open(probe):
            ln = ln.split('#')[0].split()
            if not ln:
                continue
            args = []
            for x in ln[1:]:
                if x.startswith('o'):
                    t, k = x[1:].split('.')
                    a, st = base[FAMILY[int(t)]]
                    args.append(a + st * int(k))
                else:
                    args.append(int(x, 0) & 0xffffffff)
            g.call(ln[0], *args)
            if pend and pend[-1][1] == RET_MAGIC:       # the probed call returned to the sentinel, where no code hook runs
                onret(uc, RET_MAGIC, 0, None)
    out.close()


if __name__ == '__main__':
    main()
