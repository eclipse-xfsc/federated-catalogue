package eu.xfsc.fc.core.service.verification;

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
 * Test-only constants for the verification package.
 */
class TestVerificationConstants {

  // Gaia-X 2511 vocabulary namespace — used in test fixtures to build realistic Loire credentials.
  // Not needed in production: Loire format detection is based on JWT structure, not vocabulary URLs.
  static final String GAIAX_2511_CONTEXT = "https://w3id.org/gaia-x/2511#";

  private TestVerificationConstants() {
  }
}
