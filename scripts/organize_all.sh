#!/data/data/com.termux/files/usr/bin/bash
set -u
ROOT="${ROOT:-/storage/emulated/0/All}"
STATE="$ROOT/_Organizer"
UNDO="$STATE/undo_last.tsv"
CATS=(APKs Apple iPhone_Data Metadata HTML_Dashboards Web Archives Audio Music Videos Images Icons Screenshots Documents PDFs Spreadsheets Presentations Ebooks Backups Databases Code Projects Config JSON XML YAML Logs Markdown Text Fonts Subtitles ROMs Games Executables Unknown Other)
mkdir -p "$STATE" || exit 1
for c in "${CATS[@]}"; do mkdir -p "$ROOT/$c"; done
mode="${1:-dry-run}"
lower(){ printf '%s' "$1" | tr '[:upper:]' '[:lower:]'; }
head_text(){ local f="$1"; [ "$(wc -c <"$f" 2>/dev/null || echo 0)" -le 10485760 ] || return; head -c 65536 -- "$f" 2>/dev/null | tr '[:upper:]' '[:lower:]'; }
has(){ local s="$1"; shift; for x in "$@"; do case "$s" in *"$x"*) return 0;; esac; done; return 1; }
classify(){
  local f="$1" n e h=""; n="$(lower "$(basename -- "$f")")"; e="${n##*.}"
  case "$n" in *screenshot*|*screen_shot*|*screen-capture*|*screen_capture*|screen_*) echo Screenshots; return;; esac
  if has "$n" icon logo favicon launcher_icon ic_launcher app_icon; then echo Icons; return; fi
  if has "$n" iphone ios apple icloud itunes mobilebackup mobile_backup applebackup apple_backup sysdiagnose ipsw propertylist plist; then case "$e" in png|jpg|jpeg|webp|heic) echo Apple;; html|htm) echo HTML_Dashboards;; *) echo iPhone_Data;; esac; return; fi
  if has "$n" imessage chatstorage addressbook callhistory call_history safari_history browser_history manifest camera_roll photolibrary; then echo iPhone_Data; return; fi
  if has "$n" metadata meta_data meta-data metadatafile meta.json meta.xml; then case "$e" in html|htm) echo HTML_Dashboards;; *) echo Metadata;; esac; return; fi
  if has "$n" dashboard forensic evidence extraction extracted export report; then case "$e" in html|htm|json|xml|csv|txt|log) echo HTML_Dashboards;; esac; fi
  case "$e" in
    html|htm) h="$(head_text "$f")"; if has "$h" iphone ios apple icloud metadata mobilebackup forensic dashboard "meta data"; then echo HTML_Dashboards; elif has "$n" meta data iphone ios apple report dashboard export; then echo HTML_Dashboards; else echo Web; fi; return;;
    json) h="$(head_text "$f")"; if has "$h" iphone ios apple icloud mobilebackup metadata cfbundle "meta data" || has "$n" iphone apple meta data; then echo iPhone_Data; else echo JSON; fi; return;;
    xml) h="$(head_text "$f")"; if has "$h" apple iphone ios cfbundle plist || has "$n" iphone apple plist; then echo iPhone_Data; else echo XML; fi; return;;
    plist|mobileconfig|mobileprovision) echo Apple; return;;
    db|sqlite|sqlite3|sqlitedb|db3) if has "$n" iphone ios apple message sms chat contact call safari history addressbook manifest; then echo iPhone_Data; else echo Databases; fi; return;;
    csv) if has "$n" iphone ios apple metadata meta data export extraction; then echo Metadata; else echo Spreadsheets; fi; return;;
  esac
  case "$e" in
    png|jpg|jpeg|jpe|gif|bmp|webp|heic|heif|avif|tif|tiff) echo Images;; ico|icns) echo Icons;; svg) if has "$n" icon logo favicon launcher; then echo Icons; else echo Images; fi;;
    mp4|mkv|mov|avi|wmv|flv|webm|m4v|3gp|3gpp|mpeg|mpg|ts|mts|m2ts|vob) echo Videos;; mp3|flac|m4a|aac|ogg|opus|alac|wma|aiff|aif) echo Music;; wav|wave|amr|caf|ac3|dts) echo Audio;;
    pdf) echo PDFs;; doc|docx|odt|rtf|pages) echo Documents;; xls|xlsx|ods|numbers) echo Spreadsheets;; ppt|pptx|odp|key) echo Presentations;; epub|mobi|azw|azw3|fb2|cbz|cbr) echo Ebooks;;
    zip|rar|7z|tar|gz|bz2|xz|zst|tgz|tbz|txz) echo Archives;; apk|xapk|apks|aab) echo APKs;;
    py|pyw|js|jsx|ts|tsx|java|kt|kts|c|h|cpp|cc|cxx|hpp|rs|go|rb|php|swift|dart|lua|pl|pm|r|scala|groovy|gradle|sql|vue|svelte) echo Code;; css|scss|sass|less|map|wasm) echo Web;; sh|bash|zsh|fish|bat|cmd|ps1) echo Executables;;
    env|properties|prefs|conf|cfg|ini|toml) echo Config;; yaml|yml) echo YAML;; md|markdown|mdown|mkdn|rst) echo Markdown;; log) echo Logs;;
    txt|text|nfo|data) h="$(head_text "$f")"; if has "$n" iphone ios apple icloud metadata meta data backup extract forensic mobilebackup || has "$h" iphone ios apple icloud metadata mobilebackup "meta data" cfbundle; then echo Metadata; else echo Text; fi;;
    ttf|otf|woff|woff2|eot) echo Fonts;; srt|ass|ssa|sub|vtt) echo Subtitles;; iso|nes|gba|gbc|gb|nds|n64|z64|smc|sfc) echo ROMs;; sav|savestate|pak|wad) echo Games;;
    *) if has "$n" package.json package-lock yarn.lock pnpm-lock requirements.txt pyproject cargo.toml cargo.lock dockerfile makefile cmakelists androidmanifest build.gradle settings.gradle; then echo Projects; else echo Unknown; fi;;
  esac
}
unique(){ local d="$1" n="$2" b e i=1; [ ! -e "$d/$n" ] && { printf '%s/%s\n' "$d" "$n"; return; }; if [[ "$n" == *.* && "$n" != .* ]]; then b="${n%.*}"; e=".${n##*.}"; else b="$n"; e=""; fi; while [ -e "$d/$b ($i)$e" ]; do i=$((i+1)); done; printf '%s/%s\n' "$d" "$b ($i)$e"; }
case "$mode" in
  report) find "$ROOT" -type f -not -path "$STATE/*" 2>/dev/null | wc -l; exit 0;;
  clean) find "$ROOT" -depth -type d -not -path "$ROOT" -not -path "$STATE" -not -path "$STATE/*" -empty -delete 2>/dev/null; echo "Empty directories removed."; exit 0;;
  undo) [ -s "$UNDO" ] || { echo "No undo log."; exit 0; }; tac "$UNDO" 2>/dev/null | while IFS=$'\t' read -r src dst; do [ -e "$dst" ] || continue; mkdir -p -- "${src%/*}"; mv -- "$dst" "$src" 2>/dev/null || true; done; rm -f "$UNDO"; echo "Undo complete."; exit 0;;
  dry-run|run) ;; *) echo "Usage: $0 [dry-run|run|undo|clean|report]"; exit 2;;
esac
[ "$mode" = run ] && : > "$UNDO"
scanned=0; moved=0; errors=0; declare -A count; for c in "${CATS[@]}"; do count[$c]=0; done
echo "TERMUX ALL FILE ORGANIZER — FINAL ($mode)"
while IFS= read -r -d '' f; do
  scanned=$((scanned+1)); n="$(basename -- "$f")"; c="$(classify "$f")"; count[$c]=$((count[$c]+1));
  [ "$mode" = dry-run ] && { printf '%s -> %s/\n' "$n" "$c"; continue; }
  dst="$(unique "$ROOT/$c" "$n")"
  if mv -- "$f" "$dst"; then printf '%s\t%s\n' "$f" "$dst" >> "$UNDO"; moved=$((moved+1)); else errors=$((errors+1)); fi
done < <(find "$ROOT" -type f -not -path "$STATE/*" -print0 2>/dev/null)
for c in "${CATS[@]}"; do printf '%-22s %8s\n' "$c" "${count[$c]}"; done
echo "Scanned: $scanned"; [ "$mode" = run ] && echo "Moved: $moved | Errors: $errors" || echo "DRY RUN — nothing moved"
