package eu.xfsc.fc.core.service.trustframework.compliance;

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

/**
 * Resolved configuration for a single trust-framework profile, derived from the bundle
 * metadata and used when invoking a {@link TrustFrameworkClient}.
 *
 * @param frameworkProfileId unique identifier of the trust-framework profile
 * @param familyId           family identifier grouping related profiles
 * @param clientType         key selecting the {@link TrustFrameworkClient} implementation
 * @param serviceUrl         base URL of the compliance service endpoint
 * @param compliancePath     path appended to {@code serviceUrl} when calling the compliance
 *                           endpoint; must be non-blank
 * @param apiVersion         API version string sent to the compliance service
 * @param timeoutSeconds     per-request timeout in seconds
 */
public record TrustFrameworkProfileConfig(
    String frameworkProfileId,
    String familyId,
    String clientType,
    String serviceUrl,
    String compliancePath,
    String apiVersion,
    int timeoutSeconds
) {
}
