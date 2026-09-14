package eu.xfsc.fc.core.service.trustframework;

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

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Deserialisation model for a trust-framework bundle's {@code framework.yaml} file.
 *
 * <p>Load with:
 * <pre>{@code new YAMLMapper().readValue(inputStream, FrameworkBundleConfig.class)}</pre>
 */
public record FrameworkBundleConfig(
    @JsonProperty("id") String id,
    @JsonProperty("family") String family,
    @JsonProperty("namespace") String namespace,
    @JsonProperty("validation_type") ValidationType validationType,
    @JsonProperty("base_classes") Map<String, BaseClassConfig> baseClasses,
    @JsonProperty("properties") Map<String, String> properties
) {

  public FrameworkBundleConfig {
    baseClasses = baseClasses != null ? baseClasses : Map.of();
    properties = properties != null ? properties : Map.of();
  }
}
