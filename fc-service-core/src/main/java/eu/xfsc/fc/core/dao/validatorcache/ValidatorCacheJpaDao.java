package eu.xfsc.fc.core.dao.validatorcache;

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

import eu.xfsc.fc.core.pojo.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class ValidatorCacheJpaDao implements ValidatorCacheDao {

  private final ValidatorCacheRepository repository;

  @Override
  public void addToCache(Validator validator) {
    repository.save(ValidatorCacheMapper.toEntity(validator));
  }

  @Override
  public Validator getFromCache(String didURI) {
    return repository.findById(didURI)
        .map(ValidatorCacheMapper::toValidator)
        .orElse(null);
  }

  @Override
  public void removeFromCache(String didURI) {
    repository.deleteById(didURI);
  }

  @Override
  @Transactional
  public int expireValidators() {
    return repository.deleteExpired(Instant.now());
  }
}
