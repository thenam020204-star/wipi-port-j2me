#!/usr/bin/env python3
"""Annotated ARM disassembly of named functions of the AND2011 binary.  Usage: dis.py NAME [NAME ...]"""
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from andelf import AndElf
B = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'work', 'orig', 'android', '영웅서기4.apk!', 'res', 'raw', 'jarfile.mp3!', 'binary.mod')
e = AndElf(B)
for n in sys.argv[1:]:
    print('==== %s @%#x size %d' % ((n,) + tuple(e.fbyname[n][:2])))
    print(e.dump(n))
