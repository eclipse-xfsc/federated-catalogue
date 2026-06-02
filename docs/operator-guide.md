# Operator Guide

Configuration and runtime tuning for operators of the Federated Catalogue.

For the architectural and conceptual description of the service, see the
[Architecture Document](https://github.com/eclipse-xfsc/docs/tree/main/federated-catalogue).

## Keycloak Realm Configuration

The Keycloak realm name is configurable via the `KEYCLOAK_REALM` environment variable.

| Property         | Env var          | Default                     | Description                                       |
|------------------|------------------|-----------------------------|---------------------------------------------------|
| `keycloak.realm` | `KEYCLOAK_REALM` | `federated-catalogue-realm` | Realm name the application authenticates against. |

The same variable is honored by `fc-service-server`, `fc-demo-portal`, `docker-compose.yml`, the Helm chart
(`keycloak.realm` value), and the manual Kubernetes manifests under `deployment/manual/`.

### Fresh deployment (default)

The bundled realm import files define a realm named `federated-catalogue-realm`:

- `keycloak/realms/{dev,staging,prod}/fc-realm.json` — imported by the docker-compose stack
- `deployment/helm/fc-service/fc-realm.json` — imported by the Helm chart's Keycloak ConfigMap

No further action is required.

### Existing deployment with a legacy `gaia-x` realm

Set `KEYCLOAK_REALM=gaia-x`. Keycloak's `--import-realm` is a no-op once the realm already exists in the
database, so deployments with a pre-existing `gaia-x` realm keep working without migration. No bundled JSON
ships for this case; bring your own import file if you also need to provision the realm fresh.

### Helm

```yaml
keycloak:
  realm: federated-catalogue-realm   # set to "gaia-x" for legacy
  realmFile: fc-realm.json           # bring your own JSON if you need a legacy import
```

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

## Verification: No Forced Validation on Upload

The upload path runs no forced SHACL/JSON-Schema/XML-Schema validation and no
trust-framework base-class compliance check.

Schema validation is on-demand against **stored** assets via
`POST /assets/validate` (CAT-FR-CO-05): the endpoint takes asset IDs and
schema IDs, validates them, and persists the validation report as triples
per CAT-FR-CO-02. Operators that want to discard non-conforming uploads
follow store → validate → (delete on failure); the validation report is
retained as audit evidence either way.

A caller that wants the post-strategy base-class check on `POST /verification`
must opt in explicitly with the `requireBaseClass=true` query parameter.

For the full set of verification toggles and the runtime OWL schema-validation
module, see the [Operator Wiki](https://github.com/eclipse-xfsc/federated-catalogue/wiki).

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
