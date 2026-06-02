# Demo 2 / Scenario B — Graph backend switch
#
# This scenario REQUIRES a docker-compose restart between parts B.4 and B.5.
# The flow splits into two hurl invocations across the restart; each one
# starts with its own inline auth step so {{token}} is fresh on resume.
#
#   hurl --variables-file ../environments/local.env --to-entry 5 --test scenario-b-graph-switch.hurl
#
#   docker compose --env-file ../../docker/dev.env restart fc-server
#   # wait for /actuator/health UP
#
#   hurl --variables-file ../environments/local.env --from-entry 6 --test scenario-b-graph-switch.hurl

# ============================================================================
# B.0 Authenticate — Keycloak password grant; capture {{token}}
# ============================================================================
POST {{keycloakUrl}}/realms/{{realm}}/protocol/openid-connect/token
[Form]
grant_type: password
client_id: {{clientId}}
client_secret: {{clientSecret}}
username: {{username}}
password: {{password}}
HTTP 200
[Captures]
token: jsonpath "$.access_token"

# ============================================================================
# B.1 Confirm Fuseki is active
# ============================================================================
GET {{baseUrl}}/admin/graph-database
Authorization:  Bearer {{token}}
HTTP 200
[Asserts]
jsonpath "$.activeBackend" == "FUSEKI"

# ============================================================================
# B.2 Quick SPARQL probe — anything is enough (templates from Demo 1 if present)
# ============================================================================
POST {{baseUrl}}/query
Authorization:  Bearer {{token}}
Content-Type:  application/sparql-query
Accept:  application/sparql-results+json
```
SELECT ?s ?p ?o WHERE { ?s ?p ?o } LIMIT 5
```
HTTP 200

# ============================================================================
# B.3 Switch backend → Neo4j (persisted; takes effect after restart)
# ============================================================================
POST {{baseUrl}}/admin/graph-database/switch
Authorization:  Bearer {{token}}
Content-Type:  application/json
{ "backend": "NEO4J" }
HTTP 200

# ============================================================================
# B.4 Manual step boundary — stop hurl here, restart fc-server, then resume.
#     This entry is a sanity check that the new backend is now reported.
#     If you ran hurl with --to-entry 4, this assertion still uses Fuseki
#     (until restart); re-running --from-entry 5 onwards is what proves
#     the switch.
# ============================================================================
GET {{baseUrl}}/admin/graph-database
Authorization:  Bearer {{token}}
HTTP 200

# ============================================================================
# B.4b (POST-RESTART) Re-authenticate — fresh token after the restart break
# ============================================================================
POST {{keycloakUrl}}/realms/{{realm}}/protocol/openid-connect/token
[Form]
grant_type: password
client_id: {{clientId}}
client_secret: {{clientSecret}}
username: {{username}}
password: {{password}}
HTTP 200
[Captures]
token: jsonpath "$.access_token"

# ============================================================================
# B.5 (POST-RESTART) Rebuild the graph projection in Neo4j
# ============================================================================
POST {{baseUrl}}/admin/graph/rebuild
Authorization:  Bearer {{token}}
HTTP *
[Asserts]
status >= 200
status < 300

# ============================================================================
# B.6 Wait for rebuild — poll status until COMPLETED
#     hurl has no built-in loop; use --retry and --retry-interval-ms.
# ============================================================================
GET {{baseUrl}}/admin/graph/rebuild/status
Authorization:  Bearer {{token}}
[Options]
retry: 30
retry-interval: 2000
HTTP 200
[Asserts]
jsonpath "$.status" == "COMPLETED"

# ============================================================================
# B.7 Query the same data via openCypher on Neo4j
# ============================================================================
POST {{baseUrl}}/query
Authorization: Bearer {{token}}
Content-Type:  application/opencypher-query
```
MATCH (t:ContractTemplate) RETURN t.uri AS template, t.name AS name
```
HTTP 200

# ============================================================================
# B.8 Reset — switch back to Fuseki (requires another restart + rebuild after)
# ============================================================================
POST {{baseUrl}}/admin/graph-database/switch
Authorization:  Bearer {{token}}
Content-Type:  application/json
{ "backend": "FUSEKI" }
HTTP 200
