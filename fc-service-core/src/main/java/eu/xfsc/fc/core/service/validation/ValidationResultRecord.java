package eu.xfsc.fc.core.service.validation;

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

import eu.xfsc.fc.core.dao.validation.ValidatorType;
import java.time.Instant;
import java.util.List;

/**
 * Data transfer record for persisting the outcome of an asset validation run.
 *
 * <p>Input DTO for {@link ValidationResultStoreImpl#store(ValidationResultRecord)}.
 * All resolved schema IDs are passed explicitly.
 * This ensures complete temporal reconstruction of which schemas were used.</p>
 */
public record ValidationResultRecord(
    List<String> assetIds,
    List<String> validatorIds,
    ValidatorType validatorType,
    boolean conforms,
    Instant validatedAt,
    String report,             // nullable; SHACL Turtle / JSON violations / XSD error
    String failureCategory     // nullable; set only for trust-framework checks that could not produce a verdict
) {}
