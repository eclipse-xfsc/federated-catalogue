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

/**
 *
 * @author hylke
 */
public interface ValidatorCacheDao {

  /**
   * Add the given validator to the cache.
   *
   * @param validator
   */
  void addToCache(Validator validator);

  /**
   * Search for a validator with the given DID.
   *
   * @param didURI The DID of the requested validator.
   * @return the requested validator, or null if it does not exist.
   */
  Validator getFromCache(String didURI);

  /**
   * Remove the validator with the given DID from the cache.
   *
   * @param didURI the DID of the validator to remove.
   */
  void removeFromCache(String didURI);

  /**
   * Removed expired validators from the cache.
   *
   * @return the number of deleted validators.
   */
  int expireValidators();

}
