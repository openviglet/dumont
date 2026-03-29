@echo off
rem --------------------------------------------------------------------------
rem Run the Dumont Filesystem Connector (standalone CLI tool).
rem
rem Usage:  bin\dumont-filesystem.bat [options]
rem
rem Example:
rem   bin\dumont-filesystem.bat ^
rem     --source-dir C:\shared\documents ^
rem     --server http://localhost:30130 ^
rem     --api-key YOUR_API_KEY ^
rem     --site InternalDocs ^
rem     --locale en_US
rem --------------------------------------------------------------------------
setlocal enabledelayedexpansion

set SCRIPT_DIR=%~dp0
set BASE_DIR=%SCRIPT_DIR%..
set JAR=%BASE_DIR%\filesystem\dumont-filesystem.jar

rem Resolve Java executable
set JAVA_CMD=java
if exist "%SCRIPT_DIR%java-home.conf" (
    for /f "usebackq tokens=*" %%j in ("%SCRIPT_DIR%java-home.conf") do set "DUMONT_JAVA_HOME=%%j"
    if exist "!DUMONT_JAVA_HOME!\bin\java.exe" (
        set "JAVA_CMD=!DUMONT_JAVA_HOME!\bin\java.exe"
    )
)

if not exist "%JAR%" (
    echo Error: %JAR% not found.
    exit /b 1
)

"!JAVA_CMD!" -jar "%JAR%" %*

endlocal
