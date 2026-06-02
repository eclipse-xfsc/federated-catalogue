# Demo 2 / Scenario A — SHACL validation on/off
#
# cd examples/admin-toggles-demo
# hurl --variables-file ../environments/local.env --test scenario-a-schema-validation.hurl

# ============================================================================
# A.0 Authenticate — Keycloak password grant; capture {{token}}
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
# A.1 Confirm SHACL is OFF by default (per dev.env)
# ============================================================================
GET {{baseUrl}}/admin/schema-validation
Authorization:  Bearer {{token}}
HTTP 200
[Asserts]
jsonpath "$.modules[?(@.type=='SHACL')].enabled" nth 0 == false

# ============================================================================
# A.2 Ingest the deliberately-SHACL-invalid payload — accepted because SHACL is off
# ============================================================================
POST {{baseUrl}}/assets
Authorization:  Bearer {{token}}
Content-Type:  application/ld+json
file,legal-person-shacl-invalid.jsonld;
HTTP 201

# ============================================================================
# A.3 Turn SHACL ON
# ============================================================================
PATCH {{baseUrl}}/admin/schema-validation/modules/SHACL
Authorization:  Bearer {{token}}
Content-Type:  application/merge-patch+json
{ "enabled": true }
HTTP 200

GET {{baseUrl}}/admin/schema-validation
Authorization: Bearer {{token}}
HTTP 200
[Asserts]
jsonpath "$.modules[?(@.type=='SHACL')].enabled" nth 0 == true

# ============================================================================
# A.4 Ingest the same payload (different id) — now rejected by SHACL
#     (this is the headline assertion of the demo)
# ============================================================================
POST {{baseUrl}}/assets
Authorization:  Bearer {{token}}
Content-Type:  application/ld+json
```
{
  "@context": [
    "https://www.w3.org/ns/credentials/v2",
    "https://w3id.org/gaia-x/2511#",
    { "schema": "https://schema.org/" }
  ],
  "id": "https://www.example.org/credentials/legal-person-broken-2.json",
  "type": ["VerifiableCredential", "gx:LegalPerson"],
  "issuer": "did:web:example.org",
  "validFrom": "2026-01-01T00:00:00Z",
  "credentialSubject": {
    "id": "https://www.example.org/participants/broken-issuer-2",
    "type": "gx:LegalPerson",
    "schema:name": "Broken Example AG (second copy)",
    "gx:legalAddress": {
      "type": "gx:Address",
      "gx:countryCode": "Germany"
    }
  }
}
```
HTTP 400

# ============================================================================
# A.5 Validate the already-stored asset against the (now active) shapes
# ============================================================================
POST {{baseUrl}}/assets/validate
Authorization:  Bearer {{token}}
Content-Type:  application/json
{
  "assetIds": ["https://www.example.org/credentials/legal-person-broken.json"]
}
HTTP 200

# ============================================================================
# A.6 Reset — turn SHACL off
# ============================================================================
PATCH {{baseUrl}}/admin/schema-validation/modules/SHACL
Authorization:  Bearer {{token}}
Content-Type: application/merge-patch+json
{ "enabled": false }
HTTP 200
