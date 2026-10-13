#!/bin/bash
# Regenerate src/aot/hl4/aot/LgtAot.java (HL5) from the stored profiles (absolute addresses).
cd "$(dirname "$0")/.."
P=profiles
python3 tools/aotgen.py --bin ../hl5/jar/binary.mod --fileoff 0x34 --size 0x118174 --base 0x1000 --class LgtAot \
  --out src/aot/hl4/aot/LgtAot.java --maxinsns 1000 --coldcut 0.001 --funcregions --hostcalls --funcmaxinsns 700 --maxbytes 7200 --funcmin ${FUNCMIN:-20000} --maxregions 4000 \
  --exclude 8947c-898f4 --exclude 89030-8918e --exclude 88ec8-8902e --exclude 891a8-8930e --exclude 74c7e-74c82 --exclude 74da4-74da8 --exclude 7551e-75522 --exclude 757c8-757cc --exclude 75888-7588c --exclude 5be4-5be8 --exclude 87c04-87c66 --exclude 87c68-87cca --exclude 87dbc-87f6e --exclude 4bdf0-4bdf2 --exclude 50194-50196 --exclude 501ba-501bc --exclude 5025c-50260 --exclude 5036c-50370 --exclude 504c4-504c8 --exclude 512e0-512e4 $EXTRA $P/hl5_*.txt
