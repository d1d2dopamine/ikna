#!/usr/bin/env bash
#
# Fetches the one thing this project does not carry itself, and puts it where
# Gradle looks. Run once per clone, before the first build:
#
#   bash tools/voice/fetch-voice.sh
#   gradle assembleRelease
#
# One file is downloaded and it is not committed to this repository:
#
#   app/libs/sherpa-onnx-<version>.aar      the speech runtime, native code for
#                                           four architectures, about 10 MB
#
# No model is downloaded, because no model ships. An earlier version of this
# script packed Kokoro into the APK, and it was wrong twice over: 466 MB at full
# precision, 150 MB quantised, for one language that no deck in this app is
# written in -- so most people carried a third of a gigabyte and heard the
# phone's own voice anyway. Now the runtime is in the APK and the model is
# whatever the person using it puts in from the file picker: Kokoro, or any Piper
# voice, in whatever language they are actually learning. See docs/VOICE.md.
#
# Override the runtime version from the environment:
#
#   SHERPA_VERSION=1.11.0 bash tools/voice/fetch-voice.sh
#
# There is one build now, and it contains the speech engine, so this is no
# longer optional: a checkout that has never run this script does not compile.
# The runtime is still not committed - ten megabytes of native code per
# version, in a repository whose whole point is that it is small and readable.
#
set -euo pipefail

# Pinned deliberately. The Kotlin in SherpaSpeech.kt is written against this API,
# and a newer runtime is a compile error at best -- so it moves only when
# somebody has read the release notes.
SHERPA_VERSION="${SHERPA_VERSION:-1.10.46}"

root=$(cd "$(dirname "$0")/../.." && pwd)
libs="$root/app/libs"

aar_url="https://huggingface.co/csukuangfj/sherpa-onnx-libs/resolve/main/android/aar/sherpa-onnx-${SHERPA_VERSION}.aar"

say() { printf '\n== %s\n' "$1"; }

get() {
  # Fails loudly on 404 rather than saving an HTML error page as a library.
  curl --fail --location --retry 3 --retry-delay 5 --progress-bar --output "$2" "$1" || {
    echo "could not download: $1" >&2
    echo "the pinned version may have been withdrawn; set SHERPA_VERSION" >&2
    exit 1
  }
}

mkdir -p "$libs"
aar="$libs/sherpa-onnx-${SHERPA_VERSION}.aar"

if [ -f "$aar" ]; then
  say "runtime already present: $(basename "$aar")"
else
  say "runtime: sherpa-onnx ${SHERPA_VERSION}"
  # Only one .aar may ever be in app/libs: the build takes all of them, and two
  # versions of the same native library is a crash at startup, not a warning.
  rm -f "$libs"/sherpa-onnx-*.aar
  get "$aar_url" "$aar"
fi

# An old checkout may still have the model that used to be packed into the APK,
# in a source set that no longer exists. Left in place it would add a hundred
# and fifty megabytes to the build for nothing, so it goes.
old="$root/app/src/voice"
if [ -d "$old" ]; then
  say "removing the source set that used to hold the bundled model: app/src/voice"
  rm -rf "$old"
fi

say "done"
du -sh "$aar" 2>/dev/null || true
echo
echo "now: gradle assembleRelease"
echo "the model is added inside the app: Settings -> Voice -> Add a model"
