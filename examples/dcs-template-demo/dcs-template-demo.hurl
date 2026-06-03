# DCS Template Repository — end-to-end demo
#
# Run end-to-end:
#   cd examples/dcs-template-demo
#   hurl --variables-file ../environments/local.env --test dcs-template-demo.hurl
#
# Run one step at a time (note: entry 1 is the auth step, demo steps start at entry 2):
#   hurl --variables-file ../environments/local.env --to-entry 5 dcs-template-demo.hurl
#
# Diagnostics:
#   hurl -v   ...        # one-line HTTP per request
#   hurl --vv ...        # full request/response bodies
#   hurl --curl out.sh   # emit equivalent curl commands (hurl 5.x+)

# ============================================================================
# 0. Authenticate — Keycloak password grant; capture {{token}}
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
# 1. Confirm Fuseki is the active query backend
# ============================================================================
GET {{baseUrl}}/admin/graph-database
Authorization:  Bearer {{token}}
HTTP 200
[Asserts]
jsonpath "$.activeBackend" == "FUSEKI"

# ============================================================================
# 1a. Idempotency — cascade-delete any leftover templates from prior runs.
#     DELETE /assets/{id} is idempotent (204 for unknown id) and removes the
#     asset together with all versions, human-readable companions, and
#     provenance credentials.
# ============================================================================
DELETE {{baseUrl}}/assets/by-id/https%3A%2F%2Fdeltadao.example.org%2Ftemplates%2Fdpa%2Fv1
Authorization:   Bearer {{token}}
HTTP 204

DELETE {{baseUrl}}/assets/by-id/https%3A%2F%2Fdeltadao.example.org%2Ftemplates%2Fdpa%2Fv2
Authorization:   Bearer {{token}}
HTTP 204

# ============================================================================
# 2. Upload the machine-readable template (v1)
# ============================================================================
POST {{baseUrl}}/assets
Authorization:  Bearer {{token}}
Content-Type:  application/ld+json
file,dpa-template-v1.jsonld;
HTTP 201

# ============================================================================
# 3. Upload and link the human-readable rendering (v1)
# ============================================================================
POST {{baseUrl}}/assets/https%3A%2F%2Fdeltadao.example.org%2Ftemplates%2Fdpa%2Fv1/human-readable
Authorization: Bearer {{token}}
[Multipart]
file: file,dpa-template-v1.md; text/markdown
HTTP 201

# ============================================================================
# 4. Attach creation provenance (Alice)
# ============================================================================
POST {{baseUrl}}/assets/https%3A%2F%2Fdeltadao.example.org%2Ftemplates%2Fdpa%2Fv1/provenance
Authorization:  Bearer {{token}}
Content-Type:  application/json
file,provenance-v1-created.jsonld;
HTTP 201

# ============================================================================
# 5. Attach approval provenance (Bob)
# ============================================================================
POST {{baseUrl}}/assets/https%3A%2F%2Fdeltadao.example.org%2Ftemplates%2Fdpa%2Fv1/provenance
Authorization:  Bearer {{token}}
Content-Type:  application/json
file,provenance-v1-approved.jsonld;
HTTP 201

# ============================================================================
# 6. CWE discovery — find latest approved DPA for jurisdiction DE
#    Expect v1 (only template in the graph so far)
# ============================================================================
POST {{baseUrl}}/query
Authorization:  Bearer {{token}}
Content-Type:  application/sparql-query
Accept:  application/sparql-results+json
```
PREFIX dcs:    <https://w3id.org/facis/dcs/1#>
PREFIX schema: <https://schema.org/>
PREFIX prov:   <http://www.w3.org/ns/prov#>
SELECT ?template ?version ?hash WHERE {
  ?template a dcs:ContractTemplate ;
            dcs:category    "data-processing-agreement" ;
            dcs:jurisdiction "DE" ;
            schema:version  ?version ;
            dcs:humanReadableHash ?hash .
  ?act a prov:Activity ;
       prov:used ?template ;
       dcs:action "approved" ;
       prov:endedAtTime ?approvedAt .
  FILTER NOT EXISTS {
    ?newer a dcs:ContractTemplate ;
           prov:wasDerivedFrom+ ?template ;
           ^prov:used / dcs:action "approved" .
  }
}
ORDER BY DESC(?approvedAt)
LIMIT 1
```
HTTP 200
[Asserts]
jsonpath "$.results.bindings[0].template.value" == "https://deltadao.example.org/templates/dpa/v1"
jsonpath "$.results.bindings[0].version.value" == "1.0.0"
jsonpath "$.results.bindings[0].hash.value" == "4542119cd53c96408c8225d658d8f0d40b75a12d9be240a24e7b24f3ad977f51"

