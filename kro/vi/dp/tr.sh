#!/bin/bash
# dp2tr: dp/w/<cat>/dp_work/final/<cat>.tsv -> tr/<cat>.tsv (ID, Hàn, Việt)
cd "$(dirname "$0")/.."
for c in "$@"; do
  (cd dp/w/$c && ../../dp merge --partial >/dev/null)
  cut -f1-3 dp/w/$c/dp_work/final/$c.tsv > tr/$c.tsv
  echo "$c: $(grep -c . tr/$c.tsv) dòng"
done
