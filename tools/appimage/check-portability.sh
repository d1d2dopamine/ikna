#!/usr/bin/env bash
#
# What the packaged Linux application demands of the machine it is opened on.
#
# An AppImage carries the application, its Java runtime image and every native
# library it loads, but it cannot carry glibc: the C library is the one thing
# that always comes from the distribution underneath. So the question that
# decides whether a download works for somebody is which glibc version the
# libraries inside ask for, and this script answers it by reading the version
# references out of every ELF file in a finished build.
#
# Printed, not enforced, by default. The floor is set by the JDK runtime image
# and by Skia rather than by anything this repository compiles, so a number
# that moves is news to put in the release notes -- not a reason to fail a
# build. Pass a ceiling to make it fail:
#
#   IKNA_GLIBC_LIMIT=2.35 bash tools/appimage/check-portability.sh <dir>
#
# The directory defaults to the release distribution jpackage writes, which is
# also what the AppImage is built from.
set -eu

target="${1:-desktop/build/compose/binaries/main-release/app/Ikna}"
limit="${IKNA_GLIBC_LIMIT:-}"

if [ ! -e "$target" ]; then
	echo "check-portability: nothing at $target" >&2
	exit 1
fi

reader=""
for candidate in readelf eu-readelf; do
	if command -v "$candidate" >/dev/null 2>&1; then
		reader="$candidate"
		break
	fi
done
if [ -z "$reader" ]; then
	echo "check-portability: no readelf here, skipping the audit"
	exit 0
fi

dumper=""
if command -v objdump >/dev/null 2>&1; then
	dumper=objdump
fi

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

find "$target" -type f -print | sort > "$work/all"

: > "$work/elves"
while IFS= read -r file; do
	if head -c 4 "$file" 2>/dev/null | grep -q -a ELF; then
		printf '%s\n' "$file" >> "$work/elves"
	fi
done < "$work/all"

: > "$work/needs"
while IFS= read -r file; do
	{
		"$reader" -V -W "$file" 2>/dev/null || true
		if [ -n "$dumper" ]; then "$dumper" -p "$file" 2>/dev/null || true; fi
	} |
		grep -o -E '(GLIBC|GLIBCXX|GCC)_[0-9]+(\.[0-9]+)*' |
		sort -u |
		while IFS= read -r need; do
			printf '%s\t%s\n' "$need" "$file" >> "$work/needs"
		done
done < "$work/elves"

files="$(wc -l < "$work/elves" | tr -d ' ')"
echo "Linux portability of $target"
echo "native libraries inspected: $files"

highest=""

family_max() {
	grep "^$1_" "$work/needs" 2>/dev/null |
		cut -f1 |
		sed "s/^$1_//" |
		sort -V |
		tail -n 1
}

for family in GLIBC GLIBCXX GCC; do
	max="$(family_max "$family")"
	if [ -z "$max" ]; then
		echo "$family: nothing requires it"
		continue
	fi
	asker="$(grep -F "$(printf '%s_%s\t' "$family" "$max")" "$work/needs" | head -n 1 | cut -f2)"
	echo "$family: at most $max, asked for by ${asker#$target/}"
	if [ "$family" = GLIBC ]; then
		highest="$max"
	fi
done

if [ -n "$highest" ]; then
	echo "runs on any distribution with glibc $highest or newer"
fi

if [ -n "$limit" ] && [ -n "$highest" ]; then
	worst="$(printf '%s\n%s\n' "$limit" "$highest" | sort -V | tail -n 1)"
	if [ "$worst" != "$limit" ]; then
		echo "check-portability: glibc $highest is newer than the $limit ceiling" >&2
		exit 1
	fi
fi
