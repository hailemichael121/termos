#!/usr/bin/env bash
adb logcat -v time | grep -i -E "termos|AndroidRuntime"
