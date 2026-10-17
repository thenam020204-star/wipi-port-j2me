#!/usr/bin/env python3
"""Snapshots of the reference runner (T0.3, docs/PLAN_NEXT.md): the whole emulator state between two ticks (every mapped memory region,
the used part of the heap, and the plain-data state of the HLE: clock, RNG call count, heap block lists, files, frame buffers, resources ...),
so e_trace / e_calls / e_ret / e_raw / e_state / e_cover can continue from tick N instead of replaying ticks 0..N-1.
Registers are not saved: every tick is a fresh call of timerCB (and_emu.Emu.call sets SP/LR/PC).

  e_snap.py <binary.mod> <res> <out_prefix> <t1,t2,..> [tick:key ...]   -> <out_prefix>_<t>.snap = state BEFORE tick t (after ticks 0..t-1)
  any tool that calls e_snap.boot(): env SNAPLOAD=<file.snap> starts there; the scenario tokens stay the FULL scenario (ticks < t are skipped)
  env SNAPSAVE=<t1,t2..> SNAPDIR=<dir> (with e_snap.maybe_save in the tick loop) saves snapshots while a tool runs from the start

Snapshot files hold original game memory (decoded assets): they live under work/ (gitignored) and are never committed."""
import os
import pickle
import sys
import zlib

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ref_game
from ref_game import Game
from and_emu import HEAP_BASE

VERSION = 1
SKIP = ('calls',)          # the import-call log only grows; not state


def _plain(v):
    if v is None or isinstance(v, (bool, int, float, str, bytes, bytearray)):
        return True
    if isinstance(v, (list, tuple, set)):
        return all(_plain(x) for x in v)
    if isinstance(v, dict):
        return all(_plain(k) and _plain(x) for k, x in v.items())
    return False


def save(g, path, tick):
    attrs = {k: v for k, v in g.__dict__.items() if k not in SKIP and _plain(v)}
    mem = []
    for begin, end, perm in g.uc.mem_regions():
        if begin == HEAP_BASE:
            end = max(begin, g.heap_ptr) - 1
            if end < begin:
                continue
        mem.append((begin, zlib.compress(bytes(g.uc.mem_read(begin, end - begin + 1)), 1)))
    with open(path + '.tmp', 'wb') as f:
        pickle.dump({'version': VERSION, 'tick': tick, 'attrs': attrs, 'mem': mem}, f, protocol=4)
    os.replace(path + '.tmp', path)


def load(binp, res, path, **kw):
    """-> (Game positioned before tick t, t)"""
    with open(path, 'rb') as f:
        d = pickle.load(f)
    assert d['version'] == VERSION
    g = Game(binp, res, **kw)
    g.install()
    for begin, blob in d['mem']:
        g.uc.mem_write(begin, zlib.decompress(blob))
    for k, v in d['attrs'].items():
        setattr(g, k, v)
    g.uc.ctl_flush_tb()
    return g, d['tick']


def restore(g, d):
    """put a loaded snapshot dict (pickle content) back onto an existing Game (its hooks stay): faster than load() for many runs"""
    for begin, blob in d['mem']:
        g.uc.mem_write(begin, zlib.decompress(blob))
    for k, v in d['attrs'].items():
        setattr(g, k, pickle.loads(pickle.dumps(v)))      # private copies: the run mutates dicts / lists
    g.uc.ctl_flush_tb()
    return d['tick']


def read(path):
    with open(path, 'rb') as f:
        d = pickle.load(f)
    assert d['version'] == VERSION
    return d


def boot(binp, res, **kw):
    """Game + first tick to run: from env SNAPLOAD if set, else a fresh start at tick 0"""
    p = os.environ.get('SNAPLOAD')
    if p:
        return load(binp, res, p, **kw)
    return Game(binp, res, **kw).start(), 0


_SAVE = set(int(x) for x in os.environ.get('SNAPSAVE', '').split(',') if x)


def maybe_save(g, tick):
    """call at the top of a tick loop (before tick 'tick' runs): saves SNAPDIR/snap_<tick>.snap when SNAPSAVE lists the tick"""
    if tick in _SAVE:
        d = os.environ.get('SNAPDIR', 'work/snap')
        os.makedirs(d, exist_ok=True)
        save(g, os.path.join(d, 'snap_%d.snap' % tick), tick)


def main():
    binp, res, prefix, ticks = sys.argv[1], sys.argv[2], sys.argv[3], sorted(int(x) for x in sys.argv[4].split(','))
    script = ref_game.parse_script(sys.argv[5:])
    g, first = boot(binp, res)
    for i in range(first, ticks[-1] + 1):
        if i in ticks:
            save(g, '%s_%d.snap' % (prefix, i), i)
            print('saved', '%s_%d.snap' % (prefix, i), flush=True)
        if i < ticks[-1]:
            ref_game.tick_script(g, script, i)


if __name__ == '__main__':
    main()
