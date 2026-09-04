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

public final class ValidatorCacheMapper {

  private ValidatorCacheMapper() {
  }

  public static ValidatorCache toEntity(Validator validator) {
    if (validator == null) {
      return null;
    }
    return new ValidatorCache(
        validator.getDidURI(),
        validator.getPublicKey(),
        validator.getExpirationDate());
  }

  public static Validator toValidator(ValidatorCache entity) {
    if (entity == null) {
      return null;
    }
    return new Validator(
        entity.getDidUri(),
        entity.getPublicKey(),
        entity.getExpirationTime());
  }
}
