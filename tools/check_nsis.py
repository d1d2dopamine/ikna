#!/usr/bin/env python3
"""Fast source contract for the reviewable Windows NSIS installer."""

from pathlib import Path
import re
import struct
import sys

ROOT = Path(__file__).resolve().parents[1]
NSI = ROOT / "desktop/installer/ikna.nsi"
BUILD = ROOT / "tools/nsis/build-installer.ps1"
SMOKE = ROOT / "tools/nsis/test-installer.ps1"
GRADLE = ROOT / "desktop/build.gradle.kts"
BUILD_YML = ROOT / ".github/workflows/build.yml"
RELEASE_YML = ROOT / ".github/workflows/release.yml"


def require(text: str, fragments: list[str], name: str) -> None:
    missing = [fragment for fragment in fragments if fragment not in text]
    if missing:
        raise AssertionError(f"{name} misses: {missing}")


def bmp(path: Path, expected: tuple[int, int]) -> None:
    data = path.read_bytes()
    if data[:2] != b"BM" or len(data) < 54:
        raise AssertionError(f"{path.relative_to(ROOT)} is not a BMP")
    width, height = struct.unpack_from("<ii", data, 18)
    bits = struct.unpack_from("<H", data, 28)[0]
    if (width, height) != expected or bits != 24:
        raise AssertionError(
            f"{path.relative_to(ROOT)} is {width}x{height}/{bits}bpp, expected {expected[0]}x{expected[1]}/24bpp"
        )


def png(path: Path, expected: tuple[int, int]) -> None:
    data = path.read_bytes()
    if data[:8] != b"\x89PNG\r\n\x1a\n" or data[12:16] != b"IHDR":
        raise AssertionError(f"{path.relative_to(ROOT)} is not a PNG")
    width, height, bit_depth, color_type = struct.unpack_from(">IIBB", data, 16)
    if (width, height) != expected or bit_depth != 8 or color_type != 6:
        raise AssertionError(
            f"{path.relative_to(ROOT)} is {width}x{height}/{bit_depth}-bit type {color_type}, "
            f"expected {expected[0]}x{expected[1]}/8-bit RGBA"
        )


def main() -> None:
    nsi = NSI.read_text(encoding="utf-8")
    build = BUILD.read_text(encoding="utf-8")
    smoke = SMOKE.read_text(encoding="utf-8")
    gradle = GRADLE.read_text(encoding="utf-8")
    build_yml = BUILD_YML.read_text(encoding="utf-8")
    release_yml = RELEASE_YML.read_text(encoding="utf-8")

    require(nsi, [
        "RequestExecutionLevel user",
        "SilentInstall normal",
        "SilentUnInstall normal",
        'InstallDir "$LOCALAPPDATA\\Programs\\ikna"',
        'File /r "${APP_IMAGE}\\*.*"',
        'WriteUninstaller "$INSTDIR\\Uninstall.exe"',
        'QuietUninstallString" "$\\\"$INSTDIR\\Uninstall.exe$\\\" /S"',
        'IfFileExists "$INSTDIR\\${MARKER_FILE}"',
        'RMDir /r "$INSTDIR"',
        '${GetOptions} $0 "/PURGE=" $1',
        'RMDir /r "$APPDATA\\Ikna"',
        'CreateShortcut "$SMPROGRAMS\\ikna\\ikna.lnk"',
        'CreateShortcut "$DESKTOP\\ikna.lnk"',
        'Delete "$DESKTOP\\ikna.lnk"',
        'DeleteRegKey HKCU "${UNINSTALL_KEY}"',
        'MUI_WELCOMEFINISHPAGE_BITMAP',
        '!insertmacro MUI_LANGUAGE "PortugueseBR"',
    ], "ikna.nsi")
    if nsi.index('StrCpy $PurgeData 0') > nsi.index('StrCpy $PurgeData 1'):
        raise AssertionError("user-data deletion must be opt-in")
    if nsi.count("Function ") != nsi.count("FunctionEnd"):
        raise AssertionError("unbalanced NSIS functions")
    if len(re.findall(r'^Section(?:\s|$)', nsi, re.M)) != nsi.count("SectionEnd"):
        raise AssertionError("unbalanced NSIS sections")

    require(build, [
        "makensis.exe",
        '"/INPUTCHARSET"',
        '"UTF8"',
        "/DAPP_VERSION=$Version",
        "/DAPP_IMAGE=$AppImage",
        "/DOUTPUT_FILE=$Output",
        "desktop\\installer\\ikna.nsi",
    ], "build-installer.ps1")
    require(smoke, [
        'Start-Process -FilePath $Path -ArgumentList $Arguments -Wait -PassThru',
        '$process.ExitCode',
        'Run-Exe $Installer @("/S")',
        'Run-Exe $uninstaller @("/S")',
        'Run-Exe $uninstaller @("/S", "/PURGE=1")',
        '$desktopShortcut = Join-Path ([Environment]::GetFolderPath("Desktop")) "ikna.lnk"',
        'Desktop shortcut survived uninstall',
        'Desktop shortcut survived purge uninstall',
        'Ordinary uninstall deleted user data',
        'Windows uninstall registry key survived purge uninstall',
    ], "test-installer.ps1")
    if "$LASTEXITCODE" in smoke:
        raise AssertionError("GUI installer smoke test must use the waited Process.ExitCode")

    for forbidden in ["TargetFormat.Msi", "TargetFormat.Exe", "upgradeUuid"]:
        if forbidden in gradle:
            raise AssertionError(f"desktop/build.gradle.kts still contains {forbidden}")
    for name, workflow in [("build.yml", build_yml), ("release.yml", release_yml)]:
        require(workflow, [
            "tools/nsis/build-installer.ps1",
            "tools/nsis/test-installer.ps1",
            "choco install nsis",
        ], name)
        for forbidden in ["packageReleaseMsi", ".msi", "windows-msi"]:
            if forbidden in workflow:
                raise AssertionError(f"{name} still publishes MSI via {forbidden}")
    if "continue-on-error: true" in release_yml[release_yml.index("- name: Build NSIS installer"):release_yml.index("- name: Publish", release_yml.index("- name: Build NSIS installer"))]:
        raise AssertionError("release installer must be a required artifact")

    bmp(ROOT / "desktop/installer/sidebar.bmp", (164, 314))
    bmp(ROOT / "desktop/installer/header.bmp", (150, 57))
    png(ROOT / "docs/pixel-icon.png", (42, 49))
    print("NSIS contract OK: UTF-8 text, default Desktop shortcut, /S, safe upgrades, clean uninstall, opt-in purge, branded assets and no MSI")


if __name__ == "__main__":
    try:
        main()
    except (AssertionError, OSError, UnicodeError) as error:
        print(f"NSIS contract failed: {error}", file=sys.stderr)
        raise SystemExit(1)
