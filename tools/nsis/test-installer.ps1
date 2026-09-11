[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$Installer
)

$ErrorActionPreference = "Stop"
$Installer = (Resolve-Path $Installer).Path
$installDir = Join-Path $env:LOCALAPPDATA "Programs\ikna"
$dataDir = Join-Path $env:APPDATA "Ikna"
$shortcutDir = Join-Path ([Environment]::GetFolderPath("Programs")) "ikna"
$shortcut = Join-Path $shortcutDir "ikna.lnk"
$uninstallKey = "HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\ikna"

function Wait-Removed([string]$Path) {
    for ($attempt = 0; $attempt -lt 40 -and (Test-Path $Path); $attempt++) {
        Start-Sleep -Milliseconds 250
    }
    if (Test-Path $Path) {
        throw "Path was not removed: $Path"
    }
}

function Run-Exe([string]$Path, [string[]]$Arguments) {
    & $Path @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$Path $($Arguments -join ' ') failed with exit code $LASTEXITCODE"
    }
}

# GitHub runners are fresh, but make the test deterministic when run repeatedly
# on a developer machine. Only paths dedicated to this test installation are
# touched here.
if (Test-Path (Join-Path $installDir "Uninstall.exe")) {
    Run-Exe (Join-Path $installDir "Uninstall.exe") @("/S", "/PURGE=1")
    Wait-Removed $installDir
}
Remove-Item -Recurse -Force $dataDir -ErrorAction SilentlyContinue
Remove-Item -Recurse -Force $shortcutDir -ErrorAction SilentlyContinue
Remove-Item -Recurse -Force $uninstallKey -ErrorAction SilentlyContinue

# First cycle: /S installs with no UI. Ordinary silent removal must clean every
# installer-owned artifact while preserving explicit user data.
Run-Exe $Installer @("/S")
$exe = Join-Path $installDir "Ikna.exe"
$uninstaller = Join-Path $installDir "Uninstall.exe"
foreach ($required in @($exe, $uninstaller, (Join-Path $installDir ".ikna-install-root"), $shortcut, $uninstallKey)) {
    if (!(Test-Path $required)) { throw "Silent install did not create $required" }
}
New-Item -ItemType Directory -Force -Path $dataDir | Out-Null
$userData = Join-Path $dataDir "nsis-preserve-check.txt"
Set-Content -Encoding UTF8 -Path $userData -Value "user data"
Run-Exe $uninstaller @("/S")
Wait-Removed $installDir
if (Test-Path $shortcut) { throw "Start-menu shortcut survived uninstall" }
if (Test-Path $uninstallKey) { throw "Windows uninstall registry key survived uninstall" }
if (!(Test-Path $userData)) { throw "Ordinary uninstall deleted user data" }

# Second cycle: /PURGE=1 is the explicit full-removal path. It must leave no
# application directory, shortcut, registry entry or ikna data directory.
Run-Exe $Installer @("/S")
$uninstaller = Join-Path $installDir "Uninstall.exe"
Run-Exe $uninstaller @("/S", "/PURGE=1")
Wait-Removed $installDir
Wait-Removed $dataDir
if (Test-Path $shortcut) { throw "Start-menu shortcut survived purge uninstall" }
if (Test-Path $uninstallKey) { throw "Windows uninstall registry key survived purge uninstall" }

Write-Host "PASS: NSIS /S install, preserving uninstall and /PURGE=1 clean uninstall"
