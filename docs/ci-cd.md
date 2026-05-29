# CI / CD

This service uses GitHub Actions. The authoritative workflow definitions live in `.github/workflows/` and are visible on
GitHub:

https://github.com/eclipse-xfsc/federated-catalogue/actions

| Workflow     | File                                 | Purpose                                 |
|--------------|--------------------------------------|-----------------------------------------|
| Maven build  | `.github/workflows/maven.yml`        | Compile and run unit tests on push / PR |
| Docker build | `.github/workflows/docker-build.yml` | Build and publish container images      |
| SBOM         | `.github/workflows/sbom.yml`         | Generate Software Bill of Materials     |
| Eclipse Dash | `.github/workflows/eclipse-dash.yml` | License compliance check (Eclipse Dash) |
| Publish      | `.github/workflows/publish.yml`      | Publish release artifacts               |

Workflow runs, logs, and status badges are the source of truth — this file is a pointer.
