#!/usr/bin/env bash
set -euo pipefail

# ---------------------------------------------------------------------------
# Packs the desktop application into a single Linux file.
#
# Nothing here compiles anything. `gradle :desktop:createReleaseDistributable`
# does the whole build -- the same task, the same module and the same ProGuard
# rules the Windows build uses -- and jpackage hands back an application image:
# a folder with the launcher in bin/ and a trimmed Java runtime in lib/. On
# Windows that folder becomes Ikna.exe plus an .msi and the job is done.
#
# Linux has no equivalent jpackage can produce. --type app-image is a folder,
# and deb and rpm are per-distribution installers that have to be built on the
# distribution they are for. An AppImage is neither: it is that same folder,
# squashed into one executable file that runs on any x86_64 desktop with no
# installation and nothing to uninstall. That is what this script makes, and it
# is the reason the Linux build is a packaging step rather than a port -- the
# application inside it is byte-for-byte what Gradle produced.
#
#   gradle :desktop:createReleaseDistributable
#   bash tools/appimage/build-appimage.sh
#
# writes build/appimage/ikna-x86_64.AppImage.
#
#   bash tools/appimage/build-appimage.sh out/ikna-v0.10.0-press-linux-x86_64.AppImage
#
# writes that instead, which is what the release workflow does so the file on
# the release page says which version it is.
#
#   bash tools/appimage/build-appimage.sh --appdir-only
#
# stops after assembling the folder that would be packed, for looking at what
# is about to be shipped without downloading a tool to pack it.
# ---------------------------------------------------------------------------

usage() {
	cat <<'TEXT'
usage: bash tools/appimage/build-appimage.sh [--appdir-only] [output.AppImage]

  --appdir-only   assemble the AppDir and stop, packing nothing
  output          where to write the AppImage
                  (default: build/appimage/ikna-x86_64.AppImage)

environment:
  IKNA_APPIMAGETOOL          an appimagetool binary to use instead of fetching one
  IKNA_APPIMAGETOOL_SHA256   if set, the fetched tool must have this SHA-256
TEXT
}

# Run from the repository root whatever directory it was called from, so every
# path below is the one written in the workflows.
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$root"

appdir_only=0
output=""

while [ $# -gt 0 ]; do
	case "$1" in
		--appdir-only) appdir_only=1 ;;
		-h|--help) usage; exit 0 ;;
		-*)
			echo "unknown option: $1" >&2
			usage >&2
			exit 2
			;;
		*)
			if [ -n "$output" ]; then
				echo "only one output path can be given" >&2
				exit 2
			fi
			output="$1"
			;;
	esac
	shift
done

# Where the Compose plugin leaves the release application image. The debug one
# next to it (main/app) is not shrunk and is not what gets published.
dist="desktop/build/compose/binaries/main-release/app"
work="build/appimage"
appdir="$work/Ikna.AppDir"
: "${output:=$work/ikna-x86_64.AppImage}"

# The version of the packer, pinned. "continuous" is a moving target and a
# build that packs itself differently on Tuesday is a build nobody can debug.
#
# One URL used to be enough. It is not: appimagetool moved out of the
# AppImageKit repository into its own, and the old release-13 assets were
# renamed to obsolete-*, so the address that worked for years now answers 404
# and takes the build down with it. A pinned tag is still tried first; the
# rolling build and the renamed legacy asset are there so that one more move
# upstream costs a slower download instead of a red run.
appimagetool_release="1.9.1"
appimagetool_urls="
https://github.com/AppImage/appimagetool/releases/download/1.9.1/appimagetool-x86_64.AppImage
https://github.com/AppImage/appimagetool/releases/download/continuous/appimagetool-x86_64.AppImage
https://github.com/AppImage/AppImageKit/releases/download/13/obsolete-appimagetool-x86_64.AppImage
"

if [ ! -d "$dist" ]; then
	echo "No application image at $dist." >&2
	echo "Build it first:" >&2
	echo "  gradle :desktop:createReleaseDistributable" >&2
	exit 1
fi

