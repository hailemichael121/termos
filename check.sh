#!/usr/bin/env bash
# Quality gate: unit tests + Android lint.  ./check.sh [--e2e [--emulator]]
set -e
cd "$(dirname "$0")"
./gradlew testDebugUnitTest lintDebug --no-daemon -q
echo "unit tests + lint OK  (lint: app/build/reports/lint-results-debug.html)"
if [[ "${1:-}" == "--e2e" ]]; then ./e2e.sh "${2:-}"; fi
