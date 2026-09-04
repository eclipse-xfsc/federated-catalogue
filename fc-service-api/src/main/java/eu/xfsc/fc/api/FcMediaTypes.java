package eu.xfsc.fc.api;

/*-
 * ---license-start
 * fc-service-api
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

import org.springframework.http.MediaType;

/**
 * Wire-format media types specific to the Federated Catalogue API.
 *
 * <p>Mirrors Spring's pattern of exposing both the {@link MediaType} instance and its
 * {@link String} value, so callers can pick the form their API needs (annotations and
 * MockMvc {@code .contentType(String)} use the string; WebClient and similar use the
 * {@code MediaType}).
 */
public final class FcMediaTypes {

  /**
   * Merge-patch content type per RFC 7396.
   */
  public static final String MERGE_PATCH_JSON_VALUE = "application/merge-patch+json";

  /**
   * {@link MediaType} form of {@link #MERGE_PATCH_JSON_VALUE}.
   */
  public static final MediaType MERGE_PATCH_JSON = MediaType.valueOf(MERGE_PATCH_JSON_VALUE);

  /**
   * Verifiable Presentation JWT content type (W3C VC-JOSE-COSE).
   */
  public static final String VP_JWT_VALUE = "application/vp+jwt";

  /**
   * {@link MediaType} form of {@link #VP_JWT_VALUE}.
   */
  public static final MediaType VP_JWT = MediaType.valueOf(VP_JWT_VALUE);

  private FcMediaTypes() {
  }
}
