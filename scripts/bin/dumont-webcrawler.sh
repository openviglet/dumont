#!/usr/bin/env bash
# --------------------------------------------------------------------------
# Start Dumont Connector with the Web Crawler plugin.
#
# Usage:  ./bin/dumont-webcrawler.sh [JVM options...] [-- Spring options...]
#
# Examples:
#   ./bin/dumont-webcrawler.sh
#   ./bin/dumont-webcrawler.sh -Xmx1g
#   ./bin/dumont-webcrawler.sh -Xmx1g -- --server.port=30131
# --------------------------------------------------------------------------
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BASE_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

CONNECTOR_DIR="$BASE_DIR/connector"
JAR="$CONNECTOR_DIR/dumont-connector.jar"
LIBS_DIR="$CONNECTOR_DIR/libs/webcrawler"
PROPS="$CONNECTOR_DIR/dumont-connector.properties"

if [ ! -f "$JAR" ]; then
  echo "Error: $JAR not found." >&2
  exit 1
fi

if [ ! -d "$LIBS_DIR" ]; then
  echo "Error: Web Crawler plugin directory not found: $LIBS_DIR" >&2
  exit 1
fi

# Separate JVM args from Spring args (split on --)
JVM_ARGS=()
SPRING_ARGS=()
AFTER_SEPARATOR=false
for arg in "$@"; do
  if [ "$arg" = "--" ]; then
    AFTER_SEPARATOR=true
    continue
  fi
  if [ "$AFTER_SEPARATOR" = true ]; then
    SPRING_ARGS+=("$arg")
  else
    JVM_ARGS+=("$arg")
  fi
done

# Build the command
CMD=(java)
CMD+=(-Xmx512m -Xms512m)
CMD+=("${JVM_ARGS[@]+"${JVM_ARGS[@]}"}")
CMD+=(-Dloader.path="$LIBS_DIR")
CMD+=(-jar "$JAR")

if [ -f "$PROPS" ]; then
  CMD+=(--spring.config.additional-location="file:$PROPS")
fi

CMD+=("${SPRING_ARGS[@]+"${SPRING_ARGS[@]}"}")

echo "Starting Dumont Connector (Web Crawler)..."
echo "  JAR:    $JAR"
echo "  Plugin: $LIBS_DIR"
[ -f "$PROPS" ] && echo "  Config: $PROPS"
echo ""

exec "${CMD[@]}"
