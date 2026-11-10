#!/usr/bin/env bash
# End-to-end acceptance tests on a real phone (USB) or a headless emulator.
#   ./e2e.sh              use the connected device
#   ./e2e.sh --emulator   create/boot a headless Android 34 emulator (needs KVM, ~1 GB download)
set -euo pipefail
cd "$(dirname "$0")"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
export PATH="$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin"
AVD=termos_test
IMG="system-images;android-34;google_apis;x86_64"

if [[ "${1:-}" == "--emulator" ]]; then
  [[ -r /dev/kvm && -w /dev/kvm ]] || { echo "KVM not usable. Run: sudo apt install qemu-kvm && sudo adduser \$USER kvm  (then log out/in)"; exit 1; }
  yes | sdkmanager --sdk_root="$ANDROID_HOME" --licenses >/dev/null || true
  sdkmanager --sdk_root="$ANDROID_HOME" "emulator" "$IMG"
  echo no | avdmanager create avd -n "$AVD" -k "$IMG" -d pixel_6 --force
  nohup emulator -avd "$AVD" -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect >/tmp/termos-emulator.log 2>&1 &
  trap 'adb emu kill >/dev/null 2>&1 || true' EXIT
  adb wait-for-device
  until [[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; do sleep 3; done
  echo "emulator booted"
fi

adb get-state >/dev/null 2>&1 || { echo "No device. Plug in a phone (USB debugging) or use --emulator."; exit 1; }
for s in window_animation_scale transition_animation_scale animator_duration_scale; do
  adb shell settings put global "$s" 0 || true
done
echo "NOTE: tests reset TermOS data (password/settings) on the target device."
./gradlew connectedDebugAndroidTest --no-daemon
echo "e2e OK  (report: app/build/reports/androidTests/connected/debug/index.html)"
