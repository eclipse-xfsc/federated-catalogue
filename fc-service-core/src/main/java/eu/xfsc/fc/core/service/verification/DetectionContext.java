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

import com.fasterxml.jackson.databind.JsonNode;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.lang.Nullable;

/**
 * Pre-parsed credential payload, built once by {@link CredentialFormatDetector} and passed
 * to each {@link CredentialFormatProcessor}. Avoids reparsing the JWT or body JSON per
 * processor.
 *
 * <p>{@code parsedJson} holds:
 * <ul>
 *   <li>For JWT credentials — the decoded JWT payload as {@link JsonNode}</li>
 *   <li>For non-JWT credentials — the raw body as {@link JsonNode}</li>
 *   <li>{@code Optional.empty()} if parsing failed</li>
 * </ul>
 */
public record DetectionContext(
    String body,
    @Nullable SignedJWT jwt,
    @Nullable JsonNode parsedJson
) {

  /** Returns {@code true} if the credential was successfully parsed as a signed JWT. */
  public boolean isJwt() {
    return jwt != null;
  }
}