# ============================================================================
# 7. Download the human-readable rendering — assert hash matches the VC claim
# ============================================================================
GET {{baseUrl}}/assets/https%3A%2F%2Fdeltadao.example.org%2Ftemplates%2Fdpa%2Fv1/human-readable
Authorization: Bearer {{token}}
HTTP 200
[Asserts]
sha256 == hex,4542119cd53c96408c8225d658d8f0d40b75a12d9be240a24e7b24f3ad977f51;

# ============================================================================
# 8a. Publish v2 — machine-readable
# ============================================================================
POST {{baseUrl}}/assets
Authorization:  Bearer {{token}}
Content-Type:  application/ld+json
file,dpa-template-v2.jsonld;
HTTP 201

# 8b. Publish v2 — human-readable
POST {{baseUrl}}/assets/https%3A%2F%2Fdeltadao.example.org%2Ftemplates%2Fdpa%2Fv2/human-readable
Authorization:  Bearer {{token}}
[Multipart]
file: file,dpa-template-v2.md; text/markdown
HTTP 201

# 8c. Approval provenance for v2
POST {{baseUrl}}/assets/https%3A%2F%2Fdeltadao.example.org%2Ftemplates%2Fdpa%2Fv2/provenance
Authorization:  Bearer {{token}}
Content-Type:  application/json
file,provenance-v2-approved.jsonld;
HTTP 201

# ============================================================================
# 9. CWE re-discovery — same SPARQL, v2 now wins (supersedes v1 via prov:wasDerivedFrom)
# ============================================================================
POST {{baseUrl}}/query
Authorization:  Bearer {{token}}
Content-Type:  application/sparql-query
Accept:  application/sparql-results+json
```
PREFIX dcs:    <https://w3id.org/facis/dcs/1#>
PREFIX schema: <https://schema.org/>
PREFIX prov:   <http://www.w3.org/ns/prov#>
SELECT ?template ?version ?hash WHERE {
  ?template a dcs:ContractTemplate ;
            dcs:category    "data-processing-agreement" ;
            dcs:jurisdiction "DE" ;
            schema:version  ?version ;
            dcs:humanReadableHash ?hash .
  ?act a prov:Activity ;
       prov:used ?template ;
       dcs:action "approved" ;
       prov:endedAtTime ?approvedAt .
  FILTER NOT EXISTS {
    ?newer a dcs:ContractTemplate ;
           prov:wasDerivedFrom+ ?template ;
           ^prov:used / dcs:action "approved" .
  }
}
ORDER BY DESC(?approvedAt)
LIMIT 1
```
HTTP 200
[Asserts]
jsonpath "$.results.bindings[0].template.value" == "https://deltadao.example.org/templates/dpa/v2"
jsonpath "$.results.bindings[0].version.value"  == "1.1.0"
jsonpath "$.results.bindings[0].hash.value" == "83e6ff5971b5a4791bc1ff026f2d28de125ad3d3e68327bcbc98680e4861f549"

# ============================================================================
# 10a. Audit trail — provenance list for v1 (created → approved)
# ============================================================================
GET {{baseUrl}}/assets/https%3A%2F%2Fdeltadao.example.org%2Ftemplates%2Fdpa%2Fv1/provenance
Authorization:  Bearer {{token}}
HTTP 200

# 10b. Version history for v2 — walks prov:wasDerivedFrom back to v1
GET {{baseUrl}}/assets/https%3A%2F%2Fdeltadao.example.org%2Ftemplates%2Fdpa%2Fv2/versions
Authorization:  Bearer {{token}}
HTTP 200
