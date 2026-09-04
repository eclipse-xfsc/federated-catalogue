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
 * NotFoundException is an exception that can be thrown to customize the Not Found Error of the Federated
 * Catalogue server application.
 * Implementation of the {@link ServiceException} exception.
 */
public class NotFoundException extends ServiceException {
  /**
   * Constructs a new Not Found Exception with the specified detail message.
   *
   * @param message Detailed message about the thrown exception.
   */
  public NotFoundException(String message) {
    super(message);
  }
  
  public NotFoundException(String message, Throwable cause) {
    super(message, cause);
  }
  
}
