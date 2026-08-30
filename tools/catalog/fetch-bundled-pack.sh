#!/usr/bin/env bash
set -euo pipefail

# The starter deck is a release asset rather than a generated source file. Keep
# the repository small, but make the APK reproducible: this exact byte sequence
# is pinned by both size and SHA-256 before Gradle is allowed to see it.
#
# The pin is meant to break. A rebuilt catalogue is a different deck - the
# corpus moves, and since 0.10.0 every card carries a transcription - so after
# publishing a new catalogue the pin has to be told what the new starter deck
# is. That is what --update does: it fetches the asset, checks the shape of
# every card in it, and only then writes the new size and hash into this file
# for you to commit. Refusing to build against an unknown deck is the feature;
# updating the pin by hand from a CI log is not.
#
#   bash tools/catalog/fetch-bundled-pack.sh            # verify and fetch
#   bash tools/catalog/fetch-bundled-pack.sh --update   # re-pin to what is published
url="https://github.com/d1d2dopamine/ikna/releases/download/catalog/en-ru-beginner.jsonl"
dest="app/src/main/assets/packs/en-ru-beginner.jsonl"
expected_size="1196978"
expected_sha256="70a84018057208aae631ce00f16b4c1d7be009660a9b75fee39dfa167530f8f7"

mode="verify"
case "${1:-}" in
  "") ;;
  --update) mode="update" ;;
  *)
    echo "usage: $0 [--update]" >&2
    exit 2
    ;;
esac

mkdir -p "$(dirname "$dest")"
# The old 121-card seed is no longer shipped. Removing the unreferenced file
# here also handles repositories updated by extracting this ZIP over an older
# checkout, where deleting a file from the archive cannot delete it on disk.
rm -f app/src/main/assets/packs/en-ru-core.jsonl
tmp="${dest}.part"
rm -f "$tmp"

if [ "$mode" = "verify" ] && [ -f "$dest" ]; then
  current_size=$(wc -c < "$dest" | tr -d ' ')
  current_sha256=$(sha256sum "$dest" | cut -d' ' -f1)
  if [ "$current_size" = "$expected_size" ] && [ "$current_sha256" = "$expected_sha256" ]; then
    echo "Bundled catalogue deck already verified."
    exit 0
  fi
fi

curl -fL --retry 3 --retry-all-errors --connect-timeout 20 --max-time 180 \
  "$url" -o "$tmp"

actual_size=$(wc -c < "$tmp" | tr -d ' ')
actual_sha256=$(sha256sum "$tmp" | cut -d' ' -f1)

if [ "$mode" = "verify" ]; then
  if [ "$actual_size" != "$expected_size" ] || [ "$actual_sha256" != "$expected_sha256" ]; then
    echo "The published starter deck is not the pinned one." >&2
    echo "  size:   $actual_size (pinned $expected_size)" >&2
    echo "  sha256: $actual_sha256" >&2
    echo "          $expected_sha256 (pinned)" >&2
    echo "" >&2
    echo "If the catalogue was rebuilt on purpose, re-pin it and commit the change:" >&2
    echo "  bash tools/catalog/fetch-bundled-pack.sh --update" >&2
    rm -f "$tmp"
    exit 1
  fi
fi

# The shape of every card is checked in both modes. A pin only says the bytes
# have not changed since somebody looked; this says somebody looked.
python3 - "$tmp" <<'PY'
import json
import sys

path = sys.argv[1]
count = 0
transcribed = 0
with open(path, encoding="utf-8") as handle:
    for number, line in enumerate(handle, start=1):
        card = json.loads(line)
        for key in ("id", "text", "context", "translation", "targetStart", "targetEnd", "tokens"):
            if key not in card:
                raise SystemExit(f"line {number}: missing {key}")
        start, end = card["targetStart"], card["targetEnd"]
        if card["context"][start:end] != card["text"]:
            raise SystemExit(f"line {number}: phrase does not match offsets")
        if "\n— Tatoeba #" not in card["translation"]:
            raise SystemExit(f"line {number}: source id is missing")
        count += 1
        if card.get("ipa"):
            transcribed += 1
if count < 100:
    raise SystemExit(f"starter deck is unexpectedly short: {count} cards")
print(f"Verified bundled catalogue deck: {count} cards, {transcribed} with a transcription")
PY

if [ "$mode" = "update" ]; then
  if [ "$actual_size" = "$expected_size" ] && [ "$actual_sha256" = "$expected_sha256" ]; then
    echo "The pin already matches what is published; nothing to change."
  else
    # Written to a new file and renamed over this one. The rename replaces the
    # directory entry rather than the inode, so the shell reading this script
    # keeps reading the copy it opened and does not lose its place mid-run.
    python3 - "$0" "$actual_size" "$actual_sha256" <<'PY'
import os
import re
import sys

path, size, digest = sys.argv[1], sys.argv[2], sys.argv[3]
with open(path, encoding="utf-8") as handle:
    text = handle.read()
after = re.sub(r'^expected_size="[0-9]*"$', 'expected_size="%s"' % size, text, count=1, flags=re.M)
after = re.sub(
    r'^expected_sha256="[0-9a-f]*"$', 'expected_sha256="%s"' % digest, after, count=1, flags=re.M
)
if after == text:
    raise SystemExit("could not find the pinned lines in %s" % path)
temporary = path + ".new"
with open(temporary, "w", encoding="utf-8") as handle:
    handle.write(after)
os.chmod(temporary, os.stat(path).st_mode)
os.replace(temporary, path)
PY
    echo "Re-pinned the starter deck:"
    echo "  size:   $expected_size -> $actual_size"
    echo "  sha256: $expected_sha256"
    echo "       -> $actual_sha256"
    echo "Commit tools/catalog/fetch-bundled-pack.sh so every build agrees."
  fi
fi

mv "$tmp" "$dest"
