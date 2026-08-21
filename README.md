# File Vault + Toolbox

Android file explorer plus a local-first file-organization toolbox.

The app keeps its vault separate from the phone's ordinary files. The new Toolbox can also operate on a user-selected external folder through Android's Storage Access Framework.

## Build / download

The `main` branch is configured to build the APK through GitHub Actions and publish the rolling `android-latest` release after a successful build.

The organizer toolbox includes:

- Aggressive Apple/iPhone and metadata classification
- HTML dashboard / export detection
- JSON/XML/YAML/log/code/web separation
- Images, icons, screenshots, APKs, archives, media, databases, backups, ROMs and games
- Dry Run
- Organize
- Undo Last Organize
- Storage report
- Package inventory
- Battery report
- Device information
- Memory / CPU information
- Network status

The repository also contains Termux/3C shell equivalents under `scripts/`.

## Important

The organizer never needs to upload the files being classified. The APK asks the user to choose the folder through Android's Storage Access Framework, and the organizer works against that selected tree.

The Termux organizer defaults to `/storage/emulated/0/All` and supports `dry-run`, `run`, `undo`, `clean`, and `report` modes.
