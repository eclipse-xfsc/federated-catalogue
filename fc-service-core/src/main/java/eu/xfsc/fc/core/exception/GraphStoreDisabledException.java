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
 * Exception thrown when query operations are attempted against a disabled graph store.
 */
public class GraphStoreDisabledException extends ServiceException {

  /**
   * Constructs a new GraphStoreDisabledException with the specified detail message.
   *
   * @param message Detailed message about the thrown exception.
   */
  public GraphStoreDisabledException(String message) {
    super(message);
  }
}
