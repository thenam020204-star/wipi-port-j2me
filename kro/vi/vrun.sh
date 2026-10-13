#!/bin/bash
# vrun.sh name total_ms every_ms "keys" [storage] [sheet_start n] : chạy bản Việt hoá (overlay kro/vi/build + âm thanh) trong harness
R=$(cd "$(dirname "$0")/../.." && pwd); cd $R
OV=${OV:-/tmp/krorun/ov}; rm -rf $OV; mkdir -p $OV
cp -r kro/overlay/. $OV/ 2>/dev/null; cp -r kro/vi/build/overlay/. $OV/
HL4_VI=$OV/vi RES=$OV kroj/tools/run.sh "$@"
