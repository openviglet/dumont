#!/usr/bin/env bash
# --------------------------------------------------------------------------
# package-dist.sh
# Builds Dumont from source and creates distribution packages:
#   - dumont-install.zip   (portable, works on Linux and Windows)
#   - Dumont-DEP.AppImage  (Linux self-contained executable)
#   - dumont-install.exe   (Windows installer, if makensis is available)
#
# Usage:  ./scripts/package-dist.sh [--skip-build]
# --------------------------------------------------------------------------
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

SKIP_BUILD=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-build) SKIP_BUILD=true; shift ;;
    *)            echo "Unknown option: $1"; exit 1 ;;
  esac
done

cd "$PROJECT_ROOT"

# --------------- Build ---------------
if [ "$SKIP_BUILD" = false ]; then
  echo "==> Building Dumont..."
  ./mvnw clean install -DskipTests -Dgpg.skip=true -Dturing.open-browser=false
fi

# =====================================================================
#  Shared: copy artifacts into a staging area
# =====================================================================
echo "==> Staging artifacts..."

DIST_NAME="dumont-install"
STAGE="${PROJECT_ROOT}/target/dist-stage/${DIST_NAME}"
rm -rf "$STAGE"
mkdir -p "$STAGE/connector/libs/aem"
mkdir -p "$STAGE/connector/libs/webcrawler"
mkdir -p "$STAGE/connector/export"
mkdir -p "$STAGE/db"
mkdir -p "$STAGE/filesystem"
mkdir -p "$STAGE/bin"

# Connector engine
cp "connector/connector-app/target/dumont-connector.jar" "$STAGE/connector/"

# Connector plugins
cp "aem/aem-plugin/target/aem-plugin.jar"               "$STAGE/connector/libs/aem/"
cp "web-crawler/wc-plugin/target/web-crawler-plugin.jar" "$STAGE/connector/libs/webcrawler/"

# AEM Sample plugin + export files
cp "aem/aem-plugin-sample/target/aem-plugin-sample.jar"   "$STAGE/connector/libs/aem/"
cp "aem/aem-plugin-sample/scripts/wknd/export/wknd.json"  "$STAGE/connector/export/"
cp "aem/aem-plugin-sample/scripts/wknd/export/wknd2.json" "$STAGE/connector/export/"

# Standalone CLI tools
cp "db/db-app/target/dumont-db.jar"                      "$STAGE/db/"
cp "filesystem/fs-connector/target/dumont-filesystem.jar" "$STAGE/filesystem/"

# Scripts
cp "$SCRIPT_DIR/bin/dumont-aem.sh"          "$STAGE/bin/"
cp "$SCRIPT_DIR/bin/dumont-aem.bat"         "$STAGE/bin/"
cp "$SCRIPT_DIR/bin/dumont-webcrawler.sh"   "$STAGE/bin/"
cp "$SCRIPT_DIR/bin/dumont-webcrawler.bat"  "$STAGE/bin/"
cp "$SCRIPT_DIR/bin/dumont-db.sh"           "$STAGE/bin/"
cp "$SCRIPT_DIR/bin/dumont-db.bat"          "$STAGE/bin/"
cp "$SCRIPT_DIR/bin/dumont-filesystem.sh"   "$STAGE/bin/"
cp "$SCRIPT_DIR/bin/dumont-filesystem.bat"  "$STAGE/bin/"
chmod +x "$STAGE/bin/"*.sh 2>/dev/null || true

# Config and docs
cp "$SCRIPT_DIR/config/dumont-connector.properties" "$STAGE/connector/"
cp "$SCRIPT_DIR/config/README.txt" "$STAGE/"

# =====================================================================
#  1. Zip
# =====================================================================
ZIP_OUTPUT="${PROJECT_ROOT}/target/${DIST_NAME}.zip"
echo "==> Creating zip: $ZIP_OUTPUT"
mkdir -p "$(dirname "$ZIP_OUTPUT")"
(cd "$(dirname "$STAGE")" && zip -r "$ZIP_OUTPUT" "$(basename "$STAGE")")
echo "    $(du -h "$ZIP_OUTPUT" | cut -f1)"

# =====================================================================
#  2. AppImage
# =====================================================================
echo "==> Preparing AppImage..."
APPDIR="${PROJECT_ROOT}/target/Dumont-DEP.AppDir"
rm -rf "$APPDIR"
mkdir -p "$APPDIR/usr/lib/dumont"

# Copy staged artifacts into AppDir
cp -r "$STAGE/connector" "$APPDIR/usr/lib/dumont/"
cp -r "$STAGE/db"        "$APPDIR/usr/lib/dumont/"
cp -r "$STAGE/filesystem" "$APPDIR/usr/lib/dumont/"

# AppRun entry point
cp "$SCRIPT_DIR/installer/appimage/AppRun" "$APPDIR/AppRun"
chmod +x "$APPDIR/AppRun"

# Desktop file and icon
cp "$SCRIPT_DIR/installer/appimage/dumont.desktop" "$APPDIR/dumont.desktop"
cp "$SCRIPT_DIR/installer/appimage/dumont.png" "$APPDIR/dumont.png"

# Get appimagetool
ARCH="$(uname -m)"
APPIMAGETOOL="${PROJECT_ROOT}/target/appimagetool"
if [ ! -f "$APPIMAGETOOL" ]; then
  echo "    Downloading appimagetool..."
  TOOL_URL="https://github.com/AppImage/appimagetool/releases/download/continuous/appimagetool-${ARCH}.AppImage"
  curl -fSL -o "$APPIMAGETOOL" "$TOOL_URL"
  chmod +x "$APPIMAGETOOL"
fi

APPIMAGE_OUTPUT="${PROJECT_ROOT}/target/Dumont-DEP-${ARCH}.AppImage"
ARCH="$ARCH" "$APPIMAGETOOL" "$APPDIR" "$APPIMAGE_OUTPUT"
chmod +x "$APPIMAGE_OUTPUT"
echo "    $(du -h "$APPIMAGE_OUTPUT" | cut -f1)"

# =====================================================================
#  3. Windows Installer (NSIS) — only if makensis is available
# =====================================================================
if command -v makensis &>/dev/null; then
  EXE_OUTPUT="${PROJECT_ROOT}/target/${DIST_NAME}.exe"
  echo "==> Creating Windows installer: $EXE_OUTPUT"
  makensis -V2 -DOUTDIR="../../target" -DSTAGE="$STAGE" "$SCRIPT_DIR/installer/dumont.nsi"
  echo "    $(du -h "$EXE_OUTPUT" | cut -f1)"
else
  echo "==> Skipping Windows installer (makensis not found)"
fi

# =====================================================================
echo ""
echo "==> All done. Outputs in target/:"
ls -lh "${PROJECT_ROOT}/target/${DIST_NAME}".{zip,exe} "${PROJECT_ROOT}/target/"Dumont-DEP-*.AppImage 2>/dev/null || true
