# End-to-end verification of the Architecture Appendix queries against a
# running catalogue stack (Fuseki backend, signature checks off).
#
# Usage:
#   cd examples/queries
#   hurl --variables-file ../environments/local.env --test verify-against-fuseki.hurl
#
# Diagnostics:
#   hurl -v   ...        # one-line HTTP per request
#   hurl --vv ...        # full request/response bodies
#   hurl --curl out.sh   # emit equivalent curl commands (hurl 5.x+)

# ----------------------------------------------------------------------------
# Authenticate — Keycloak password grant; capture {{token}}
# ----------------------------------------------------------------------------
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

# ----------------------------------------------------------------------------
# Sanity — Fuseki is the active backend
# ----------------------------------------------------------------------------
GET {{baseUrl}}/admin/graph-database
Authorization:  Bearer {{token}}
HTTP 200
[Asserts]
jsonpath "$.activeBackend" == "FUSEKI"

# ----------------------------------------------------------------------------
# Idempotency — cascade-delete any leftover assets from prior runs.
# DELETE /assets/{id} returns 204 for unknown ids and removes the asset
# along with every version and provenance credential linked to it.
# ----------------------------------------------------------------------------
DELETE {{baseUrl}}/assets/by-id/https%3A%2F%2Fwww.example.org%2Fparticipants%2Ftest-issuer-1
Authorization:   Bearer {{token}}
HTTP 204

DELETE {{baseUrl}}/assets/by-id/https%3A%2F%2Fwww.example.org%2Fparticipants%2Ftest-issuer-2
Authorization:   Bearer {{token}}
HTTP 204

DELETE {{baseUrl}}/assets/by-id/https%3A%2F%2Fwww.example.org%2Fparticipants%2Ftest-issuer-3
Authorization:   Bearer {{token}}
HTTP 204

DELETE {{baseUrl}}/assets/by-id/https%3A%2F%2Fwww.example.org%2Fservice-offerings%2Fservice-1
Authorization:  Bearer {{token}}
HTTP 204

DELETE {{baseUrl}}/assets/by-id/https%3A%2F%2Fwww.example.org%2Fservice-offerings%2Felasticsearch
Authorization:   Bearer {{token}}
HTTP 204

# ----------------------------------------------------------------------------
# Upload the five Loire payloads
# ----------------------------------------------------------------------------
POST {{baseUrl}}/assets
Authorization:  Bearer {{token}}
Content-Type:  application/ld+json
file,test-issuer.jsonld;
HTTP *
[Asserts]
status >= 200
status <  300

POST {{baseUrl}}/assets
Authorization:  Bearer {{token}}
Content-Type: application/ld+json
file,test-issuer2.jsonld;
HTTP *
[Asserts]
status >= 200
status < 300

POST {{baseUrl}}/assets
Authorization:  Bearer {{token}}
Content-Type:  application/ld+json
file,test-issuer3.jsonld;
HTTP *
[Asserts]
status >= 200
status < 300

POST {{baseUrl}}/assets
Authorization:  Bearer {{token}}
Content-Type:  application/ld+json
file,credentialSubject2.jsonld;
HTTP *
[Asserts]
status >= 200
status < 300

POST {{baseUrl}}/assets
Authorization:  Bearer {{token}}
Content-Type:  application/ld+json
file,serviceElasticSearch.jsonld;
HTTP *
[Asserts]
status >= 200
status < 300

# ----------------------------------------------------------------------------
# All triples (sanity)
# ----------------------------------------------------------------------------
POST {{baseUrl}}/query
Authorization:  Bearer {{token}}
Content-Type:  application/sparql-query
Accept:  application/sparql-results+json
```
SELECT ?s ?p ?o WHERE { ?s ?p ?o } LIMIT 100
```
HTTP 200
[Asserts]
jsonpath "$.results.bindings" count > 0

# ----------------------------------------------------------------------------
# Simple Relationship — services provided by test-issuer-1
# ----------------------------------------------------------------------------
POST {{baseUrl}}/query
Authorization:  Bearer {{token}}
Content-Type: application/sparql-query
Accept:  application/sparql-results+json
```
PREFIX cred:   <https://www.w3.org/2018/credentials#>
PREFIX gx:     <https://w3id.org/gaia-x/2511#>
PREFIX schema: <https://schema.org/>
SELECT ?service ?name WHERE {
  <<(?service a gx:ServiceOffering)>>                                                 cred:credentialSubject ?service .
  <<(?service gx:providedBy <https://www.example.org/participants/test-issuer-1>)>>   cred:credentialSubject ?service .
  <<(?service schema:name ?name)>>                                                    cred:credentialSubject ?service .
}
ORDER BY ?service
```
HTTP 200
[Asserts]
jsonpath "$.results.bindings" count == 2

