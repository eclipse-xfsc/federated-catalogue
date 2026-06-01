# Demo 2 — Admin toggles change behaviour

The catalogue's Admin UI exposes two operational switches that change what the catalogue accepts and how it answers
queries. This demo shows the cause-and-effect of each, end-to-end via curl. Every step has a 1:1 OpenAPI operation
listed in [`../../openapi/fc_openapi.yaml`](../../openapi/fc_openapi.yaml).

## What you'll see

| Scenario                     | Toggle                                         | Visible effect                                                                                                                              |
|------------------------------|------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------|
| **A** — SHACL on/off         | `PATCH /admin/schema-validation/modules/SHACL` | Same payload is stored when SHACL is off and rejected with violation details when SHACL is on.                                              |
| **B** — Graph backend switch | `POST /admin/graph-database/switch`            | A `gx:LegalPerson` ingested while Fuseki was active is queryable by both SPARQL (Fuseki) and openCypher (Neo4j) after the switch + rebuild. |

## Prerequisites

- Catalogue stack running: `cd ../../docker && docker compose --env-file dev.env up -d`.
- Token in `$TOKEN`: `export TOKEN=$(../auth.sh)` (see [`../README.md`](../README.md#authenticating-curl)).

---

## Scenario A — SHACL validation on/off

Executable as [`scenario-a-schema-validation.hurl`](./scenario-a-schema-validation.hurl):

```bash
cd examples/admin-toggles-demo
hurl --variable token=$(../auth.sh) \
     --variable baseUrl=http://localhost:8081 \
     --test scenario-a-schema-validation.hurl
```

The scenario uses [`legal-person-shacl-invalid.jsonld`](./legal-person-shacl-invalid.jsonld), a `gx:LegalPerson` that
deliberately violates three constraints of the loaded Gaia-X 2511 shapes:

1. No `gx:registrationNumber` (SHACL requires one).
2. No `gx:headquartersAddress` (SHACL requires both addresses).
3. `gx:countryCode` is `"Germany"`, which is not in the ISO-3166-1 alpha-2/alpha-3/numeric enum the shape allows.

The credential is **semantically valid Loire** (correct type, valid JSON-LD, valid VC-2.0 envelope). Only the
*shape-level* rules from the registry are violated.

| Entry                                                                                    | What it asserts                                                                                           |
|------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------|
| A.1 `GET /admin/schema-validation`                                                       | SHACL is `enabled: false` (dev default)                                                                   |
| A.2 `POST /assets` (invalid payload)                                                     | 201 — accepted because SHACL is off                                                                       |
| A.3 `PATCH /admin/schema-validation/modules/SHACL {"enabled": true}` then re-read status | SHACL now `enabled: true`                                                                                 |
| A.4 `POST /assets` (same payload, different id)                                          | **400 — rejected; the headline assertion of the demo**                                                    |
| A.5 `POST /assets/validate` against the asset stored in A.2                              | The already-stored asset can be re-validated retrospectively; storage is independent of validation policy |
| A.6 Reset SHACL to `enabled: false`                                                      | Leaves the catalogue in its starting state                                                                |

---

## Scenario B — Graph backend switch (Fuseki → Neo4j)

The catalogue stores assets in a persistence store (PostgreSQL) and projects RDF claims into a *separately configurable*
graph store. Switching backends doesn't touch the assets — it rebuilds the graph projection on the new backend.

> **Note:** the switch persists the choice but the running JVM keeps its current backend. The change becomes effective
> after the catalogue server restarts, and the graph must then be rebuilt from the persistence store. Hurl can't drive
> the restart itself, so [`scenario-b-graph-switch.hurl`](./scenario-b-graph-switch.hurl) is split: run the first half,
> restart `fc-server` manually, then resume.

```bash
cd examples/admin-toggles-demo

# Part 1 — confirm Fuseki, switch to Neo4j (persisted, not yet active)
hurl --variable token=$(../auth.sh) \
     --variable baseUrl=http://localhost:8081 \
     --to-entry 4 \
     --test scenario-b-graph-switch.hurl

# Manual restart — hurl can't do this for you
docker compose --env-file ../../docker/dev.env restart fc-server
until curl -fsS http://localhost:8081/actuator/health >/dev/null 2>&1; do sleep 2; done

# Part 2 — rebuild on Neo4j, query via openCypher, reset to Fuseki
hurl --variable token=$(../auth.sh) \
     --variable baseUrl=http://localhost:8081 \
     --from-entry 5 \
     --test scenario-b-graph-switch.hurl

# Second restart needed to make the Fuseki reset take effect
docker compose --env-file ../../docker/dev.env restart fc-server
```

| Entry                                                         | What it asserts                                                   |
|---------------------------------------------------------------|-------------------------------------------------------------------|
| B.1 `GET /admin/graph-database`                               | Fuseki is the active backend                                      |
| B.2 `POST /query` SPARQL `?s ?p ?o` LIMIT 5                   | Sanity — the graph is queryable                                   |
| B.3 `POST /admin/graph-database/switch {"backend": "NEO4J"}`  | Selection persisted (takes effect on restart)                     |
| B.4 `GET /admin/graph-database`                               | Read-back — backend still reports Fuseki *until* the restart      |
| —                                                             | **Manual:** `docker compose restart fc-server`                    |
| B.5 `POST /admin/graph/rebuild`                               | Catalogue re-projects RDF from PostgreSQL into Neo4j              |
| B.6 `GET /admin/graph/rebuild/status` (polled)                | Status reaches `COMPLETED`                                        |
| B.7 `POST /query` openCypher `MATCH (t:ContractTemplate) …`   | Same data answers the same logical question — *without re-upload* |
| B.8 `POST /admin/graph-database/switch {"backend": "FUSEKI"}` | Resets the persisted backend choice                               |

If you haven't run Demo 1 yet, the Neo4j-side query in B.7 returns an empty result set. Either run the DCS demo first
(see [`../dcs-template-demo/`](../dcs-template-demo/)) or seed the catalogue with [`../queries/`](../queries/) fixtures
before starting Scenario B.

## What this demo proves

- **Toggles are real, not cosmetic.** Disabling SHACL changes which assets are accepted; the previously-stored asset is
  still there and can be re-validated retrospectively when SHACL is re-enabled.
- **The graph store is replaceable, the data is not.** Switching backends doesn't lose data — the persistence store
  remains the source of truth and both Fuseki and Neo4j are projections.
- **Same questions, two query languages.** SPARQL on Fuseki and openCypher on Neo4j both surface the same logical
  answers, with backend-specific syntax.
- **The Admin UI is just a thin layer over these endpoints.** Anything the UI does, you can do with `curl` — what you
  see in this demo is what the UI sees behind its buttons.
