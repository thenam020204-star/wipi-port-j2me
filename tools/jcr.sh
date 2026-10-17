#!/bin/bash
# Java side of the call recorder (T0.1): instrument the chosen functions in a copy of the sources, compile it with the tests, run GameTraceTest.
#   tools/jcr.sh <spec>[,<spec>..] <reference calls (tools/e_calls.py)> <out calls> <GameTraceTest args...>
# spec: see tools/e_calls.py.  GameTraceTest gets --cont (a frame difference must not stop the call recording).  Then: python3 tools/callcmp.py <reference calls> <out calls>
cd "$(dirname "$0")/.." || exit 1
export LC_ALL=C.UTF-8 JAVA_TOOL_OPTIONS=
SPEC="$1"; REF="$2"; OUT="$3"; shift 3
W=work/callrec
python3 tools/callrec_inst.py $W/src "$SPEC" || exit 1
rm -rf $W/classes && mkdir -p $W/classes
javac --release 8 -nowarn -encoding UTF-8 -cp work/tools/j2me/midp-2.0-stub-1.0.jar -d $W/classes $(find $W/src j2me/test -name '*.java') 2>&1 | grep -v '^Note:' || true
test -f $W/classes/hl4/test/CallRec.class || exit 1
FROM=$(grep -m1 '^C ' "$REF" | cut -d' ' -f2)
exec java $JT_OPTS -Dcallrec.dump="$CALLDUMP" -Dcallrec.out="$OUT" -Dcallrec.ref="$REF" -Dcallrec.from="${FROM:-0}" -cp $W/classes:work/tools/j2me/midp-2.0-stub-1.0.jar:work/res "$@" --cont
