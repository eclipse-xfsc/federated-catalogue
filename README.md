# Federated Catalogue

The Federated Catalogue is an Eclipse XFSC service that makes assets — metadata descriptions of Providers, their Service
Offerings, and Resources — available to Consumers. It supports verifiable-credential-based onboarding and pluggable
trust-framework validation (Gaia-X Loire and standard JWT-VC formats).

This project originated as the Reference Implementation
of [Gaia-X Federation Services Lot 5 — Federated Catalogue / Core Catalogue Features](https://www.gxfs.eu/core-catalogue-features/).

## Installation & Setup

The supported way to build and run the Federated Catalogue locally is via the bundled Docker Compose stack:

- Build & run: [Steps to build FC](https://github.com/eclipse-xfsc/federated-catalogue/blob/main/docker/README.md)
- Operator configuration (trust framework, credential formats, Loire
  ontology): [docs/operator-guide.md](https://github.com/eclipse-xfsc/federated-catalogue/blob/main/docs/operator-guide.md)

Build & test workflows run automatically on every push and pull request —
see [docs/ci-cd.md](https://github.com/eclipse-xfsc/federated-catalogue/blob/main/docs/ci-cd.md).

## Documentation

| Topic                                                       | Location                                                                                                                                                 |
|-------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------|
| Architecture, requirements, deployment, ADRs (canonical)    | [Architecture Document](https://github.com/eclipse-xfsc/docs/tree/main/federated-catalogue)                                                              |
| API reference                                               | [docs/api-docs.md](https://github.com/eclipse-xfsc/federated-catalogue/blob/main/docs/api-docs.md)                                                       |
| Operator guide (trust framework, Loire, credential formats) | [docs/operator-guide.md](https://github.com/eclipse-xfsc/federated-catalogue/blob/main/docs/operator-guide.md)                                           |
| CI / CD workflows                                           | [docs/ci-cd.md](https://github.com/eclipse-xfsc/federated-catalogue/blob/main/docs/ci-cd.md)                                                             |
| Operator notes (non-canonical)                              | [Wiki](https://github.com/eclipse-xfsc/federated-catalogue/wiki) — supplementary; the Architecture Document is the source of truth where the two overlap |
| Issues & support                                            | [GitHub Issues](https://github.com/eclipse-xfsc/federated-catalogue/issues)                                                                              |
| Releases                                                    | [GitHub Releases](https://github.com/eclipse-xfsc/federated-catalogue/releases)                                                                          |

## Credential support

The catalogue accepts Gaia-X Loire (VC 2.0 JWT) and standard JWT-VC formats. VC 1.1 JSON-LD with Linked Data Proof (
Tagus / Elbe) is not accepted.

See
the [Operator Guide — Supported Credential Formats](https://github.com/eclipse-xfsc/federated-catalogue/blob/main/docs/operator-guide.md#supported-credential-formats)
for the full matrix.

> The asset / credential naming distinction (CAT-NFR-01) is described in the
> [Operator Guide](https://github.com/eclipse-xfsc/federated-catalogue/blob/main/docs/operator-guide.md#asset--credential-terminology-cat-nfr-01).

## Use-case diagram

Not applicable for this microservice. The Federated Catalogue's role within the Gaia-X / XFSC ecosystem is documented in
the [Architecture Document](https://github.com/eclipse-xfsc/docs/tree/main/federated-catalogue) (system scope and
context, building-block view).

## Contributing

Contributions are welcome. Please
read [CONTRIBUTING.md](https://github.com/eclipse-xfsc/federated-catalogue/blob/main/CONTRIBUTING.md)
and [CODE_OF_CONDUCT.md](https://github.com/eclipse-xfsc/federated-catalogue/blob/main/CODE_OF_CONDUCT.md) before
opening a pull request. As an Eclipse Foundation project, contributors must sign
the [Eclipse Contributor Agreement (ECA)](https://www.eclipse.org/legal/ECA.php).

## Authors & Contact

Project page: [projects.eclipse.org/projects/technology.xfsc](https://projects.eclipse.org/projects/technology.xfsc)
Contact: open an [issue](https://github.com/eclipse-xfsc/federated-catalogue/issues) for technical questions and bug
reports.

The full list of contributors is maintained
in [CONTRIBUTORS.md](https://github.com/eclipse-xfsc/federated-catalogue/blob/main/CONTRIBUTORS.md) and the
GitHub [contributor graph](https://github.com/eclipse-xfsc/federated-catalogue/graphs/contributors).

## License

Apache License 2.0 — see [LICENSE](https://github.com/eclipse-xfsc/federated-catalogue/blob/main/LICENSE).
