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

import org.springframework.stereotype.Component;

import eu.xfsc.fc.core.service.pubsub.ces.CesTracking;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CesTrackerJpaDao implements CesTrackerDao {

  private final CesTrackerRepository repository;

  @Override
  public void insert(CesTracking event) {
    repository.save(CesTrackerMapper.toEntity(event));
  }

  @Override
  public CesTracking select(String cesId) {
    return repository.findById(cesId)
        .map(CesTrackerMapper::toTracking)
        .orElse(null);
  }

  @Override
  public CesTracking selectLatest() {
    return repository.findFirstByOrderByCreatedAtDesc()
        .map(CesTrackerMapper::toTracking)
        .orElse(null);
  }
}
