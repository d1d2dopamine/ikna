# Kept separate so native output/exit handling can be tested without a build.
function Invoke-IknaHotGradle {
    param(
        [Parameter(Mandatory = $true)][string]$GradleExe,
        [Parameter(Mandatory = $true)][string]$Log
    )

    # Merge native stderr inside cmd.exe. Windows PowerShell 5.1 otherwise
    # turns native stderr into ErrorRecords under ErrorActionPreference=Stop.
    # /s strips the outer pair of quotes; the inner pair protects spaced paths.
    $command = '""{0}" --daemon --watch-fs --console=plain -Pcompose.reload.logStdout=true -Pcompose.reload.logLevel=Debug :desktop:hotRun --auto 2>&1"' -f $GradleExe
    & $env:ComSpec /d /s /c $command |
        Tee-Object -FilePath $Log -Append | Out-Host
    return $LASTEXITCODE
}

# One-shot request: a normal close must still offer Enter, not a restart loop.
function Receive-IknaProfileRestart {
    param([Parameter(Mandatory = $true)][string]$Path)
    if (-not (Test-Path $Path -PathType Leaf)) { return $false }
    $value = (Get-Content $Path -Raw).Trim()
    Remove-Item $Path -Force
    return $value -eq "RESTART"
}
