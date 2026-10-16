#!/bin/bash
# Run the built MIDlet in MicroEmulator 2.0.4 under Xvfb, send keys, take screenshots (test tool only; J2ME Loader/MicroEmulator != Nokia KVM).
# usage: me_run.sh <out dir> <seconds before shot1> "<keys: delay:KEY ...>" <seconds after keys before shot2>
export LC_ALL=C.UTF-8
ROOT=/home/user/wipi-port-j2me; T=$ROOT/work/tools/j2me; OUT=$1; mkdir -p $OUT
CP=$T/microemulator-2.0.4.jar:$T/microemu-javase-swing-2.0.4.jar:$T/microemu-jsr-135-2.0.4.jar:$T/cldcapi11-2.0.4.jar:$T/midpapi20-2.0.4.jar
javac -d $OUT $ROOT/tools/KeyRobot.java 2>/dev/null
cat > $OUT/run.sh <<EOF
java -cp $CP org.microemu.app.Main --resizableDevice 240 320 $ROOT/work/build/hl4.jad > $OUT/me.log 2>&1 &
sleep $2
import -window root $OUT/shot1.png
java -cp $OUT KeyRobot $3
sleep $4
import -window root $OUT/shot2.png
kill %1
EOF
xvfb-run -a -s "-screen 0 400x500x24" bash $OUT/run.sh
ls $OUT
