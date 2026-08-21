#!/data/data/com.termux/files/usr/bin/bash
OUT="/storage/emulated/0/3C_Network_Report_$(date +%Y%m%d_%H%M%S).txt"
{ echo "NETWORK REPORT"; date; echo; echo "[INTERFACES]"; ip addr 2>/dev/null; echo; echo "[ROUTES]"; ip route 2>/dev/null; echo; echo "[DNS]"; getprop | grep -Ei 'dns|dhcp'; echo; echo "[CONNECTIVITY]"; ping -c 1 -W 2 1.1.1.1 2>/dev/null; } > "$OUT" 2>&1
printf 'Saved: %s\n' "$OUT"
