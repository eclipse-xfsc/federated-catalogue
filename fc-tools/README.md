# fc-tools

Developer tooling for the Federated Catalogue. **New users should start
at [`../examples/README.md`](../examples/README.md)** — that guide walks through running the catalogue locally and
exercising the API with concrete scenarios.

## What's here

| Path                            | Purpose                                                                                                                               |
|---------------------------------|---------------------------------------------------------------------------------------------------------------------------------------|
| `signing/`                      | **Current** Python signers — produce JWT-VC 2.0 and EVC/EVP envelopes that the post-Loire catalogue accepts                           |
| `signer/`                       | **Deprecated** Java signer — produces `JsonWebSignature2020` LD proofs (rejected by current FC). Kept for negative-test fixtures only |
| `credential-generator/`         | Generates the example Verifiable Credential payloads under `../examples/queries/`                                                     |
| `extract-ontology-hierarchy.py` | Pulls the Gaia-X OWL ontology and emits the SHACL subclass hierarchy used by the catalogue                                            |

## Interactive API client

The single source of truth for the API is [`../openapi/fc_openapi.yaml`](../openapi/fc_openapi.yaml). Import it into the
OpenAPI-aware client of your choice (Bruno, Insomnia, Postman, Hoppscotch, …) to get a per-endpoint workspace that stays
in sync with the spec.

For end-to-end scenarios — including how to authenticate against Keycloak, ingest assets, and query the graph — follow
the curl walkthroughs under [`../examples/`](../examples/). They are tool-agnostic; copy the curl into your client of
choice or run them as-is.

## History

Earlier revisions of this folder included Postman collections (`Federated Catalogue API.postman_collection.json`,
`Asset IRI Assignment Tests.postman_collection.json`,
`Remove SCHACL schema validation - Manual Tests.postman_collection.json`) and a Bruno collection (
`Eclipse XFSC Federated Catalogue/`). Both were retired: the OpenAPI spec now serves the same purpose with one click
of "Import", and the scenario demos moved to `../examples/` as tool-agnostic curl walkthroughs. `git log` recovers
either if a specific request shape is needed for archaeology.
