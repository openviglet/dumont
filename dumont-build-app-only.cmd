@ECHO OFF
SETLOCAL

SET ROOT=%~dp0

ECHO ========================================
ECHO Building commons + connector (app only)
ECHO ========================================
cd /d "%ROOT%"
call mvn clean install -pl commons,connector/connector-commons,connector/connector-app -am "-DskipTests" "-Dskip.npm"
IF %ERRORLEVEL% NEQ 0 (
    ECHO ERROR: build failed.
    EXIT /B 1
)
