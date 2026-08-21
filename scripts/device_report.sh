#!/data/data/com.termux/files/usr/bin/bash
OUT="/storage/emulated/0/3C_Device_Report_$(date +%Y%m%d_%H%M%S).txt"
{ echo "DEVICE REPORT"; date; echo; echo "[DEVICE]"; getprop ro.product.manufacturer; getprop ro.product.model; getprop ro.build.version.release; getprop ro.build.version.sdk; getprop ro.build.version.security_patch; echo; echo "[KERNEL]"; uname -a; echo; echo "[MEMORY]"; grep -E 'MemTotal|MemAvailable|SwapTotal|SwapFree' /proc/meminfo; echo; echo "[CPU]"; grep -E 'model name|Hardware|processor' /proc/cpuinfo | head -30; } > "$OUT" 2>&1
printf 'Saved: %s\n' "$OUT"
