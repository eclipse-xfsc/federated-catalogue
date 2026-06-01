#!/usr/bin/env bash
# auth.sh — fetch a Keycloak access token for the Federated Catalogue.
#
# Prints the access token to stdout (suitable for `export TOKEN=$(./auth.sh)`).
# All diagnostic output goes to stderr.
#
# Writes:
#   .token            — current access token (used by the curl walkthroughs)
#   .refresh_token    — refresh token, used to refresh silently next time
#
# Subsequent runs re-use the refresh token instead of re-prompting for the
# password grant, until the refresh token itself expires.
#
# Configuration (env vars, overriding the dev.env defaults):
#   FC_USERNAME       Keycloak user (default: admin)
#   FC_PASSWORD       Keycloak password (default: admin)
#   FC_CLIENT_ID      Keycloak client (default: federated-catalogue)
#   FC_CLIENT_SECRET  Keycloak client secret (default: read from ../docker/dev.env)
#   KEYCLOAK_URL      Keycloak base URL (default: http://localhost:8080)
#   KEYCLOAK_REALM    Realm name (default: gaia-x)
#
# Typical usage:
#   export TOKEN=$(./auth.sh)
#   curl -H "Authorization: Bearer $TOKEN" http://localhost:8081/...
#
# Or:
#   ./auth.sh > /dev/null && curl -H "Authorization: Bearer $(cat .token)" ...

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEV_ENV="${SCRIPT_DIR}/../docker/dev.env"

: "${FC_USERNAME:=admin}"
: "${FC_PASSWORD:=admin}"
: "${FC_CLIENT_ID:=federated-catalogue}"
: "${KEYCLOAK_URL:=http://localhost:8080}"
: "${KEYCLOAK_REALM:=gaia-x}"

if [[ -z "${FC_CLIENT_SECRET:-}" && -f "${DEV_ENV}" ]]; then
  FC_CLIENT_SECRET="$(grep -E '^FC_CLIENT_SECRET=' "${DEV_ENV}" | head -1 | cut -d= -f2-)"
fi
: "${FC_CLIENT_SECRET:?FC_CLIENT_SECRET not set and not found in ${DEV_ENV}}"

TOKEN_URL="${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/token"

log() { printf '%s\n' "$*" >&2; }

extract() { python3 -c 'import sys,json;d=json.load(sys.stdin);print(d.get("'"$1"'",""))'; }

response=""

if [[ -f .refresh_token ]]; then
  log "auth.sh: refreshing existing session"
  response="$(curl -fsS -X POST "${TOKEN_URL}" \
      --data-urlencode "grant_type=refresh_token" \
      --data-urlencode "client_id=${FC_CLIENT_ID}" \
      --data-urlencode "client_secret=${FC_CLIENT_SECRET}" \
      --data-urlencode "refresh_token=$(cat .refresh_token)" 2>/dev/null)" || response=""
fi

if [[ -z "${response}" ]]; then
  log "auth.sh: requesting new token for user '${FC_USERNAME}'"
  response="$(curl -fsS -X POST "${TOKEN_URL}" \
      --data-urlencode "grant_type=password" \
      --data-urlencode "client_id=${FC_CLIENT_ID}" \
      --data-urlencode "client_secret=${FC_CLIENT_SECRET}" \
      --data-urlencode "username=${FC_USERNAME}" \
      --data-urlencode "password=${FC_PASSWORD}" \
      --data-urlencode "scope=openid")"
fi

access_token="$(printf '%s' "${response}" | extract access_token)"
refresh_token="$(printf '%s' "${response}" | extract refresh_token)"

[[ -n "${access_token}" ]] || { log "auth.sh: no access_token in response: ${response}"; exit 1; }

printf '%s' "${access_token}" > .token
[[ -n "${refresh_token}" ]] && printf '%s' "${refresh_token}" > .refresh_token

printf '%s\n' "${access_token}"
