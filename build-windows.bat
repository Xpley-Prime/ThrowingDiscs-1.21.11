@echo off
setlocal
where gradle >nul 2>nul
if errorlevel 1 (
 echo Gradle not found. Install Gradle 9.2.1 or newer, then add it to PATH.
 pause
 exit /b 1
)
gradle build --no-daemon
if errorlevel 1 (
 echo Build failed. Check the compilation errors above.
 pause
 exit /b 1
)
echo Done! File: build\libs\throwingdiscs-1.0.0.jar
pause
