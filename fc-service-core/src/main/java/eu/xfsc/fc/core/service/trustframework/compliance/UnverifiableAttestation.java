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
 * Outcome indicating that the attestation presented by the asset could not be verified.
 *
 * @param failureCategory   the category of failure that prevented verification
 * @param rawAttestation    the raw attestation payload that was inspected
 * @param verificationError human-readable description of why verification failed
 */
public record UnverifiableAttestation(
    FailureCategory failureCategory,
    String rawAttestation,
    String verificationError
) implements ComplianceCheckOutcome {

  @Override
  public boolean compliant() {
    return false;
  }
}
