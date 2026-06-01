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

This scenario uses [`legal-person-shacl-invalid.jsonld`](./legal-person-shacl-invalid.jsonld), a `gx:LegalPerson` that
deliberately violates three constraints of the loaded Gaia-X 2511 shapes:

1. No `gx:registrationNumber` (SHACL requires one).
2. No `gx:headquartersAddress` (SHACL requires both addresses).
3. `gx:countryCode` is `"Germany"`, which is not in the ISO-3166-1 alpha-2/alpha-3/numeric enum the shape allows.

The credential is **semantically valid Loire** (correct type, valid JSON-LD, valid VC-2.0 envelope). Only the
*shape-level* rules from the registry are violated.

### A.1 Confirm the dev default — SHACL is off

```bash
curl -sS -H "Authorization: Bearer $TOKEN" \
  http://localhost:8081/admin/schema-validation | jq '.modules[] | {type, enabled}'
```

Expect `{type: "SHACL", enabled: false}` (per `dev.env`: signature checks and SHACL are off by default; see [
`../../docker/README.md`](../../docker/README.md) §Verification Defaults).

### A.2 Ingest the invalid payload — it succeeds

```bash
curl -sS -X POST http://localhost:8081/assets \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/ld+json" \
  --data-binary @legal-person-shacl-invalid.jsonld | jq .
```

201 Created. The catalogue extracts triples and stores the asset; nothing checks shapes.

### A.3 Turn SHACL on

```bash
curl -sS -X PATCH http://localhost:8081/admin/schema-validation/modules/SHACL \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/merge-patch+json" \
  -d '{"enabled": true}'
```

Re-read the status to confirm:

```bash
curl -sS -H "Authorization: Bearer $TOKEN" \
  http://localhost:8081/admin/schema-validation | jq '.modules[] | select(.type=="SHACL")'
```

### A.4 Ingest the same payload — now rejected

Change the asset `id` (the previously-stored asset would otherwise conflict on hash) and post again:

```bash
sed 's|/legal-person-broken.json|/legal-person-broken-2.json|' \
  legal-person-shacl-invalid.jsonld \
  | curl -sS -X POST http://localhost:8081/assets \
      -H "Authorization: Bearer $TOKEN" \
      -H "Content-Type: application/ld+json" \
      --data-binary @- | jq .
```

400 Bad Request. The response body lists each SHACL violation — missing `gx:registrationNumber`, missing
`gx:headquartersAddress`, `gx:countryCode` not in the allowed enum. *The same payload that was accepted in A.2 is now
refused.*

### A.5 Verify a stored asset against the (now-active) shapes

The asset stored in A.2 is still in the catalogue. `/assets/validate` re-runs the active modules against it without
ingesting again:

```bash
curl -sS -X POST http://localhost:8081/assets/validate \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"assetIds": ["https://www.example.org/credentials/legal-person-broken.json"]}' | jq .
```

Same violation list. Storage is independent of validation policy — you can flip the toggle to retrospectively assess
what's in the graph.

### A.6 Reset

```bash
curl -sS -X PATCH http://localhost:8081/admin/schema-validation/modules/SHACL \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/merge-patch+json" \
  -d '{"enabled": false}'
```

---

## Scenario B — Graph backend switch (Fuseki → Neo4j)

The catalogue stores assets in a persistence store (PostgreSQL) and projects RDF claims into a *separately configurable*
graph store. Switching backends doesn't touch the assets — it rebuilds the graph projection on the new backend.

> **Note:** the switch persists the choice but the running JVM keeps its current backend. The change becomes effective
> after the catalogue server restarts, and the graph must then be rebuilt from the persistence store. The demo therefore
> includes the restart and rebuild steps explicitly.

### B.1 Confirm Fuseki is active

```bash
curl -sS -H "Authorization: Bearer $TOKEN" \
  http://localhost:8081/admin/graph-database | jq .
```

Expect `{"backend": "FUSEKI", ...}`.

### B.2 Ingest the DCS template fixtures (reuse Demo 1)

If you've already run Demo 1, skip this. Otherwise:

```bash
for f in ../dcs-template-demo/dpa-template-v1.jsonld \
         ../dcs-template-demo/dpa-template-v2.jsonld; do
  curl -sS -X POST http://localhost:8081/assets \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/ld+json" \
    --data-binary @"$f" | jq -c '{id, status}'
done
```

### B.3 Query via SPARQL (Fuseki)

```bash
curl -sS -X POST http://localhost:8081/query \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/sparql-query" \
  -H "Accept: application/sparql-results+json" \
  --data-binary @- <<'SPARQL' | jq '.results.bindings'
PREFIX dcs:    <https://w3id.org/facis/dcs/1#>
PREFIX schema: <https://schema.org/>
SELECT ?template ?name WHERE {
  ?template a dcs:ContractTemplate ;
            schema:name ?name .
}
SPARQL
```

Expect two bindings (v1 + v2).

### B.4 Switch to Neo4j

```bash
curl -sS -X POST http://localhost:8081/admin/graph-database/switch \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"backend": "NEO4J"}' | jq .
```

### B.5 Restart the catalogue server

```bash
docker compose --env-file dev.env restart fc-server
```

Wait until `http://localhost:8081/actuator/health` reports `UP` (5–15 s).

### B.6 Rebuild the graph projection

```bash
curl -sS -X POST -H "Authorization: Bearer $TOKEN" \
  http://localhost:8081/admin/graph/rebuild | jq .

# Poll until done (status: COMPLETED)
until [ "$(curl -sS -H "Authorization: Bearer $TOKEN" \
            http://localhost:8081/admin/graph/rebuild/status \
            | jq -r .status)" = "COMPLETED" ]; do sleep 2; done
```

### B.7 Query the same data via openCypher (Neo4j)

```bash
curl -sS -X POST http://localhost:8081/query \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/opencypher-query" \
  --data-binary 'MATCH (t:ContractTemplate) RETURN t.uri AS template, t.name AS name'
```

Expect the same two rows. The data was ingested once, persisted in PostgreSQL, and the catalogue re-projected it into
Neo4j during the rebuild — *no re-upload needed*.

### B.8 Reset to Fuseki

```bash
curl -sS -X POST http://localhost:8081/admin/graph-database/switch \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"backend": "FUSEKI"}'
docker compose --env-file dev.env restart fc-server
curl -sS -X POST -H "Authorization: Bearer $TOKEN" \
  http://localhost:8081/admin/graph/rebuild
```

## What this demo proves

- **Toggles are real, not cosmetic.** Disabling SHACL changes which assets are accepted; the previously-stored asset is
  still there and can be re-validated retrospectively when SHACL is re-enabled.
- **The graph store is replaceable, the data is not.** Switching backends doesn't lose data — the persistence store
  remains the source of truth and both Fuseki and Neo4j are projections.
- **Same questions, two query languages.** SPARQL on Fuseki and openCypher on Neo4j both surface the same logical
  answers, with backend-specific syntax.
- **The Admin UI is just a thin layer over these endpoints.** Anything the UI does, you can do with `curl` — what you
  see in this demo is what the UI sees behind its buttons.
