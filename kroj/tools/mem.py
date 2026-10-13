import struct, sys
def load(p):
    d = open(p, 'rb').read(); mem = {}
    o = 0
    while o < len(d):
        pi = struct.unpack_from('>i', d, o)[0]; o += 4
        mem[pi] = d[o:o + 65536]; o += 65536
    return mem
def find_seq(mem, vals, fmt, maxgap=16):
    sz = struct.calcsize('<' + fmt)
    res = []
    for pi, pg in mem.items():
        for off in range(0, len(pg) - sz, 1 if sz == 1 else 2):
            if struct.unpack_from('<' + fmt, pg, off)[0] != vals[0]: continue
            # try strides
            for st in range(sz, maxgap + 1, sz):
                ok = True
                for k, v in enumerate(vals[1:], 1):
                    o2 = off + k * st
                    if o2 + sz > len(pg) or struct.unpack_from('<' + fmt, pg, o2)[0] != v: ok = False; break
                if ok: res.append((hex((pi << 16) + off), st))
    return res
