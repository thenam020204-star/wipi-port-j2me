#!/bin/bash
# Build the Android APK of the MMF player (WebView shell around the same page).
#   tools/mmfplayer/android/build_apk.sh <Nokia.sf2> [out.apk]
# Needs the Android SDK in work/tools/android-sdk (cmdline-tools + "build-tools;34.0.0" + "platforms;android-34").
# The soundfont is bundled as assets/sf2data.js (window.NOKIA_SF2_B64); it is not committed.
set -e
H=$(cd "$(dirname "$0")" && pwd); M=$H/..; R=$(cd "$M/../.." && pwd)
SF2=${1:?Nokia.sf2}; OUT=${2:-$R/work/apk/MayNgheMMF.apk}
SDK=$R/work/tools/android-sdk; BT=$SDK/build-tools/34.0.0; AJ=$SDK/platforms/android-34/android.jar
W=$R/work/apk/build; rm -rf "$W"; mkdir -p "$W/assets" "$W/flat" "$W/cls" "$W/dex" "$W/gen"
export JAVA_TOOL_OPTIONS=
python3 - "$M" "$W/assets" "$SF2" <<'PY'
import base64, re, sys
m, out, sf2 = sys.argv[1:]
s = open(m + '/page.src.html').read()
s = s.replace('/*SMAF*/', open(m + '/smaf.js').read()).replace('/*SYNTH*/', open(m + '/sf2synth.js').read())
s = re.sub(r'<link rel="preconnect"[^>]*>\n', '', s)
s = re.sub(r'<link rel="stylesheet" href="https://fonts.googleapis.com[^>]*>\n', '', s)   # offline: system fonts
s = s.replace('<meta name="claude:viewport-fit-sides" content="cover">\n', '')
pass
s = s.replace('<script>\n', '<script src="sf2data.js"></script>\n<script>\n', 1)
open(out + '/index.html', 'w').write('<!doctype html>\n<html lang="vi">\n<head>\n<meta charset="utf-8">\n'
    '<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">\n</head>\n<body>\n' + s + '\n</body>\n</html>\n')
open(out + '/sf2data.js', 'w').write('window.NOKIA_SF2_B64="' + base64.b64encode(open(sf2, 'rb').read()).decode() + '";\n')
PY
$BT/aapt2 compile --dir "$H/res" -o "$W/flat/"
$BT/aapt2 link -I $AJ --manifest "$H/AndroidManifest.xml" -A "$W/assets" --min-sdk-version 21 --target-sdk-version 34 \
    --java "$W/gen" -o "$W/base.apk" "$W"/flat/*.flat
javac -encoding UTF-8 -nowarn --release 8 -classpath $AJ -d "$W/cls" "$H"/src/vn/mmfplayer/*.java "$W"/gen/vn/mmfplayer/R.java
$BT/d8 --lib $AJ --min-api 21 --output "$W/dex" "$W"/cls/vn/mmfplayer/*.class
cp "$W/base.apk" "$W/unsigned.apk"; (cd "$W/dex" && zip -q -j ../unsigned.apk classes.dex)
$BT/zipalign -f -p 4 "$W/unsigned.apk" "$W/aligned.apk"
KS=$R/work/apk/mmf.keystore
[ -f "$KS" ] || keytool -genkeypair -keystore "$KS" -storepass mmfplayer -keypass mmfplayer -alias mmf -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=MMF Player" >/dev/null
$BT/apksigner sign --ks "$KS" --ks-pass pass:mmfplayer --ks-key-alias mmf --out "$OUT" "$W/aligned.apk"
$BT/apksigner verify "$OUT" && ls -l "$OUT"
