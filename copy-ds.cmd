@echo off
setlocal

rem D43 -- the destination is derived from this script's own directory, never spelled out.
rem The hardcoded path this replaced was d:\Git\viglet\dumont\dumont-react\..., missing the
rem version directory, so every copy landed in a tree nobody reads while the script said
rem "Done." %~dp0 cannot drift from the checkout it lives in, so 2026.4 costs no edit.
set DS_SRC=d:\Git\viglet\viglet-design-system
set CONSOLE=%~dp0dumont-react
set NODE_MODULES=%CONSOLE%\node_modules
set DS_DEST=%NODE_MODULES%\@viglet\viglet-design-system

if not exist "%DS_SRC%\package.json" (
    echo FAILED: no design system at %DS_SRC%
    exit /b 1
)

rem The signal the old script suppressed. xcopy /i creates whatever it is pointed at, so a
rem copy into a console that was never installed reported success; if node_modules is
rem absent, npm has not run here and the copy would be the only thing in it.
if not exist "%NODE_MODULES%" (
    echo FAILED: %NODE_MODULES% does not exist -- run "pnpm install" at the repo root first.
    echo Copying into a path that has to be created is how a stale dist goes unnoticed.
    exit /b 1
)

echo Building viglet-design-system...
pushd "%DS_SRC%"
call npm run build
if errorlevel 1 (
    echo BUILD FAILED
    popd
    exit /b 1
)
popd

echo Copying to %DS_DEST% ...
rem This workspace is pnpm, so DS_DEST is normally a *symlink* into node_modules/.pnpm and not a
rem directory. Plain rmdir removes a link whatever its target holds; `rmdir /s /q` is the fallback
rem for a real directory a previous run of this script left behind. Doing it the other way round
rem would risk emptying the store copy every other package in the workspace shares.
if exist "%DS_DEST%" (
    rmdir "%DS_DEST%" 2>nul || rmdir /s /q "%DS_DEST%"
)
mkdir "%DS_DEST%"
if errorlevel 1 (
    echo FAILED: could not create %DS_DEST%
    exit /b 1
)

rem No /i: the destination is created above, so /i can only mask a wrong path.
xcopy "%DS_SRC%\dist" "%DS_DEST%\dist\" /e /q
if errorlevel 1 (
    echo FAILED: xcopy of dist did not complete
    exit /b 1
)
copy "%DS_SRC%\package.json" "%DS_DEST%\package.json" >nul
if errorlevel 1 (
    echo FAILED: could not copy package.json
    exit /b 1
)

rem A copy that produced no entry point is a copy that did not happen.
if not exist "%DS_DEST%\dist\exports.json" (
    echo FAILED: %DS_DEST%\dist\exports.json is missing after the copy
    exit /b 1
)

echo Done -- %DS_DEST%
