# Example Verifiable Credentials

This folder contains unsigned **Gaia-X 2511** (post-Loire development snapshot)
Verifiable Credential payloads. They are the input data referenced by the
sample queries in the catalogue's Architecture Document Appendix
(`docs/federated-catalogue/src/docs/architecture/chapters/13_appendix.adoc`).

Each `.jsonld` file is the *credential payload only* — it is not signed.
To ingest it, sign it as a Verifiable Credential JWT (VC-JWT) and POST the
JWT to `/assets`.

## Prerequisites

Catalogue stack running and Keycloak bootstrapped — see [`../README.md`](../README.md) for the full setup and
authentication flow (`auth.sh`).

## Examples

| File                          | `credentialSubject` type | Purpose                                                                    |
|-------------------------------|--------------------------|----------------------------------------------------------------------------|
| `test-issuer.jsonld`          | `gx:LegalPerson`         | Legal Person (Hamburg) — full legal & headquarters address                 |
| `test-issuer2.jsonld`         | `gx:LegalPerson`         | Second Legal Person (Dresden) — used for multi-participant queries         |
| `test-issuer3.jsonld`         | `gx:LegalPerson`         | Third Legal Person (Munich) — uses `gx:subOrganisationOf` to link issuer-1 |
| `credentialSubject2.jsonld`   | `gx:ServiceOffering`     | Service Offering provided by `test-issuer-1`                               |
| `serviceElasticSearch.jsonld` | `gx:ServiceOffering`     | "Elastic Search DB" service offering; depends on `service-1`               |

All payloads conform to:

- W3C Verifiable Credentials Data Model 2.0 (`https://www.w3.org/ns/credentials/v2`)
- Gaia-X Ontology 2511 (`https://w3id.org/gaia-x/2511#`)

The Gaia-X 2511 SHACL shapes that validate these payloads ship with the
catalogue at `fc-service-core/src/main/resources/trustframeworks/gaia-x-2511/shapes.ttl`.

References:

- Gaia-X Architecture, "Gaia-X Credential Format" and "Verifying Gaia-X Credentials" sections.
- Gaia-X ICAM, credential and proof-of-possession requirements.

## Signing — producing a VC-JWT

The Federated Catalogue requires the **VC-JWT** format
(see `fc-service-core/.../verification/CredentialVerificationStrategy.java`).
Linked-Data proofs such as `JsonWebSignature2020` are rejected.

### Prerequisites

1. A **did:web** identifier (e.g. `did:web:example.org`) whose DID document is
   resolvable over HTTPS at `https://example.org/.well-known/did.json`.
2. A verification method in that DID document containing a `publicKeyJwk` with:
    - `alg` set to the JOSE algorithm used to sign (e.g. `PS256`, `ES256`).
    - either `x5u` (URL to a PEM chain) or `x5c` (inline chain) that resolves to
      a certificate issued by a Gaia-X trusted Trust Anchor.
3. The matching private key, kept off-repo.

### JWT structure

Headers:

| Header | Value                                              |
|--------|----------------------------------------------------|
| `alg`  | matches `publicKeyJwk.alg` (e.g. `PS256`)          |
| `typ`  | `vc+jwt`                                           |
| `cty`  | `vc+ld+json`                                       |
| `iss`  | the issuer DID (e.g. `did:web:example.org`)        |
| `kid`  | full verification method URL, including `#` anchor |

Claims:

- `iss` — issuer DID; must match the credential payload's `issuer`.
- `vc`  — the full credential payload (the JSON object from the `.jsonld` file).
- `exp` — optional JWT expiry; independent of `validUntil` in the payload.

### Signing example (Python, `jwcrypto`)

```python
import json
from jwcrypto import jwk, jwt

with open("test-issuer.jsonld") as f:
    vc = json.load(f)

key = jwk.JWK.from_pem(open("issuer-private.pem", "rb").read())

token = jwt.JWT(
    header={
        "alg": "PS256",
        "typ": "vc+jwt",
        "cty": "vc+ld+json",
        "kid": "did:web:example.org#key-1",
    },
    claims={
        "iss": vc["issuer"],
        "vc": vc,
    },
)
token.make_signed_token(key)
print(token.serialize())  # the signed VC-JWT
```

For an end-to-end reference, see the Gaia-X Digital Clearing House test suite
under `gaiax-docs/gxdch/gxdch_test/loire/`, which signs and verifies Loire
credentials against the official Compliance Engine.

## Submitting to the Federated Catalogue

POST the signed VC-JWT to `/assets`. The endpoint accepts either
`multipart/form-data` or `application/octet-stream`. The Content-Type signals
the credential format.

The IANA-registered media type for signed Verifiable Credential JWTs is:

- `application/vc+jwt` — W3C VC WG, IANA-registered.

```bash
# raw body upload
curl -X POST http://localhost:8081/assets \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/vc+jwt" \
  --data-binary @test-issuer.vc.jwt
```

```bash
# multipart upload
curl -X POST http://localhost:8081/assets \
  -H "Authorization: Bearer ${TOKEN}" \
  -F "file=@test-issuer.vc.jwt;type=application/vc+jwt"
```

The catalogue verifies:

1. The JWT signature, using the public key resolved via `iss` + `kid`.
2. The trust chain (`x5u`/`x5c`) terminates at a Gaia-X Trust Anchor.
3. The embedded credential validates against the loaded SHACL shapes
   (`trustframeworks/gaia-x-2511/shapes.ttl`).
4. The credential's `validFrom`/`validUntil` window is current.

On success, the catalogue returns `201 Created` with a `Location` header
pointing at `/assets/{id}`. Once stored, the credentials can be exercised by
the queries in the Architecture Appendix.

## Verifying the appendix queries end-to-end

[`verify-against-fuseki.hurl`](./verify-against-fuseki.hurl) ingests all five payloads against a running Fuseki-backed
stack and asserts that every SPARQL query from the Architecture Appendix returns the documented bindings:

```bash
cd examples/queries
hurl --variable token=$(../auth.sh) \
     --variable baseUrl=http://localhost:8081 \
     --test verify-against-fuseki.hurl
```

`hurl -v` prints one HTTP exchange per request; `hurl --vv` prints full request/response bodies; `hurl --curl out.sh`
(hurl 5.x+) writes an equivalent curl command per request if you need to hand a single call to a teammate without hurl.

## Recommended ingestion order

The examples reference each other, so ingest them in this order to keep
referential integrity in the graph:

1. `test-issuer.jsonld`           — base Legal Person.
2. `test-issuer2.jsonld`          — second Legal Person.
3. `test-issuer3.jsonld`          — references `test-issuer-1` via `gx:subOrganisationOf`.
4. `credentialSubject2.jsonld`    — Service Offering provided by `test-issuer-1`.
5. `serviceElasticSearch.jsonld`  — Service Offering that depends on `service-1`.
