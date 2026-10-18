#!/usr/bin/env bash
# Native SQLite waits may outlive coroutine/JUnit deadlines. Bound the entire
# Gradle process group, while retaining logcat before the emulator action stops it.
set -euo pipefail
mkdir -p ci-logs
serial="emulator-${EMULATOR_PORT:-5554}"
# Bash file-size units are KiB: retain at most 32 MiB, then report capture failure
# separately. Test outcomes must not depend on adb/logcat availability.
(ulimit -f 32768; exec timeout --signal=TERM --kill-after=5s 960s adb -s "$serial" logcat -v threadtime > ci-logs/android-logcat.log 2>&1) &
logcat_pid=$!
cleanup() {
  # This PID belongs only to this wrapper; never stop unrelated adb/emulators.
  capture_status=0
  if kill -0 "$logcat_pid" 2>/dev/null; then
    kill "$logcat_pid" 2>/dev/null || true
    wait "$logcat_pid" 2>/dev/null || capture_status=$?
    message="Logcat stopped at test completion (capture status $capture_status)."
  else
    wait "$logcat_pid" 2>/dev/null || capture_status=$?
    message="Logcat exited before test completion (capture status $capture_status); inspect diagnostic availability/size limit."
  fi
  printf '%s\n' "$message" > ci-logs/android-logcat-status.txt || true
}
trap cleanup EXIT
code=0
timeout --signal=TERM --kill-after=30s 900s bash tools/ci/run-gradle.sh \
  ci-logs/android-connected-tests.log :app:connectedDebugAndroidTest \
  -Pikna.abi=emulator || code=$?
printf 'Android instrumentation process exit: %s\n' "$code" > ci-logs/android-exit-status.txt || true
exit "$code"
