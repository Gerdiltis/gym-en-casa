#!/bin/bash
# Pre-records the session voice cues ONCE (macOS voice "Mónica", es_ES) into app/src/main/res/raw.
# The APK ships these files; nothing is synthesised at runtime. Re-run only to change the wording or the voice.
set -euo pipefail
cd "$(dirname "$0")/../.."
OUT=app/src/main/res/raw
TMP=$(mktemp -d)
gen() { # name, text
  say -v "Mónica" -r 175 -o "$TMP/$1.aiff" "$2"
  ffmpeg -loglevel error -y -i "$TMP/$1.aiff" -af "silenceremove=start_periods=1:start_threshold=-50dB,areverse,silenceremove=start_periods=1:start_threshold=-50dB,areverse,loudnorm=I=-14:TP=-1" \
    -ac 1 -ar 22050 -c:a aac -b:a 48k "$OUT/$1.m4a"
}
gen voz_descansa "Descansa"
gen voz_siguiente_serie "Siguiente serie"
gen voz_siguiente_ejercicio "Siguiente tipo de ejercicio"
rm -rf "$TMP"
ls -l "$OUT"/voz_*.m4a
