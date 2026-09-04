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
 * Identifies the credential format so the verification pipeline can route
 * to the correct parser path.
 *
 * <ul>
 *   <li>{@link #GAIAX_V2_LOIRE} — VC 2.0 JWT per VC-JOSE-COSE (Gaia-X Loire / ICAM 24.07)</li>
 *   <li>{@link #VC2_DANUBETECH} — VC 2.0 JWT with {@code vc}/{@code vp} wrapper claims (danubetech-style, NOT Gaia-X Danube!)</li>
 *   <li>{@link #UNKNOWN} — format not recognized; should be rejected with a diagnostic message</li>
 * </ul>
 */
public enum CredentialFormat {
  GAIAX_V2_LOIRE,
  VC2_DANUBETECH, // Note: Not Gaia-x! Don't confuse danubetech with the Gaia-X Danube release, which uses Loire-format JWTs (without wrapper claims)!
  UNKNOWN
}
