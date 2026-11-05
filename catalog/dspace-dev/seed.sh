#!/usr/bin/env bash
# Seeds the local DSpace (docker-compose.yml here) with the fictional Lumen University repository:
# an administrator, one community with two collections, the SAF items under seed/saf, their PDF
# text and thumbnails, a withdrawn and two private items, and the OAI index.
#
#   docker compose -p dumont-dspace up -d && ./seed.sh
#
# Runs once per fresh stack: it stops when the repository already has a community. To start over:
#   docker compose -p dumont-dspace down -v && docker compose -p dumont-dspace up -d && ./seed.sh
# Needs bash, curl and docker (Git Bash on Windows is fine).
set -euo pipefail

CONTAINER=${CONTAINER:-dumont-dspace}
SERVER=${SERVER:-http://localhost:18080/server}
API=$SERVER/api
ADMIN_EMAIL=${ADMIN_EMAIL:-admin@lumen.test}
ADMIN_PASSWORD=${ADMIN_PASSWORD:-Lumen-dev-2026}
HERE=$(cd "$(dirname "$0")" && pwd)
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

# Git Bash would rewrite /dspace and /seed into Windows paths; only docker must be spared that.
in_dspace() { MSYS_NO_PATHCONV=1 docker exec "$CONTAINER" "$@"; }
dspace() { in_dspace /dspace/bin/dspace "$@"; }
log() { printf '\n== %s\n' "$*"; }

log "Waiting for $API"
for _ in $(seq 1 120); do
  [ "$(curl -s -o /dev/null -w '%{http_code}' "$API")" = 200 ] && break
  sleep 5
done
[ "$(curl -s -o /dev/null -w '%{http_code}' "$API")" = 200 ] || { echo "DSpace REST never answered"; exit 1; }

if curl -s "$API/core/communities" | grep -q '"totalElements" : [1-9]'; then
  echo "Already seeded. To start over: docker compose -p dumont-dspace down -v, then up -d and ./seed.sh"
  exit 0
fi

log "Administrator $ADMIN_EMAIL"
dspace create-administrator -e "$ADMIN_EMAIL" -f Repository -l Admin -p "$ADMIN_PASSWORD" -c en

log "Community and collections"
dspace structure-builder -f /seed/structure.xml -o /tmp/structure-out.xml -e "$ADMIN_EMAIL"
STRUCTURE=$(in_dspace cat /tmp/structure-out.xml)
collection_handle() {
  printf '%s' "$STRUCTURE" | grep -o "collection identifier=\"[^\"]*\"><name>$1<" | sed 's/.*identifier="\([^"]*\)".*/\1/'
}
THESES=$(collection_handle "Theses and Dissertations")
ARTICLES=$(collection_handle "Journal Articles")
echo "theses=$THESES articles=$ARTICLES"

log "Items (Simple Archive Format)"
dspace import -a -e "$ADMIN_EMAIL" -c "$THESES" -s /seed/saf/theses -m /tmp/map-theses
dspace import -a -e "$ADMIN_EMAIL" -c "$ARTICLES" -s /seed/saf/articles -m /tmp/map-articles
in_dspace sh -c 'sed "s#^#theses/#" /tmp/map-theses; sed "s#^#articles/#" /tmp/map-articles' > "$WORK/map"
cat "$WORK/map"

log "Full text and thumbnails (filter-media)"
dspace filter-media

# REST login: a CSRF token first, then the JWT; every write sends both.
JAR=$WORK/cookies
curl -s -c "$JAR" -b "$JAR" -D "$WORK/h0" -o /dev/null "$API/security/csrf"
CSRF=$(grep -i '^dspace-xsrf-token' "$WORK/h0" | tr -d '\r' | awk '{print $2}')
curl -s -c "$JAR" -b "$JAR" -D "$WORK/h1" -o /dev/null -X POST "$API/authn/login" -H "X-XSRF-TOKEN: $CSRF" \
  --data-urlencode "user=$ADMIN_EMAIL" --data-urlencode "password=$ADMIN_PASSWORD"
AUTH=$(grep -i '^authorization' "$WORK/h1" | tr -d '\r' | cut -d' ' -f2-)
NEW_CSRF=$(grep -i '^dspace-xsrf-token' "$WORK/h1" | tr -d '\r' | awk '{print $2}' || true)
[ -n "$NEW_CSRF" ] && CSRF=$NEW_CSRF
[ -n "$AUTH" ] || { echo "REST login failed"; exit 1; }

patch_item() { # handle, path, value
  local uuid
  uuid=$(curl -s -L -b "$JAR" -H "Authorization: $AUTH" "$API/pid/find?id=$1" \
    | sed -n 's/^  "uuid" : "\([^"]*\)".*/\1/p' | head -1)
  [ -n "$uuid" ] || { echo "No item for handle $1"; exit 1; }
  curl -s -f -o /dev/null -b "$JAR" -c "$JAR" -X PATCH "$API/core/items/$uuid" \
    -H "Authorization: $AUTH" -H "X-XSRF-TOKEN: $CSRF" -H 'Content-Type: application/json-patch+json' \
    -d "[{\"op\":\"replace\",\"path\":\"/$2\",\"value\":$3}]"
  echo "$1 ($uuid): $2 = $3"
}
handle_of() { grep "^$1 " "$WORK/map" | awk '{print $2}'; }

apply_roles() { # phase: before | after
  while read -r dir role; do
    [ -n "$dir" ] || continue
    case "$1:$role" in
      before:withdrawn) patch_item "$(handle_of "$dir")" withdrawn true ;;
      before:private) patch_item "$(handle_of "$dir")" discoverable false ;;
      after:private-after-oai) patch_item "$(handle_of "$dir")" discoverable false ;;
    esac
  done < "$HERE/seed/roles.txt"
}

log "Withdrawn and private items"
apply_roles before

log "OAI index (oai import -c)"
dspace oai import -c

log "Private after the OAI index was built (stale OAI, REST knows)"
apply_roles after

log "Done"
curl -s "$SERVER/oai/request?verb=ListIdentifiers&metadataPrefix=dim" | grep -o '<header[^>]*><identifier>[^<]*' \
  | sed 's/<header status="deleted"><identifier>/deleted  /; s/<header><identifier>/live     /'
cat <<EOF

REST:  $API
OAI:   $SERVER/oai/request?verb=ListRecords&metadataPrefix=dim
Admin: $ADMIN_EMAIL / $ADMIN_PASSWORD
EOF
