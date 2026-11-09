#!/usr/bin/env bash
# Build + install on a USB-connected phone (USB debugging enabled)
set -e
cd "$(dirname "$0")"
./build.sh
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
# Try to set TermOS as the default home (works on some devices; otherwise press Home on the phone and pick TermOS)
adb shell cmd package set-home-activity dev.termos/.MainActivity || true
adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME || true
