[CmdletBinding()]
param(
    [switch]$UseRealData
)

$ErrorActionPreference = "Stop"

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$GradleVersion = "8.10.2"
$GradleUrl = "https://services.gradle.org/distributions/gradle-$GradleVersion-bin.zip"

$LocalBase = if ($env:LOCALAPPDATA) {
    Join-Path $env:LOCALAPPDATA "Ikna\dev-hot-reload"
} else {
    Join-Path ([System.IO.Path]::GetTempPath()) "Ikna-dev-hot-reload"
}
$ToolsDir = Join-Path $LocalBase "tools"
$LogsDir = Join-Path $LocalBase "logs"
$DevHome = Join-Path $LocalBase "profile"
$GradleHome = Join-Path $ToolsDir "gradle-$GradleVersion"
$GradleExe = Join-Path $GradleHome "bin\gradle.bat"
. (Join-Path $PSScriptRoot "hot-reload-process.ps1")

$RequiredPaths = @(
    "settings.gradle.kts",
    "build.gradle.kts",
    "desktop\build.gradle.kts",
    "shared\build.gradle.kts",
    "gradlew.bat",
    "desktop\src\main\kotlin\dev\ikna\desktop\Main.kt"
)

function Ensure-Directories {
    New-Item -ItemType Directory -Force -Path $ToolsDir | Out-Null
    New-Item -ItemType Directory -Force -Path $LogsDir | Out-Null
    New-Item -ItemType Directory -Force -Path $DevHome | Out-Null
}

function Test-RepositoryReady {
    foreach ($relative in $RequiredPaths) {
        if (-not (Test-Path (Join-Path $RepoRoot $relative) -PathType Leaf)) {
            return $false
        }
    }
    return $true
}

function Wait-RepositoryReady {
    $announced = $false
    while (-not (Test-RepositoryReady)) {
        if (-not $announced) {
            Write-Host ""
            Write-Host "Ikna source tree is being replaced. Waiting for the new ZIP contents..." -ForegroundColor Yellow
            $announced = $true
        }
        Start-Sleep -Milliseconds 500
    }

    if ($announced) {
        # Give Explorer/archiver a short quiet window so one large ZIP replacement
        # is observed as one update instead of hundreds of half-written files.
        Start-Sleep -Milliseconds 900
        Write-Host "Source tree is ready again." -ForegroundColor Green
    }
}


function Ensure-JavaBootstrap {
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\java.exe") -PathType Leaf)) {
        return
    }

    $java = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($java) {
        return
    }

    $candidates = @()
    if ($env:ProgramFiles) {
        $candidates += (Join-Path $env:ProgramFiles "Android\Android Studio\jbr")
        $jetBrains = Join-Path $env:ProgramFiles "JetBrains"
        if (Test-Path $jetBrains -PathType Container) {
            $candidates += Get-ChildItem $jetBrains -Directory -ErrorAction SilentlyContinue |
                ForEach-Object { Join-Path $_.FullName "jbr" }
        }
    }

    foreach ($candidate in $candidates) {
        $javaExe = Join-Path $candidate "bin\java.exe"
        if (Test-Path $javaExe -PathType Leaf) {
            $env:JAVA_HOME = $candidate
            $env:Path = (Join-Path $candidate "bin") + ";" + $env:Path
            Write-Host "Using Java from $candidate" -ForegroundColor DarkGray
            return
        }
    }

    throw @"
Java is required once to start Gradle. Install JDK 17 (or Android Studio / IntelliJ, which include Java), then run dev-hot-reload.cmd again. Gradle and the JetBrains Runtime used by Hot Reload are downloaded automatically after that.
"@
}

function Ensure-Gradle {
    if (Test-Path $GradleExe -PathType Leaf) {
        return
    }

    Write-Host "First run: downloading Gradle $GradleVersion once..." -ForegroundColor Cyan
    $zip = Join-Path $ToolsDir "gradle-$GradleVersion-bin.zip"
    $shaFile = "$zip.sha256"
    $extractDir = Join-Path $ToolsDir "extract-$GradleVersion"

    Remove-Item $zip, $shaFile -Force -ErrorAction SilentlyContinue
    Remove-Item $extractDir -Recurse -Force -ErrorAction SilentlyContinue

    Invoke-WebRequest -UseBasicParsing -Uri $GradleUrl -OutFile $zip
    Invoke-WebRequest -UseBasicParsing -Uri "$GradleUrl.sha256" -OutFile $shaFile

    $expected = (Get-Content $shaFile -Raw).Trim().Split()[0].ToLowerInvariant()
    $actual = (Get-FileHash -Algorithm SHA256 $zip).Hash.ToLowerInvariant()
    if ($expected -ne $actual) {
        Remove-Item $zip -Force -ErrorAction SilentlyContinue
        throw "Gradle download checksum mismatch. Expected $expected, got $actual."
    }

    Expand-Archive -Path $zip -DestinationPath $extractDir -Force
    $expanded = Join-Path $extractDir "gradle-$GradleVersion"
    if (-not (Test-Path (Join-Path $expanded "bin\gradle.bat") -PathType Leaf)) {
        throw "Gradle archive did not contain the expected launcher."
    }

    if (Test-Path $GradleHome) {
        Remove-Item $GradleHome -Recurse -Force
    }
    Move-Item $expanded $GradleHome
    Remove-Item $extractDir -Recurse -Force -ErrorAction SilentlyContinue
    Remove-Item $zip, $shaFile -Force -ErrorAction SilentlyContinue
    Write-Host "Gradle $GradleVersion is ready." -ForegroundColor Green
}

