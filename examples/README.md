# Federated Catalogue — Getting Started

This folder is the front door for new users. The walkthroughs are tool-agnostic: every step is shown as `curl`. If you
prefer a GUI, import [`../openapi/fc_openapi.yaml`](../openapi/fc_openapi.yaml) into your favourite OpenAPI client (
Bruno, Insomnia, Postman, …) and replay the same calls from there.

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
- Keycloak bootstrapped with at least one user that has the `Ro-MU-CA` or `ADMIN_ALL` role.
- `curl`, `jq`, `sha256sum`, `python3` in your `$PATH`.

## Authenticating curl

Every demo starts by getting a Keycloak access token. Use [`./auth.sh`](./auth.sh) — it handles password grant on first
call and silent refresh thereafter:

```bash
export TOKEN=$(./auth.sh)                                  # admin/admin defaults
export TOKEN=$(FC_USERNAME=alice FC_PASSWORD=alice ./auth.sh)
```

The token is also written to `.token` in the current directory, so you can use
`--header "Authorization: Bearer $(cat .token)"` in scripts without re-running. `.token` and `.refresh_token` are
gitignored.

## Signing fixtures for strict mode

The default catalogue profile accepts unsigned JSON-LD. For the strict profile, each VC must be signed as a VC-JWT 2.0.
See the dedicated section in [
`dcs-template-demo/README.md`](./dcs-template-demo/README.md#signing-the-fixtures-for-strict-mode) — the same flow
applies to every demo.

Short version: the XFSC **Trust Service API (TSA)** is the canonical signer, but it doesn't yet support W3C VC 2.0.
Until it does, we use the Python utilities under [`../fc-tools/signing/`](../fc-tools/signing) which produce the same
JWT shapes the catalogue's verifier expects.
