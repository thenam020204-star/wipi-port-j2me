#!/usr/bin/env python3
"""Reference STATE dump (byte-exact structs of the ORIGINAL) after a tick, for state-level differential debugging of the Java port.
  e_state.py dump <binary.mod> <res> <out.txt> <tick>[,<tick>...] <ticks_total> [tick:key ...]   -> lines 'T name hex'
  e_state.py diff <ref.txt> <java.txt> [T]   -> per struct: differing byte ranges (pointer slots are expected to differ: see MASK in this file)
Structs: pool objects (hero, summon, monster0..24, npc0..19, mapobj0..899(non-free only), item/arms/drop/tile/sprobj/effect slots in use) and the globals."""
import os, struct, sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

POOLX = {  # name: (type base offset within _pool, stride, count)  (MP_getObj @0xad250; the +0xe94c style constants of Ghidra are pool-relative)
    'hero': (0, 0xd50, 1), 'summon': (0xd50, 0x63c, 1),
    'monster': (0x1e94c - 0x10000, 0x430, 25),
}
GLOBALS = ['_game', '_map', '_event', '_gameMenu', '_slotInfo', '_lcdEff', '_itemBag', '_armsBag', '_quest', '_questBoss', '_menuOptions', '_mainMenu']


def addr_of_ghidra(g, ghidra_addr):
    """Ghidra addresses are real + 0x10000."""
    return ghidra_addr - 0x10000


def dump(g):
    out = {}
    for nm in ('_game', '_map', '_event', '_gameMenu', '_slotInfo', '_lcdEff', '_menuOptions', '_questBoss'):
        try:
            a = g.objaddr(nm)
            sz = [o[1] for o in g.elf.objs if o[2] == nm][0]
            out[nm] = g.read(a, sz)
        except Exception:
            pass
    out['hero'] = g.read(addr_of_ghidra(g, 0x1017b0 + 0x10000), 0xd50) if False else g.read(g.objaddr('_pool'), 0xd50)
    out['summon'] = g.read(addr_of_ghidra(g, 0x112500), 0x63c)
    for i in range(25):
        out['monster%d' % i] = g.read(addr_of_ghidra(g, 0x1200fc) + i * 0x430, 0x430)
    for i in range(20):
        out['npc%d' % i] = g.read(addr_of_ghidra(g, 0x155e40) + i * 0x1cc, 0x1cc)
    for i in range(900):
        b = g.read(addr_of_ghidra(g, 0x112b3c) + i * 0x3c, 0x3c)
        if b[0x15] == 0:
            out['mapobj%d' % i] = b
    for i in range(200):
        b = g.read(addr_of_ghidra(g, 0x1269b0) + i * 0x270, 0x270)
        if b[0x15] == 0:
            out['item%d' % i] = b
    for i in range(70):
        b = g.read(addr_of_ghidra(g, 0x145130) + i * 0x270, 0x270)
        if b[0x15] == 0:
            out['arms%d' % i] = b
    for i in range(100):
        b = g.read(addr_of_ghidra(g, 0x158ed8) + i * 0xa0, 0xa0)
        if b[0x15] == 0:
            out['sprobj%d' % i] = b
        b = g.read(addr_of_ghidra(g, 0x14fbd0) + i * 0x44, 0x44)
        if b[0x15] == 0:
            out['effect%d' % i] = b
        b = g.read(addr_of_ghidra(g, 0x151660) + i * 0xb8, 0xb8)
        if b[0x15] == 0:
            out['teffect%d' % i] = b
    for i in range(30):
        b = g.read(addr_of_ghidra(g, 0x158230) + i * 0x60, 0x60)
        if b[0x15] == 0:
            out['drop%d' % i] = b
    for i in range(20):
        b = g.read(addr_of_ghidra(g, 0x11fe2c) + i * 0x24, 0x24)
        if b[0x15] == 0:
            out['tile%d' % i] = b
    return out


def main():
    if sys.argv[1] == 'dump':
        from ref_game import Game
        binp, res, outp, ticks, total = sys.argv[2], sys.argv[3], sys.argv[4], set(int(x) for x in sys.argv[5].split(',')), int(sys.argv[6])
        script = {}
        for kv in sys.argv[7:]:
            t, k = kv.split(':')
            script[int(t)] = int(k, 0)
        g = Game(binp, res).start()
        lines = []
        for i in range(total):
            k = script.get(i)
            g.tick(press=k, release=k) if k is not None else g.tick()
            if i in ticks:
                for nm, b in dump(g).items():
                    lines.append('%d %s %s' % (i, nm, b.hex()))
        open(outp, 'w').write('\n'.join(lines) + '\n')
    else:
        rd = {}
        for l in open(sys.argv[2]):
            t, n, h = l.split()
            rd[(int(t), n)] = bytes.fromhex(h)
        jd = {}
        for l in open(sys.argv[3]):
            t, n, h = l.split()
            jd[(int(t), n)] = bytes.fromhex(h)
        want = int(sys.argv[4]) if len(sys.argv) > 4 else None
        for k in sorted(set(rd) | set(jd), key=lambda x: (x[0], x[1])):
            if want is not None and k[0] != want:
                continue
            a, b = rd.get(k), jd.get(k)
            if a is None or b is None:
                print('T%d %-10s only in %s' % (k[0], k[1], 'ref' if b is None else 'java'))
                continue
            if a == b:
                continue
            diffs = [i for i in range(min(len(a), len(b))) if a[i] != b[i]]
            runs = []
            for i in diffs:
                if runs and i == runs[-1][1] + 1:
                    runs[-1][1] = i
                else:
                    runs.append([i, i])
            print('T%d %-10s %d bytes differ: %s' % (k[0], k[1], len(diffs), ' '.join('%#x-%#x(ref %s java %s)' % (r[0], r[1], a[r[0]:r[1] + 1][:6].hex(), b[r[0]:r[1] + 1][:6].hex()) for r in runs[:14])))


main()
