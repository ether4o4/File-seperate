#!/data/data/com.termux/files/usr/bin/bash
OUT="/storage/emulated/0/3C_Battery_Report_$(date +%Y%m%d_%H%M%S).txt"
{ echo "BATTERY REPORT"; date; echo; dumpsys battery 2>/dev/null; echo; for f in /sys/class/power_supply/battery/capacity /sys/class/power_supply/battery/status /sys/class/power_supply/battery/health /sys/class/power_supply/battery/temperature /sys/class/power_supply/battery/voltage_now /sys/class/power_supply/battery/current_now; do [ -r "$f" ] && printf '%s: ' "$f" && cat "$f"; done; } > "$OUT" 2>&1
printf 'Saved: %s\n' "$OUT"
