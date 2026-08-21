#!/data/data/com.termux/files/usr/bin/bash
OUT="/storage/emulated/0/3C_Access_Probe_$(date +%Y%m%d_%H%M%S).txt"
{ echo "ACCESS PROBE"; date; echo; id; echo; echo "[COMMANDS]"; for c in sh bash toybox getprop dumpsys pm am cmd ip ping ps top logcat df du find grep awk sed; do command -v "$c" >/dev/null 2>&1 && echo "YES $c" || echo "NO  $c"; done; echo; echo "[PATHS]"; for p in /proc /sys /data /sdcard /storage /system /vendor /dev; do [ -r "$p" ] && echo "READABLE $p" || echo "BLOCKED  $p"; done; } > "$OUT" 2>&1
printf 'Saved: %s\n' "$OUT"
