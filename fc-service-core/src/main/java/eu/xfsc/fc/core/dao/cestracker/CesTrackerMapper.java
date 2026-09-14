package eu.xfsc.fc.core.dao.cestracker;

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

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import eu.xfsc.fc.core.service.pubsub.ces.CesTracking;

public final class CesTrackerMapper {

  private CesTrackerMapper() {
  }

  public static CesTracking toTracking(CesTracker entity) {
    if (entity == null) {
      return null;
    }
    return new CesTracking(
        entity.getCesId(),
        entity.getEvent(),
        entity.getCreatedAt().toInstant(ZoneOffset.UTC),
        entity.getCredProcessed(),
        entity.getCredId(),
        entity.getError());
  }

  public static CesTracker toEntity(CesTracking tracking) {
    if (tracking == null) {
      return null;
    }
    CesTracker entity = new CesTracker();
    entity.setCesId(tracking.getCesId());
    entity.setEvent(tracking.getEvent());
    entity.setCreatedAt(LocalDateTime.ofInstant(tracking.getCreatedAt(), ZoneOffset.UTC));
    entity.setCredProcessed(tracking.getCredProcessed());
    entity.setCredId(tracking.getCredId());
    entity.setError(tracking.getError());
    return entity;
  }
}
