# Manual Kubernetes manifests — unsupported, kept for reference

**These manifests are not a supported deployment path and are not maintained.** They are retained so
that the earlier manual deployment remains readable; they are not exercised by CI and are not
verified against current releases.

## Use the Helm chart instead

The supported demo and reference deployment path is the bundled Helm chart at
[`deployment/helm/fc-service/`](../helm/fc-service/README.md). It stands up the full stack —
`fc-service`, `fc-demo-portal`, Keycloak, PostgreSQL and Fuseki — and is the path the project tests
and publishes.

## What is in this directory

A complete manual deployment of the stack, one directory per component, each with Deployments,
Services, Ingresses, PersistentVolumes and Secrets:

| Directory | Component |
|---|---|
| `fc/` | Catalogue server |
| `demo-portal/` | Demo portal |
| `keycloak/` | Keycloak |
| `postgres/` | PostgreSQL |
| `neo4j/` | Neo4j graph store |

## Why they are unsupported

- They pin versions that are no longer current: Neo4j `4.4.12`, Keycloak `17.0.1`, PostgreSQL `15.1`.
- They pull application images from a private registry under a floating `latest` tag. Current images
  are published to GHCR and referenced by digest or by an immutable `sha-` tag.
- They provision Neo4j, whereas the supported chart provisions Fuseki as the graph store.
- Nothing in the build or release process applies, validates or updates them.

## Provenance

The manifests entered this repository on 2025-05-20 with the move of the existing codebase, before
the Helm chart existed. They described the deployment of the earlier hosted demo environment.

## Remaining reference

[`docs/operator-guide.md`](../../docs/operator-guide.md) mentions this directory when listing the
places that honour the `KEYCLOAK_REALM` variable. That reference is informational; it does not make
this path supported.
