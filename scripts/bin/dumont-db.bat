@echo off
rem --------------------------------------------------------------------------
rem Run the Dumont Database Connector (standalone CLI tool).
rem
rem Usage:  bin\dumont-db.bat [options]
rem
rem Example:
rem   bin\dumont-db.bat ^
rem     --server http://localhost:30130 ^
rem     --api-key YOUR_API_KEY ^
rem     --driver org.mariadb.jdbc.Driver ^
rem     --connect "jdbc:mariadb://localhost:3306/mydb" ^
rem     --query "SELECT id, title, body FROM articles" ^
rem     --site MySite ^
rem     --locale en_US
rem --------------------------------------------------------------------------
setlocal enabledelayedexpansion

set SCRIPT_DIR=%~dp0
set BASE_DIR=%SCRIPT_DIR%..
set JAR=%BASE_DIR%\db\dumont-db.jar

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
