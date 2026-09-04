package eu.xfsc.fc.core.service.graphdb;

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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import eu.xfsc.fc.api.generated.model.QueryLanguage;
import eu.xfsc.fc.core.pojo.RdfClaim;
import eu.xfsc.fc.core.pojo.GraphBackendType;
import eu.xfsc.fc.core.pojo.GraphQuery;
import eu.xfsc.fc.core.pojo.PaginatedResults;

//@Slf4j
@Component
public class DummyGraphStore implements GraphStore {

    @Override
    public void addClaims(List<RdfClaim> claimList, String credentialSubject) {
        // Dummy implementation
    }

    @Override
    public void deleteClaims(String credentialSubject) {
        // Dummy implementation
    }

    @Override
    public void deleteValidationResultClaims(String resultIri) {
        // Dummy implementation
    }

    @Override
    public PaginatedResults<Map<String, Object>> queryData(GraphQuery query) {
        // Dummy implementation
        return new PaginatedResults<>(Collections.emptyList());
    }

    @Override
    public Optional<QueryLanguage> getSupportedQueryLanguage() {
        return Optional.empty();
    }

    @Override
    public GraphBackendType getBackendType() {
        return GraphBackendType.NONE;
    }

    @Override
    public boolean isHealthy() {
        return true;
    }
}
