#!/usr/bin/env bash
# Keep a diagnostic artifact even if dexing fails before test reports exist.
# pipefail is essential: tee must never turn a failed Gradle build green.
set -euo pipefail
log_file="${1:?Usage: run-gradle.sh LOG_FILE GRADLE_ARGUMENTS...}"
shift
mkdir -p "$(dirname "$log_file")"
gradle --no-daemon --console=plain --stacktrace "$@" 2>&1 | tee "$log_file"
