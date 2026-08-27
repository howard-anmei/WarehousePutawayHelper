```bat
@echo off
setlocal EnableExtensions

title WarehousePutawayHelper - Build & Install

cd /d "%~dp0"

echo.
echo ==========================================
echo   WarehousePutawayHelper APK Update
echo ==========================================
echo.
echo Project:
echo %CD%
echo.

REM ------------------------------------------
REM 1. Check Gradle wrapper
REM ------------------------------------------

if not exist "gradlew.bat" (
    echo [ERROR] gradlew.bat not found.
    echo Make sure this BAT is in the project root.
    echo.
    pause
    exit /b 1
)

REM ------------------------------------------
REM 2. Check ADB
REM ------------------------------------------

set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"

if not exist "%ADB%" (
    echo [ERROR] adb.exe not found:
    echo %ADB%
    echo.
    pause
    exit /b 1
)

echo [1/5] Checking Android device...
echo.

"%ADB%" devices

echo.

REM ------------------------------------------
REM 3. Check if at least one device exists
REM ------------------------------------------

"%ADB%" get-state >nul 2>&1

if errorlevel 1 (
    echo [ERROR] No Android device detected.
    echo.
    echo Please:
    echo   1. Connect the Urovo PDA
    echo   2. Enable USB debugging
    echo   3. Accept the USB debugging prompt
    echo.
    pause
    exit /b 1
)

echo Device detected.
echo.

REM ------------------------------------------
REM 4. Build APK
REM ------------------------------------------

echo [2/5] Building APK...
echo.

call gradlew.bat assembleDebug

if errorlevel 1 (
    echo.
    echo ==========================================
    echo   BUILD FAILED
    echo ==========================================
    echo.
    echo Check the Gradle/Kotlin error above.
    echo.
    pause
    exit /b 1
)

echo.
echo Build successful.
echo.

REM ------------------------------------------
REM 5. Check APK
REM ------------------------------------------

set "APK=app\build\outputs\apk\debug\app-debug.apk"

echo [3/5] Checking APK...

if not exist "%APK%" (
    echo [ERROR] APK not found:
    echo %CD%\%APK%
    echo.
    pause
    exit /b 1
)

echo APK:
echo %CD%\%APK%
echo.

for %%A in ("%APK%") do echo APK size: %%~zA bytes

echo.

REM ------------------------------------------
REM 6. Install APK
REM ------------------------------------------

echo [4/5] Installing APK...
echo.

"%ADB%" install -r "%APK%"

if errorlevel 1 (
    echo.
    echo ==========================================
    echo   INSTALL FAILED
    echo ==========================================
    echo.
    echo Possible causes:
    echo   - Device disconnected
    echo   - USB debugging unavailable
    echo   - Another installation is in progress
    echo   - APK signature conflict
    echo.
    pause
    exit /b 1
)

echo.
echo Installation successful.
echo.

REM ------------------------------------------
REM 7. Verify package
REM ------------------------------------------

echo [5/5] Verifying installed package...
echo.

"%ADB%" shell pm path com.anmei.warehouseputawayrecorder

if errorlevel 1 (
    echo.
    echo [WARNING] Package verification failed.
    echo.
    pause
    exit /b 1
)

echo.
echo ==========================================
echo   UPDATE SUCCESSFUL
echo ==========================================
echo.
echo Application:
echo WarehousePutawayHelper
echo.
echo Package:
echo com.anmei.warehouseputawayrecorder
echo.
echo APK:
echo %CD%\%APK%
echo.
echo The new APK has been installed on the device.
echo.

pause
```