# jpackage names the folder after packageName, so it is read rather than
# assumed: renaming the application in desktop/build.gradle.kts must not turn
# into a broken AppRun that nobody notices until the file is downloaded.
app=""
for candidate in "$dist"/*; do
	if [ -d "$candidate/bin" ]; then
		app="$candidate"
		break
	fi
done
if [ -z "$app" ]; then
	echo "Nothing under $dist looks like an application image:" >&2
	find "$dist" -maxdepth 2 >&2
	exit 1
fi

launcher=""
for candidate in "$app"/bin/*; do
	if [ -f "$candidate" ] && [ -x "$candidate" ]; then
		launcher="$(basename "$candidate")"
		break
	fi
done
if [ -z "$launcher" ]; then
	echo "No executable launcher in $app/bin:" >&2
	ls -l "$app/bin" >&2
	exit 1
fi

echo "Application image: $app"
echo "Launcher:          bin/$launcher"

# ---------------------------------------------------------------------------
# The AppDir.
#
# The application image goes in whole, under usr/, because the jpackage
# launcher finds its runtime and its jars relative to itself: bin/Ikna expects
# ../lib beside it, and splitting the two is how an AppImage that starts on the
# build machine dies on somebody's desktop.
# ---------------------------------------------------------------------------
rm -rf "$appdir"
mkdir -p "$appdir/usr"
cp -a "$app/." "$appdir/usr/"

# AppRun is what the AppImage runtime executes after mounting itself. readlink
# resolves it inside the mount point, which is a different path on every run,
# so the launcher is reached relatively and never by an absolute path baked in
# here.
cat > "$appdir/AppRun" <<APPRUN
#!/bin/sh
# Generated by tools/appimage/build-appimage.sh -- do not edit inside the
# AppImage; edit the script and build again.
here="\$(dirname "\$(readlink -f "\$0")")"

# The SQLite that Room talks to is compiled into the application and brings its
# own copy of the C++ standard library. Under a national locale on a recent
# glibc that copy's locale facets come up empty, and the first statement dies
# with SIGSEGV about a second after launch, before any window appears. So
# native code is given C.UTF-8, which is built into glibc and reads no locale
# data, while the interface keeps the language: whatever locale was in effect
# is handed to the JVM as user.language and user.country. C.UTF-8 and not C,
# because under C the JVM reads file names as ASCII and anything outside it --
# a deck in a home folder with a national name -- stops being reachable.
# IKNA_KEEP_LOCALE=1 starts with the locale untouched, IKNA_NATIVE_LOCALE names
# another one, and both exist for the day this is worth testing again.
if [ -z "\${IKNA_KEEP_LOCALE:-}" ]; then
	requested="\${LC_ALL:-\${LC_CTYPE:-\${LANG:-}}}"
	tag="\$(printf '%s' "\$requested" | cut -d. -f1 | cut -d@ -f1)"
	language="\$(printf '%s' "\$tag" | cut -d_ -f1)"
	country="\$(printf '%s' "\$tag" | cut -s -d_ -f2)"

	native="\${IKNA_NATIVE_LOCALE:-}"
	if [ -z "\$native" ]; then
		native=C.UTF-8
		if command -v locale >/dev/null 2>&1; then
			available="\$(locale -a 2>/dev/null | grep -i -x -e 'C.UTF-8' -e 'C.utf8' | head -n 1)"
			if [ -n "\$available" ]; then
				native="\$available"
			else
				native=C
			fi
		fi
	fi

	options="-Dfile.encoding=UTF-8"
	if [ -n "\$language" ]; then
		options="\$options -Duser.language=\$language"
	fi
	if [ -n "\$country" ]; then
		options="\$options -Duser.country=\$country"
	fi
	JAVA_TOOL_OPTIONS="\$options\${JAVA_TOOL_OPTIONS:+ \$JAVA_TOOL_OPTIONS}"
	export JAVA_TOOL_OPTIONS

	unset LC_CTYPE LC_NUMERIC LC_TIME LC_COLLATE LC_MONETARY LC_MESSAGES LC_PAPER LC_NAME LC_ADDRESS LC_TELEPHONE LC_MEASUREMENT LC_IDENTIFICATION
	LANG="\$native"
	LC_ALL="\$native"
	export LANG LC_ALL
fi

# Symbols are resolved when a library is loaded instead of at its first call.
# The application carries two C++ runtimes it never chose to carry: one
# compiled into the prebuilt SQLite library Room uses, one loaded from the
# system by Skia. With lazy binding the linker is free to answer a call in the
# first with the implementation from the second, and the two do not agree about
# how a std::string is laid out, so the process dies rather than misbehaves.
# The application opens its database before it draws anything, so with
# immediate binding the SQLite library is resolved against itself and stays
# that way for the rest of the run. IKNA_LAZY_BIND=1 restores the default for
# anyone bisecting this again.
if [ -z "\${IKNA_LAZY_BIND:-}" ]; then
	LD_BIND_NOW=1
	export LD_BIND_NOW
fi

exec "\$here/usr/bin/${launcher}" "\$@"
APPRUN
chmod +x "$appdir/AppRun"

# The desktop entry, with Exec pointed at the launcher that actually exists.
sed "s|^Exec=.*|Exec=${launcher}|" tools/appimage/ikna.desktop > "$appdir/ikna.desktop"

# The icon. jpackage copies the one named in the linux block of
# desktop/build.gradle.kts into lib/<name>.png, so that is the first place to
# look; the file in the module's resources -- the same 512px mark the window
# and the taskbar already show -- is the fallback, and both are the same image.
icon="$app/lib/${launcher}.png"
if [ ! -f "$icon" ]; then
	icon="desktop/src/main/resources/icon.png"
fi
if [ ! -f "$icon" ]; then
	echo "No icon found for the AppImage (looked for $app/lib/${launcher}.png)" >&2
	exit 1
fi

# Three copies of two files, and all three are wanted. The pair at the root of
# the AppDir is what appimagetool reads and what a file manager shows for the
# file itself; the pair under usr/share is what a desktop picks up if the user
# ever integrates the AppImage into their menu.
cp "$icon" "$appdir/ikna.png"
ln -sf ikna.png "$appdir/.DirIcon"
mkdir -p "$appdir/usr/share/applications"
mkdir -p "$appdir/usr/share/icons/hicolor/512x512/apps"
cp "$appdir/ikna.desktop" "$appdir/usr/share/applications/ikna.desktop"
cp "$icon" "$appdir/usr/share/icons/hicolor/512x512/apps/ikna.png"

if [ "$appdir_only" = "1" ]; then
	echo "AppDir assembled, nothing packed: $appdir"
	du -sh "$appdir"
	exit 0
fi

# ---------------------------------------------------------------------------
# The packer.
#
# appimagetool is itself an AppImage, and mounting one needs libfuse.so.2 --
# which a CI runner does not have and which Fedora stopped installing by
# default. APPIMAGE_EXTRACT_AND_RUN tells the runtime to unpack itself into a
# temporary folder and run from there instead of mounting, so no FUSE is
# needed anywhere in this script.
# ---------------------------------------------------------------------------
tool="${IKNA_APPIMAGETOOL:-}"
if [ -z "$tool" ]; then
	mkdir -p "$work"
	tool="$work/appimagetool-${appimagetool_release}-x86_64.AppImage"
	if [ ! -f "$tool" ]; then
		fetched=0
		for url in $appimagetool_urls; do
			echo "Fetching appimagetool from $url"
			if curl -fL --retry 2 --retry-all-errors --connect-timeout 20 \
				--max-time 300 "$url" -o "$tool.part"; then
				fetched=1
				break
			fi
			echo "  unavailable, trying the next source"
			rm -f "$tool.part"
		done
		if [ "$fetched" != 1 ]; then
			echo "" >&2
			echo "Could not download appimagetool from any known source." >&2
			echo "Supply one instead:" >&2
			echo "  IKNA_APPIMAGETOOL=/path/to/appimagetool-x86_64.AppImage \\" >&2
			echo "    bash tools/appimage/build-appimage.sh" >&2
			exit 1
		fi
		mv "$tool.part" "$tool"
	fi
fi

if [ ! -f "$tool" ]; then
	echo "appimagetool not found at $tool" >&2
	exit 1
fi
chmod +x "$tool"

# Printed rather than pinned. The pin would have to be checked by a person
# before it could be trusted, and this file is a build-time utility that never
# reaches a user's machine -- but the log should still say which bytes packed
# the release, and IKNA_APPIMAGETOOL_SHA256 turns that into a check when
# somebody wants one.
tool_sha256="$(sha256sum "$tool" | cut -d' ' -f1)"
echo "appimagetool: $tool"
echo "  sha256: $tool_sha256"
if [ -n "${IKNA_APPIMAGETOOL_SHA256:-}" ] && [ "$tool_sha256" != "$IKNA_APPIMAGETOOL_SHA256" ]; then
	echo "appimagetool does not match IKNA_APPIMAGETOOL_SHA256" >&2
	echo "  expected $IKNA_APPIMAGETOOL_SHA256" >&2
	exit 1
fi

mkdir -p "$(dirname "$output")"
rm -f "$output"

# ARCH is what appimagetool stamps into the file and it refuses to guess when
# it cannot see an ELF it recognises. --no-appstream because this project ships
# no AppStream metadata: the alternative is a validator failing a build over a
# file that exists for software centres, and the AppImage is downloaded from a
# release page. The flag is passed only when the packer admits to having it,
# because versions disagree and an unknown option is a failed build.
appstream_flag=""
if APPIMAGE_EXTRACT_AND_RUN=1 "$tool" --help 2>&1 | grep -q -- "--no-appstream"; then
	appstream_flag="--no-appstream"
fi

# Unquoted on purpose: an empty flag must disappear rather than become an
# empty argument that the packer would read as a path.
# shellcheck disable=SC2086
ARCH="${ARCH:-x86_64}" APPIMAGE_EXTRACT_AND_RUN=1 \
	"$tool" $appstream_flag "$appdir" "$output"

if [ ! -f "$output" ]; then
	echo "appimagetool reported success but wrote no file at $output" >&2
	exit 1
fi
chmod +x "$output"

echo ""
echo "AppImage: $output"
echo "  size:   $(du -h "$output" | cut -f1)"
echo "  sha256: $(sha256sum "$output" | cut -d' ' -f1)"
