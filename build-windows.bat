@echo off
setlocal enabledelayedexpansion

rem ============================================================
rem  Windows server build script
rem  Output: MapleServerDesktop\build\install\MapleServerDesktop\
rem          bin\MapleServerDesktop.bat (runnable distribution)
rem  Core source in server-core\ is shared with the Android build,
rem  so a single edit feeds both the desktop dist and the APK.
rem ============================================================

set "ROOT=%~dp0"
set "DESKTOP=%ROOT%MapleServerDesktop"
if not exist "%DESKTOP%\gradlew.bat" (
    echo [ERROR] Desktop project not found: %DESKTOP%
    exit /b 1
)
cd /d "%DESKTOP%"

rem --- JDK 21 (desktop sourceCompatibility = 21) ---
set "JAVA_HOME=D:\AndroidDependency\.jdks\jdk-21.0.12.1+1"
if not exist "%JAVA_HOME%\bin\java.exe" (
    echo [ERROR] JDK 21 not found: %JAVA_HOME%
    exit /b 1
)
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo [INFO] JAVA_HOME = %JAVA_HOME%
echo [INFO] Building Windows server (installDist) ...
echo.

call gradlew.bat installDist %*
if errorlevel 1 (
    echo.
    echo [FAIL] Windows server build did not succeed.
    exit /b 1
)

echo.
echo [OK] Distribution generated: %DESKTOP%\build\install\MapleServerDesktop\
echo [RUN] Set the asset/data dirs before launching, for example:
echo     set "MAPLE_SERVER_DESKTOP_OPTS=-Dmaple.assets=%ROOT%app\src\main\assets -Dmaple.data=%DESKTOP%\data"
echo     "%DESKTOP%\build\install\MapleServerDesktop\bin\MapleServerDesktop.bat"
endlocal
