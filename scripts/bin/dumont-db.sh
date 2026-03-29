#!/usr/bin/env bash
# --------------------------------------------------------------------------
# Run the Dumont Database Connector (standalone CLI tool).
#
# Usage:  ./bin/dumont-db.sh [options]
#
# Example:
#   ./bin/dumont-db.sh \
#     --server http://localhost:30130 \
#     --api-key YOUR_API_KEY \
#     --driver org.mariadb.jdbc.Driver \
#     --connect "jdbc:mariadb://localhost:3306/mydb" \
#     --query "SELECT id, title, body FROM articles" \
#     --site MySite \
#     --locale en_US
# --------------------------------------------------------------------------
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BASE_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
JAR="$BASE_DIR/db/dumont-db.jar"

if [ ! -f "$JAR" ]; then
  echo "Error: $JAR not found." >&2
  exit 1
fi

exec java -jar "$JAR" "$@"
