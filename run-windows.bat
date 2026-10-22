@echo off
setlocal enabledelayedexpansion

rem ============================================================
rem  Windows server run script
rem  Launches the distribution built by build-windows.bat.
rem  Assets (609MB) are referenced by path, not copied.
rem ============================================================

set "ROOT=%~dp0"
set "DESKTOP=%ROOT%MapleServerDesktop"
set "LAUNCHER=%DESKTOP%\build\install\MapleServerDesktop\bin\MapleServerDesktop.bat"

if not exist "%LAUNCHER%" (
    echo [ERROR] Distribution not found. Run build-windows.bat first.
    echo         expected: %LAUNCHER%
    exit /b 1
)

rem --- JDK 21 (must match the build) ---
set "JAVA_HOME=D:\AndroidDependency\.jdks\jdk-21.0.12.1+1"
if not exist "%JAVA_HOME%\bin\java.exe" (
    echo [ERROR] JDK 21 not found: %JAVA_HOME%
    exit /b 1
)
set "PATH=%JAVA_HOME%\bin;%PATH%"

rem --- Runtime paths: assets template + writable data dir ---
set "MAPLE_ASSETS=%ROOT%app\src\main\assets"
set "MAPLE_DATA=%DESKTOP%\data"
if not exist "%MAPLE_ASSETS%" (
    echo [ERROR] Assets dir not found: %MAPLE_ASSETS%
    exit /b 1
)
set "MAPLE_SERVER_DESKTOP_OPTS=-Dmaple.assets=%MAPLE_ASSETS% -Dmaple.data=%MAPLE_DATA%"

if not exist "%MAPLE_DATA%\files\config.yaml" (
    echo [WARN] %MAPLE_DATA%\files\config.yaml missing; it will be seeded from assets.
    echo [WARN] After first launch, set HOST/LANHOST to this PC's LAN IP so phones can connect.
)

echo [INFO] JAVA_HOME = %JAVA_HOME%
echo [INFO] assets    = %MAPLE_ASSETS%
echo [INFO] data      = %MAPLE_DATA%
echo [INFO] Starting Windows server (Ctrl+C to stop) ...
echo.

cd /d "%DESKTOP%"
call "%LAUNCHER%" %*
endlocal
