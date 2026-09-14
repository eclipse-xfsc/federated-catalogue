package eu.xfsc.fc.core.service.verification;

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

import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.Validator;
import org.springframework.lang.Nullable;

/**
 * Result of {@link CredentialFormatProcessor#process}. Carries the unwrapped JSON-LD
 * payload, whether the input was a compact JWT, and (when signatures were verified)
 * the JWT validator the strategy will surface in the credential verification result.
 */
public record ProcessedEnvelope(
    ContentAccessor unwrappedPayload,
    boolean wasJwt,
    @Nullable Validator jwtValidator
) {
}
