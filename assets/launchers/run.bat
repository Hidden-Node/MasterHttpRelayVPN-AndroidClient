@echo off
REM mhrv-rs launcher for Windows.
REM Runs the CLI once to initialize the MITM CA (may trigger a UAC prompt when
REM installing into the Windows trust store), then launches the UI.

setlocal
cd /d "%~dp0"

if not exist "mhrv-rs.exe" (
    echo error: mhrv-rs.exe not found next to this script.
    pause
    exit /b 1
)

echo Initializing MITM CA (a UAC prompt may appear)...
mhrv-rs.exe --install-cert
if errorlevel 1 (
    echo warning: CA install returned non-zero. The UI can still run,
    echo but HTTPS sites may show certificate warnings until the CA is trusted.
)

if exist "mhrv-rs-ui.exe" (
    echo Starting mhrv-rs UI...
    start "" "mhrv-rs-ui.exe"
) else (
    echo UI binary not found. Running CLI proxy instead.
    mhrv-rs.exe
)

endlocal
