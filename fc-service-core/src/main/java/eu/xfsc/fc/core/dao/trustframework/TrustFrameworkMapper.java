package eu.xfsc.fc.core.dao.trustframework;

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

import java.time.ZoneOffset;

import eu.xfsc.fc.core.pojo.TrustFrameworkConfig;

public final class TrustFrameworkMapper {

  private TrustFrameworkMapper() {
  }

  public static TrustFrameworkConfig toConfig(TrustFramework entity) {
    if (entity == null) {
      return null;
    }
    return new TrustFrameworkConfig(
        entity.getId(),
        entity.getName(),
        entity.isEnabled(),
        entity.getCreatedAt() != null ? entity.getCreatedAt().toInstant(ZoneOffset.UTC) : null,
        entity.getUpdatedAt() != null ? entity.getUpdatedAt().toInstant(ZoneOffset.UTC) : null
    );
  }
}
