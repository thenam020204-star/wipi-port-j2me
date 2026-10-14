#!/bin/bash
# The Farm 2 (SKT WIPI-Java) -> J2ME MIDP 2.0 / CLDC 1.1 port.
# usage: ./build.sh [out_name]      (env: OVERLAY=<dir of resources to add/replace>, HOOKS=<hooks file>)
# Pipeline: unpack packed images to plain PNG sheets -> compile shim+host (Java 1.3 source, CLDC classes) -> remap the
# platform call sites of the original game classes -> convert SMAF sounds ->
# ProGuard preverify (CLDC StackMap) -> jar + jad -> compatibility scan
# (reject on error) -> binary diff against the original jar.
set -e
P=$(cd "$(dirname "$0")" && pwd)
L=$P/tools/lib
NAME=${1:-Farm2_VI}
OUT=$P/release
W=$P/build/work
unset JAVA_TOOL_OPTIONS
rm -rf "$W"; mkdir -p "$W"/{orig,jar,cls,game_in,game,pre,tools,res} "$OUT"
API="$L/cldc-1.1-stub-1.0.jar:$L/midp-2.0-stub-1.0.jar"
ASM="$L/asm-9.7.jar:$L/asm-commons-9.7.jar:$L/asm-tree-9.7.jar:$L/asm-analysis-9.7.jar:$L/asm-util-9.7.jar"

