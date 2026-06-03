# Demo 2 / Scenario B — Graph backend switch
#
# Backend switch takes effect immediately; no restart required.
# Run the full scenario end-to-end with:
#   hurl --variables-file ../environments/local.env --test scenario-b-graph-switch.hurl

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
# B.1a Query data via openCypher on Fuseki -> 415 (unsupported)
# ============================================================================
POST {{baseUrl}}/query
Authorization: Bearer {{token}}
Content-Type:   application/opencypher-query
Accept:    application/json
```
MATCH (n) RETURN n LIMIT 5
```
HTTP 415
[Asserts]
header "Content-Type" contains "application/json"

# ============================================================================
# B.2 Quick SPARQL probe — anything is enough (templates from Demo 1 if present)
# ============================================================================
POST {{baseUrl}}/query
Authorization:  Bearer {{token}}
Content-Type:  application/sparql-query
Accept:    application/sparql-results+json
```
SELECT ?s ?p ?o WHERE { ?s ?p ?o } LIMIT 5
```
HTTP 200
[Asserts]
header "Content-Type" contains "application/sparql-results+json"

# ============================================================================
# B.3 Switch backend → Neo4j (takes effect immediately)
# ============================================================================
POST {{baseUrl}}/admin/graph-database/switch
Authorization:  Bearer {{token}}
Content-Type:  application/json
{ "backend": "NEO4J" }
HTTP 200

# ============================================================================
# B.4 Verify backend switch took effect
# ============================================================================
GET {{baseUrl}}/admin/graph-database
Authorization:  Bearer {{token}}
HTTP 200
[Asserts]
jsonpath "$.activeBackend" == "NEO4J"

# ============================================================================
# B.5 Rebuild the graph projection in Neo4j
# ============================================================================
POST {{baseUrl}}/admin/graph/rebuild
Authorization:  Bearer {{token}}
HTTP *
[Asserts]
status >= 200
status < 300

# ============================================================================
# B.6 Wait for rebuild — poll status until complete
#     hurl polls with --retry and --retry-interval.
# ============================================================================
GET {{baseUrl}}/admin/graph/rebuild/status
Authorization:  Bearer {{token}}
[Options]
retry: 30
retry-interval: 2000
HTTP 200
[Asserts]
jsonpath "$.complete" == true
jsonpath "$.failed" == false

# ============================================================================
# B.7 Query the same data via openCypher on Neo4j
# ============================================================================
POST {{baseUrl}}/query
Authorization: Bearer {{token}}
Content-Type:  application/opencypher-query
Accept:   application/json
```
MATCH (n) RETURN n LIMIT 5
```
HTTP 200
[Asserts]
jsonpath "$.totalCount" >= 1

# ============================================================================
# B.8 Reset — switch back to Fuseki
# ============================================================================
POST {{baseUrl}}/admin/graph-database/switch
Authorization:  Bearer {{token}}
Content-Type:  application/json
{ "backend": "FUSEKI" }
HTTP 200
