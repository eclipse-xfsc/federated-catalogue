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
- `curl`, `jq`, `sha256sum`, and [`hurl`](https://hurl.dev) (≥ 4.x).

## How to run

The whole scenario is one executable [hurl](https://hurl.dev) file —
[`dcs-template-demo.hurl`](./dcs-template-demo.hurl) — that captures responses, asserts what should come back, and
chains the steps together. It is both the demo *and* the integration test.

```bash
cd examples/dcs-template-demo
hurl --variable token=$(../auth.sh) \
     --variable baseUrl=http://localhost:8081 \
     --test dcs-template-demo.hurl
```

Replay one step at a time with `--to-entry N`. Each entry below corresponds to a numbered block in the .hurl file:

| #  | Entry                                                           | What it proves                                                                                              |
|----|-----------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------|
| 1  | `GET /admin/graph-database`                                     | Fuseki is the active backend                                                                                |
| 2  | `POST /assets` (`dpa-template-v1.jsonld`)                       | Machine-readable template is stored                                                                         |
| 3  | `POST /assets/.../human-readable` (`dpa-template-v1.md`)        | Human-readable rendering is linked to the metadata VC                                                       |
| 4  | `POST /assets/.../provenance` (`provenance-v1-created.jsonld`)  | `prov:Activity` created by Alice is recorded                                                                |
| 5  | `POST /assets/.../provenance` (`provenance-v1-approved.jsonld`) | Approval by Bob; lifecycle state derivable from latest event                                                |
| 6  | `POST /query` SPARQL — latest approved DPA for DE               | CWE discovers v1                                                                                            |
| 7  | `GET /assets/.../human-readable`, asserts SHA-256               | Downloaded body matches `dcs:humanReadableHash` claim                                                       |
| 8  | Publish v2 (machine-readable + human-readable + approval)       | `prov:wasDerivedFrom v1` is recorded                                                                        |
| 9  | Re-run the same SPARQL                                          | v2 now wins via `FILTER NOT EXISTS { ?newer prov:wasDerivedFrom+ ?template … }` — *no client change needed* |
| 10 | `GET /assets/.../provenance` and `.../versions`                 | Full audit trail and version lineage                                                                        |

Diagnostics: `hurl -v` for one-line HTTP per request, `hurl --vv` for full bodies, `hurl --curl out.sh` for an
equivalent curl command per request (hurl 5.x+).

### The headline query

The CWE-discovery SPARQL is the pedagogically interesting moment — it's what makes "publish v2" automatically promote v2
without any client-side bookkeeping. It lives in entries 6 and 9 of the .hurl file; here it is in isolation:

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

Pre-v2 publication this binds `?template = .../templates/dpa/v1`, `?version = "1.0.0"`,
`?hash = "4542119c..."`. After step 8 it binds v2:
`?version = "1.1.0"`, `?hash = "83e6ff59..."`. Both bindings are asserted by the hurl file.

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
