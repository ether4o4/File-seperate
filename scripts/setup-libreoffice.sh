#!/usr/bin/env bash
#
# Places a LibreOfficeKit payload into the app and sanity-checks it.
# See docs/LIBREOFFICE.md for how to obtain the payload (build from source or a
# prebuilt Collabora/LibreOffice artifact).
#
# Usage:
#   scripts/setup-libreoffice.sh <payload-dir>
#
# <payload-dir> is expected to contain any of:
#   jniLibs/<abi>/*.so                (native engine, e.g. arm64-v8a)
#   program/                          (LibreOffice resource tree)
#   libreofficekit.aar                (the org.libreoffice.kit classes)
#
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="${1:-}"

if [[ -z "$SRC" || ! -d "$SRC" ]]; then
  echo "usage: $0 <payload-dir>" >&2
  exit 2
fi

JNI_DST="$ROOT/app/src/main/jniLibs"
ASSETS_DST="$ROOT/app/src/main/assets/libreoffice"
LIBS_DST="$ROOT/libs"

echo "==> Installing LibreOfficeKit payload from: $SRC"

if [[ -d "$SRC/jniLibs" ]]; then
  mkdir -p "$JNI_DST"
  cp -a "$SRC/jniLibs/." "$JNI_DST/"
  echo "    jniLibs -> $JNI_DST"
fi

if [[ -d "$SRC/program" ]]; then
  mkdir -p "$ASSETS_DST"
  cp -a "$SRC/program" "$ASSETS_DST/"
  echo "    program/ -> $ASSETS_DST/program"
fi

if [[ -f "$SRC/libreofficekit.aar" ]]; then
  mkdir -p "$LIBS_DST"
  cp -a "$SRC/libreofficekit.aar" "$LIBS_DST/"
  echo "    libreofficekit.aar -> $LIBS_DST"
  echo "    NOTE: uncomment the LOKit implementation() line in app/build.gradle.kts"
fi

echo "==> Verifying"
ok=1
if ! find "$JNI_DST" -name 'liblo-native-code.so' 2>/dev/null | grep -q .; then
  echo "    MISSING: liblo-native-code.so under $JNI_DST/<abi>/" >&2
  ok=0
else
  echo "    found liblo-native-code.so for ABIs: $(ls "$JNI_DST" 2>/dev/null | tr '\n' ' ')"
fi
if [[ ! -d "$ASSETS_DST/program" ]]; then
  echo "    MISSING: $ASSETS_DST/program (LibreOffice resources)" >&2
  ok=0
fi

if [[ "$ok" -eq 1 ]]; then
  echo "==> OK. Build with: ./gradlew :app:assembleDebug"
else
  echo "==> Incomplete payload — see docs/LIBREOFFICE.md" >&2
  exit 1
fi
