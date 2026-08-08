# Full LibreOffice on-device (LibreOfficeKit)

The office viewer renders Writer/Calc/Impress/Draw documents with the real
LibreOffice engine, on-device and fully offline — no server, no other app, no
network. It does this by bundling **Collabora Office's LibreOfficeKit engine**
(`liblo-native-code.so` + the `program/`/`share/` resource tree) and driving it
through LibreOfficeKit's **C API** via a small JNI bridge in `app/src/main/cpp`.

The engine is ~200 MB, so it is **not committed to git**. You stage it once with a
script, then build.

## One-time setup + build

```bash
# 1. Download the Collabora engine and stage it into the app
#    (libs -> jniLibs, resources -> assets/lo/lo-assets.zip, C headers -> cpp/include)
scripts/setup-libreoffice.sh            # arm64-v8a, latest snapshot
#   or: scripts/setup-libreoffice.sh arm64-v8a 2026-07-03

# 2. Build (needs Android SDK + NDK 26.3.11579264 + CMake 3.22.1)
./gradlew :app:assembleRelease
```

The resulting APK is ~250 MB and **arm64-v8a only** (stage other ABIs and add them
to `defaultConfig.ndk.abiFilters` to support 32-bit / x86 devices). Builds without
running the setup script still work — the office viewer just shows "engine not
installed" and every other viewer (PDF, images, text/code, SQLite, media, HTML)
works normally.

## How it fits together

| Piece | Where |
|---|---|
| Engine + deps (`liblo-native-code.so`, nss, sqlite, …) | `app/src/main/jniLibs/<abi>/` (staged) |
| LibreOffice resources (`program/`, `share/`, `etc/`, `user/`) | `app/src/main/assets/lo/lo-assets.zip` (staged) |
| LibreOfficeKit C headers | `app/src/main/cpp/include/LibreOfficeKit/` (staged) |
| JNI bridge (`libreofficekit_hook_2` → `paintTile`) | `app/src/main/cpp/lok_bridge.cpp` |
| Kotlin surface | `office/NativeLok.kt` |
| Unpack + init (asset zip → filesDir, `nativeInit`) | `office/LoEnvironment.kt` |
| `DocumentRenderer` implementation | `office/NativeLokRenderer.kt` |

**Runtime flow:** on first office-file open, `LoEnvironment` unpacks the resource
zip into `filesDir/lo` (private storage), loads the engine, then calls
`libreofficekit_hook_2(installPath = filesDir/lo/program, userProfile =
file://filesDir/lo/user)`. Each page: `setPart` → `getDocumentSize` (twips) →
`paintTile` into an Android bitmap (R/B swapped if the engine paints BGRA).

## Why this build works with the C API

Verified against the Collabora 25.04 arm64 snapshot:
- `liblo-native-code.so` exports `libreofficekit_hook_2` / `libreofficekit_hook`.
- It uses the standard UNO bootstrap (`fundamentalrc` / `URE_BOOTSTRAP`, normal
  `dlopen` — no Android-specific `lo_dlopen` loader), so the documented desktop
  init path applies.

(The Collabora *app* itself, `org.libreoffice.androidapp`, uses a WebView +
embedded-server design and does **not** expose the `org.libreoffice.kit` Java
classes — which is why this project talks to the engine directly via the C API
instead of reusing their Java layer.)

## Not yet verified on a device

This bridge compiles and packages, but the LOKit **runtime init on Android**
(pointing the engine at the unpacked `program/` dir) is the finicky part that
really needs one real-device run to confirm. If office files fail to open, grab a
logcat filtered on `LokBridge` — the bridge logs the exact `getError()` from the
engine, which pins down any remaining env/path tweak.

## Source & license

- Engine: Collabora Office Android snapshots —
  https://www.collaboraoffice.com/downloads/Collabora-Office-Android-Snapshot/
- LibreOffice / Collabora Online is **MPL-2.0 / LGPL-3.0**. Bundling these
  libraries in a distributed app carries attribution + source-availability
  obligations. Fine for personal use; read the licenses before shipping.
