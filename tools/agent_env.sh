#!/bin/bash
# work/ of a git worktree (parallel agents, docs/AGENT_BRIEF.md): the read-only inputs are symlinked from the main checkout,
# build outputs (work/build, work/callrec, work/jt.cp) stay private to the worktree.
#   tools/agent_env.sh <main checkout>        (run inside the worktree)
set -e
cd "$(dirname "$0")/.."
MAIN=$(cd "$1" && pwd)
test "$MAIN" != "$PWD" || { echo "run inside a worktree, not the main checkout"; exit 1; }
mkdir -p work
for d in tools orig golden ghidra snap; do
    test -e "work/$d" || ln -s "$MAIN/work/$d" "work/$d"
done
ln -sfn "$(readlink -f "$MAIN/work/res")" work/res
python3 tools/j2me_build.py build | tail -1
