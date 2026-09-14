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
 * This exception is thrown whenever a claim is passed to the
 * graph store that has syntax errors w.r.t. its RDF
 * serialisation, e.g. broken URIs, invalid literals etc.
 */
public class QueryException extends ServiceException {
    
  public QueryException(String msg) {
    super(msg);
  }

  public QueryException(String message, Throwable cause) {
    super(message, cause);
  }

}
