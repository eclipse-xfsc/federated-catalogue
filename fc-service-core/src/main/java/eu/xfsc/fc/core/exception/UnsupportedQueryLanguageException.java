package eu.xfsc.fc.core.exception;

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
 * Exception thrown when a query is submitted in a language not supported by the active graph database backend.
 * Carries structured error details for constructing an informative error response.
 */
public class UnsupportedQueryLanguageException extends ServiceException {

  private final String activeBackend;
  private final String supportedLanguage;
  private final String requestedLanguage;
  private final String expectedContentType;
  private final String hint;

  /**
   * Constructs a new UnsupportedQueryLanguageException with structured detail fields.
   *
   * @param activeBackend the name of the active graph database backend (e.g., "NEO4J")
   * @param supportedLanguage the query language supported by the active backend (e.g., "OPENCYPHER")
   * @param requestedLanguage the query language that was requested (e.g., "SPARQL")
   * @param expectedContentType the Content-Type expected for the supported language
   * @param hint an actionable hint for the user
   */
  public UnsupportedQueryLanguageException(String activeBackend, String supportedLanguage,
      String requestedLanguage, String expectedContentType, String hint) {
    super("Query language " + requestedLanguage + " is not supported by the active " + activeBackend
        + " backend. Supported language: " + supportedLanguage
        + ". Expected Content-Type: " + expectedContentType
        + ". Hint: " + hint);
    this.activeBackend = activeBackend;
    this.supportedLanguage = supportedLanguage;
    this.requestedLanguage = requestedLanguage;
    this.expectedContentType = expectedContentType;
    this.hint = hint;
  }

  public String getActiveBackend() {
    return activeBackend;
  }

  public String getSupportedLanguage() {
    return supportedLanguage;
  }

  public String getRequestedLanguage() {
    return requestedLanguage;
  }

  public String getExpectedContentType() {
    return expectedContentType;
  }

  public String getHint() {
    return hint;
  }
}
