#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"
./gradlew assembleDebug
echo "APK: $(pwd)/app/build/outputs/apk/debug/app-debug.apk"