function Prepare-DevelopmentProfile {
    if ($UseRealData) {
        Remove-Item Env:IKNA_HOME_OVERRIDE -ErrorAction SilentlyContinue
        Write-Host "Data: normal Ikna profile (--UseRealData)." -ForegroundColor Yellow
        return
    }

    $env:IKNA_HOME_OVERRIDE = $DevHome
    # The hot-reload profile is intentionally isolated and starts in Developer
    # Mode on its first run. After that the profile file belongs to the app's
    # own Developer Mode switch, so a "return to normal mode" choice made in
    # Settings survives relaunches instead of being overwritten here.
    $profileFile = Join-Path $DevHome "ikna-data-profile"
    if (Test-Path $profileFile) {
        Write-Host "Data: isolated profile ($DevHome), keeping the mode selected in the app." -ForegroundColor DarkGray
    } else {
        Set-Content -Path $profileFile -Value "DEVELOPER" -Encoding ascii
        Write-Host "Data: isolated Developer Sandbox ($DevHome)." -ForegroundColor DarkGray
    }
}

function New-LogPath {
    $stamp = Get-Date -Format "yyyy-MM-dd_HH-mm-ss"
    return Join-Path $LogsDir "hot-reload-$stamp.log"
}

Ensure-Directories
Wait-RepositoryReady
Ensure-JavaBootstrap
Ensure-Gradle
# Inherited by the app/devtools and their continuous recompiler process.
$env:IKNA_HOT_RELOAD_GRADLE_EXE = $GradleExe
$env:IKNA_PROFILE_RESTART_FILE = Join-Path $LogsDir ("profile-restart-" + [guid]::NewGuid().ToString("N") + ".request")
Prepare-DevelopmentProfile

Write-Host ""
Write-Host "Ikna Hot Reload" -ForegroundColor Cyan
Write-Host "Repository: $RepoRoot"
Write-Host "Change/save files or replace the repository contents with a new full ZIP." -ForegroundColor DarkGray
Write-Host "Keep this window open. Press Ctrl+C to stop." -ForegroundColor DarkGray
Write-Host ""

while ($true) {
    Wait-RepositoryReady
    Remove-Item $env:IKNA_PROFILE_RESTART_FILE -Force -ErrorAction SilentlyContinue
    $log = New-LogPath
    Set-Content -Path (Join-Path $LogsDir "latest.log.path") -Value $log -Encoding utf8
    @(
        "Ikna Hot Reload session: $(Get-Date -Format o)",
        "Repository: $RepoRoot",
        "Gradle: $GradleExe (8.10.2)",
        "Recompiler bridge: $(Join-Path $RepoRoot 'gradlew.bat')",
        "Profile: $(if ($UseRealData) { 'normal desktop data' } else { $DevHome })",
        "Compose Hot Reload: 1.1.1; auto mode; daemon/file watching enabled",
        "Starting :desktop:hotRun --auto; waiting for compiler/watcher output.",
        "An open app window alone does not prove reload. Look for a subsequent build and changed UI."
    ) | Tee-Object -FilePath $log

    Push-Location $RepoRoot
    try {
        Write-Host "Starting :desktop:hotRun --auto ..." -ForegroundColor Cyan
        Write-Host "Log: $log" -ForegroundColor DarkGray

        $exitCode = Invoke-IknaHotGradle -GradleExe $GradleExe -Log $log
    }
    finally {
        Pop-Location
    }

    Write-Host ""
    Write-Host "Hot Reload process stopped (exit $exitCode)." -ForegroundColor Yellow
    Add-Content -Path $log -Value "Hot Reload process stopped (exit $exitCode)."
    Write-Host "The log is still available at: $log" -ForegroundColor Yellow

    if (-not (Test-RepositoryReady)) {
        Wait-RepositoryReady
        Write-Host "Restarting automatically after repository replacement..." -ForegroundColor Cyan
        continue
    }

    if (Receive-IknaProfileRestart -Path $env:IKNA_PROFILE_RESTART_FILE) {
        Write-Host "Restarting automatically after a confirmed profile/scenario change..." -ForegroundColor Cyan
        continue
    }

    Write-Host "Press Enter to restart, or close this window / Ctrl+C to stop." -ForegroundColor DarkGray
    [void](Read-Host)
}
