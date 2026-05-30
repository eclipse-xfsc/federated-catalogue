# Federated Catalogue

The Federated Catalogue is an Eclipse XFSC service that makes assets — metadata descriptions of Providers, their Service
Offerings, and Resources — available to Consumers. It supports verifiable-credential-based onboarding and pluggable
trust-framework validation (Gaia-X Loire and standard JWT-VC formats). The service's role within the Gaia-X / XFSC
ecosystem (system scope and context, building-block view) is described in
the [Architecture Document](https://github.com/eclipse-xfsc/docs/tree/main/federated-catalogue).

This project originated as the Reference Implementation
of [Gaia-X Federation Services Lot 5 — Federated Catalogue / Core Catalogue Features](https://www.gxfs.eu/core-catalogue-features/).

## Installation & Setup

The supported way to build and run the Federated Catalogue locally is via the bundled Docker Compose
stack: [Steps to build FC](https://github.com/eclipse-xfsc/federated-catalogue/blob/main/docker/README.md).

### Keycloak realm configuration

The Keycloak realm name is configurable via the `KEYCLOAK_REALM` environment variable. Default:
`federated-catalogue-realm`.

- Fresh deployments work out of the box with the default — the bundled realm import files
  (`keycloak/realms/{dev,staging,prod}/fc-realm.json` and `deployment/helm/fc-service/fc-realm.json`) define a
  realm with that name.
- **Existing deployments** (e.g. our QA stage) that already have a `gaia-x` realm persisted in Keycloak's
  database keep working by setting `KEYCLOAK_REALM=gaia-x`. Keycloak's `--import-realm` is a no-op once
  the realm exists, so no migration is required.
- A fresh deployment that wants the legacy `gaia-x` realm name must supply its own realm import JSON via
  volume mount or ConfigMap — we no longer ship one.

The same variable is honored by the Spring config (`fc-service-server`, `fc-demo-portal`), `docker-compose.yml`,
the Helm chart (`keycloak.realm` value), and the manual Kubernetes manifests under `deployment/manual/`.

## Documentation

| Topic                                                       | Location                                                                                                       |
|-------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------|
| Architecture, requirements, deployment, ADRs (canonical)    | [Architecture Document](https://github.com/eclipse-xfsc/docs/tree/main/federated-catalogue)                    |
| API reference                                               | [docs/api-docs.md](https://github.com/eclipse-xfsc/federated-catalogue/blob/main/docs/api-docs.md)             |
| Operator guide (trust framework, Loire, credential formats) | [docs/operator-guide.md](https://github.com/eclipse-xfsc/federated-catalogue/blob/main/docs/operator-guide.md) |
| CI / CD workflows                                           | [docs/ci-cd.md](https://github.com/eclipse-xfsc/federated-catalogue/blob/main/docs/ci-cd.md)                   |
| Operator notes (non-canonical)                              | [Wiki](https://github.com/eclipse-xfsc/federated-catalogue/wiki)                                               |
| Issues & support                                            | [GitHub Issues](https://github.com/eclipse-xfsc/federated-catalogue/issues)                                    |
| Releases                                                    | [GitHub Releases](https://github.com/eclipse-xfsc/federated-catalogue/releases)                                |

## Credential support

The catalogue accepts Gaia-X Loire (VC 2.0 JWT) and standard JWT-VC; see
the [Operator Guide](https://github.com/eclipse-xfsc/federated-catalogue/blob/main/docs/operator-guide.md#supported-credential-formats)
for the full matrix and the asset/credential terminology note (CAT-NFR-01).

## Contributing & Contact

Contributions are welcome. Please
read [CONTRIBUTING.md](https://github.com/eclipse-xfsc/federated-catalogue/blob/main/CONTRIBUTING.md)
and [CODE_OF_CONDUCT.md](https://github.com/eclipse-xfsc/federated-catalogue/blob/main/CODE_OF_CONDUCT.md) before
opening a pull request. As an Eclipse Foundation project, contributors must sign
the [Eclipse Contributor Agreement (ECA)](https://www.eclipse.org/legal/ECA.php).

Project page: [projects.eclipse.org/projects/technology.xfsc](https://projects.eclipse.org/projects/technology.xfsc).
For technical questions and bug reports, open
an [issue](https://github.com/eclipse-xfsc/federated-catalogue/issues). The full list of contributors is maintained
in [CONTRIBUTORS.md](https://github.com/eclipse-xfsc/federated-catalogue/blob/main/CONTRIBUTORS.md) and the
GitHub [contributor graph](https://github.com/eclipse-xfsc/federated-catalogue/graphs/contributors).

## License

Apache License 2.0 — see [LICENSE](https://github.com/eclipse-xfsc/federated-catalogue/blob/main/LICENSE).
