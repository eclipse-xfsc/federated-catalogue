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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import eu.xfsc.fc.core.exception.QueryException;
import eu.xfsc.fc.core.pojo.RdfClaim;

/**
 * Regression tests for literal validation in {@link ClaimValidator#validateClaims(List)}.
 *
 * <p>Jena 6 stopped honouring {@code JenaParameters.enableEagerLiteralValidation} on the RIOT
 * parsing path: an ill-formed typed literal is only reported as a parser warning, and the
 * {@code DatatypeFormatException} surfaces later, when the value is read. {@code ClaimValidator}
 * compensates with an explicit post-parse check. These tests pin that behaviour so a future Jena
 * major cannot drop it silently — the rejection assertion is keyed to the message the explicit
 * check produces, not to any message RIOT emits on its own.
 */
class ClaimValidatorTest {

    private static final String XSD_INT = "<http://www.w3.org/2001/XMLSchema#int>";
    private static final String SUBJECT = "<http://example.org/claims/subject>";
    private static final String PREDICATE = "<http://example.org/claims/count>";

    /** Fragment of the message emitted by the explicit ill-formed-literal check. */
    private static final String ILL_FORMED_LITERAL_MSG = "is not valid for datatype";

    private final ClaimValidator claimValidator = new ClaimValidator();

    @Test
    @DisplayName("A typed literal with an ill-formed lexical form is rejected as a QueryException")
    void rejectsIllFormedTypedLiteral() {
        List<RdfClaim> claims = List.of(
                new RdfClaim(SUBJECT, PREDICATE, "\"Fourty two\"^^" + XSD_INT));

        QueryException ex = assertThrows(QueryException.class, () -> claimValidator.validateClaims(claims));

        assertTrue(ex.getMessage().contains(ILL_FORMED_LITERAL_MSG),
                "Expected the ill-formed-literal check to report the offending lexical form, but got: "
                        + ex.getMessage());
        assertTrue(ex.getMessage().contains("Fourty two"),
                "Expected the offending lexical form in the message, but got: " + ex.getMessage());
    }

    @Test
    @DisplayName("A typed literal with a well-formed lexical form is accepted")
    void acceptsWellFormedTypedLiteral() {
        List<RdfClaim> claims = List.of(
                new RdfClaim(SUBJECT, PREDICATE, "\"42\"^^" + XSD_INT));

        assertDoesNotThrow(() -> claimValidator.validateClaims(claims));
    }
}
