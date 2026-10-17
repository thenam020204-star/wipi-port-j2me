#!/bin/bash
# Compile main+tests (no preverify) and run a test class: tools/jt.sh <class> [args...]   (class path includes work/res)
# Works from any checkout / git worktree (its own work/build); see tools/agent_env.sh for a worktree's work/ links.
cd "$(dirname "$0")/.." || exit 1
export LC_ALL=C.UTF-8 JAVA_TOOL_OPTIONS=
mkdir -p work
python3 - <<'PY' || exit 1
import sys
sys.path.insert(0, 'tools')
import j2me_build as B
cls = B.compile_main()
tc = B.compile_tests()
open('work/jt.cp', 'w').write(cls + ':' + tc)
PY
CP=$(cat work/jt.cp):work/res
exec java $JT_OPTS -cp "$CP" "$@"
