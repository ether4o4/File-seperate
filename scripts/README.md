# Toolbox scripts

The APK's **Toolbox** provides native Android equivalents for the most useful scripts below. The shell scripts are kept here for Termux/3C use.

## Organizer

`organize_all.sh` is the final aggressive organizer. It supports:

- `dry-run` — classify without moving
- `run` — organize and record an undo log
- `undo` — restore the last organization pass
- `clean` — remove empty source directories
- `report` — quick file count

Default root: `/storage/emulated/0/All`.

It aggressively separates Apple/iPhone material, metadata, HTML dashboards, web files, JSON/XML/YAML, logs, Markdown, code, media, screenshots, icons, APKs, archives, databases and leftovers. Small text-like files are inspected for Apple/iPhone/metadata indicators before classification.

## Other scripts

- `device_report.sh` — Android/build/kernel/CPU/memory report
- `storage_report.sh` — storage and large-file report
- `network_report.sh` — interfaces/routes/DNS/connectivity
- `battery_report.sh` — battery service and sysfs information
- `package_inventory.sh` — installed package list
- `access_probe.sh` — shell command and readable-path probe
- `process_snapshot.sh` — process/load snapshot
- `duplicate_scan.sh` — SHA-256 duplicate scan
- `large_files.sh` — largest accessible files

All scripts are local-only utilities. They do not upload collected data.
