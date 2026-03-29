#!/usr/bin/env bash
# --------------------------------------------------------------------------
# Run the Dumont Filesystem Connector (standalone CLI tool).
#
# Usage:  ./bin/dumont-filesystem.sh [options]
#
# Example:
#   ./bin/dumont-filesystem.sh \
#     --source-dir /mnt/shared/documents \
#     --server http://localhost:30130 \
#     --api-key YOUR_API_KEY \
#     --site InternalDocs \
#     --locale en_US
# --------------------------------------------------------------------------
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BASE_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
JAR="$BASE_DIR/filesystem/dumont-filesystem.jar"

if [ ! -f "$JAR" ]; then
  echo "Error: $JAR not found." >&2
  exit 1
fi

exec java -jar "$JAR" "$@"
