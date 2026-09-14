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
 * Sealed result type for a trust-framework compliance check.
 *
 * <p>Permitted subtypes cover every possible outcome: an issued attestation credential,
 * a trust-list membership entry, or a failure that could not be verified.
 * Callers should use an exhaustive switch expression to dispatch on the concrete type.
 */
public sealed interface ComplianceCheckOutcome
    permits IssuedAttestation, UnverifiableAttestation {

  /**
   * Returns {@code true} when the check produced a positive compliance result.
   */
  boolean compliant();
}
