#!/usr/bin/env bash
# Fast: JVM unit tests only (no phone)
set -e
cd "$(dirname "$0")"
./gradlew testDebugUnitTest --no-daemon
echo "unit tests passed  (report: app/build/reports/tests/testDebugUnitTest/index.html)"
