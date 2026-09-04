package eu.xfsc.fc.core.util;

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

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Shared string prefixes used by content-based format detection heuristics.
 *
 * <p>For JWT detection use {@code VerificationConstants.JWT_PREFIX} directly — JWT is a
 * credential-envelope concern, not an RDF format heuristic, and lives in the verification
 * package as the single source of truth.</p>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FormatDetectionConstants {

  public static final String JSON_LD_PREFIX = "{";
  public static final String RDF_XML_PREFIX_1 = "<?xml";
  public static final String RDF_XML_PREFIX_2 = "<rdf:RDF";
  public static final String TURTLE_PREFIX_1 = "@prefix";
  public static final String TURTLE_PREFIX_2 = "@base";
}