echo "== 0. tools"
javac -nowarn -d "$W/tools" -cp "$ASM" "$P"/tools/src/*.java 2>&1 | grep -v "^Note:" || true

echo "== 1. original archive"
( cd "$W/orig" && unzip -q -o "$P/orig/Farm2_SKT.zip" )
( cd "$W/jar" && unzip -q -o "$W/orig/0066128696.jar" 2>/dev/null || true )
rm -rf "$W/jar/META-INF"
cp "$W/orig/0066128696.jar" "$W/orig.jar"
# game classes out of the resource tree
( cd "$W/jar" && find . -name '*.class' | while read c; do mkdir -p "$W/game_in/$(dirname "$c")"; mv "$c" "$W/game_in/$c"; done )

echo "== 1b. packed images -> plain PNG sprite sheets (+ frame lists), same-length references in the containers"
java -cp "$W/tools" Unpack "$W/jar" "$W/unpack.txt"

echo "== 2. compile shim + host (+ ui) against CLDC 1.1 / MIDP 2.0, with the game classes on the classpath"
SRC=$(find "$P/src" -name '*.java')
java -jar "$L/ecj-4.6.1.jar" -encoding UTF-8 -source 1.3 -target 1.2 -nowarn -bootclasspath "$API" \
     -cp "$W/game_in" -d "$W/cls" $SRC

# the new UI layer (src/ui, default package: reads the game's package-private fields) goes through
# the same remap as the game classes (its display literals -> Str.lit / ui.txt)
mv "$W/cls"/Ui*.class "$W/game_in/"

echo "== 3. remap platform call sites in the game classes (+ drawing bodies -> new UI layer)"
java -Dui="$W/res/text/ui.txt" -Dbody="$P/tools/ui_body.txt" -cp "$W/tools:$ASM" Remap "$W/game_in" "$W/game" "$W/remap.txt" "${HOOKS:-$P/tools/ui_hooks.txt}"

echo "== 3b. text encoding table (CP949 codes used by the game data)"
mkdir -p "$W/res/enc"
python3 "$P/tools/encmap.py" "$W/jar" "$W/res/enc/map.bin" ${ENC_EXTRA:+"$ENC_EXTRA"}

echo "== 3c. game text -> plain UTF-8 .txt files (+ hash index) read at run time"
python3 -I "$P/tools/textx.py" "$W/jar" "$W/res" "$W/textx.txt" "$W/textvec.txt"
mkdir -p "$W/encres"; cp -r "$W/res/text" "$W/res/enc" "$W/encres/"

echo "== 3d. text layer check: port's Enc.txt == Enc.str for every game string (unedited .txt)"
java -cp "$W/cls:$W/encres:$W/tools" TextCheck "$W/textvec.txt"

echo "== 3f. translation (i18n/\${LANG_DIR:-vi}: key=text overlays, ui.txt by original text; checks codes/limits)"
if [ "${LANG_DIR:-vi}" != "none" ]; then
  python3 -I "$P/tools/i18n.py" "$W/res/text" "$P/i18n/${LANG_DIR:-vi}" "$W/i18n.txt"
  cp "$W/i18n.txt" "$P/build/reports/i18n_$NAME.txt" 2>/dev/null || true
fi

echo "== 3e. save files of the original handset (imported into RMS on first run; Farm-Save-Seed: 0 disables)"
mkdir -p "$W/res/save"; : > "$W/res/save/index.txt"
for f in FSave0.dat FSave1.dat FSave2.dat Option.dat; do
  if [ -f "$W/orig/$f" ]; then cp "$W/orig/$f" "$W/res/save/"; echo "$f" >> "$W/res/save/index.txt"; fi
done
echo "seed saves: $(tr '\n' ' ' < "$W/res/save/index.txt")"

echo "== 4. sounds (SMAF -> MIDI/WAV)"
java -cp "$W/tools" SndConvert "$W/jar/Sound" "$W/res" > "$W/sound.txt"
tail -1 "$W/sound.txt"

echo "== 5. preverify (ProGuard -microedition, no shrink/optimize/obfuscate)"
mkdir -p "$W/all"; cp -r "$W/cls/." "$W/all/"; cp -r "$W/game/." "$W/all/"
cat > "$W/pg.pro" <<PRO
-injars $W/all
-outjars $W/pre
-libraryjars $L/cldc-1.1-stub-1.0.jar
-libraryjars $L/midp-2.0-stub-1.0.jar
-microedition
-dontobfuscate
-dontoptimize
-dontshrink
-dontnote
-dontwarn
-keep class ** { *; }
PRO
java -jar "$L/proguard-base-6.2.2.jar" @"$W/pg.pro" > "$W/pg.log" 2>&1 || { cat "$W/pg.log"; exit 1; }

echo "== 6. jar"
cp -r "$W/pre/." "$W/jar/"
cp -r "$W/res/." "$W/jar/"
mkdir -p "$W/jar/font"; cp "$P/font/"*.bin "$W/jar/font/"
cp "$P/icon.png" "$W/jar/icon.png" 2>/dev/null || true
if [ -n "$OVERLAY" ]; then cp -r "$OVERLAY"/. "$W/jar/"; fi
cat > "$W/MANIFEST.MF" <<MF
Manifest-Version: 1.0
MIDlet-1: Nong Trai Vui Ve 2,/icon.png,wipi.host.FarmMidlet
MIDlet-Name: Nong Trai Vui Ve 2
MIDlet-Vendor: Com2us (J2ME port)
MIDlet-Version: 1.0.0
MicroEdition-Configuration: CLDC-1.1
MicroEdition-Profile: MIDP-2.0
Farm-Jlet: TheFarm2
Farm-Screen: auto
Farm-Scale: auto
Farm-Save-Seed: 1
MF
[ -n "$EXTRA_MANIFEST" ] && printf "%s\n" "$EXTRA_MANIFEST" >> "$W/MANIFEST.MF"
rm -f "$OUT/$NAME.jar"
( cd "$W/jar" && jar cfm "$OUT/$NAME.jar" "$W/MANIFEST.MF" . )
SIZE=$(stat -c %s "$OUT/$NAME.jar")
{ grep -v '^Manifest-Version' "$W/MANIFEST.MF"; echo "MIDlet-Jar-URL: $NAME.jar"; echo "MIDlet-Jar-Size: $SIZE"; } > "$OUT/$NAME.jad"
echo "built $OUT/$NAME.jar ($SIZE bytes)"

echo "== 7. compatibility scan (rejects on error)"
mkdir -p "$P/build/reports"
java -cp "$W/tools:$ASM" Scan "$OUT/$NAME.jar" "$P/build/reports/scan_$NAME.txt" \
     "$L/cldc-1.1-stub-1.0.jar" "$L/midp-2.0-stub-1.0.jar"

echo "== 8. binary diff vs original"
java -cp "$W/tools:$ASM" BinDiff "$W/orig.jar" "$OUT/$NAME.jar" "$P/build/reports/bindiff_$NAME.txt"
cp "$W/remap.txt" "$P/build/reports/remap_$NAME.txt"
cp "$W/unpack.txt" "$P/build/reports/unpack_$NAME.txt"
cp "$W/textx.txt" "$P/build/reports/textx_$NAME.txt"
