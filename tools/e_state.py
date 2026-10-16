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


def mask_for(name):
    """byte ranges that hold ORIGINAL pointers (a Java port keeps references elsewhere), so they are not compared"""
    import re
    base = re.sub(r'\d+$', '', name)
    r = []
    if base in ('hero', 'summon', 'monster', 'npc', 'mapobj', 'item', 'arms', 'sprobj', 'effect', 'teffect', 'drop', 'tile'):
        r += [(0, 8), (0x10, 0x14)]
    if base in ('hero', 'summon', 'monster', 'npc'):
        r += [(0x48, 0x94), (0x130, 0x13c + 12)]   # +0x20..0x47 = SPR header ints (anchor, move rect, rect 2) are compared
    if base == 'monster':
        # AI script heap pointers (EnemyAIParsing_LoadData / setAclionList / ActionCheck; the Java port keeps them in Char.ai),
        # the speech-string pointer (+0x2b0) and the attack-property table / cells pointers (+0x2b4, +0x2c0; Char.hitTable / hitCells)
        for a in (0x210, 0x214, 0x218, 0x21c, 0x224, 0x22c, 0x238, 0x23c, 0x240, 0x244, 0x24c, 0x258, 0x260, 0x284, 0x288, 0x2b0, 0x2b4, 0x2c0):
            r += [(a, a + 4)]
    if base == 'hero':
        r += [(0x22c, 0x230), (0x238, 0x23c)]     # attack-property table / cells pointers (Char.hitTable / hitCells)
    if base == 'npc':
        r += [(0x1b0, 0x1b4)]
    if base == 'mapobj':
        r += [(0x20, 0x24)]
    if base == 'sprobj':
        r += [(0x20, 0x94)]
    if base in ('effect', 'teffect'):
        r += [(0x20, 0x48)]
    if base == 'teffect':
        r += [(0x48, 0x4c), (0x54, 0x58)]          # sprite / attack-property table pointers (TEff.spr, TEff.hitTable)
    if base == 'hero':
        r += [(0x1f0, 0x1f4), (0xa50, 0xad0)]   # particle-info pointer; the embedded particle SPR struct (HERO_Copy_Sprite_ImageInfo: image pointers; Java Particles keep their own Sprite)
    if base == 'summon':
        r += [(0x634, 0x638)]                    # SUMMON_init stores a pointer to the hero object
    if name == '_menuOptions':
        r += [(10, 24)]                          # Java keeps 10 option bytes (the rest of the original's 24 is not an option)
    if name == '_map':
        r += [(0x14a, 0x164), (0x21c, 0x238)]    # minimap tile image state (Map_Draw_Create_Minimap_Tile, HUD pass pending)
        r += [(0x340, 0x344)]                    # pointer (loaded effect sprite)
        r += [(0x8, 0x44), (0x278, 0x288), (0x2a4, 0x2c8), (0x35c, 0x364)]
    if name == '_game':
        r += [(0x68, 0x118), (8, 12)]            # +8: pointer to the monster of the info bar (MainGameFrame.infoMonster)
    return r


def main():
    if sys.argv[1] == 'dump':
        from ref_game import Game
        binp, res, outp, ticks, total = sys.argv[2], sys.argv[3], sys.argv[4], set(int(x) for x in sys.argv[5].split(',')), int(sys.argv[6])
        import ref_game
        script = ref_game.parse_script(sys.argv[7:])
        g = Game(binp, res).start()
        lines = []
        for i in range(total):
            ref_game.tick_script(g, script, i)
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
            mk = mask_for(k[1])
            diffs = [i for i in range(min(len(a), len(b))) if a[i] != b[i] and not any(lo <= i < hi for lo, hi in mk)]
            if not diffs:
                continue
            runs = []
            for i in diffs:
                if runs and i == runs[-1][1] + 1:
                    runs[-1][1] = i
                else:
                    runs.append([i, i])
            print('T%d %-10s %d bytes differ: %s' % (k[0], k[1], len(diffs), ' '.join('%#x-%#x(ref %s java %s)' % (r[0], r[1], a[r[0]:r[1] + 1][:6].hex(), b[r[0]:r[1] + 1][:6].hex()) for r in runs[:14])))


main()
