#!/bin/bash
# compile headless test harness: tbuild.sh [outdir]   (env AOTDIR = dir with hl4/aot/LgtAot.java)
cd "$(dirname "$0")"
OUT=${1:-build/t}
rm -rf "$OUT.tmp"; mkdir -p "$OUT.tmp"
javac -nowarn -encoding UTF-8 -d "$OUT.tmp" $(find src/core $AOTDIR -name '*.java') test/src/RunHeadless.java test/src/SndConvert.java test/src/SndConvert5.java 2>&1 | grep -v "^Note:\|JAVA_TOOL"
rm -rf "$OUT"; mv "$OUT.tmp" "$OUT"
