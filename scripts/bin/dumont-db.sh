#!/usr/bin/env bash
# --------------------------------------------------------------------------
# Run the Dumont Connector with the Database plugin.
#
# Usage:  ./bin/dumont-db.sh [spring-boot options]
# --------------------------------------------------------------------------
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BASE_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
JAR="$BASE_DIR/connector/dumont-connector.jar"
LIBS_DIR="$BASE_DIR/connector/libs/db"
PROPS="$BASE_DIR/connector/dumont-connector.properties"

if [ ! -f "$JAR" ]; then
  echo "Error: $JAR not found." >&2
  exit 1
fi

SPRING_CONFIG=""
if [ -f "$PROPS" ]; then
  SPRING_CONFIG="--spring.config.additional-location=file:$PROPS"
fi

exec java -Xmx512m -Xms512m \
  -Dloader.path="$LIBS_DIR" \
  -jar "$JAR" \
  $SPRING_CONFIG \
  "$@"
