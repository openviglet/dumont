@ECHO OFF
SETLOCAL

SET ROOT=%~dp0

ECHO ========================================
ECHO [2/4] Building connector (skip tests)...
ECHO ========================================
cd /d "%ROOT%connector"
call mvn clean install "-DskipTests"
IF %ERRORLEVEL% NEQ 0 (
    ECHO ERROR: connector build failed.
    EXIT /B 1
)

ECHO ========================================
ECHO [3/4] Building aem (skip tests)...
ECHO ========================================
cd /d "%ROOT%aem"
call mvn clean install "-DskipTests"
IF %ERRORLEVEL% NEQ 0 (
    ECHO ERROR: aem build failed.
    EXIT /B 1
)

ECHO ========================================
ECHO [4/4] Running aem-plugin-sample...
ECHO ========================================
cd /d "%ROOT%aem\aem-plugin-sample"
call compile-and-run.cmd
