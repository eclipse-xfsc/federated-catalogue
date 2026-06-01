# Demo 1 — DCS Template Repository

This demo shows how the Federated Catalogue serves as the **Template Repository** component of the FACIS Digital
Contracting Service (DCS). Per [SRS FACIS.DCS §2.2.1](../../../design-documents/01-input/SRS_FACIS_DCS.txt), the
Template Repository (TR) is responsible for storing machine-readable contract templates, their human-readable
renderings, search/discovery, versioning, and provenance — capabilities the catalogue already provides.

## Story

> **Alice**, a legal-team steward at *deltaDAO* (a DCS participant), publishes a "Data Processing Agreement" (DPA)
> contract template.
> **Bob**, a senior reviewer at the same participant, approves it.
> Later, **cwe@deltaDAO** — the Contract Workflow Engine acting on behalf of a deal-initiator — queries the catalogue to
> find the latest approved DPA template for jurisdiction `DE`, downloads its human-readable rendering, and verifies the
> hash before initiating a contract with a counterparty.
> Over time, the template is revised to v1.1.0; the same query now surfaces v2, while v1 remains discoverable via
> version history and provenance.

## Files

| File                            | Purpose                                                           |
|---------------------------------|-------------------------------------------------------------------|
| `dpa-template-v1.jsonld`        | Machine-readable template metadata VC (v1.0.0)                    |
| `dpa-template-v1.md`            | Human-readable DPA template body, v1.0.0, with `{{placeholders}}` |
| `dpa-template-v2.jsonld`        | Revised metadata VC (v1.1.0); declares `prov:wasDerivedFrom` v1   |
| `dpa-template-v2.md`            | Revised human-readable body                                       |
| `provenance-v1-created.jsonld`  | PROV-O activity: Alice created v1                                 |
| `provenance-v1-approved.jsonld` | PROV-O activity: Bob approved v1                                  |
| `provenance-v2-approved.jsonld` | PROV-O activity: Bob approved v2                                  |

## Vocabulary

The metadata uses three vocabularies:

| Prefix    | IRI                             | Used for                                                                                                                                 |
|-----------|---------------------------------|------------------------------------------------------------------------------------------------------------------------------------------|
| `dcs:`    | `https://w3id.org/facis/dcs/1#` | DCS-specific predicates (`dcs:ContractTemplate`, `dcs:category`, `dcs:jurisdiction`, `dcs:state`, `dcs:humanReadableHash`, `dcs:action`) |
| `prov:`   | `http://www.w3.org/ns/prov#`    | Provenance — actors, activities, derivation lineage. Pure W3C PROV-O.                                                                    |
| `schema:` | `https://schema.org/`           | Generic descriptive metadata (`schema:name`, `schema:description`, `schema:version`, `schema:inLanguage`)                                |

## Prerequisites

- Federated Catalogue stack running: `cd ../../docker && docker compose --env-file dev.env up -d` (Fuseki backend, see [
  `../README.md`](../README.md)).
- A Keycloak user `alice` with a role permitted to manage assets (e.g. `Ro-MU-CA` or `ADMIN_ALL`). See [
  `../../docker/README.md`](../../docker/README.md) §Keycloak setup.
- For step 7 onward, a second user `bob` with the same role (or reuse `alice`; in this demo Alice and Bob represent two
  roles, not strictly two users).
- `curl`, `jq`, `sha256sum`.

## Walkthrough

The walkthrough below is **curl-first**. Every step has a 1:1 counterpart in the Bruno collection at
`fc-tools/Eclipse XFSC Federated Catalogue/Demos/01 DCS Template Repository/` — open Bruno if you prefer a GUI.

### 0. Get a token

Use the shared helper at [`../auth.sh`](../auth.sh):

```bash
cd /path/to/federated-catalogue/examples/dcs-template-demo
export TOKEN=$(FC_USERNAME=alice FC_PASSWORD=alice ../auth.sh)
```

The helper fetches an access token via Keycloak's password grant, writes it to `.token` (and the refresh token to
`.refresh_token`) in the current directory, and prints the token on stdout. Subsequent runs use the refresh token
silently. Defaults: `admin`/`admin`, Keycloak at `http://localhost:8080`, realm `gaia-x`, client secret read from
`../../docker/dev.env`. Override any of `FC_USERNAME`, `FC_PASSWORD`, `FC_CLIENT_ID`, `FC_CLIENT_SECRET`,
`KEYCLOAK_URL`, `KEYCLOAK_REALM` via env vars.

### 1. Confirm Fuseki is the active query backend

