#!/usr/bin/env python3
"""Reference model of AND2011 UM_Random/UM_Rand (BSD random() TYPE_3) seeded from the
binary's own _randtbl initial contents. Usage: python3 tools/and_rng.py <binary.mod> [n]
Mirrors UM_Random @0xc8f2c: *f += *r; out = *f >> 1; f,r advance and wrap over tbl[1..31]."""
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from andelf import AndElf

class Rng:
    def __init__(self, e):
        base = 0xe1f90                      # _randtbl (type word + 31 state words)
        self.t = [e.u32(base + 4 * i) for i in range(1, 32)]
        self.f = (e.u32(0xe2010) - base) // 4 - 1   # _fptr initial -> &tbl[4] -> index 3
        self.r = (e.u32(0xe2014) - base) // 4 - 1   # _rptr initial -> &tbl[1] -> index 0
    def random(self):
        self.t[self.f] = (self.t[self.f] + self.t[self.r]) & 0xffffffff
        out = self.t[self.f] >> 1
        self.f = (self.f + 1) % 31; self.r = (self.r + 1) % 31
        return out
    def rand(self, a, b):                   # UM_Rand @0xc8fc0 (unsigned modulo)
        return (self.random() % ((b - a + 1) & 0xffffffff)) + a

if __name__ == '__main__':
    e = AndElf(sys.argv[1]); g = Rng(e)
    n = int(sys.argv[2]) if len(sys.argv) > 2 else 10
    print('fptr idx', g.f, 'rptr idx', g.r)
    print(' '.join(str(g.random()) for _ in range(n)))
