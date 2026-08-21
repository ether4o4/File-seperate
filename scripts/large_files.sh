#!/data/data/com.termux/files/usr/bin/bash
ROOT="${1:-/storage/emulated/0/All}"
find "$ROOT" -type f -printf '%s\t%p\n' 2>/dev/null | sort -nr | head -200 | awk -F '\t' '{printf "%.2f MB\t%s\n", $1/1048576, $2}'
