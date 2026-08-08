#!/usr/bin/env bash
#
# Fetch the Collabora Office engine and stage it into the app so the office
# viewer can render with real LibreOffice on-device.
#
# This performs the steps that a locked-down/sandboxed CI agent is not allowed
# to do automatically (download + repackage a large third-party native binary):
#   1. download the official Collabora Office Android snapshot APK
#   2. extract the LibreOfficeKit engine .so files -> app/src/main/jniLibs/<abi>/
#   3. build the merged LibreOffice resource tree -> app/src/main/assets/lo/lo-assets.zip
#   4. vendor the LibreOfficeKit C API headers    -> app/src/main/cpp/include/
#
# After running this, build the app:  ./gradlew :app:assembleRelease
#
# Usage:
#   scripts/setup-libreoffice.sh [ABI] [SNAPSHOT_DATE]
#     ABI            arm64-v8a (default) | armeabi-v7a | x86_64 | x86
#     SNAPSHOT_DATE  e.g. 2026-07-03 (default: latest listed for the ABI)
#
# Source: https://www.collaboraoffice.com/downloads/Collabora-Office-Android-Snapshot/
# License: Collabora Office / LibreOffice is MPL-2.0 / LGPL-3.0. Bundling these
# libraries in a distributed app carries attribution + source-availability
# obligations. For personal use this is fine; read the licenses before shipping.
set -euo pipefail

ABI="${1:-arm64-v8a}"
DATE="${2:-}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BASE="https://www.collaboraoffice.com/downloads/Collabora-Office-Android-Snapshot"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

echo "==> Resolving latest snapshot for $ABI"
if [[ -z "$DATE" ]]; then
  DATE="$(curl -fsSL "$BASE/" \
    | grep -oE "collabora-office-mobile-[0-9.]+-snapshot-${ABI}-[0-9-]+\.apk" \
    | sort | tail -1 | grep -oE '[0-9]{4}-[0-9]{2}-[0-9]{2}')"
fi
APK_NAME="$(curl -fsSL "$BASE/" \
  | grep -oE "collabora-office-mobile-[0-9.]+-snapshot-${ABI}-${DATE}\.apk" | tail -1)"
[[ -n "$APK_NAME" ]] || { echo "Could not find an APK for $ABI $DATE" >&2; exit 1; }

echo "==> Downloading $APK_NAME (large, ~250 MB)"
curl -fL --retry 3 -o "$WORK/co.apk" "$BASE/$APK_NAME"

echo "==> 1/3 Extracting engine libraries -> jniLibs/$ABI"
JNI="$ROOT/app/src/main/jniLibs/$ABI"
mkdir -p "$JNI"
unzip -oq "$WORK/co.apk" "lib/$ABI/*" -d "$WORK/libs"
for so in "$WORK/libs/lib/$ABI/"*.so; do
  base="$(basename "$so")"
  # libandroidapp.so is Collabora's WebView app glue; not needed for the C-API path.
  [[ "$base" == "libandroidapp.so" ]] && continue
  cp "$so" "$JNI/"
done
echo "    $(ls "$JNI" | wc -l) libraries staged"

echo "==> 2/3 Building merged LibreOffice resource bundle -> assets/lo/lo-assets.zip"
unzip -oq "$WORK/co.apk" "assets/unpack/*" "assets/program/*" "assets/share/*" -d "$WORK/ex"
LOROOT="$WORK/loroot"; mkdir -p "$LOROOT/program" "$LOROOT/share"
cp -a "$WORK/ex/assets/unpack/." "$LOROOT/"          # program/ share/ etc/ user/
cp -a "$WORK/ex/assets/program/." "$LOROOT/program/" # overlay rc + services.rdb
cp -a "$WORK/ex/assets/share/." "$LOROOT/share/"     # overlay UI config/registry
[[ -f "$LOROOT/program/fundamentalrc" ]] || { echo "merge failed: no fundamentalrc" >&2; exit 1; }
mkdir -p "$ROOT/app/src/main/assets/lo"
( cd "$LOROOT" && zip -qr "$ROOT/app/src/main/assets/lo/lo-assets.zip" . )
echo "    $(du -h "$ROOT/app/src/main/assets/lo/lo-assets.zip" | cut -f1) bundle"

echo "==> 3/3 Vendoring LibreOfficeKit C API headers"
H="$ROOT/app/src/main/cpp/include/LibreOfficeKit"; mkdir -p "$H"
HB="https://raw.githubusercontent.com/LibreOffice/core/master/include/LibreOfficeKit"
for f in LibreOfficeKit.h LibreOfficeKitEnums.h LibreOfficeKitTypes.h LibreOfficeKitInit.h; do
  curl -fsSL "$HB/$f" -o "$H/$f"
done
echo "    headers: $(ls "$H" | tr '\n' ' ')"

cat <<EOF

==> Done. Engine staged for $ABI ($DATE).
    Now build:   ./gradlew :app:assembleRelease
    Output APK will be ~250 MB and arm64-only unless you stage more ABIs.
EOF
