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

import eu.xfsc.fc.core.pojo.ContentAccessor;

/**
 * Aggregate that groups a trust-framework's config, ontology, and SHACL shapes
 * into a single loadable unit.
 *
 * <p>{@code ontology} and {@code shapes} are nullable: a bundle declared with
 * {@code validationType: json-schema} carries no SHACL shapes.
 */
public record TrustFrameworkBundle(
    FrameworkBundleConfig config,
    ContentAccessor ontology,
    ContentAccessor shapes
) {
}
