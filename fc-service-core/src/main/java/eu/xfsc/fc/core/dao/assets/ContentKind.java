package eu.xfsc.fc.core.dao.assets;

/*-
 * ---license-start
 * fc-service-core
 * ---
 * Copyright (c) 2022 - 2026 Contributors to the Eclipse Foundation
 * ---
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 * ---license-end
 */

/**
 * Distinguishes whether an asset has RDF content or not.
 * This dimension is orthogonal to AssetType (which encodes MR-HR linking status).
 */
public enum ContentKind {
  /** Asset has RDF content */
  RDF,

  /** Asset has non-RDF content (e.g., PDF, JSON, binary) */
  NON_RDF
}
