package eu.xfsc.fc.core.service.verification.claims;

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

import java.util.List;

import eu.xfsc.fc.core.pojo.RdfClaim;
import eu.xfsc.fc.core.pojo.ContentAccessor;

public interface ClaimExtractor {

    List<RdfClaim> extractClaims(ContentAccessor content) throws Exception;
    
}
