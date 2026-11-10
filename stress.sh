#!/usr/bin/env bash
# Random-input stress + memory check on a connected device.  ./stress.sh [events=1500]
cd "$(dirname "$0")"
PKG=dev.termos; EVENTS="${1:-1500}"
fail() { echo "FAIL: $*"; exit 1; }
adb get-state >/dev/null 2>&1 || fail "no device"
pss()  { adb shell dumpsys meminfo $PKG | grep -E "^\s*TOTAL" | head -1 | grep -oE '[0-9]+' | head -1; }
acts() { adb shell dumpsys meminfo $PKG | grep -E "Activities:" | grep -oE '[0-9]+' | tail -1; }
adb shell am force-stop $PKG
adb logcat -c
adb shell am start -n $PKG/.MainActivity >/dev/null; sleep 3
B=$(pss); echo "PSS before: ${B:-?} KB"
adb shell monkey -p $PKG --pct-syskeys 0 --pct-appswitch 0 --throttle 60 -s 42 "$EVENTS" >/tmp/termos-monkey.log 2>&1 || true
for i in 1 2 3; do adb shell input keyevent KEYCODE_BACK; done
adb shell am send-trim-memory $PKG COMPLETE >/dev/null 2>&1 || true; sleep 3
A=$(pss); N=$(acts); echo "PSS after : ${A:-?} KB   live Activities: ${N:-?}"
adb logcat -d -b crash | grep -q "$PKG" && fail "crash (adb logcat -b crash)"
adb logcat -d | grep -qE "ANR in $PKG" && fail "ANR"
[[ -n "${N:-}" && "$N" -gt 2 ]] && fail "$N live Activities (leak?)"
if [[ -n "${B:-}" && -n "${A:-}" && "$A" -gt $(( B * 2 )) ]]; then fail "PSS more than doubled"; fi
adb logcat -d -s LeakCanary | grep -iE "leak" | head -5 && echo "WARN: LeakCanary reported something (see notification / logcat -s LeakCanary)"
echo "PASS: no crash, no ANR, bounded memory"
