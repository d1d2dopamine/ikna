#!/usr/bin/env bash
#
# Fetches everything the voice build needs, and puts it where Gradle looks.
#
#   bash tools/voice/fetch-voice.sh
#   gradle assembleVoiceRelease
#
# Two things are downloaded and neither is committed to this repository:
#
#   app/libs/sherpa-onnx-<version>.aar      the runtime, native code for four
#                                           architectures
#   app/src/voice/assets/kokoro/            the model, its voices, its tokens
#                                           and espeak-ng's phoneme data
#
# Both are large, both belong to other projects, and both are reproducible from
# the two URLs below -- which is exactly what a repository should not be storing.
# The lite build needs none of it and builds on a checkout that has never run
# this script.
#
# Override either version from the environment:
#
#   SHERPA_VERSION=1.11.0 MODEL_NAME=kokoro-int8-multi-lang-v1_1 bash tools/voice/fetch-voice.sh
#
set -euo pipefail

SHERPA_VERSION="${SHERPA_VERSION:-1.10.46}"
MODEL_NAME="${MODEL_NAME:-kokoro-multi-lang-v1_0}"

root=$(cd "$(dirname "$0")/../.." && pwd)
libs="$root/app/libs"
assets="$root/app/src/voice/assets/kokoro"

aar_url="https://huggingface.co/csukuangfj/sherpa-onnx-libs/resolve/main/android/aar/sherpa-onnx-${SHERPA_VERSION}.aar"
model_url="https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/${MODEL_NAME}.tar.bz2"

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

say() { printf '\n== %s\n' "$1"; }

get() {
  # Fails loudly on 404 rather than saving an HTML error page as a model.
  curl --fail --location --retry 3 --retry-delay 5 --progress-bar --output "$2" "$1" || {
    echo "could not download: $1" >&2
    echo "the pinned version may have been withdrawn; set SHERPA_VERSION or MODEL_NAME" >&2
    exit 1
  }
}

# ---------------------------------------------------------------- runtime
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

# ------------------------------------------------------------------ model
if [ -f "$assets/model.onnx" ] && [ -d "$assets/espeak-ng-data" ]; then
  say "model already present: $assets"
else
  say "model: ${MODEL_NAME}"
  get "$model_url" "$work/model.tar.bz2"

  tar -xjf "$work/model.tar.bz2" -C "$work"
  src="$work/${MODEL_NAME}"
  [ -d "$src" ] || { echo "unexpected archive layout in ${MODEL_NAME}.tar.bz2" >&2; exit 1; }

  rm -rf "$assets"
  mkdir -p "$assets"

  # Named one by one on purpose. These releases also carry sample wavs, a README
  # and sometimes a second copy of the model in another precision; packing the
  # whole folder would put tens of megabytes of nothing into the APK.
  for f in model.onnx voices.bin tokens.txt lexicon-us-en.txt lexicon-zh.txt; do
    [ -f "$src/$f" ] && cp "$src/$f" "$assets/$f"
  done
  for d in espeak-ng-data dict; do
    [ -d "$src/$d" ] && cp -r "$src/$d" "$assets/$d"
  done

  [ -f "$assets/model.onnx" ] || { echo "no model.onnx in the archive" >&2; exit 1; }
fi

# -------------------------------------------------------------- speaker ids
# Which numbered voice to use per language. Kokoro keeps dozens of voices in one
# file and addresses them by number, and which number is which language belongs
# to the release, not to the app -- so it is written here as data that
# KokoroSpeech reads, and can be corrected without touching Kotlin.
#
# The voice list for a release is at
# https://k2-fsa.github.io/sherpa/onnx/tts/all/index.html
if [ ! -f "$assets/speakers.txt" ]; then
  cat > "$assets/speakers.txt" <<'EOF'
# language = speaker id in voices.bin
# Anything not listed here falls back to 0.
en=0
EOF
fi

say "done"
du -sh "$aar" "$assets" 2>/dev/null || true
echo
echo "now: gradle assembleVoiceRelease"