# ----------------------------------------------------------------------------
# Essential Attributes — test-issuer-2 legal address
# ----------------------------------------------------------------------------
POST {{baseUrl}}/query
Authorization:  Bearer {{token}}
Content-Type:  application/sparql-query
Accept:  application/sparql-results+json
```
PREFIX cred:   <https://www.w3.org/2018/credentials#>
PREFIX gx:     <https://w3id.org/gaia-x/2511#>
PREFIX schema: <https://schema.org/>
PREFIX vcard:  <http://www.w3.org/2006/vcard/ns#>
SELECT ?name ?countryCode ?region ?locality ?postalCode ?streetAddress WHERE {
  <<(<https://www.example.org/participants/test-issuer-2> a gx:LegalPerson)>>             cred:credentialSubject <https://www.example.org/participants/test-issuer-2> .
  <<(<https://www.example.org/participants/test-issuer-2> schema:name ?name)>>            cred:credentialSubject <https://www.example.org/participants/test-issuer-2> .
  <<(<https://www.example.org/participants/test-issuer-2> gx:legalAddress ?addr)>>        cred:credentialSubject <https://www.example.org/participants/test-issuer-2> .
  <<(?addr gx:countryCode      ?countryCode)>>      cred:credentialSubject <https://www.example.org/participants/test-issuer-2> .
  <<(?addr vcard:locality      ?locality)>>         cred:credentialSubject <https://www.example.org/participants/test-issuer-2> .
  <<(?addr vcard:postal-code   ?postalCode)>>       cred:credentialSubject <https://www.example.org/participants/test-issuer-2> .
  <<(?addr vcard:street-address ?streetAddress)>>   cred:credentialSubject <https://www.example.org/participants/test-issuer-2> .
  OPTIONAL { <<(?addr vcard:region ?region)>>       cred:credentialSubject <https://www.example.org/participants/test-issuer-2> . }
}
```
HTTP 200
[Asserts]
jsonpath "$.results.bindings[0].name.value" == "deltaDAO Dresden GmbH"
jsonpath "$.results.bindings[0].countryCode.value"   == "DE"
jsonpath "$.results.bindings[0].region.value" == "Sachsen"
jsonpath "$.results.bindings[0].locality.value" == "Dresden"
jsonpath "$.results.bindings[0].postalCode.value" == "01069"
jsonpath "$.results.bindings[0].streetAddress.value" == "Pragerstraße 12"

# ----------------------------------------------------------------------------
# Entity having a Property — service dependsOn edges
# ----------------------------------------------------------------------------
POST {{baseUrl}}/query
Authorization:  Bearer {{token}}
Content-Type:  application/sparql-query
Accept: application/sparql-results+json
```
PREFIX cred: <https://www.w3.org/2018/credentials#>
PREFIX gx:   <https://w3id.org/gaia-x/2511#>
SELECT ?dependent ?dependency WHERE {
  <<(?dependent a gx:ServiceOffering)>>           cred:credentialSubject ?dependent .
  <<(?dependent gx:dependsOn ?dependency)>>       cred:credentialSubject ?dependent .
  <<(?dependency a gx:ServiceOffering)>>          cred:credentialSubject ?dependency .
}
```
HTTP 200
[Asserts]
jsonpath "$.results.bindings[0].dependent.value" == "https://www.example.org/service-offerings/elasticsearch"
jsonpath "$.results.bindings[0].dependency.value" == "https://www.example.org/service-offerings/service-1"

# ----------------------------------------------------------------------------
# Entities by Name — "Elastic Search DB"
# ----------------------------------------------------------------------------
POST {{baseUrl}}/query
Authorization:  Bearer {{token}}
Content-Type:  application/sparql-query
Accept: application/sparql-results+json
```
PREFIX cred:   <https://www.w3.org/2018/credentials#>
PREFIX schema: <https://schema.org/>
SELECT ?entity WHERE {
  <<(?entity schema:name "Elastic Search DB")>> cred:credentialSubject ?entity
} LIMIT 25
```
HTTP 200
[Asserts]
jsonpath "$.results.bindings[0].entity.value" == "https://www.example.org/service-offerings/elasticsearch"

# ----------------------------------------------------------------------------
# Provenance via RDF-star — claims for test-issuer-1
# ----------------------------------------------------------------------------
POST {{baseUrl}}/query
Authorization:  Bearer {{token}}
Content-Type:  application/sparql-query
Accept:  application/sparql-results+json
```
PREFIX cred: <https://www.w3.org/2018/credentials#>
SELECT ?s ?p ?o WHERE {
  <<(?s ?p ?o)>> cred:credentialSubject
    <https://www.example.org/participants/test-issuer-1> .
}
```
HTTP 200
[Asserts]
jsonpath "$.results.bindings" count > 0
