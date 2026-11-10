#!/usr/bin/env bash
# Unit tests (no phone needed)
set -e
cd "$(dirname "$0")"
./gradlew testDebugUnitTest --no-daemon
echo "unit tests passed"
