# Federated Catalogue

## Description
The Federated Catalogue Service makes assets (metadata descriptions of Providers, their Service Offerings and Resources) available to Consumers.

> **Note:** This project was originally developed for Gaia-X, where assets were called "Self-Descriptions" (SDs). As part of the CAT-NFR-01 naming refactoring, "Self-Description" has been replaced with two terms:
> - **Asset** — at the API and storage layer (e.g., `POST /assets`, `AssetStore`, `AssetMetadata`). Covers any uploaded item: RDF credentials, PDFs, templates, binary files.
> - **Credential** — inside the verification pipeline (e.g., `verifyCredential()`, `storeCredential()`, `CredentialVerificationResult`). Applies only when the uploaded asset is a Verifiable Credential/Presentation (JSON-LD).
>
> See the [naming refactoring (CAT-NFR-01)](https://github.com/eclipse-xfsc/docs/blob/f3c6e6b6fbcc87732a1dfe83f060fa58a9a97873/federated-catalogue/src/docs/CAT%20Enhancement/CAT_Enhancement_Specifications%20v1.0.pdf) for details.

This project was initiated as a Reference Implementation of Gaia-X Federation Services Lot 5 [Federated Catalogue / Core Catalogue Features](https://www.gxfs.eu/core-catalogue-features/).

## Documentation
All service documentation, installation instructions and other materials can be found in [our wiki](https://github.com/eclipse-xfsc/federated-catalogue/wiki).

## Support
To get support you can open an issue in the project [Issues](https://github.com/eclipse-xfsc/federated-catalogue/issues) section.

## Getting Started
To start with FC project please follow the instructions: [Steps to build FC](./docker/README.md).

## Trust Framework Configuration

The Federated Catalogue can be operated with or without Gaia-X Trust Framework validation. By default, Gaia-X compliance validation is **disabled**, allowing the catalogue to be used in other ecosystems and use cases.

### Configuration Options

| Property | Default | Description |
|----------|---------|-------------|
| `federated-catalogue.verification.trust-framework.gaiax.enabled` | `false` | Enable/disable Gaia-X Trust Framework validation |
| `federated-catalogue.verification.trust-framework.gaiax.trust-anchor-url` | (Gaia-X registry URL) | URL for Gaia-X Trust Anchor Registry (only used when enabled) |

### Behavior

**When `gaiax.enabled: false` (default):**
- Credentials can be uploaded without Gaia-X compliance validation
- No calls are made to the Gaia-X Digital Clearing House or Trust Anchor Registry
- Signature verification still works but does not require Gaia-X trust anchors
- Any valid Verifiable Credential/Presentation can be stored

**When `gaiax.enabled: true`:**
- Full Gaia-X Trust Framework validation is enforced
- Credentials must have valid trust anchor chains registered with Gaia-X
- Calls are made to the Gaia-X Trust Anchor Registry for validation

### Example Configuration (application.yml)

```yaml
federated-catalogue:
  verification:
    trust-framework:
      gaiax:
        enabled: false  # Set to true for Gaia-X compliance
        trust-anchor-url: "https://registry.lab.gaia-x.eu/v1/api/trustAnchor/chain/file"
```

## Gaia-X Loire Compatibility (2511 Ontology)

The Federated Catalogue supports both the legacy Tagus (gax-core) and current Loire (Gaia-X 2511) credential formats.

### Bundled Ontology and SHACL Shapes

| File | Source | Purpose |
|------|--------|---------|
| `fc-service-core/src/main/resources/defaultschema/ontology/gx-2511.ttl` | Stripped from Gaia-X 2511 OWL | Class hierarchy for Loire type resolution (`rdfs:subClassOf` only) |
| `fc-service-core/src/main/resources/defaultschema/shacl/gx-2511-shapes.ttl` | [Gaia-X Trust Shape Registry](https://registry.lab.gaia-x.eu/development/api/trusted-shape-registry/v1/shapes/jsonld/trustframework#) | SHACL validation shapes for Loire credentials |

Legacy Tagus files (`gax-core_generated.ttl`, `gax-trust-framework_generated.ttl`, `mergedShapesGraph.ttl`) remain loaded alongside the 2511 files for backward compatibility.

### Namespace Configuration

| Namespace | URI | Usage |
|-----------|-----|-------|
| Loire (2511) | `https://w3id.org/gaia-x/2511#` | Loire credential types (`gx:LegalPerson`, `gx:ServiceOffering`, etc.) |
| Tagus (gax-core) | `https://w3id.org/gaia-x/core#` | Legacy Tagus credential types (`gax-core:Participant`, etc.) |

Configure type resolution in `application.yml`:

```yaml
federated-catalogue:
  verification:
    participant:
      type: "https://w3id.org/gaia-x/2511#Participant"       # Loire
      legacy-type: "https://w3id.org/gaia-x/core#Participant" # Tagus
    resource:
      type: "https://w3id.org/gaia-x/2511#Resource"
      legacy-type: "https://w3id.org/gaia-x/core#Resource"
    service-offering:
      type: "https://w3id.org/gaia-x/2511#ServiceOffering"
      legacy-type: "https://w3id.org/gaia-x/core#ServiceOffering"
    doc-loader:
      additional-context:
        '[https://w3id.org/gaia-x/2511#]': https://registry.lab.gaia-x.eu/development/context/2511
```

### Namespace Coexistence

Both ontology sets are loaded simultaneously via `SchemaStoreImpl.addSchemasFromDirectory()`. No namespace conflicts occur because the old (`gax-core:`) and new (`gx:`) files use different URIs. Credential format detection (`FormatDetector`) selects the appropriate type URI set per credential: Loire credentials use 2511 URIs; Tagus credentials use gax-core URIs.

To update to a future 2511 release: replace `gx-2511.ttl` (run `fc-tools/extract-ontology-hierarchy.py` against the new OWL file) and `gx-2511-shapes.ttl` (download from the registry), then update the `doc-loader.additional-context` mapping.

## Roadmap
The project v1.0.0 was released in February 2023.

## Contributing
If you want to contribute to the project, please see [the Eclipse XFSC contribution guidelines](https://github.com/eclipse-xfsc/federated-catalogue#contributing-ov-file).

## Authors and acknowledgment

The initial version of this project was implemented by the following team:
- [Denis Sukhoroslov](https://gitlab.eclipse.org/dsukhoroslov)
- [Peter Benko](https://gitlab.com/pebenko)
- [Ladislav Jurcisin](https://gitlab.com/ladislav.jurcisin)
- [Shyam Thorat](https://gitlab.com/shyamthorat)
- [Natallia Smoliak](https://gitlab.com/nsmoliak)

In the scope of the [GXFS (Gaia-X Federation Services) project](https://gxfs.eu/), the following colleagues …

- [Christoph Lange](https://gitlab.eclipse.org/langec), Fraunhofer FIT
- [Paul Moosmann](https://gitlab.eclipse.org/moosmannp), Fraunhofer FIT

… are in charge of:
- aligning the roadmap of this project with the requirements of further funded projects in the Gaia-X community, [including these in Germany](https://gaia-x-hub.de/gaia-x-foerdervorhaben/), and
- providing support to this community, especially in events such as the [GXFS Tech Workshops](https://www.gxfs.eu/events/).

The project implementation wouldn't have been possible without a great help of our partners from these Fraunhofer Institutes:
- Christoph Lange (see above)
- [Hylke van der Schaaf](https://gitlab.com/hylkevds), Fraunhofer IOSB
- [Jürgen Reuter](https://gitlab.com/j_reuter), Fraunhofer IOSB
- [Nikhil Acharya](https://gitlab.com/nik77612), Fraunhofer IAIS
- [Sebastian Duda](https://gitlab.com/sebastian.duda), Fraunhofer FIT
- [Patrick Westphal](https://gitlab.com/patrick_westphal), Fraunhofer IAIS
- [Ahmad Hemid](https://gitlab.com/ahmad.hemid), Fraunhofer FIT
- [Diego Collarana](https://gitlab.com/collaran), Fraunhofer IAIS
- Paul Moosmann (see above)
- [Philipp Hertweck](https://gitlab.com/phertweck), Fraunhofer IOSB
- [Khalil Baydoun](https://gitlab.com/baydounkhalil), Fraunhofer FIT

## License
The XFSC Federated Catalogue Service is Open Source software released under the [Apache 2.0 license](https://www.apache.org/licenses/LICENSE-2.0.html).
