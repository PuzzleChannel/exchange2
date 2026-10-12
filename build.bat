@echo off
setlocal
cd /d "%~dp0"
echo VCoin Exchange - Minecraft 26.2 / Fabric
echo Building with Gradle. JDK 25 is required.
echo.
call "%~dp0gradlew.bat" build --console=plain
if errorlevel 1 goto failed
echo.
echo BUILD SUCCESSFUL. Use the JAR in build\libs without -sources in its name.
pause
exit /b 0
:failed
echo.
echo BUILD FAILED. Review the error above.
echo Make sure JDK 25 is installed and JAVA_HOME points to that JDK.
pause
exit /b 1
