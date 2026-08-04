# Enabling full LibreOffice (LibreOfficeKit)

The office viewer is built against an engine-agnostic contract
(`office/DocumentRenderer.kt`). The default binding, `LibreOfficeKitRenderer`,
talks to **LibreOfficeKit (LOKit)** — the native LibreOffice engine that Collabora
maintains for Android. Same technology as the LibreOffice/Collabora Android
viewers; true, offline, LibreOffice-grade rendering of Writer, Calc, Impress and
Draw.

It is **not bundled** here because the payload is large (~150–250 MB). You add it
once; the app then lights up with no code changes, because
`LibreOfficeKitRenderer.isAvailable()` detects the engine at runtime.

## The payload — three parts

1. **Native libraries** in `app/src/main/jniLibs/<abi>/`. The Android LOKit build
   loads (via `System.loadLibrary`, in this order):
   `nspr4, plds4, plc4, nssutil3, freebl3, sqlite3, softokn3, nss3, nssckbi,
   nssdbm3, smime3, ssl3, c++_shared, lo-native-code`.
   `liblo-native-code.so` is the big one (the whole office engine); the rest are
   the bundled NSS/SQLite deps.
2. **`program/` assets** in `app/src/main/assets/` — LibreOffice's resource tree
   (fonts, import/export filters, `types/`, configuration). `LibreOfficeKit.init()`
   unpacks / points the engine at these on first launch.
3. **Java glue** — the `org.libreoffice.kit.*` classes (`LibreOfficeKit`, `Office`,
   `Document`), packaged as an AAR into `./libs/` (or added as a source module).

## Verified API (what `LibreOfficeKitRenderer` reflects into)

From `android/Bootstrap/src/org/libreoffice/kit/` in github.com/LibreOffice/core:

```java
// LibreOfficeKit (static)
static synchronized void init(Activity activity)   // sets dirs, unpacks program/
static native ByteBuffer getLibreOfficeKitHandle()

// Office
Office(ByteBuffer handle)                           // constructed from the handle
native String getError()
Document documentLoad(String url)                   // path or file:// URL
native void destroy()

// Document
void initializeForRendering()                       // call once after load
native int  getParts()                              // pages / sheets / slides
native void setPart(int partIndex)
native long getDocumentWidth()                      // twips (1/1440 inch)
native long getDocumentHeight()                     // twips
void paintTile(ByteBuffer buffer, int canvasW, int canvasH,
               int tilePosX, int tilePosY, int tileW, int tileH)  // tile* in twips
native void destroy()
```

`LibreOfficeKitRenderer` already calls exactly this sequence via reflection, so it
activates the moment the classes + libs are present. Two things to verify on real
hardware once the payload is in: LOKit paints **BGRA**, so if red/blue look
swapped, swap channels before `copyPixelsFromBuffer`; and `init()` needs the
foreground **Activity** (already wired through `OfficeViewer`).

## Getting the payload

### Option A — Build from the LibreOffice source (authoritative)

1. Clone `core` from https://git.libreoffice.org/core (or Collabora's maintained
   `libreoffice-*` / `collabora-online` branch).
2. Set up an Android build with the NDK — see `android/README.md` in the tree.
   Configure via `autogen.input` / a `distro-configs/` file targeting Android +
   your ABI (`--with-distro=LibreOfficeAndroidX86_64`, etc.).
3. `make` (long — hours, tens of GB). Then collect from `android/source/`:
   - generated `*.so`      → `app/src/main/jniLibs/<abi>/`
   - the `program/` tree   → `app/src/main/assets/libreoffice/program/`  *(see note)*
   - `org.libreoffice.kit` classes → an AAR in `./libs/`

> Note on the assets path: the stock LO Android app expects `program/` directly
> under `assets/`. This project namespaces it under `assets/libreoffice/` (kept out
> of git via `.gitignore`). If you use the stock `LibreOfficeKit.init` unpack
> logic, either place `program/` directly under `assets/` or adjust the unpack root.

### Option B — Consume a prebuilt Collabora / LibreOffice artifact

If you have a prebuilt LOKit AAR + jniLibs + assets (a Collabora Online Development
Edition drop, or your own CI artifact), just drop the pieces into the paths above.

Either way, run `scripts/setup-libreoffice.sh <path-to-payload>` to place the files
and sanity-check that `liblo-native-code.so` and `program/` are present.

## Wiring it into the build

1. In `app/build.gradle.kts`, uncomment:
   ```kotlin
   implementation(name = "libreofficekit", ext = "aar")
   ```
   (`settings.gradle.kts` already adds `flatDir { dirs("libs") }`.)
2. The build is already prepared for it:
   - `packaging.jniLibs.useLegacyPackaging = true` so LOKit can `dlopen` its libs,
   - `androidResources.noCompress += "so"` + uncompressed assets for mmap,
   - `ndk.abiFilters = ["arm64-v8a", "armeabi-v7a"]`.
3. Build. `isAvailable()` now returns true and the office viewer renders documents
   page-by-page.

## Size management

- Ship only the ABIs you need (drop `armeabi-v7a` for 64-bit-only devices).
- Use an **App Bundle** (`./gradlew :app:bundleRelease`) so Play delivers per-ABI
  splits instead of one fat APK.