```bash
curl -sS -H "Authorization: Bearer $TOKEN" \
  http://localhost:8081/query/backend | jq .
```

Expect `"backend": "fuseki"`.

### 2. Upload the machine-readable template (v1)

```bash
curl -sS -X POST http://localhost:8081/assets \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/ld+json" \
  --data-binary @dpa-template-v1.jsonld | jq .
```

Note the `id` field in the response — that is the catalogue's **asset IRI** for v1. Export it:

```bash
export TPL_V1_ID="https://deltadao.example.org/templates/dpa/v1"
```

### 3. Upload and link the human-readable rendering

```bash
curl -sS -X POST "http://localhost:8081/assets/$(printf %s "$TPL_V1_ID" | jq -sRr @uri)/human-readable" \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@dpa-template-v1.md;type=text/markdown" | jq .
```

### 4. Attach creation provenance (Alice)

```bash
curl -sS -X POST "http://localhost:8081/assets/$(printf %s "$TPL_V1_ID" | jq -sRr @uri)/provenance" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  --data-binary @provenance-v1-created.jsonld | jq .
```

### 5. Attach approval provenance (Bob)

```bash
curl -sS -X POST "http://localhost:8081/assets/$(printf %s "$TPL_V1_ID" | jq -sRr @uri)/provenance" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  --data-binary @provenance-v1-approved.jsonld | jq .
```

Conceptually, the template is now "approved" — the catalogue holds two PROV-O activity VCs whose `dcs:action` values
progress `created` → `approved`. CWE can derive the current state by querying the latest provenance event.

### 6. CWE discovery — find the latest approved DPA for jurisdiction DE

```sparql
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

```bash
curl -sS -X POST http://localhost:8081/query \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/sparql-query" \
  -H "Accept: application/sparql-results+json" \
  --data-binary @- <<'SPARQL' | jq .
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
SPARQL
```

Expect one binding: `?template = https://deltadao.example.org/templates/dpa/v1`, `?version = "1.0.0"`,
`?hash = "0d30a254..."`.

### 7. Download the rendering and verify the hash

```bash
curl -fsS "http://localhost:8081/assets/$(printf %s "$TPL_V1_ID" | jq -sRr @uri)/human-readable" \
  -H "Authorization: Bearer $TOKEN" \
  -o /tmp/dpa-v1.md
sha256sum /tmp/dpa-v1.md
```

Compare against `0d30a254b01a6379912b1d61058affac6a770a895b94b7a64ab3bd5fa0383d2b` (the `dcs:humanReadableHash` claim).
Match ⇒ the template is intact and CWE can safely use it.

### 8. Publish v2 — revised template

```bash
curl -sS -X POST http://localhost:8081/assets \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/ld+json" \
  --data-binary @dpa-template-v2.jsonld | jq .

export TPL_V2_ID="https://deltadao.example.org/templates/dpa/v2"

curl -sS -X POST "http://localhost:8081/assets/$(printf %s "$TPL_V2_ID" | jq -sRr @uri)/human-readable" \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@dpa-template-v2.md;type=text/markdown" | jq .

curl -sS -X POST "http://localhost:8081/assets/$(printf %s "$TPL_V2_ID" | jq -sRr @uri)/provenance" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  --data-binary @provenance-v2-approved.jsonld | jq .
```

The v2 metadata VC declares `prov:wasDerivedFrom v1` — that statement, plus the `approved` provenance activity on v2,
satisfies the discovery query's `FILTER NOT EXISTS` clause for v1.

### 9. CWE re-discovery — same query, different answer

Re-run the query from step 6. The binding is now `?template = .../templates/dpa/v2`, `?version = "1.1.0"`,
`?hash = "07add930..."`. v1 has been silently superseded — no client change needed.

### 10. Audit trail — list provenance and version history

```bash
# Full provenance log for v1 — created (Alice) → approved (Bob)
curl -sS -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8081/assets/$(printf %s "$TPL_V1_ID" | jq -sRr @uri)/provenance" | jq .

# Version history walks the prov:wasDerivedFrom chain
curl -sS -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8081/assets/$(printf %s "$TPL_V2_ID" | jq -sRr @uri)/versions" | jq .
```

## What this demo proves

- **Template = machine-readable VC + human-readable file linked by asset IRI.** The catalogue's `Add asset` /
  `Upload and link human-readable representation` endpoints carry both forms.
- **Discovery = SPARQL over the graph.** CWE doesn't need to know about specific templates ahead of time — it queries by
  category, jurisdiction, and `dcs:action = "approved"`.
