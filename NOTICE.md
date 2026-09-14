# Notices for Eclipse XFSC Federated Catalogue

This content is produced and maintained by the Eclipse XFSC Federated Catalogue project.

* Project home: <https://projects.eclipse.org/projects/technology.xfsc>

See the git repository logs for information regarding authorship of content.

## Trademarks

Eclipse XFSC is a trademark of the Eclipse Foundation.

## Copyright

All content is the property of the respective authors or their employers. For
more information regarding authorship of content, please consult the listed
source code repository logs.

## Declared Project Licenses

This program and the accompanying materials are made available under the
terms of the Apache License, Version 2.0 which is available at
https://www.apache.org/licenses/LICENSE-2.0.

SPDX-License-Identifier: Apache-2.0

## Source Code

The project maintains the following source code repository:

* <https://github.com/eclipse-xfsc/federated-catalogue>

## Third-party Content

This project depends on third-party libraries resolved through the Maven build,
and vendors a small number of files outside that dependency tree: committed
`.jar` files, Maven `system`-scope/`systemPath` dependencies, vendored
front-end JS/CSS/font files checked into `static`/`webapp`/`public` resource
directories, and images colocated with those vendored front-end libraries that
need manual license attribution. `fc-tools/scan-vendored-assets.sh` discovers
this set. For the inventory, see:

* [`fc-tools/oss-inventory-vendored-assets.csv`](fc-tools/oss-inventory-vendored-assets.csv)
  (`path,component,dash_coordinate`) — the subset resolvable by the Eclipse
  Dash license tool: committed jars published to Maven Central, plus vendored
  front-end files resolvable via npm coordinates.
* [`fc-tools/oss-inventory-vendored-assets-manual.csv`](fc-tools/oss-inventory-vendored-assets-manual.csv)
  (`path,component,version,license,confidence,evidence,spdx,source`) — the
  subset Eclipse Dash cannot resolve, with license evidence recorded per row
  by hand.

The licenses for the coordinates in `oss-inventory-vendored-assets.csv` are
resolved by the `vendored-assets-dash-scan` job in
`.github/workflows/eclipse-dash.yml` and published as the
`vendored-assets-dependencies-summary` workflow artifact; durable
release-asset publication of that summary is tracked separately.

Verbatim third-party files vendored outside this scanned scope — such as
`docker/fuseki/Dockerfile` and `docker/fuseki/download.sh` (Apache Jena,
ASF-headed, Apache-2.0 — same license as this project) — carry their own
license headers in-file and are intentionally not duplicated in these CSVs.

For the full transitive dependency list resolved from the Maven build,
see the `DEPENDENCIES` file published as a release asset alongside each
release. Unlike this NOTICE (mandated by the Eclipse Foundation Development
Process / IP Policy), `DEPENDENCIES` follows the Eclipse Dash license tool
convention and is generated per release rather than committed to the
repository.

## Cryptography

Content may contain encryption software. The country in which you are currently
may have restrictions on the import, possession, and use, and/or re-export to
another country, of encryption software. BEFORE using any encryption software,
please check the country's laws, regulations and policies concerning the import,
possession, or use, and re-export of encryption software, to see if this is
permitted. This project vendors and depends on cryptography-related
third-party content, including Bouncy Castle (`bcpkix-jdk15on`, `bcprov-jdk18on`)
and Nimbus JOSE+JWT.
