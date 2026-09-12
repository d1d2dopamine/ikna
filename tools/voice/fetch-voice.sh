#!/usr/bin/env bash
#
# Fetches the one thing this project does not carry itself, and puts it where
# Gradle looks. Run once per clone, before the first Android build:
#
#   bash tools/voice/fetch-voice.sh
#   gradle assembleRelease
#
# One file is downloaded and it is not committed to this repository:
#
#   app/libs/sherpa-onnx-<version>.aar      the speech runtime, native code for
#                                           four architectures
#
# No model is downloaded, because no model ships. The runtime is in the APK and
# the model is whatever the person using it adds from the file picker: Kokoro,
# or any compatible Piper voice, in the language they are actually learning.
# See docs/VOICE.md.
#
# Override the runtime version from the environment:
#
#   SHERPA_VERSION=1.11.0 bash tools/voice/fetch-voice.sh
#
# There is one Android build and it contains the speech engine, so a checkout
# that has never run this script does not compile. The runtime stays outside the
# repository because it is a large third-party binary.
#
set -euo pipefail

# Pinned deliberately. The Kotlin in SherpaSpeech.kt is written against this API,
# and a newer runtime can be a compile error, so it moves only after review.
SHERPA_VERSION="${SHERPA_VERSION:-1.10.46}"

root=$(cd "$(dirname "$0")/../.." && pwd)
libs="$root/app/libs"

# GitHub Releases is the primary source because GitHub Actions is already running
# on GitHub infrastructure. Hugging Face remains a fallback. The old script used
# Hugging Face only, which made otherwise unrelated pushes fail when that host
# returned HTTP 429 (rate limited).
github_url="https://github.com/k2-fsa/sherpa-onnx/releases/download/v${SHERPA_VERSION}/sherpa-onnx-${SHERPA_VERSION}.aar"
hf_url="https://huggingface.co/csukuangfj/sherpa-onnx-libs/resolve/main/android/aar/sherpa-onnx-${SHERPA_VERSION}.aar"

say() { printf '\n== %s\n' "$1"; }

download_one() {
  url="$1"
  dst="$2"
  tmp="${dst}.part"

  rm -f "$tmp"
  if curl \
      --fail \
      --location \
      --retry 6 \
      --retry-delay 10 \
      --retry-max-time 240 \
      --connect-timeout 20 \
      --progress-bar \
      --output "$tmp" \
      "$url"; then
    mv "$tmp" "$dst"
    return 0
  fi

  rm -f "$tmp"
  return 1
}

get() {
  dst="$1"
  shift

  for url in "$@"; do
    echo "trying: $url"
    if download_one "$url" "$dst"; then
      # An AAR is a ZIP archive. This catches an interrupted or unexpected body
      # before Gradle reports a much less useful dependency error later.
      if command -v unzip >/dev/null 2>&1 && ! unzip -tq "$dst" >/dev/null 2>&1; then
        echo "downloaded file is not a valid AAR: $url" >&2
        rm -f "$dst"
        continue
      fi
      echo "downloaded: $url"
      return 0
    fi
    echo "source failed, trying the next one" >&2
  done

  echo "could not download sherpa-onnx ${SHERPA_VERSION} from any configured source" >&2
  echo "set SHERPA_VERSION only if the Kotlin API has been checked against that release" >&2
  return 1
}

mkdir -p "$libs"
aar="$libs/sherpa-onnx-${SHERPA_VERSION}.aar"

if [ -f "$aar" ]; then
  say "runtime already present: $(basename "$aar")"
else
  say "runtime: sherpa-onnx ${SHERPA_VERSION}"
  # Only one .aar may ever be in app/libs: the build takes all of them, and two
  # versions of the same native library can crash at startup.
  rm -f "$libs"/sherpa-onnx-*.aar
  get "$aar" "$github_url" "$hf_url"
fi

# An old checkout may still have the model that used to be packed into the APK,
# in a source set that no longer exists. Left in place it would add a large dead
# payload to the build, so remove it.
old="$root/app/src/voice"
if [ -d "$old" ]; then
  say "removing obsolete bundled-model source set: app/src/voice"
  rm -rf "$old"
fi

say "done"
du -sh "$aar" 2>/dev/null || true
echo
echo "now: gradle assembleRelease"
echo "the model is added inside the app: Settings -> Voice -> Add a model"
