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

import java.time.Instant;

/**
 * Outcome indicating that the trust framework issued a verifiable attestation credential
 * for the asset under check.
 *
 * @param attestationCredential raw attestation credential string (JWT), or {@code null} if not
 *                              retained; the JWT's {@code iss} claim identifies the issuing service
 * @param credentialValidUntil  expiry timestamp of the issued credential, taken from the JWT
 *                              {@code exp} claim or the VC 2.0 {@code validUntil} claim;
 *                              {@code null} if the credential carries neither
 */
public record IssuedAttestation(
    String attestationCredential, Instant credentialValidUntil
) implements ComplianceCheckOutcome {

  @Override
  public boolean compliant() {
    return true;
  }
}
