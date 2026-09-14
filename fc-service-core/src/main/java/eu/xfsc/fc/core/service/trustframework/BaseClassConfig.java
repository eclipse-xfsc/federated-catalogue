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

import java.util.List;

/**
 * Per-base-class configuration entry from a bundle's {@code framework.yaml}.
 *
 * <p>{@code additionalRoots} enables SHACL sibling-class grouping: explicit URIs that
 * resolve to this base class even though they are not OWL subclasses of the base class's primary root.
 * Use when an external ontology genuinely declares siblings without subsumption — for example,
 * {@code gx:DigitalServiceOffering} in gx-2511 lacks {@code rdfs:subClassOf gx:ServiceOffering}
 * in the ontology, so this field is the correct mapping mechanism for that case.
 * {@code types} is defined for forward-compatibility with JSON Schema validation engines
 * and is intentionally unused in the SHACL resolver until other trust frameworks with different validations are integrated.
 */
public record BaseClassConfig(
    @JsonProperty("additional_roots") List<String> additionalRoots,
    @JsonProperty("types") List<String> types
) {

  public BaseClassConfig {
    additionalRoots = additionalRoots != null ? additionalRoots : List.of();
    types = types != null ? types : List.of();
  }
}
