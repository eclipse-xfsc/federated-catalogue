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

import eu.xfsc.fc.core.pojo.ContentAccessor;

/**
 * SPI for trust-framework compliance check implementations.
 *
 * <p>Each implementation handles one client type (e.g. {@code "jwt-vc-compliance"} for REST + JWT-VC
 * compliance services, or future shapes such as {@code "train"} for DNS/DNSSEC-anchored trust lists).
 * Implementations are registered via {@link TrustFrameworkClientRegistry}.
 */
public interface TrustFrameworkClient {

  /**
   * Returns the client-type key that this implementation handles.
   * Must match the {@code clientType} field in {@link TrustFrameworkProfileConfig}.
   */
  String clientType();

  /**
   * Performs a compliance check for the given credential against the specified profile configuration.
   *
   * @param credential the credential payload to check (verifiable presentation or self-description)
   * @param config     resolved profile configuration providing endpoint and trust-list parameters
   * @return the outcome of the compliance check; never {@code null}
   */
  ComplianceCheckOutcome check(ContentAccessor credential, TrustFrameworkProfileConfig config);
}
