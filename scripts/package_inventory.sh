#!/data/data/com.termux/files/usr/bin/bash
OUT="/storage/emulated/0/3C_Packages_$(date +%Y%m%d_%H%M%S).txt"
{ echo "PACKAGE INVENTORY"; date; echo; pm list packages 2>/dev/null; echo; echo "COUNT:"; pm list packages 2>/dev/null | wc -l; } > "$OUT" 2>&1
printf 'Saved: %s\n' "$OUT"
