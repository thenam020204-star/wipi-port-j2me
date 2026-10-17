#!/bin/bash
# Compile main+tests (no preverify) and run a test class: tools/jt.sh <class> [args...]   (class path includes work/res)
cd /home/user/wipi-port-j2me || exit 1
export LC_ALL=C.UTF-8 JAVA_TOOL_OPTIONS=
python3 - <<'PY' || exit 1
import sys
sys.path.insert(0, 'tools')
import j2me_build as B
cls = B.compile_main()
tc = B.compile_tests()
open('/tmp/jt.cp', 'w').write(cls + ':' + tc)
PY
CP=$(cat /tmp/jt.cp):work/res
exec java $JT_OPTS -cp "$CP" "$@"
