#!/bin/sh
# usage: ./build.sh <tools dir with ecj/proguard/cldc/midp jars> <out dir>
# Builds release/S60Probe.jar + .jad (ecj 1.3 source -> ProGuard -microedition preverify).
set -e
T=${1:?tools dir}; O=${2:?out dir}
H=$(cd "$(dirname "$0")" && pwd)
rm -rf "$O/cls" "$O/pre"; mkdir -p "$O/cls" "$O/pre" "$O/release"
java -jar "$T/ecj-4.6.1.jar" -source 1.3 -target 1.2 -nowarn -bootclasspath "$T/cldc-1.1-stub-1.0.jar:$T/midp-2.0-stub-1.0.jar" -d "$O/cls" "$H/src/Probe.java"
python3 -I "$H/gen_big.py" "$O/cls"
(cd "$O/cls" && jar cf ../in.jar .)
cat > "$O/pg.pro" <<PRO
-injars $O/in.jar
-outjars $O/pre/out.jar
-libraryjars $T/cldc-1.1-stub-1.0.jar
-libraryjars $T/midp-2.0-stub-1.0.jar
-microedition
-dontobfuscate
-dontoptimize
-dontshrink
-keep class ** { *; }
PRO
java -jar "$T/proguard-base-6.2.2.jar" @"$O/pg.pro"
mkdir -p "$O/pkg/META-INF"; (cd "$O/pkg" && unzip -qo ../pre/out.jar)
printf 'Manifest-Version: 1.0\r\nMIDlet-1: S60Probe, , Probe\r\nMIDlet-Name: S60Probe\r\nMIDlet-Vendor: wipi-port\r\nMIDlet-Version: 1.0\r\nMicroEdition-Configuration: CLDC-1.0\r\nMicroEdition-Profile: MIDP-2.0\r\n' > "$O/manifest.mf"
jar cfm "$O/release/S60Probe.jar" "$O/manifest.mf" -C "$O/pkg" .
SZ=$(wc -c < "$O/release/S60Probe.jar")
cat > "$O/release/S60Probe.jad" <<JAD
MIDlet-1: S60Probe, , Probe
MIDlet-Name: S60Probe
MIDlet-Vendor: wipi-port
MIDlet-Version: 1.0
MIDlet-Jar-URL: S60Probe.jar
MIDlet-Jar-Size: $SZ
MicroEdition-Configuration: CLDC-1.0
MicroEdition-Profile: MIDP-2.0
JAD
ls -l "$O/release"
