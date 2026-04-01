@echo off
rem --------------------------------------------------------------------------
rem Run the Dumont Connector with the Database plugin.
rem
rem Usage:  bin\dumont-db.bat [spring-boot options]
rem --------------------------------------------------------------------------
setlocal enabledelayedexpansion

set SCRIPT_DIR=%~dp0
set BASE_DIR=%SCRIPT_DIR%..
set JAR=%BASE_DIR%\connector\dumont-connector.jar
set LIBS_DIR=%BASE_DIR%\connector\libs\db
set PROPS=%BASE_DIR%\connector\dumont-connector.properties

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

set SPRING_CONFIG=
if exist "%PROPS%" set SPRING_CONFIG=--spring.config.additional-location=file:%PROPS%

"!JAVA_CMD!" -Xmx512m -Xms512m -Dloader.path="%LIBS_DIR%" -jar "%JAR%" %SPRING_CONFIG% %*

endlocal
