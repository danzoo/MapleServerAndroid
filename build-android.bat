@echo off
setlocal enabledelayedexpansion

rem ============================================================
rem  Android server build script
rem  Output: app\build\outputs\apk\debug\app-debug.apk
rem  Core source in server-core\ is shared with the Windows build,
rem  so a single edit feeds both the APK and the desktop dist.
rem ============================================================

set "ROOT=%~dp0"
cd /d "%ROOT%"

rem --- JDK 21 (app compileOptions/kotlinOptions target Java 21) ---
set "JAVA_HOME=D:\AndroidDependency\.jdks\jdk-21.0.12.1+1"
if not exist "%JAVA_HOME%\bin\java.exe" (
    echo [ERROR] JDK 21 not found: %JAVA_HOME%
    exit /b 1
)
set "PATH=%JAVA_HOME%\bin;%PATH%"

rem --- Android SDK ---
if not defined ANDROID_HOME set "ANDROID_HOME=D:\AndroidDependency\Sdk"
if not defined ANDROID_SDK_ROOT set "ANDROID_SDK_ROOT=%ANDROID_HOME%"
if not exist "%ANDROID_HOME%" (
    echo [ERROR] Android SDK not found: %ANDROID_HOME%
    exit /b 1
)

echo [INFO] JAVA_HOME    = %JAVA_HOME%
echo [INFO] ANDROID_HOME = %ANDROID_HOME%
echo [INFO] Building Android server (:app:assembleDebug) ...
echo.

call gradlew.bat :app:assembleDebug %*
if errorlevel 1 (
    echo.
    echo [FAIL] Android server build did not succeed.
    exit /b 1
)

echo.
echo [OK] APK generated: %ROOT%app\build\outputs\apk\debug\app-debug.apk
endlocal
