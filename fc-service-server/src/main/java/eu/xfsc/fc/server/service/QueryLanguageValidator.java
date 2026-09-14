package eu.xfsc.fc.server.service;

/*-
 * ---license-start
 * fc-service-server
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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import eu.xfsc.fc.api.generated.model.QueryLanguage;
import eu.xfsc.fc.core.exception.GraphStoreDisabledException;
import eu.xfsc.fc.core.exception.UnsupportedQueryLanguageException;
import eu.xfsc.fc.core.service.graphdb.GraphStore;

/**
 * Validates that the requested query language is supported by the active graph store backend.
 * Throws structured exceptions for unsupported languages or disabled graph stores.
 */
@Component
public class QueryLanguageValidator {

  @Autowired
  private GraphStore graphStore;

  /**
   * Validates that the requested query language matches the active backend's supported language.
   *
   * @param requested the query language requested by the client
   * @throws GraphStoreDisabledException if the graph store is disabled (dummy backend)
   * @throws UnsupportedQueryLanguageException if the requested language does not match the supported language
   */
  public void validateLanguageSupport(QueryLanguage requested) {
    QueryLanguage supported = graphStore.getSupportedQueryLanguage()
        .orElseThrow(() -> new GraphStoreDisabledException(
            "Graph database is disabled. Query functionality is not available."));
    if (supported != requested) {
      String backend = graphStore.getBackendType().name();
      QueryLanguageProperties props = QueryLanguageProperties.of(supported);
      String hint = "Please use " + props.displayName() + " queries with Content-Type: " + props.contentType();
      throw new UnsupportedQueryLanguageException(
          backend, supported.name(), requested.name(), props.contentType(), hint);
    }
  }
}
