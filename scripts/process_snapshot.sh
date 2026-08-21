#!/data/data/com.termux/files/usr/bin/bash
OUT="/storage/emulated/0/3C_Process_$(date +%Y%m%d_%H%M%S).txt"
{ echo "PROCESS SNAPSHOT"; date; echo; cat /proc/loadavg; echo; ps -A 2>/dev/null; echo; top -n 1 -m 30 2>/dev/null; } > "$OUT" 2>&1
printf 'Saved: %s\n' "$OUT"
