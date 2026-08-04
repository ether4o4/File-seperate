# File Vault

An Android file-explorer app that keeps its own **separate, private memory**. Files
you add live inside the app's own storage — a tree of folders you build yourself —
and are never mixed with the rest of the phone's files, never uploaded to a cloud,
never handed to another app. Viewers for common file types are **bundled in**, so
you don't install extra reader apps.

> Status: this repository is a complete, buildable Android Studio project (Kotlin +
> Jetpack Compose). One piece — the full LibreOffice rendering engine — ships as a
> documented integration seam because its native payload is ~200 MB and is added
> separately. See [docs/LIBREOFFICE.md](docs/LIBREOFFICE.md). Everything else works
> out of the box.

## What it does

- **Computer-style tree explorer.** A left-hand, expandable folder tree. Create
  folders and subfolders to any depth; rename, move, and delete nodes.
- **Its own isolated storage.** Every byte is written to the app's *internal*
  storage (`filesDir`). Other apps and the phone's file managers can't see it, and
  the manifest excludes it from cloud backup and device transfer.
- **Truly offline.** The app requests **no `INTERNET` permission at all**, so
  nothing it holds — including anything opened in a WebView — can reach a network.
- **Import from USB / SD / external storage.** Pull in a single file, several
  files, or a whole folder tree (e.g. an entire USB-OTG drive) through the Storage
  Access Framework. The bytes are copied *into* the vault; the original is untouched
  and nothing leaves the device.
- **Bundled viewers** (no runtime downloads):
  | Type | Viewer |
  |------|--------|
  | Text & code (`.txt .md .js .ts .json .html .kt .py .src` …) | WebView + **bundled** highlight.js, offline syntax highlighting |
  | Images (`.png .jpg .webp .gif` …) | Coil, with pinch-to-zoom |
  | PDF | Android framework `PdfRenderer` |
  | HTML | sandboxed WebView (null origin, no file/network access) |
  | SQLite (`.db .sqlite`) | read-only table browser |
  | Audio & video | Media3 / ExoPlayer |
  | Office (`.docx .xlsx .pptx .odt` …) | **full LibreOffice** via LibreOfficeKit (see docs) |

## Architecture

```
app/src/main/java/com/ether4o4/filevault/
├─ VaultApplication.kt         # process singletons (DB + repository)
├─ MainActivity.kt             # single activity, Compose entry point
├─ data/
│  ├─ db/FileNode.kt           # Room entity: one node in the virtual tree
│  ├─ db/FileNodeDao.kt        # tree queries (reactive Flow)
│  ├─ db/VaultDatabase.kt      # Room database
│  ├─ VaultStorage.kt          # physical blob store in internal storage
│  ├─ VaultRepository.kt       # create/rename/move/delete/import, recursive delete
│  └─ FileKind.kt              # extension/MIME → which viewer
├─ importer/VaultImporter.kt   # SAF import of files and whole folder trees
├─ office/
│  ├─ DocumentRenderer.kt      # engine-agnostic rendering contract
│  └─ LibreOfficeKitRenderer.kt# LOKit binding (activates when payload present)
└─ ui/
   ├─ theme/Theme.kt
   ├─ explorer/                # tree UI + ViewModel
   └─ viewer/                  # one file per bundled viewer + dispatcher
```

**Why a database + blob store instead of real folders?** The folder tree (names,
nesting, order, metadata) lives in Room; the actual bytes live as flat,
UUID-named blobs in internal storage. This makes the "file system" fully owned by
the app (real isolation), lets names be anything, and makes move/rename a cheap
metadata update instead of a byte copy.

## Building

Requires **Android Studio** (Ladybug or newer) with the Android SDK — this repo
ships the Gradle wrapper but not the SDK.

```bash
# From Android Studio: Open the project and Run, or from the CLI once the SDK is set up:
./gradlew :app:assembleDebug
```

- `compileSdk` / `targetSdk` 35, `minSdk` 26 (Android 8.0+)
- Kotlin 2.0, Jetpack Compose (Material 3), Room, Media3, Coil

The office viewer works without the LibreOffice payload (it shows a clear message
and offers a text fallback for plain-text formats). To enable full document
rendering, follow [docs/LIBREOFFICE.md](docs/LIBREOFFICE.md).

## Isolation guarantees, concretely

- No `INTERNET` permission → the OS blocks all outbound sockets.
- No `READ/WRITE_EXTERNAL_STORAGE` → the app can't roam the phone's storage;
  import is user-driven through SAF.
- `allowBackup=false` + `data_extraction_rules` exclude everything → the vault is
  never copied to Google backup or transferred to a new device.
- All data under `filesDir` → private to the app, removed on uninstall.

## Roadmap / not yet implemented

- Full LibreOffice payload wiring (seam is in place — see docs).
- Optional passphrase + at-rest encryption of the blob store.
- `.lnk` shortcut target parsing (display only).
- Direct raw `UsbManager` mass-storage reading for devices whose DocumentsProvider
  doesn't expose a USB volume (SAF covers the common case today).
