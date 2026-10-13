#!/bin/bash
# run.sh name total_ms every_ms "keys" [storage_src] [sheet_start n]
# env: CP (classpath, default kroj/build/t), JOPT, RES (overlay dir), GAMEZIP, S (out dir), plus HL4_* debug vars
R=$(cd "$(dirname "$0")/../.." && pwd); cd $R
S=${S:-/tmp/krorun}; mkdir -p $S   # thư mục kết quả (đặt biến S để đổi)
O=$S/$1; rm -rf $O; mkdir -p $O/storage
[ -n "$5" ] && cp -r $5/. $O/storage/
export JAVA_TOOL_OPTIONS=
HL4_RES=${RES:-} java $JOPT -Dstorage=$O/storage -cp ${CP:-kroj/build/t} RunHeadless ${GAMEZIP:-kro/KRO.zip} $2 $3 $O "$4" > $O.log 2>&1
python3 kroj/tools/jsheet.py $O $O.png ${6:-0} ${7:-40} 0.4
