#!/usr/bin/env bash
# On-device smoke test: install, launch, check it is a HOME candidate, check for crashes.
cd "$(dirname "$0")"
APK=app/build/outputs/apk/debug/app-debug.apk
fail() { echo "FAIL: $*"; exit 1; }
adb get-state >/dev/null 2>&1 || fail "no device (USB debugging on?)"
[[ -f $APK ]] || fail "build first: ./build.sh"
adb install -r "$APK" | grep -q Success || fail "install"
adb logcat -c
adb shell am start -n dev.termos/.MainActivity >/dev/null
sleep 3
adb shell pidof dev.termos >/dev/null || fail "app not running"
adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME \
  | grep -q dev.termos || fail "not registered as HOME"
if adb logcat -d -b crash | grep -q "dev.termos"; then fail "crash in logcat (./logs.sh)"; fi
echo "PASS: installed, running, HOME-capable, no crash"
