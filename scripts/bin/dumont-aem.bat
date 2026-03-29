@echo off
rem --------------------------------------------------------------------------
rem Start Dumont Connector with the AEM plugin.
rem
rem Usage:  bin\dumont-aem.bat [JVM options...]
rem
rem Examples:
rem   bin\dumont-aem.bat
rem   bin\dumont-aem.bat -Xmx1g
rem --------------------------------------------------------------------------
setlocal enabledelayedexpansion

set SCRIPT_DIR=%~dp0
set BASE_DIR=%SCRIPT_DIR%..
set CONNECTOR_DIR=%BASE_DIR%\connector
set JAR=%CONNECTOR_DIR%\dumont-connector.jar
set LIBS_DIR=%CONNECTOR_DIR%\libs\aem
set PROPS=%CONNECTOR_DIR%\dumont-connector.properties

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

if not exist "%LIBS_DIR%" (
    echo Error: AEM plugin directory not found: %LIBS_DIR%
    exit /b 1
)

set SPRING_CONFIG=
if exist "%PROPS%" (
    set "SPRING_CONFIG=--spring.config.additional-location=file:%PROPS%"
)

echo Starting Dumont Connector (AEM)...
echo   Java:   !JAVA_CMD!
echo   JAR:    %JAR%
echo   Plugin: %LIBS_DIR%
if exist "%PROPS%" echo   Config: %PROPS%
echo.

set "DATA_DIR=%LOCALAPPDATA%\Viglet\Dumont\aem"
if not exist "!DATA_DIR!" mkdir "!DATA_DIR!"
cd /d "!DATA_DIR!"
"!JAVA_CMD!" -Xmx512m -Xms512m %* -Dloader.path="%LIBS_DIR%" -Dspring.output.ansi.enabled=never -jar "%JAR%" "!SPRING_CONFIG!"

endlocal
