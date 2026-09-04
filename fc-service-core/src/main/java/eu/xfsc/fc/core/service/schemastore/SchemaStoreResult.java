package eu.xfsc.fc.core.service.schemastore;

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

import java.time.Instant;

/**
 * Result of adding or updating a schema, carrying the identifier and an optional warning.
 *
 * @param id              the internal identifier of the schema
 * @param warning         user-visible warning if protected namespace statements were filtered, or null
 * @param createdAt       timestamp when the schema was first uploaded
 * @param version         current version number after this operation, or null if not applicable
 * @param previousVersion version number before this operation, or null if first version
 */
public record SchemaStoreResult(String id, String warning, Instant createdAt,
    Integer version, Integer previousVersion) {

  public SchemaStoreResult(String id, String warning) {
    this(id, warning, null, null, null);
  }

  public SchemaStoreResult(String id, String warning, Instant createdAt) {
    this(id, warning, createdAt, null, null);
  }
}
