#!/usr/bin/env python3
"""Disassemble a scene's script tables (events / second table) into opcode lists.  Usage: scn_dis.py <e0000_scn path>"""
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import hl4des, scn
CNT = {0:4,1:2,2:2,3:5,4:2,5:2,6:3,7:5,8:0,9:4,10:3,11:2,12:2,13:5,14:4,15:2,16:3,17:1,18:1,19:1,20:1,21:1,22:4,23:1,24:2,25:4,26:1,27:1,28:3,29:3,30:2,31:2,32:2,33:1,34:2,35:1,36:2,37:4,38:4,39:1,40:1,41:4,42:2,43:3,44:2,45:1,46:4,47:1,48:1,49:1,50:1,51:1,52:1,53:1,54:1,55:1,56:1,57:0,58:1,59:1}
def dis(b):
    p = 0; out = []
    while p < len(b):
        op = b[p]; p += 1
        if op == 0xff:
            t = b[p]; n = b[p + 1]; p += 2
            ids = []; tot = 0
            for _ in range(n):
                ids.append(b[p]); tot += b[p + 1]; p += 2
            out.append('MSGBOX(title=%d ids=%s skip=%d)' % (t, ids, tot)); p += tot
        else:
            c = CNT.get(op)
            if c is None:
                out.append('?%#x' % op); continue
            out.append('%#x(%s)' % (op, ','.join(str(x) for x in b[p:p + c]))); p += c
    return out
if __name__ == '__main__':
    d = scn.parse(hl4des.decrypt(open(sys.argv[1], "rb").read()))
    print(d['hdr'])
    for i, e in enumerate(d['events']): print('event', i, e.hex())
    for i, s in enumerate(d['second']): print('script', i, ' '.join(dis(s)))
    for i, s in enumerate(d['strings']): print('str', i, s)
