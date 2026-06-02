# Demo 2 — Admin: Graph backend switch

The catalogue stores assets in a persistence store (PostgreSQL) and projects RDF claims into a *separately configurable*
graph store. The Admin UI exposes a switch to change that projection between **Fuseki** (SPARQL) and **Neo4j**
(openCypher). Switching backends doesn't touch the stored assets — the persistence store stays authoritative; the
graph projection is rebuilt on the new backend.

This demo runs the switch end-to-end via hurl. Every step has a 1:1 OpenAPI operation listed in
[`../../openapi/fc_openapi.yaml`](../../openapi/fc_openapi.yaml).

## What you'll see

| Toggle                              | Visible effect                                                                                                                              |
|-------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------|
| `POST /admin/graph-database/switch` | A `gx:LegalPerson` ingested while Fuseki was active is queryable by both SPARQL (Fuseki) and openCypher (Neo4j) after the switch + rebuild. |

## Prerequisites

- Catalogue stack running: `cd ../../docker && docker compose --env-file dev.env up -d`.
- Keycloak credentials in [`../environments/local.env`](../environments/local.env) — the scenario authenticates inline
  on its first entry (see [`../README.md`](../README.md#authentication)).
- Some data already projected into the graph. Either run [`../queries/`](../queries/) first (seeds a few VCs) or accept
  that the openCypher query in step B.7 will return an empty result set against an empty graph.

## How to run

The scenario is in [`scenario-b-graph-switch.hurl`](./scenario-b-graph-switch.hurl). It **requires a manual
`fc-server` restart between parts B.4 and B.5** — the switch persists the new backend choice but the running JVM keeps
its current backend until restart. Hurl can't drive the restart itself, so the flow is split into two invocations:

```bash
cd examples/admin-toggles-demo

# Part 1 — auth + confirm Fuseki + switch to Neo4j (persisted, not yet active)
hurl --variables-file ../environments/local.env --to-entry 5 --test scenario-b-graph-switch.hurl

# Manual restart — hurl can't do this for you
docker compose --env-file ../../docker/dev.env restart server
until curl -fsS http://localhost:8081/actuator/health >/dev/null 2>&1; do sleep 2; done

# Part 2 — re-auth + rebuild on Neo4j + query via openCypher + reset to Fuseki
hurl --variables-file ../environments/local.env --from-entry 6 --test scenario-b-graph-switch.hurl

# Second restart needed to make the Fuseki reset take effect
docker compose --env-file ../../docker/dev.env restart server
```

Per-step assertions live as comments in [`scenario-b-graph-switch.hurl`](./scenario-b-graph-switch.hurl). The
pedagogically interesting moment is the openCypher query after the rebuild: same data, different query language,
*without re-upload*.

## What this demo proves

- **The graph store is replaceable, the data is not.** Switching backends doesn't lose data — the persistence store
  remains the source of truth and both Fuseki and Neo4j are projections.
- **Same questions, two query languages.** SPARQL on Fuseki and openCypher on Neo4j both surface the same logical
  answers, with backend-specific syntax.
- **The Admin UI is just a thin layer over these endpoints.** Anything the UI does, you can do with `curl` — what you
  see in this demo is what the UI sees behind its buttons.

## Why no SHACL-toggle scenario?

An earlier draft of this demo also paired the SHACL admin toggle with an upload that violated the loaded shapes,
asserting *"same payload accepted when SHACL is off, rejected when SHACL is on."* That demonstration no longer holds:
per **CAT-FR-SF-04**, upload-time SHACL validation has been removed from `POST /assets`. The admin SHACL toggle still
exists and still does work — it gates `POST /assets/validate` (on-demand validation) and the background revalidation
sweep — but the upload-time cause-and-effect that made for a clean demo is gone by design. See
`CredentialVerificationStrategy.java` and `AssetValidationServiceImpl.java` for the current behaviour.
