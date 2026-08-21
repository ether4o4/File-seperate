#!/data/data/com.termux/files/usr/bin/bash
OUT="/storage/emulated/0/3C_Storage_Report_$(date +%Y%m%d_%H%M%S).txt"
{ echo "STORAGE REPORT"; date; echo; df -h; echo; echo "[LARGE FILES >100MB]"; find /storage/emulated/0 -type f -size +100M -print 2>/dev/null | head -300; } > "$OUT" 2>&1
printf 'Saved: %s\n' "$OUT"
