[CmdletBinding()]
param(
    [string]$AppImage = "",
    [string]$Version = "",
    [string]$Output = ""
)

$ErrorActionPreference = "Stop"
$repo = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$nsi = Join-Path $repo "desktop\installer\ikna.nsi"

if ([string]::IsNullOrWhiteSpace($Version)) {
    $gradle = Get-Content -Raw (Join-Path $repo "desktop\build.gradle.kts")
    $match = [regex]::Match($gradle, 'packageVersion\s*=\s*"([0-9]+\.[0-9]+\.[0-9]+)"')
    if (!$match.Success) {
        throw "Cannot read packageVersion from desktop/build.gradle.kts"
    }
    $Version = $match.Groups[1].Value
}
if ($Version -notmatch '^[0-9]+\.[0-9]+\.[0-9]+$') {
    throw "NSIS version must be numeric MAJOR.MINOR.PATCH, got '$Version'"
}

if ([string]::IsNullOrWhiteSpace($AppImage)) {
    $AppImage = Join-Path $repo "desktop\build\compose\binaries\main-release\app\Ikna"
} elseif (![IO.Path]::IsPathRooted($AppImage)) {
    $AppImage = Join-Path $repo $AppImage
}
$AppImage = (Resolve-Path $AppImage).Path.TrimEnd('\')
if (!(Test-Path (Join-Path $AppImage "Ikna.exe") -PathType Leaf)) {
    throw "The application image has no Ikna.exe: $AppImage"
}

if ([string]::IsNullOrWhiteSpace($Output)) {
    $Output = Join-Path $repo "desktop\build\compose\binaries\main-release\nsis\ikna-$Version-windows-x64-setup.exe"
} elseif (![IO.Path]::IsPathRooted($Output)) {
    $Output = Join-Path $repo $Output
}
$Output = [IO.Path]::GetFullPath($Output)
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $Output) | Out-Null

$command = Get-Command makensis.exe -ErrorAction SilentlyContinue
if ($command) {
    $makensis = $command.Source
} else {
    $candidate = Join-Path ${env:ProgramFiles(x86)} "NSIS\makensis.exe"
    if (!(Test-Path $candidate -PathType Leaf)) {
        throw "makensis.exe was not found. Install NSIS 3 (for example: choco install nsis)."
    }
    $makensis = $candidate
}

$quadVersion = "$Version.0"
$arguments = @(
    "/V4",
    "/INPUTCHARSET",
    "UTF8",
    "/DAPP_VERSION=$Version",
    "/DAPP_VERSION_QUAD=$quadVersion",
    "/DAPP_IMAGE=$AppImage",
    "/DOUTPUT_FILE=$Output",
    $nsi
)

Write-Host "NSIS: $makensis"
Write-Host "Application image: $AppImage"
Write-Host "Installer: $Output"
& $makensis @arguments
if ($LASTEXITCODE -ne 0) {
    throw "makensis failed with exit code $LASTEXITCODE"
}
if (!(Test-Path $Output -PathType Leaf)) {
    throw "makensis returned success but did not create $Output"
}

$item = Get-Item $Output
Write-Host ("Built {0} ({1:N0} bytes)" -f $item.FullName, $item.Length)
