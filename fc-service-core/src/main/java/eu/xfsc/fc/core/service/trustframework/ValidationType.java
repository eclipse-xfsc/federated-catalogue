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

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Validation strategy declared by a trust-framework bundle in {@code framework.yaml}.
 *
 * <p>Only two concrete strategies exist:
 * <ul>
 *   <li>{@link #SHACL} — RDF/OWL frameworks (e.g. Gaia-X Loire). Bundles supply {@code shapes.ttl};
 *       credential types are auto-discovered via OWL {@code rdfs:subClassOf*} traversal.</li>
 *   <li>{@link #JSON_SCHEMA} — JSON-Schema-based frameworks (e.g. UNTP v0.7, JSON Schema draft
 *       2020-12). Bundles supply {@code schemas/*.json} and declare roles explicitly. Recognized at
 *       load time; the validation engine is wired up when the first such framework is onboarded.</li>
 * </ul>
 *
 * <p>{@link #UNKNOWN} is a deserialization fallback for unrecognised or absent values — not a valid
 * bundle strategy. Bundles resolving to {@code UNKNOWN} are excluded from active type resolution.
 */
public enum ValidationType {
  SHACL,
  JSON_SCHEMA,
  UNKNOWN;

  @JsonCreator
  public static ValidationType fromString(String value) {
    if (value == null) {
      return UNKNOWN;
    }
    try {
      return valueOf(value.toUpperCase().replace("-", "_"));
    } catch (IllegalArgumentException e) {
      return UNKNOWN;
    }
  }
}
