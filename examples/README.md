# Federated Catalogue — Getting Started

This folder is the front door for new users. Each demo ships as an executable [hurl](https://hurl.dev) file — `hurl` is
a small CLI that runs HTTP requests from a plain-text file and asserts the responses. The same files double as the
narrative for the demo and as the integration test that proves the catalogue still behaves the way the docs claim.

Prefer a GUI? Import [`../openapi/fc_openapi.yaml`](../openapi/fc_openapi.yaml) into your favourite OpenAPI client
(Bruno, Insomnia, Postman, Hoppscotch, …) and replay the same calls from there.

## Demos

| Path                                                     | Topic                                                                                                                                                                                                                                                                      | Status        |
|----------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------|
| [`dcs-template-demo/`](./dcs-template-demo/README.md)    | **Primary demo** — using the catalogue as the DCS Template Repository: publish a DPA contract template (machine-readable VC + human-readable rendering), attach PROV-O provenance, let CWE discover the latest approved version, revise to v2, follow the version lineage. | ✅ Ready       |
| [`admin-toggles-demo/`](./admin-toggles-demo/README.md)  | Show that Admin UI toggles change catalogue behaviour: schema validation on/off, graph backend switch (Fuseki ↔ Neo4j).                                                                                                                                                    | ✅ Ready       |
| [`queries/`](./queries/README.md)                        | The Loire VC payloads referenced by the Architecture Document's appendix queries, plus a verification script.                                                                                                                                                              | ✅ Ready       |
| [`future-gxdch-appendix.md`](./future-gxdch-appendix.md) | How to test Loire compliance against a real GXDCH (x5c binding, no local did:web). Documented; preconditions (TSP cert chain, notarisable participant, registered profile) listed.                                                                                         | 📄 Documented |

## Prerequisites

- The catalogue stack running locally: `cd ../docker && docker compose --env-file dev.env up -d` (Fuseki backend,
  signature checks off by default — see [`../docker/README.md`](../docker/README.md) for strict mode).
- Keycloak bootstrapped with at least one user that has the `Ro-MU-CA` or `ADMIN_ALL` role (the dev realm bundled
  at `../keycloak/realms/dev/fc-realm.json` provides this — user `fc-ca-test`).
- [`hurl`](https://hurl.dev) ≥ 4.x for the executable demos. `curl`, `jq`, `sha256sum`, `python3` also expected in your
  `$PATH` for ad-hoc poking and the signing helpers.
- `127.0.0.1 key-server` in `/etc/hosts` (per [`../docker/README.md`](../docker/README.md#keycloak-setup)) so the
  token's `iss` claim matches what fc-server expects.

## Authentication

Each `.hurl` scenario starts with an inline Keycloak password-grant step that captures the access token into a
`{{token}}` variable. Subsequent requests use `Authorization: Bearer {{token}}`. No shell wrapper is required — hurl
handles the full flow itself.

The Keycloak coordinates (URL, realm, client, user, password) live in the environment file passed via
`--variables-file`. The bundled [`environments/local.env`](./environments/local.env) targets the docker-compose stack
with the dev realm's defaults; copy [`environments/qa.env.example`](./environments/qa.env.example) for hosted stacks.

```bash
# Run the primary demo against the local stack
hurl --variables-file environments/local.env --test dcs-template-demo/dcs-template-demo.hurl

# Run a single entry (handy while debugging — entry 1 is auth, demo steps start at 2)
hurl --variables-file environments/local.env --to-entry 5 dcs-template-demo/dcs-template-demo.hurl

# Point at a hosted QA stack
cp environments/qa.env.example environments/qa.env
$EDITOR environments/qa.env
hurl --variables-file environments/qa.env --test dcs-template-demo/dcs-template-demo.hurl
```

**Convention:** one `KEY=VALUE` file per target stack under `environments/`. All files except `local.env` and
`*.env.example` are gitignored — copy the example and never commit your tenant's secrets.

## Signing fixtures for strict mode

The default catalogue profile accepts unsigned JSON-LD. For the strict profile, each VC must be signed as a VC-JWT 2.0.
See the dedicated section in [
`dcs-template-demo/README.md`](./dcs-template-demo/README.md#signing-the-fixtures-for-strict-mode) — the same flow
applies to every demo.

Short version: the XFSC **Trust Service API (TSA)** is the canonical signer, but it doesn't yet support W3C VC 2.0.
Until it does, we use the Python utilities under [`../fc-tools/signing/`](../fc-tools/signing) which produce the same
JWT shapes the catalogue's verifier expects.
