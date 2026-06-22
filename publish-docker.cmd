@echo off
setlocal enabledelayedexpansion

REM ============================================================================
REM publish-docker.cmd — Build the Viglet Dumont ES Docker image LOCALLY and
REM push it to the GitHub Container Registry (ghcr.io/openviglet/dumont-ce).
REM
REM Unlike .github/workflows/publish-docker.yml (which runs Maven INSIDE the
REM container), this builds the jars on the HOST using your authenticated ~/.m2
REM settings, then packages them with Dockerfile.local. Why: viglet-core is a
REM PRIVATE GitHub Packages dependency — an in-container Maven build has no
REM credentials and fails with 401. Building on the host also guarantees the
REM image contains your local working-tree changes, not the last pushed commit.
REM
REM (Sibling docker-local.cmd builds + RUNS locally; this one builds + PUSHES.)
REM
REM Auth (push to ghcr.io): set a GitHub PAT with `write:packages` scope:
REM   set CR_PAT=ghp_xxxxxxxxxxxxxxxxxxxx
REM   set CR_USER=your-github-username        (defaults to "openviglet")
REM If CR_PAT is unset, the script assumes you are already `docker login`-ed.
REM
REM Usage:
REM   publish-docker.cmd            Build the jars on the host, then image + push.
REM   publish-docker.cmd --nobuild  Skip the Maven build; reuse the existing jars.
REM ============================================================================

set "REGISTRY=ghcr.io"
set "IMAGE_NAME=openviglet/dumont-ce"
set "IMAGE=%REGISTRY%/%IMAGE_NAME%"
set "JAR=connector\connector-app\target\dumont-connector.jar"

set "SKIP_BUILD=0"
if /i "%~1"=="--nobuild" set "SKIP_BUILD=1"
if /i "%~1"=="-n"        set "SKIP_BUILD=1"

REM --- Resolve project version from the root pom.xml -------------------------
for /f "delims=" %%v in ('powershell -NoProfile -Command "(Select-Xml -Path pom.xml -XPath '/*[local-name()=\"project\"]/*[local-name()=\"version\"]').Node.InnerText"') do set "VERSION=%%v"
if "%VERSION%"=="" (
    echo [ERROR] Could not resolve project version from pom.xml.
    exit /b 1
)

REM --- Resolve current commit SHA for the image revision label ---------------
for /f "delims=" %%s in ('git rev-parse HEAD') do set "GIT_SHA=%%s"
set "SHA_SHORT=%GIT_SHA:~0,12%"

echo ============================================================
echo  Image   : %IMAGE%
echo  Version : %VERSION%
echo  Commit  : %GIT_SHA%
echo  Build   : %SKIP_BUILD%==0 ^(host mvn build^) / 1 ^(reuse jars^)
echo ============================================================

REM --- Build the jars on the host (authenticated ~/.m2) ----------------------
REM Same module exclusions as the root Dockerfile: skip the *-sample plugins.
if "%SKIP_BUILD%"=="0" (
    echo [INFO] Building Dumont jars on the host ...
    call mvnw.cmd clean package -DskipTests -pl "!aem/aem-plugin-sample,!assets/assets-plugin-sample,!db/db-plugin-sample,!web-crawler/wc-plugin-sample"
    if errorlevel 1 (
        echo [ERROR] Maven build failed.
        exit /b 1
    )
) else (
    echo [INFO] --nobuild: reusing existing jars.
)

if not exist "%JAR%" (
    echo [ERROR] %JAR% not found. Run without --nobuild, or build it first.
    exit /b 1
)

REM --- Log in to ghcr.io if a PAT is provided --------------------------------
if not "%CR_PAT%"=="" (
    if "%CR_USER%"=="" set "CR_USER=openviglet"
    echo [INFO] Logging in to %REGISTRY% as !CR_USER! ...
    echo !CR_PAT! | docker login %REGISTRY% -u !CR_USER! --password-stdin
    if errorlevel 1 (
        echo [ERROR] docker login failed.
        exit /b 1
    )
) else (
    echo [INFO] CR_PAT not set — assuming an existing docker login to %REGISTRY%.
)

REM --- Build the runtime image (multi-tag: latest, version, sha) and push ----
docker buildx build ^
    --file Dockerfile.local ^
    --tag %IMAGE%:latest ^
    --tag %IMAGE%:%VERSION% ^
    --tag %IMAGE%:sha-%SHA_SHORT% ^
    --build-arg CACHE_BUST=%GIT_SHA% ^
    --push ^
    .

if errorlevel 1 (
    echo [ERROR] Docker build/push failed.
    exit /b 1
)

echo.
echo [OK] Pushed:
echo      %IMAGE%:latest
echo      %IMAGE%:%VERSION%
echo      %IMAGE%:sha-%SHA_SHORT%
endlocal
