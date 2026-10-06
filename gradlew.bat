@echo off
setlocal
rem Compose Hot Reload 1.1.1 calls this name from the project root.
rem Reuse the launcher's checked Gradle distribution; this is not a wrapper jar.
if not defined IKNA_HOT_RELOAD_GRADLE_EXE (
    echo Ikna Hot Reload: start dev-hot-reload.cmd first; Gradle bridge is not configured. 1>&2
    exit /b 2
)
if not exist "%IKNA_HOT_RELOAD_GRADLE_EXE%" (
    echo Ikna Hot Reload: configured Gradle launcher does not exist. Restart dev-hot-reload.cmd. 1>&2
    exit /b 2
)
rem Blocking Hot Reload adds --no-daemon to its child build. Gradle 8.10.2
rem continuous mode needs a daemon. The last mutually exclusive option wins.
call "%IKNA_HOT_RELOAD_GRADLE_EXE%" %* --daemon --watch-fs
set "IKNA_EXIT=%ERRORLEVEL%"
endlocal & exit /b %IKNA_EXIT%
