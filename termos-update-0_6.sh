#!/usr/bin/env bash
# TermOS 0.5.x -> 0.6.0 (look/banner, colours, dates, autofocus, logout)
# Usage: ./termos-update-0_6.sh [--install] [--skip-tests] [--no-build] [project_dir]
set -euo pipefail
DO_INSTALL=0; SKIP_TESTS=0; NO_BUILD=0
PROJECT_DIR=""
for a in "$@"; do
  case "$a" in
    --install) DO_INSTALL=1 ;;
    --skip-tests) SKIP_TESTS=1 ;;
    --no-build) NO_BUILD=1 ;;
    *) PROJECT_DIR="$a" ;;
  esac
done
[[ -z "$PROJECT_DIR" ]] && PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
PKG="app/src/main/java/dev/termos"
c_g='\033[1;32m'; c_r='\033[1;31m'; c_0='\033[0m'
log() { echo -e "${c_g}[termos-0.6]${c_0} $*"; }
die() { echo -e "${c_r}[error]${c_0} $*" >&2; exit 1; }

[[ -f "$PROJECT_DIR/$PKG/Registry.kt" ]] || die "Not a TermOS project: $PROJECT_DIR"
if grep -q 'const val VERSION = "0.6.0"' "$PROJECT_DIR/$PKG/Registry.kt" && [[ -f "$PROJECT_DIR/$PKG/Look.kt" ]]; then
  log "Project already at 0.6.0 — building only."
elif ! grep -qE 'const val VERSION = "0\.5\.0"' "$PROJECT_DIR/$PKG/Registry.kt"; then
  die "Expected 0.5.0 or 0.6.0. Current Registry.kt version:"
  grep VERSION "$PROJECT_DIR/$PKG/Registry.kt" || true
  die "Restore 0.5 from .backups/ or use the full patch from chat/Downloads."
else
  die "This tree is still 0.5.0. Open the project in Cursor and run the 0.6 apply there, or copy ~/Desktop/termos (already 0.6.0)."
fi

export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
export PATH="$PATH:$ANDROID_HOME/platform-tools"
cd "$PROJECT_DIR"
BK=".backups/pre-0.6-rebuild-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$BK" && cp -r app/src "$BK/src" 2>/dev/null || true
log "Backup: $PROJECT_DIR/$BK"

if [[ $SKIP_TESTS -eq 0 ]]; then
  log "Unit tests..."
  ./gradlew testDebugUnitTest --no-daemon -q || die "Unit tests failed."
  log "Unit tests passed."
fi
APK="app/build/outputs/apk/debug/app-debug.apk"
if [[ $NO_BUILD -eq 0 ]]; then
  log "Building debug APK..."
  ./gradlew assembleDebug compileDebugAndroidTestKotlin --no-daemon -q || die "Build failed."
  log "APK: $PROJECT_DIR/$APK"
fi
if [[ $DO_INSTALL -eq 1 ]]; then
  if adb get-state >/dev/null 2>&1; then
    adb install -r "$APK" && adb shell am start -n dev.termos/.MainActivity
    log "Installed 0.6.0."
  else
    die "No phone on adb. Run: adb devices"
  fi
fi
log "Done — TermOS 0.6.0"
