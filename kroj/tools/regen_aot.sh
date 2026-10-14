#!/bin/bash
# Regenerate kroj/aot/hl4/aot/LgtAot.java (Chroisen) from kroj/profiles/kro_*.txt.
# Sinh từ binary.mod ĐÃ Việt hoá (kro/vi/build/overlay, tạo bởi build_vi.py); literal được đọc từ bộ nhớ lúc chạy
# nên AOT không phụ thuộc chuỗi. --exclude: các địa chỉ bị KroText hook/vá (runtime cũng aotBlock các chỗ này).
cd "$(dirname "$0")/.."
BIN=${BIN:-../kro/vi/build/overlay/binary.mod}
python3 tools/aotgen.py --bin $BIN --fileoff 0x34 --size 0x5dfd7 --base 0x1000 --class LgtAot \
  --out aot/hl4/aot/LgtAot.java --maxinsns 1000 --coldcut 0.001 --funcregions --hostcalls --funcmaxinsns 700 --maxbytes 7200 \
  --funcmin ${FUNCMIN:-20000} --maxregions 4000 \
  --exclude 28e14-28e1c --exclude 297e8-297ec --exclude 29aa0-29aa4 $EXTRA profiles/kro_*.txt
