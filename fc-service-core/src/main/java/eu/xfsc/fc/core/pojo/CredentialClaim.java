package eu.xfsc.fc.core.pojo;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.jena.rdf.model.Statement;

/**
 * Subclass of {@link RdfClaim} representing a claim extracted from a Verifiable Credential.
 * Currently, it does not add any additional fields or methods, but serves as a semantic marker
 * for claims originating from credentials, allowing for future extensions specific to credential claims.
 */
public class CredentialClaim extends RdfClaim {

    public CredentialClaim(Statement triple, ObjectMapper objectMapper) {
        super(triple, objectMapper);
    }

    public CredentialClaim(String subject, String predicate, String object) {
        super(subject, predicate, object);
    }
}
