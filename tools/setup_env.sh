#!/bin/bash
# Rebuilds the gitignored work/ tree in a fresh container (see docs/HANDOFF.md §2).
#   tools/setup_env.sh <KTF zip> <hl4_android.zip> [--ghidra]
# 1. python deps of the reference runner / disassembler, 2. J2ME build jars from the committed reference zip (sha256-checked),
# 3. original inputs -> work/orig (tools/inventory.py), work/res symlink.  Golden traces are regenerated separately (HANDOFF §4).
set -e
cd "$(dirname "$0")/.." || exit 1
export LC_ALL=C.UTF-8
KTF="$1"
AND="$2"
pip install -q unicorn==2.1.4 capstone==5.0.9
T=work/tools/j2me
mkdir -p "$T"
# the build jars ship inside the committed reference project zip (HL4_project/tools/*.jar); checked against the hashes the port was built with
unzip -o -j -q HL4_project_2026-09-27j.zip 'HL4_project/tools/*.jar' -d "$T"
(cd "$T" && sha256sum -c --quiet <<'SUMS'
9cddda75f4a1b4469e73f44e7b61a3e897d0f657df4797f9106ffe88c4eeade0  ecj-4.6.1.jar
7587a3a415d6aea1946f597f4babe710a245f085bac4dc5e7cda653acfc39b03  proguard-base-6.2.2.jar
755049cafafd179033f9937791bccc8ed49712e82a071d2f311101c52be6d95a  cldc-1.1-stub-1.0.jar
33ebcfc5b2c6d50dce95ba57b3d128736aa315c5d681209f863caa1131b9ed3b  midp-2.0-stub-1.0.jar
f86f41bfb4097e4bd9cdfeca528960f6977cbda0f5785342adef9909a6a3baef  cldcapi11-2.0.4.jar
b7f6ed5e0344c7000643ecc114e6edff3587e8e77face546af0e12ffd6dbe826  midpapi20-2.0.4.jar
4b8653aa8259d55ba13fd9da9e3a55bf2ef660edffa073ef7a6f1160ba001639  microemu-javase-swing-2.0.4.jar
9de8726d16d559da98bd36b30e83c992ced946200e80d93a9f4c61f71ceda6af  microemu-jsr-135-2.0.4.jar
SUMS
)
if [ -n "$KTF" ] && [ ! -d work/orig/android ]; then
    echo "49ade89578c59befbcc12945fa38070e0e31b625d9eff9975b5b5916d7055c42  $KTF" | sha256sum -c --quiet
    echo "45adb3eef88a117679f0d64c20fc2285b151d693f77fd4ce7e4b3f0d1c070a93  $AND" | sha256sum -c --quiet
    python3 -I tools/inventory.py --ktf "$KTF" --ref HL4_project_2026-09-27j.zip --android "$AND" --out work/orig --docs docs
fi
ln -sfn "$PWD/work/orig/android/영웅서기4.apk!/res/raw/jarfile.mp3!/res" work/res
B="work/orig/android/영웅서기4.apk!/res/raw/jarfile.mp3!/binary.mod"
test -f "$B" && sha256sum "$B"
python3 tools/j2me_build.py build | tail -2
# optional: Ghidra pseudo-C for tools/cfunc.py (reading aid only; ~450 MB download, ~10 min)
if [ "$3" = "--ghidra" ] && [ ! -f work/ghidra/decomp_and2011.c ]; then
    mkdir -p work/ghidra/proj
    G=work/tools/ghidra_11.3.2_PUBLIC
    if [ ! -d "$G" ]; then
        curl -sSfL https://github.com/NationalSecurityAgency/ghidra/releases/download/Ghidra_11.3.2_build/ghidra_11.3.2_PUBLIC_20250415.zip -o work/tools/ghidra.zip
        unzip -q work/tools/ghidra.zip -d work/tools
    fi
    cp "$B" work/ghidra/binary.mod
    $G/support/analyzeHeadless work/ghidra/proj hl4 -import work/ghidra/binary.mod -processor "ARM:LE:32:v5t" -scriptPath tools/ghidra \
        -postScript DecompAll.java work/ghidra/decomp_and2011.c -deleteProject > work/ghidra/run.log 2>&1
fi
