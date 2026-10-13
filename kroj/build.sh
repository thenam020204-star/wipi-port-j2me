#!/bin/bash
# Build the Xenonia J2ME jar (LGT WIPI-C port) from the original archive.
# usage: build.sh <original.zip> <out_dir>      env: OVERLAY (extra files), SUFFIX, HL4_SAVEDBG, FPS
set -e
HERE=$(cd "$(dirname "$0")" && pwd)
TOOLS=$HERE/../tools
ZIP=$1; OUT=$2
BOOT=$TOOLS/cldc-1.1-stub-1.0.jar:$TOOLS/midp-2.0-stub-1.0.jar
W=$(mktemp -d)
mkdir -p $W/classes $W/pre $W/jar $W/orig "$OUT"
# 1) compile (Java 1.3 source, class files for CLDC); the KTF front end is not needed
SRC="$(find $HERE/src/core $HERE/src/midp -name '*.java' | grep -v '/ktf/')$( [ -n "$AOTDIR" ] && [ -f $AOTDIR/hl4/aot/LgtAot.java ] && echo " $AOTDIR/hl4/aot/LgtAot.java" || true)"
java -jar $TOOLS/ecj-4.6.1.jar -source 1.3 -target 1.2 -nowarn -bootclasspath $BOOT -d $W/classes $SRC
# 2) preverify with ProGuard
cat > $W/pg.pro <<PRO
-injars $W/classes
-outjars $W/pre
-libraryjars $TOOLS/cldc-1.1-stub-1.0.jar
-libraryjars $TOOLS/midp-2.0-stub-1.0.jar
-microedition
-dontobfuscate
-dontoptimize
-dontshrink
-dontnote
-dontwarn
-keep class hl4.** { *; }
PRO
java -jar $TOOLS/proguard-base-6.2.2.jar @$W/pg.pro > $W/pg.log 2>&1 || { cat $W/pg.log; exit 1; }
# 3) original archive contents
cd $W/orig && unzip -q -o "$ZIP"
JAR=$(ls $W/orig/*.jar | head -1)
cd $W/jar && unzip -q -o "$JAR" && rm -rf META-INF
cp -r $W/pre/* $W/jar/
# 3b) overlay (converted sound, ...)
if [ -n "$OVERLAY" ]; then cp -r "$OVERLAY"/. $W/jar/; fi
cp $W/orig/middle.png $W/jar/icon.png 2>/dev/null || cp $W/orig/small.png $W/jar/icon.png 2>/dev/null || true
# 4) manifest
NAME="${GNAME:-Xenonia}${SUFFIX}"
cat > $W/MANIFEST.MF <<MF
Manifest-Version: 1.0
MIDlet-1: $NAME,/icon.png,hl4.midp.HL4Midlet
MIDlet-Name: $NAME
MIDlet-Vendor: Gamevil (port)
MIDlet-Version: 1.0.0
MIDlet-Icon: /icon.png
MicroEdition-Configuration: CLDC-1.1
MicroEdition-Profile: MIDP-2.0
HL4-Platform: LGT
Nokia-MIDlet-Canvas-Scaling-Orientation-Switch: false
MIDlet-Touch-Support: false
HL4-FPS: ${FPS:-25}
HL5-LSK: ${LSK:-CLR}
MF
[ -n "$HL4_SAVEDBG" ] && echo "HL4-SAVEDBG: 1" >> $W/MANIFEST.MF
echo "HL4-AID: $(grep -a '^AID:' $W/orig/app_info | cut -d: -f2 | tr -d '\r\n')" >> $W/MANIFEST.MF
echo "HL4-PID: $(grep -a '^PID:' $W/orig/app_info | cut -d: -f2 | tr -d '\r\n')" >> $W/MANIFEST.MF
BASE=${GBASE:-XEN_LGT}${SUFFIX}
cd $W/jar && rm -f "$OUT/$BASE.jar" && jar cfm "$OUT/$BASE.jar" $W/MANIFEST.MF .
SIZE=$(stat -c %s "$OUT/$BASE.jar")
{ cat $W/MANIFEST.MF | grep -v '^Manifest-Version'; echo "MIDlet-Jar-URL: $BASE.jar"; echo "MIDlet-Jar-Size: $SIZE"; } > "$OUT/$BASE.jad"
echo "built $OUT/$BASE.jar ($SIZE bytes)"
rm -rf $W
