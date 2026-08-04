# Enabling full LibreOffice (LibreOfficeKit)

The office viewer is built against an engine-agnostic contract
(`office/DocumentRenderer.kt`). The default binding, `LibreOfficeKitRenderer`,
talks to **LibreOfficeKit (LOKit)** — the native LibreOffice engine that Collabora
maintains for Android. It's the same technology behind the LibreOffice/Collabora
Android viewers and gives true, offline, LibreOffice-grade rendering of Writer,
Calc, Impress and Draw documents.

It is **not bundled** in this repo because the payload is large (~150–250 MB:
native `.so` libraries per ABI plus the `program/` resources — fonts, import/export
filters, configuration). You add it once; the app then lights up with no code
changes, because `LibreOfficeKitRenderer.isAvailable()` detects the engine at
runtime.

## What the payload consists of

1. **Native libraries** — `liblo-native-code.so` (and friends) for each ABI you
   ship (`arm64-v8a`, `armeabi-v7a`, …).
2. **`program/` assets** — LibreOffice's resource tree, shipped in `assets/` and
   unpacked / memory-mapped on first launch.
3. **Java glue** — the `org.libreoffice.kit.*` classes (`LibreOfficeKit`, `Office`,
   `Document`) that JNI-bridge to the native code.

## Option A — Build it from the LibreOffice source (authoritative)

1. Check out `core` from https://git.libreoffice.org/core (or Collabora's
   `collabora-online` / `libreoffice-*` branch for their maintained fork).
2. Configure an Android build with the NDK (see `android/README.md` in the source
   tree) using a `distro-configs/`/`autogen.input` targeting Android + your ABI.
3. Build; collect from `android/source/`:
   - the generated `.so` files  → `app/src/main/jniLibs/<abi>/`
   - the `program/` assets       → `app/src/main/assets/libreoffice/program/`
   - the `org.libreoffice.kit` classes → package them as an AAR into `./libs/`
     (or add the source module to the build).

This is a large, multi-hour build with a specific toolchain. Budget for it.

## Option B — Consume a prebuilt Collabora / LibreOffice AAR

If you have access to a prebuilt LOKit AAR + jniLibs + assets (e.g. from a
Collabora Online Development Edition drop or your own CI artifact), just drop the
pieces in:

```
./libs/libreofficekit.aar                    # the org.libreoffice.kit classes
app/src/main/jniLibs/arm64-v8a/*.so          # native engine
app/src/main/jniLibs/armeabi-v7a/*.so
app/src/main/assets/libreoffice/program/...  # resources unpacked at runtime
```

## Wiring it into the build

1. In `app/build.gradle.kts`, uncomment:
   ```kotlin
   implementation(name = "libreofficekit", ext = "aar")
   ```
   (`settings.gradle.kts` already adds `flatDir { dirs("libs") }`.)
2. The manifest/build are already prepared:
   - `packaging.jniLibs.useLegacyPackaging = true` so LOKit can `dlopen` its libs,
   - `androidResources.noCompress += "so"` and uncompressed assets for mmap,
   - `abiFilters` set to `arm64-v8a` + `armeabi-v7a`.
3. Build. `LibreOfficeKitRenderer.isAvailable()` now returns true and the office
   viewer renders documents page-by-page.

## About the current `LibreOfficeKitRenderer`

It calls LOKit via **reflection** so the whole app compiles and runs *without* the
payload. That's ideal for a scaffold, but once LOKit is a hard dependency you'll
want the direct, type-safe calls for performance and clarity. Sketch of the direct
version (replace the reflection body):

```kotlin
LibreOfficeKit.init(appContext)                 // unpacks program/, sets lib dirs
val office: Office = LibreOfficeKit.getOffice()
val doc: Document = office.documentLoad(file.absolutePath)
val parts = doc.parts                            // pages / sheets / slides
doc.setPart(index)
val w = IntArray(1); val h = IntArray(1)
doc.getDocumentSize(w, h)                        // twips (1/1440 inch)
// paintTile(buffer, canvasW, canvasH, tilePosX, tilePosY, tileW, tileH)
doc.paintTile(byteBuffer, canvasW, canvasH, 0, 0, w[0], h[0])
```

Exact method names/signatures track the LibreOfficeKit version you ship — verify
against the `org.libreoffice.kit` classes in your AAR and adjust
`LibreOfficeKitRenderer` accordingly.

## Size management

- Ship only the ABIs you need (drop `armeabi-v7a` if you target 64-bit only).
- Use an Android **App Bundle** (`./gradlew :app:bundleRelease`) so Play delivers
  per-device ABI splits instead of one fat APK.
