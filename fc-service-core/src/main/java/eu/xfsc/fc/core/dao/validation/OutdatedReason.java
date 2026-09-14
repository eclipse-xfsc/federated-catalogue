package eu.xfsc.fc.core.dao.validation;

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

/** Reason a {@link ValidationResult} was marked outdated. */
public enum OutdatedReason {

  /** The asset's content was updated, superseding previous validation results. */
  ASSET_UPDATED,

  /** The asset was revoked or reached end-of-life, invalidating prior results. */
  ASSET_REVOKED
}
