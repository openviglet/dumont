@ECHO OFF
SETLOCAL ENABLEDELAYEDEXPANSION

REM ============================================================================
REM  docker-local.cmd
REM  Builds and runs the Viglet Dumont ES Docker image locally from the root
REM  Dockerfile. The Dockerfile builds from THIS repository (the build context),
REM  so it reflects your local working tree — no GitHub clone, nothing external
REM  beyond pulling the JDK/JRE base images.
REM
REM  Usage:
REM    docker-local.cmd            Build the image and run it (detached)
REM    docker-local.cmd --no-run   Build the image only
REM
REM  Optional environment variables:
REM    IMAGE_NAME      Image repo/name   (default: openviglet/dumont-ce)
REM    DUMONT_PORT     Host port         (default: 30130)
REM    TURING_APIKEY   API key for the container (default: local-dev)
REM ============================================================================

SET "ROOT=%~dp0"
IF "%IMAGE_NAME%"=="" SET "IMAGE_NAME=openviglet/dumont-ce"
IF "%DUMONT_PORT%"=="" SET "DUMONT_PORT=30130"
IF "%TURING_APIKEY%"=="" SET "TURING_APIKEY=local-dev"
SET "TAG=local"
SET "CONTAINER=dumont-connector-local"

SET "NO_RUN=0"
IF /I "%~1"=="--no-run" SET "NO_RUN=1"

REM --- Cache bust = current commit SHA (for the image revision label) ---
SET "CACHE_BUST=local"
FOR /F "delims=" %%S IN ('git -C "%ROOT%." rev-parse HEAD 2^>NUL') DO SET "CACHE_BUST=%%S"

ECHO ========================================
ECHO [1/2] Building image %IMAGE_NAME%:%TAG% from local repo
ECHO ========================================
docker build --file "%ROOT%Dockerfile" --build-arg "CACHE_BUST=%CACHE_BUST%" --tag "%IMAGE_NAME%:%TAG%" "%ROOT%."
IF %ERRORLEVEL% NEQ 0 (
    ECHO ERROR: docker build failed.
    EXIT /B 1
)

IF "%NO_RUN%"=="1" (
    ECHO.
    ECHO Built %IMAGE_NAME%:%TAG% ^(--no-run, not starting^).
    EXIT /B 0
)

ECHO ========================================
ECHO [2/2] Starting container %CONTAINER% on port %DUMONT_PORT%
ECHO ========================================
docker rm -f "%CONTAINER%" >nul 2>&1
docker run -d --name "%CONTAINER%" ^
    -p "%DUMONT_PORT%:30130" ^
    -e "TURING_APIKEY=%TURING_APIKEY%" ^
    -v dumont-local-store:/app/store ^
    "%IMAGE_NAME%:%TAG%"
IF %ERRORLEVEL% NEQ 0 (
    ECHO ERROR: docker run failed.
    EXIT /B 1
)

ECHO.
ECHO ========================================
ECHO Running. Useful commands:
ECHO   docker logs -f %CONTAINER%
ECHO   docker stop %CONTAINER%
ECHO   App:  http://localhost:%DUMONT_PORT%
ECHO ========================================
ENDLOCAL
EXIT /B 0
