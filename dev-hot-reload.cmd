@echo off
setlocal
rem -NoExit is intentional: even a launcher/Gradle/PowerShell failure must not
rem make a double-clicked terminal disappear before the error can be read.
powershell.exe -NoProfile -NoExit -ExecutionPolicy Bypass -File "%~dp0tools\dev-hot-reload.ps1" %*
set "IKNA_EXIT=%ERRORLEVEL%"
if not "%IKNA_EXIT%"=="0" (
    echo.
    echo Ikna Hot Reload PowerShell exited with code %IKNA_EXIT%.
    echo This CMD window will stay open too. Press any key to close it.
    pause >nul
)
endlocal & exit /b %IKNA_EXIT%
