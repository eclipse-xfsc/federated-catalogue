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
import java.util.Set;

import eu.xfsc.fc.core.service.schemastore.SchemaStore.SchemaType;

public record SchemaRecord(String schemaId, String nameHash, SchemaType type, Instant createdAt, Instant modifiedAt,
    String content, Set<String> terms, Integer version) {

  public SchemaRecord(String schemaId, String nameHash, SchemaType type, Instant createdAt, Instant modifiedAt,
      String content, Set<String> terms) {
    this(schemaId, nameHash, type, createdAt, modifiedAt, content, terms, null);
  }

  public SchemaRecord(String schemaId, String nameHash, SchemaType type, String content, Set<String> terms) {
    this(schemaId, nameHash, type, Instant.now(), Instant.now(), content, terms, null);
  }

  public String getId() {
    return schemaId == null ? nameHash : schemaId;
  }
}
