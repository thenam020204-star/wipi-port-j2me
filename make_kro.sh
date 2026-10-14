#!/bin/bash
# Build Chroisen (크로이센, LGT WIPI, AID 0002CCCB) -> J2ME MIDP2/CLDC1.1 jar + jad.
#   ./make_kro.sh [suffix]          bản gốc (chữ Hàn)
#   VI=1 ./make_kro.sh _vi1         bản Việt hoá (kro/vi/tr/*.tsv, ảnh kro/img, logo NAMP)
#   env: NOAOT=1 (không dùng kroj/aot), FPS (mặc định 27)
# Kết quả: dist/CHROISEN_LGT<suffix>.jar/.jad + quét tương thích J2ME (tools/j2me_scan.py)
set -e
R=$(cd "$(dirname "$0")" && pwd); cd $R
export JAVA_TOOL_OPTIONS=
if [ ! -f kro/jar/binary.mod ]; then
  rm -rf kro/jar kro/orig && mkdir -p kro/jar kro/orig
  unzip -q -o kro/KRO.zip -d kro/orig && (cd kro/jar && unzip -q -o ../orig/*.jar)
fi
(cd kroj && AOTDIR= ./tbuild.sh build/t)
OV=$R/kro/overlay; rm -rf $OV; mkdir -p $OV/sound
java -cp kroj/build/t SndConvert5 kro/jar/sound $OV/sound /sound/
if [ -n "$VI" ]; then
  python3 kro/vi/extract.py > /dev/null
  python3 kro/vi/build_vi.py --out kro/vi/build/overlay
  cp -r kro/vi/build/overlay/. $OV/
  [ -f kro/img/mk_images.py ] && python3 kro/img/mk_images.py kro/jar $OV
fi
AOT=; [ -z "$NOAOT" ] && [ -f kroj/aot/hl4/aot/LgtAot.java ] && AOT=$R/kroj/aot
mkdir -p dist
(cd kroj && OVERLAY=$OV SUFFIX=$1 AOTDIR=$AOT ./build.sh $R/kro/KRO.zip $R/dist)
python3 tools/j2me_scan.py dist/CHROISEN_LGT$1.jar tools/cldc-1.1-stub-1.0.jar tools/midp-2.0-stub-1.0.jar
