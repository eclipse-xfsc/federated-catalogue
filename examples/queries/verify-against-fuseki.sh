#!/usr/bin/env bash
# End-to-end verification of the example queries against a running catalogue stack.
#
# Assumes:
#   - docker compose --env-file dev.env up -d  (Fuseki backend, signature checks off)
#   - Keycloak is bootstrapped (a user exists with role Ro-MU-CA or ADMIN_ALL).
#
# Auth is delegated to ../auth.sh — set FC_USERNAME/FC_PASSWORD and any other
# overrides (FC_CLIENT_ID, FC_CLIENT_SECRET, KEYCLOAK_URL, KEYCLOAK_REALM) in
# the environment; see ../auth.sh --help-style header for the full list.
#
# Usage:
#   FC_USERNAME=alice FC_PASSWORD=alice ./verify-against-fuseki.sh
#   ./verify-against-fuseki.sh                  # uses admin/admin defaults
#
# Optional overrides specific to this script:
#   FC_BASE_URL          (default http://localhost:8081)

set -euo pipefail

QUERIES_DIR="$(cd "$(dirname "$0")" && pwd)"
AUTH_SH="${QUERIES_DIR}/../auth.sh"

: "${FC_BASE_URL:=http://localhost:8081}"

log()   { printf '\n\033[1;34m== %s\033[0m\n' "$*"; }
warn()  { printf '\033[1;33m!! %s\033[0m\n' "$*" >&2; }
fail()  { printf '\033[1;31mXX %s\033[0m\n' "$*" >&2; exit 1; }

# ---------- 1. Fetch access token via the shared helper ----------
log "Fetching access token via ${AUTH_SH}"
ACCESS_TOKEN="$("${AUTH_SH}")" \
  || fail "Token request failed. Check Keycloak is up and credentials are valid."
echo "   got token (length ${#ACCESS_TOKEN})"

AUTH="Authorization: Bearer ${ACCESS_TOKEN}"

# ---------- 2. Upload the 5 unsigned JSON-LD payloads to /assets ----------
FILES=(
  test-issuer.jsonld
  test-issuer2.jsonld
  test-issuer3.jsonld
  credentialSubject2.jsonld
  serviceElasticSearch.jsonld
)

log "Uploading ${#FILES[@]} credentials to ${FC_BASE_URL}/assets"
for f in "${FILES[@]}"; do
  path="${QUERIES_DIR}/${f}"
  [[ -f "${path}" ]] || fail "Missing file: ${path}"
  printf '   -> %-32s ' "${f}"
  http_code="$(curl -sS -o /tmp/fc-upload-resp.json -w '%{http_code}' \
    -X POST "${FC_BASE_URL}/assets" \
    -H "${AUTH}" \
    -H "Content-Type: application/ld+json" \
    --data-binary @"${path}")"
  echo "HTTP ${http_code}"
  if [[ "${http_code}" != "201" && "${http_code}" != "200" ]]; then
    warn "Upload of ${f} returned ${http_code}:"
    cat /tmp/fc-upload-resp.json
    echo
  fi
done

# ---------- 3. Run the 5 SPARQL-star queries from the appendix ----------
run_query() {
  local title="$1"
  local body="$2"
  log "Query: ${title}"
  printf '%s\n' "${body}"
  echo "---- result ----"
  curl -sS -X POST "${FC_BASE_URL}/query" \
    -H "${AUTH}" \
    -H "Content-Type: application/sparql-query" \
    -H "Accept: application/sparql-results+json" \
    --data-binary "${body}" \
    | python3 -m json.tool 2>/dev/null \
    || warn "(non-JSON response, raw output above)"
}

run_query "All triples (LIMIT 100)" '
SELECT ?s ?p ?o WHERE { ?s ?p ?o } LIMIT 100
'

run_query "Simple Relationship — services provided by test-issuer-1" '
PREFIX gx:     <https://w3id.org/gaia-x/2511#>
PREFIX schema: <https://schema.org/>
SELECT ?service ?name WHERE {
  ?service a gx:ServiceOffering ;
           gx:providedBy <https://www.example.org/participants/test-issuer-1> ;
           schema:name ?name .
}
'

run_query "Essential Attributes — test-issuer-2 legal address" '
PREFIX gx:     <https://w3id.org/gaia-x/2511#>
PREFIX schema: <https://schema.org/>
PREFIX vcard:  <http://www.w3.org/2006/vcard/ns#>
SELECT ?name ?countryCode ?region ?locality ?postalCode ?streetAddress WHERE {
  <https://www.example.org/participants/test-issuer-2> a gx:LegalPerson ;
    schema:name ?name ;
    gx:legalAddress ?addr .
  ?addr gx:countryCode      ?countryCode ;
        vcard:locality      ?locality ;
        vcard:postal-code   ?postalCode ;
        vcard:street-address ?streetAddress .
  OPTIONAL { ?addr vcard:region ?region . }
}
'

run_query "Entity having a Property — service dependsOn edges" '
PREFIX gx: <https://w3id.org/gaia-x/2511#>
SELECT ?dependent ?dependency WHERE {
  ?dependent a gx:ServiceOffering ;
             gx:dependsOn ?dependency .
  ?dependency a gx:ServiceOffering .
}
'

run_query "Entities by Name — \"Elastic Search DB\"" '
PREFIX schema: <https://schema.org/>
SELECT ?entity WHERE { ?entity schema:name "Elastic Search DB" } LIMIT 25
'

run_query "Provenance via RDF-star — claims for test-issuer-1" '
PREFIX cred: <https://www.w3.org/2018/credentials#>
SELECT ?s ?p ?o WHERE {
  <<(?s ?p ?o)>> cred:credentialSubject
    <https://www.example.org/participants/test-issuer-1> .
}
'

log "Done. Compare each result block against the documented output in 13_appendix.adoc."
