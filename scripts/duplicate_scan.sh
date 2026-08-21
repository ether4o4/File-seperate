#!/data/data/com.termux/files/usr/bin/bash
ROOT="${1:-/storage/emulated/0/All}"
OUT="/storage/emulated/0/3C_Duplicates_$(date +%Y%m%d_%H%M%S).txt"
find "$ROOT" -type f -print0 2>/dev/null | xargs -0 sha256sum 2>/dev/null | sort > "$OUT.sha256"
awk '{h=$1; $1=""; sub(/^ /,""); if (h==last) { if (!shown) {print prev; shown=1}; print $0} else {shown=0}; last=h; prev=$0}' "$OUT.sha256" > "$OUT"
rm -f "$OUT.sha256"
printf 'Saved: %s\n' "$OUT"
