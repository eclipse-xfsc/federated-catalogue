# Operator Guide

Configuration and runtime tuning for operators of the Federated Catalogue.

For the architectural and conceptual description of the service, see the
[Architecture Document](https://github.com/eclipse-xfsc/docs/tree/main/federated-catalogue).

## Trust Framework Configuration

The Federated Catalogue activates trust frameworks via a list of **trust-framework family IDs**. Families listed at
startup are flipped to `enabled=true` in the persistence store; unlisted families remain disabled. Runtime changes go
through the trust-framework admin API.

### Configuration

| Property                                       | Type / format                  | Default | Description                                                                              |
|------------------------------------------------|--------------------------------|---------|------------------------------------------------------------------------------------------|
| `federated-catalogue.enabled-trust-frameworks` | comma-separated family-ID list | `""`    | Families to enable at startup. Empty = none enabled. Unknown IDs are logged and ignored. |

Environment-variable form: `FEDERATED_CATALOGUE_ENABLED_TRUST_FRAMEWORKS`.

### Example Configuration (application.yml)

```yaml
federated-catalogue:
  enabled-trust-frameworks: "gaia-x"
```

Or via environment:

```
FEDERATED_CATALOGUE_ENABLED_TRUST_FRAMEWORKS=gaia-x
```

The built-in family ID for Gaia-X is `gaia-x` (profile `gaia-x-2511`). Additional families are contributed via
trust-framework bundles; the admin API and the `getTrustFrameworks` endpoint expose the registered family IDs.

### Behavior

- **Empty list (default):** no trust-framework compliance is enforced at upload time. Signature verification still runs;
  any valid Verifiable Credential/Presentation can be stored.
- **One or more families listed:** each listed family is flipped to `enabled=true` at startup; credentials matching that
  family are validated against its trust-framework bundle (compliance checks, trust-anchor registry calls,
  ontology/SHACL shapes).

For the architecture of the trust-framework bundle / family / profile model, see the
[Architecture Document](https://github.com/eclipse-xfsc/docs/tree/main/federated-catalogue).

## Supported Credential Formats

The Federated Catalogue accepts the following Verifiable Credential formats for submission:

| Format               | Encoding                                          | Description                                                               |
|----------------------|---------------------------------------------------|---------------------------------------------------------------------------|
| **Gaia-X Loire JWT** | VC 2.0 JWT (`typ: vc+ld+json+jwt`)                | ICAM 24.07. Requires Gaia-X trust chain when `gaiax.enabled: true`.       |
| **Standard JWT-VC**  | VCDM 1.1 or 2.0 JWT with `vc`/`vp` wrapper claims | Compatible with Catena-X, EBSI (VCDM 1.1), and IDSA/DCP trust frameworks. |

**Not accepted:** VC 1.1 JSON-LD with Linked Data Proof (Gaia-X Tagus / Elbe, ICAM 22.10 and earlier). These credentials
use `https://www.w3.org/2018/credentials/v1` as context with an embedded `proof` block instead of a JWT envelope.
Submitting a credential in this format returns a `400` error.

## Gaia-X Loire Compatibility (2511 Ontology)

The Federated Catalogue supports the current Loire (Gaia-X 2511) credential format.

### Bundled Ontology and SHACL Shapes

| File                                                                        | Source                                                                                                                                | Purpose                                                            |
|-----------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------|
| `fc-service-core/src/main/resources/defaultschema/ontology/gx-2511.ttl`     | Stripped from Gaia-X 2511 OWL                                                                                                         | Class hierarchy for Loire type resolution (`rdfs:subClassOf` only) |
| `fc-service-core/src/main/resources/defaultschema/shacl/gx-2511-shapes.ttl` | [Gaia-X Trust Shape Registry](https://registry.lab.gaia-x.eu/development/api/trusted-shape-registry/v1/shapes/jsonld/trustframework#) | SHACL validation shapes for Loire credentials                      |

New submissions in Tagus credential format (VC 1.1 JSON-LD with Linked Data Proof) are not accepted and return a `400`
error at format detection.

### Namespace

| Namespace    | URI                             | Usage                                                           |
|--------------|---------------------------------|-----------------------------------------------------------------|
| Loire (2511) | `https://w3id.org/gaia-x/2511#` | Credential types (`gx:LegalPerson`, `gx:ServiceOffering`, etc.) |

Configure type resolution and document loader in `application.yml`:

```yaml
federated-catalogue:
  verification:
    participant:
      type: "https://w3id.org/gaia-x/2511#Participant"
    resource:
      type: "https://w3id.org/gaia-x/2511#Resource"
    service-offering:
      type: "https://w3id.org/gaia-x/2511#ServiceOffering"
    doc-loader:
      additional-context:
        '[https://w3id.org/gaia-x/2511#]': https://registry.lab.gaia-x.eu/development/context/2511
```

### Updating the bundled ontology

To update to a future 2511 release: replace `gx-2511.ttl` (run `fc-tools/extract-ontology-hierarchy.py` against the new
OWL file) and `gx-2511-shapes.ttl` (download from the registry), then update the `doc-loader.additional-context`
mapping.

## Asset / Credential terminology (CAT-NFR-01)

Originally developed for Gaia-X, this project used the term "Self-Description" (SD). The CAT-NFR-01 naming refactoring
replaced SD with two terms:

- **Asset** — at the API and storage layer (e.g., `POST /assets`, `AssetStore`, `AssetMetadata`). Covers any uploaded
  item: RDF credentials, PDFs, templates, binary files.
- **Credential** — inside the verification pipeline (e.g., `verifyCredential()`, `storeCredential()`,
  `CredentialVerificationResult`). Applies only when the uploaded asset is a Verifiable Credential/Presentation (
  JSON-LD).
