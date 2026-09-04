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
and vendors a small number of files that Maven dependency resolution does not
see (committed `.jar` files and static front-end assets such as bundled JS/CSS/
font libraries). For the vendored files, see:

* [`fc-tools/oss-inventory-vendored-assets.csv`](fc-tools/oss-inventory-vendored-assets.csv) —
  path, component, version, license (SPDX identifier), and source for every
  vendored asset in this repository.

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
