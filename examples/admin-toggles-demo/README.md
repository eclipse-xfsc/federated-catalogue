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
- Keycloak credentials in [`../environments/local.env`](../environments/local.env) — each scenario authenticates inline
  on its first entry (see [`../README.md`](../README.md#authentication)).

---

## Scenario A — SHACL validation on/off

Executable as [`scenario-a-schema-validation.hurl`](./scenario-a-schema-validation.hurl):

```bash
cd examples/admin-toggles-demo
hurl --variables-file ../environments/local.env --test scenario-a-schema-validation.hurl
```

The scenario uses [`legal-person-shacl-invalid.jsonld`](./legal-person-shacl-invalid.jsonld), a `gx:LegalPerson` that
deliberately violates three constraints of the loaded Gaia-X 2511 shapes:

1. No `gx:registrationNumber` (SHACL requires one).
2. No `gx:headquartersAddress` (SHACL requires both addresses).
3. `gx:countryCode` is `"Germany"`, which is not in the ISO-3166-1 alpha-2/alpha-3/numeric enum the shape allows.

The credential is **semantically valid Loire** (correct type, valid JSON-LD, valid VC-2.0 envelope). Only the
*shape-level* rules from the registry are violated.

The headline assertion: the same payload that is *accepted* with SHACL off is *rejected with violation details* once
SHACL is turned on — and the previously-stored asset can be re-validated retrospectively, proving that storage is
independent of validation policy. See the comments in [
`scenario-a-schema-validation.hurl`](./scenario-a-schema-validation.hurl)
for the step-by-step.

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

# Part 1 — auth + confirm Fuseki + switch to Neo4j (persisted, not yet active)
hurl --variables-file ../environments/local.env --to-entry 5 --test scenario-b-graph-switch.hurl

# Manual restart — hurl can't do this for you
docker compose --env-file ../../docker/dev.env restart fc-server
until curl -fsS http://localhost:8081/actuator/health >/dev/null 2>&1; do sleep 2; done

# Part 2 — re-auth + rebuild on Neo4j + query via openCypher + reset to Fuseki
hurl --variables-file ../environments/local.env --from-entry 6 --test scenario-b-graph-switch.hurl

# Second restart needed to make the Fuseki reset take effect
docker compose --env-file ../../docker/dev.env restart fc-server
```

Per-step assertions live as comments in [`scenario-b-graph-switch.hurl`](./scenario-b-graph-switch.hurl). The
pedagogically interesting moment is the openCypher query after the rebuild: same data, different query language,
*without re-upload*.

If you haven't run Demo 1 yet, the Neo4j-side openCypher query returns an empty result set. Either run the DCS demo
first
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
