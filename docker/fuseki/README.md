# FC Fuseki image

Self-built Apache Jena Fuseki container used by the federated catalogue.

## Source

Vendored verbatim from Apache's official `jena-fuseki-docker` package:

- Distribution: <https://repo1.maven.org/maven2/org/apache/jena/jena-fuseki-docker/5.5.0/jena-fuseki-docker-5.5.0.zip>
- Upstream docs: <https://jena.apache.org/documentation/fuseki2/fuseki-docker.html>
- Pinned version: `JENA_VERSION=5.5.0` (set as Dockerfile `ARG` default).

Only deviation from upstream: the `ARG JENA_VERSION=""` default in the Dockerfile is
pinned to `5.5.0` so `docker compose up -d fuseki` builds without extra build args.

## Runtime configuration

Fuseki-main is CLI-driven (no bundled `assembler.ttl`). The dataset is configured via
arguments passed by the caller, e.g.:

```
--tdb2 --loc /fuseki/databases/ds /ds --update
```

TDB2 default-graph semantics apply (`unionDefaultGraph` is `false`), which is the
reason for the migration — claim writes to the default graph are visible to claim
queries against the default graph.

`JAVA_OPTIONS` is the env var that controls heap (e.g. `-Xmx1g`).

## Upgrade procedure

1. Download the new Apache zip:
   ```
   curl -fsSL -O https://repo1.maven.org/maven2/org/apache/jena/jena-fuseki-docker/<NEW>/jena-fuseki-docker-<NEW>.zip
   ```
2. Diff `Dockerfile`, `entrypoint.sh`, `download.sh`, `log4j2.properties` against the
   vendored copies in this directory.
3. Apply non-trivial upstream changes manually. Re-apply the local pin
   (`ARG JENA_VERSION=<NEW>`).
4. Bump the Helm `fuseki.image.tag` value to match.
5. Rebuild locally (`docker compose build fuseki`)