- **Integrity = `dcs:humanReadableHash`.** CWE verifies the rendering it downloads against the stored hash before using
  the template.
- **Versioning = `prov:wasDerivedFrom` chain.** A new template version supersedes the old one *in the discovery query*,
  with no client code change.
- **Provenance = PROV-O activities as separate VCs.** Each lifecycle event (created, approved, …) is its own credential,
  queryable as a PROV-O graph and signable by the actor responsible.
- **Per SRS §2.2.1**, all required functions are covered: template storage, human-readable rendering, identifier (asset
  IRI + `dcs:templateUuid`), provenance, versioning, search, hash-based integrity. No DCS-specific endpoint needed.

## Signing the fixtures for strict mode

The walkthrough above runs against the catalogue's **non-strict (default)** verification profile, which accepts the
fixtures as raw JSON-LD. To run the same flow against the **strict** profile (`./dev.sh strict`), each VC must arrive as
a signed VC-JWT 2.0.

### Where the canonical signer should live, and why it doesn't yet

The XFSC **Trust Service API (TSA)** — [`eclipse-xfsc/tsa-service`](https://github.com/eclipse-xfsc/tsa-service) — is
the federation's canonical signing/verification service. In production, DCS components and other consumers obtain VC
signatures by calling TSA; the FACIS architecture treats TSA as the natural counterpart to the catalogue's verification
side.

**TSA does not yet support W3C Verifiable Credentials Data Model 2.0** (validFrom/validUntil, EVC/EVP envelopes, the
post-Loire JWT shape the catalogue requires). Until that gap closes, we sign locally with the Python utilities under [
`../../fc-tools/signing/`](../../fc-tools/signing) — same JOSE primitives, same outputs, just shell-driven instead of an
HTTP service. Once TSA gains VC 2.0 support, the recommended flow flips to "POST the fixture to TSA, attach the returned
JWT," and these scripts can be retired.

The Java signer under [`../../fc-tools/signer/`](../../fc-tools/signer) is **deprecated** — it produces
`JsonWebSignature2020` Linked-Data Proofs which the post-Loire catalogue rejects (
`CredentialVerificationStrategy.java:574`). Useful only as a source of negative-test fixtures.

### Signing each fixture as VC-JWT 2.0

Prerequisites:

```bash
pip install 'PyJWT[crypto]' cryptography
```

Then sign every fixture in this folder using the local docker stack's `did:web:did-server` key:

```bash
SIGN=../../fc-tools/signing/generate-jwt-fixture.py
KEY=../../docker/did-server/certs/jwt-signing.pem

for f in dpa-template-v1.jsonld dpa-template-v2.jsonld \
         provenance-v1-created.jsonld provenance-v1-approved.jsonld \
         provenance-v2-approved.jsonld; do
  python3 "$SIGN" --payload "$f" --key "$KEY" --output "${f%.jsonld}.vc.jwt"
done
```

You'll get one `*.vc.jwt` per fixture. Replace the body of each curl step that currently sends `application/ld+json`
with the signed token and switch the Content-Type:

```bash
curl -sS -X POST http://localhost:8081/assets \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/vc+jwt" \
  --data-binary @dpa-template-v1.vc.jwt | jq .
```

`application/vc+jwt` is the IANA-registered media type for signed Verifiable Credential JWTs (see [
`../queries/README.md`](../queries/README.md) §Submitting to the Federated Catalogue for the verification chain).

To also sign the markdown bodies (so the rendering itself carries an attestation, not only the hash referenced inside
the metadata VC), wrap the file content in a small "human-readable attestation" VC first, then run the same script. The
demo's hash-only integrity check is sufficient for most use cases; signed renderings are a hardening step for
high-assurance contracting.

> Once TSA supports VC 2.0, this section becomes: *"POST the fixture and your participant DID to your tenant's TSA
endpoint; attach the returned JWT to the asset upload."* The Python scripts remain as an offline fallback for air-gapped
> environments and CI.

## Other production notes

- **IRI scheme**: the demo uses `https://deltadao.example.org/templates/dpa/v{n}` for template IRIs. Production
  deployments should use IRIs under the participant's resolvable DID-web origin.
- **`dcs:state` field**: the metadata VC declares `"draft"`. The *current* state is derived from the latest provenance
  activity's `dcs:action` / `dcs:newState`. We deliberately keep the metadata-VC `state` as the *initial* state at
  publication; it's the provenance graph that records subsequent transitions. This avoids needing to rewrite the
  metadata VC on every state change.
